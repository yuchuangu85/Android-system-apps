/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.server.telecom.callfiltering;

import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.provider.CallLog;
import android.telecom.Log;
import android.telecom.Logging.Runnable;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.text.TextUtils;

import com.android.internal.telephony.CallerInfo;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallScreeningServiceHelper;
import com.android.server.telecom.CallerInfoLookupHelper;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.LogUtils;
import com.android.server.telecom.ParcelableCallUtils;
import com.android.server.telecom.PhoneAccountRegistrar;
import com.android.server.telecom.TelecomServiceImpl;
import com.android.server.telecom.TelecomSystem;

/**
 * This class supports binding to the various {@link android.telecom.CallScreeningService}:
 * carrier, default dialer and user chosen. Carrier's CallScreeningService implementation will be
 * bound first, and then default dialer's and user chosen's. If Carrier's CallScreeningService
 * blocks a call, no further CallScreeningService after it will be bound.
 */
public class CallScreeningServiceController implements IncomingCallFilter.CallFilter,
        CallScreeningServiceFilter.CallScreeningFilterResultCallback {

    private final Context mContext;
    private final CallsManager mCallsManager;
    private final PhoneAccountRegistrar mPhoneAccountRegistrar;
    private final ParcelableCallUtils.Converter mParcelableCallUtilsConverter;
    private final TelecomSystem.SyncRoot mTelecomLock;
    private final TelecomServiceImpl.SettingsSecureAdapter mSettingsSecureAdapter;
    private final CallerInfoLookupHelper mCallerInfoLookupHelper;
    private final CallScreeningServiceHelper.AppLabelProxy mAppLabelProxy;

    private final int CARRIER_CALL_FILTERING_TIMED_OUT = 2000; // 2 seconds
    private final int CALL_FILTERING_TIMED_OUT = 4500; // 4.5 seconds

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private Call mCall;
    private CallFilterResultCallback mCallback;

    private CallFilteringResult mResult = new CallFilteringResult(
            true, // shouldAllowCall
            false, // shouldReject
            true, // shouldAddToCallLog
            true // shouldShowNotification
    );

    private boolean mIsFinished;
    private boolean mIsCarrierFinished;
    private boolean mIsDefaultDialerFinished;
    private boolean mIsUserChosenFinished;

    public CallScreeningServiceController(
            Context context,
            CallsManager callsManager,
            PhoneAccountRegistrar phoneAccountRegistrar,
            ParcelableCallUtils.Converter parcelableCallUtilsConverter,
            TelecomSystem.SyncRoot lock,
            TelecomServiceImpl.SettingsSecureAdapter settingsSecureAdapter,
            CallerInfoLookupHelper callerInfoLookupHelper,
            CallScreeningServiceHelper.AppLabelProxy appLabelProxy) {
        mContext = context;
        mCallsManager = callsManager;
        mPhoneAccountRegistrar = phoneAccountRegistrar;
        mParcelableCallUtilsConverter = parcelableCallUtilsConverter;
        mTelecomLock = lock;
        mSettingsSecureAdapter = settingsSecureAdapter;
        mCallerInfoLookupHelper = callerInfoLookupHelper;
        mAppLabelProxy = appLabelProxy;
    }

    @Override
    public void startFilterLookup(Call call, CallFilterResultCallback callBack) {
        mCall = call;
        mCallback = callBack;
        mIsFinished = false;
        mIsCarrierFinished = false;
        mIsDefaultDialerFinished = false;
        mIsUserChosenFinished = false;

        bindCarrierService();

        // Call screening filtering timed out
        mHandler.postDelayed(new Runnable("ICF.pFTO", mTelecomLock) {
            @Override
            public void loggedRun() {
                if (!mIsFinished) {
                    Log.i(CallScreeningServiceController.this, "Call screening has timed out.");
                    finishCallScreening();
                }
            }
        }.prepare(), CALL_FILTERING_TIMED_OUT);
    }

    @Override
    public void onCallScreeningFilterComplete(Call call, CallFilteringResult result,
            String packageName) {
        synchronized (mTelecomLock) {
            mResult = result.combine(mResult);
            if (!TextUtils.isEmpty(packageName) && packageName.equals(getCarrierPackageName())) {
                mIsCarrierFinished = true;
                if (result.mCallBlockReason == CallLog.Calls.BLOCK_REASON_CALL_SCREENING_SERVICE) {
                    finishCallScreening();
                } else {
                    checkContactExistsAndBindService();
                }
            } else if (!TextUtils.isEmpty(packageName) &&
                    packageName.equals(getDefaultDialerPackageName())) {
                // Default dialer defined CallScreeningService cannot skip the call log.
                mResult.shouldAddToCallLog = true;
                mIsDefaultDialerFinished = true;
                if (result.mCallBlockReason == CallLog.Calls.BLOCK_REASON_CALL_SCREENING_SERVICE ||
                        mIsUserChosenFinished) {
                    finishCallScreening();
                }
            } else if (!TextUtils.isEmpty(packageName) &&
                    packageName.equals(getUserChosenPackageName())) {
                // User defined CallScreeningService cannot skip the call log.
                mResult.shouldAddToCallLog = true;
                mIsUserChosenFinished = true;
                if (mIsDefaultDialerFinished) {
                    finishCallScreening();
                }
            }
        }
    }

    private void bindCarrierService() {
        String carrierPackageName = getCarrierPackageName();
        if (TextUtils.isEmpty(carrierPackageName)) {
            mIsCarrierFinished = true;
            bindDefaultDialerAndUserChosenService();
        } else {
            createCallScreeningServiceFilter().startCallScreeningFilter(mCall, this,
                    carrierPackageName, mAppLabelProxy.getAppLabel(carrierPackageName),
                    CallScreeningServiceFilter.CALL_SCREENING_FILTER_TYPE_CARRIER);
        }

        // Carrier filtering timed out
        mHandler.postDelayed(new Runnable("ICF.pFTO", mTelecomLock) {
            @Override
            public void loggedRun() {
                if (!mIsCarrierFinished) {
                    mIsCarrierFinished = true;
                    checkContactExistsAndBindService();
                }
            }
        }.prepare(), CARRIER_CALL_FILTERING_TIMED_OUT);
    }

    private void bindDefaultDialerAndUserChosenService() {
        if (mIsCarrierFinished) {
            String dialerPackageName = getDefaultDialerPackageName();
            String systemDialerPackageName = getSystemDialerPackageName();
            if (TextUtils.isEmpty(dialerPackageName)) {
                mIsDefaultDialerFinished = true;
            } else {
                int dialerType = dialerPackageName.equals(systemDialerPackageName) ?
                        CallScreeningServiceFilter.CALL_SCREENING_FILTER_TYPE_SYSTEM_DIALER :
                        CallScreeningServiceFilter.CALL_SCREENING_FILTER_TYPE_DEFAULT_DIALER;
                createCallScreeningServiceFilter().startCallScreeningFilter(mCall,
                        CallScreeningServiceController.this, dialerPackageName,
                        mAppLabelProxy.getAppLabel(dialerPackageName), dialerType);
            }

            String userChosenPackageName = getUserChosenPackageName();
            if (TextUtils.isEmpty(userChosenPackageName)) {
                mIsUserChosenFinished = true;
            } else {
                createCallScreeningServiceFilter().startCallScreeningFilter(mCall,
                        CallScreeningServiceController.this, userChosenPackageName,
                        mAppLabelProxy.getAppLabel(userChosenPackageName),
                        CallScreeningServiceFilter.CALL_SCREENING_FILTER_TYPE_USER_SELECTED);
            }

            if (mIsDefaultDialerFinished && mIsUserChosenFinished) {
                finishCallScreening();
            }
        }
    }

    private CallScreeningServiceFilter createCallScreeningServiceFilter() {
        return new CallScreeningServiceFilter(
                mContext,
                mCallsManager,
                mPhoneAccountRegistrar,
                mParcelableCallUtilsConverter,
                mTelecomLock,
                mSettingsSecureAdapter);
    }

    private void checkContactExistsAndBindService() {
        mCallerInfoLookupHelper.startLookup(mCall.getHandle(),
                new CallerInfoLookupHelper.OnQueryCompleteListener() {
                    @Override
                    public void onCallerInfoQueryComplete(Uri handle, CallerInfo info) {
                        boolean contactExists = info != null && info.contactExists;
                        Log.i(CallScreeningServiceController.this, "Contact exists: " +
                                contactExists);
                        if (!contactExists) {
                            bindDefaultDialerAndUserChosenService();
                        } else {
                            finishCallScreening();
                        }
                    }

                    @Override
                    public void onContactPhotoQueryComplete(Uri handle, CallerInfo
                            info) {
                        // ignore
                    }
                });
    }

    private void finishCallScreening() {
        Log.addEvent(mCall, LogUtils.Events.CONTROLLER_SCREENING_COMPLETED, mResult);
        mCallback.onCallFilteringComplete(mCall, mResult);
        mIsFinished = true;
    }

    private String getCarrierPackageName() {
        ComponentName componentName = null;
        CarrierConfigManager configManager = (CarrierConfigManager) mContext.getSystemService
                (Context.CARRIER_CONFIG_SERVICE);
        PersistableBundle configBundle = configManager.getConfig();
        if (configBundle != null) {
            componentName = ComponentName.unflattenFromString(configBundle.getString
                    (CarrierConfigManager.KEY_CARRIER_CALL_SCREENING_APP_STRING, ""));
        }

        return componentName != null ? componentName.getPackageName() : null;
    }

    private String getDefaultDialerPackageName() {
        return TelecomManager.from(mContext).getDefaultDialerPackage();
    }

    private String getSystemDialerPackageName() {
        return TelecomManager.from(mContext).getSystemDialerPackage();
    }

    private String getUserChosenPackageName() {
        return mCallsManager.getRoleManagerAdapter().getDefaultCallScreeningApp();
    }
}
