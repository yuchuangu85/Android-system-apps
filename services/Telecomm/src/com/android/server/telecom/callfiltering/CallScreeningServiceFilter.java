/*
 * Copyright (C) 2016 The Android Open Source Project
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
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.CallLog;
import android.provider.Settings;
import android.telecom.Log;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.text.TextUtils;

import com.android.internal.telecom.ICallScreeningAdapter;
import com.android.internal.telecom.ICallScreeningService;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallScreeningServiceHelper;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.LogUtils;
import com.android.server.telecom.ParcelableCallUtils;
import com.android.server.telecom.PhoneAccountRegistrar;
import com.android.server.telecom.TelecomServiceImpl.SettingsSecureAdapter;
import com.android.server.telecom.TelecomSystem;

/**
 * Binds to {@link ICallScreeningService} to allow call blocking. A single instance of this class
 * handles a single call.
 */
public class CallScreeningServiceFilter {

    public static final int CALL_SCREENING_FILTER_TYPE_USER_SELECTED = 1;
    public static final int CALL_SCREENING_FILTER_TYPE_DEFAULT_DIALER = 2;
    public static final int CALL_SCREENING_FILTER_TYPE_SYSTEM_DIALER = 3;
    public static final int CALL_SCREENING_FILTER_TYPE_CARRIER = 4;

    public interface CallScreeningFilterResultCallback {
        void onCallScreeningFilterComplete(Call call, CallFilteringResult result, String
                packageName);
    }

    private class CallScreeningServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.startSession("CSCR.oSC");
            try {
                synchronized (mTelecomLock) {
                    Log.addEvent(mCall, LogUtils.Events.SCREENING_BOUND, componentName);
                    if (!mHasFinished) {
                        onServiceBound(ICallScreeningService.Stub.asInterface(service));
                    }
                }
            } finally {
                Log.endSession();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.startSession("CSCR.oSD");
            try {
                synchronized (mTelecomLock) {
                    finishCallScreening();
                }
            } finally {
                Log.endSession();
            }
        }
    }

    private class CallScreeningAdapter extends ICallScreeningAdapter.Stub {
        @Override
        public void allowCall(String callId) {
            Log.startSession("CSCR.aC");
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mTelecomLock) {
                    Log.d(this, "allowCall(%s)", callId);
                    if (mCall != null && mCall.getId().equals(callId)) {
                        mResult = new CallFilteringResult(
                                true, // shouldAllowCall
                                false, //shouldReject
                                false, //shouldSilence
                                true, //shouldAddToCallLog
                                true // shouldShowNotification
                        );
                    } else {
                        Log.w(this, "allowCall, unknown call id: %s", callId);
                    }
                    finishCallScreening();
                }
            } finally {
                Binder.restoreCallingIdentity(token);
                Log.endSession();
            }
        }

        @Override
        public void disallowCall(
                String callId,
                boolean shouldReject,
                boolean shouldAddToCallLog,
                boolean shouldShowNotification,
                ComponentName componentName) {
            Log.startSession("CSCR.dC");
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mTelecomLock) {
                    boolean isServiceRequestingLogging = isLoggable(componentName,
                            shouldAddToCallLog);
                    Log.i(this, "disallowCall(%s), shouldReject: %b, shouldAddToCallLog: %b, "
                                    + "shouldShowNotification: %b", callId, shouldReject,
                            isServiceRequestingLogging, shouldShowNotification);
                    if (mCall != null && mCall.getId().equals(callId)) {
                        mResult = new CallFilteringResult(
                                false, // shouldAllowCall
                                shouldReject, //shouldReject
                                false, // shouldSilenceCall
                                isServiceRequestingLogging, //shouldAddToCallLog
                                shouldShowNotification, // shouldShowNotification
                                CallLog.Calls.BLOCK_REASON_CALL_SCREENING_SERVICE, //callBlockReason
                                mAppName, //callScreeningAppName
                                componentName.flattenToString() //callScreeningComponentName
                        );
                    } else {
                        Log.w(this, "disallowCall, unknown call id: %s", callId);
                    }
                    finishCallScreening();
                }
            } finally {
                Binder.restoreCallingIdentity(token);
                Log.endSession();
            }
        }

        @Override
        public void silenceCall(String callId) {
            Log.startSession("CSCR.sC");
            long token = Binder.clearCallingIdentity();
            try {
                synchronized (mTelecomLock) {
                    Log.d(this, "silenceCall(%s)", callId);
                    if (mCall != null && mCall.getId().equals(callId)) {
                        mResult = new CallFilteringResult(
                                true, // shouldAllowCall
                                false, //shouldReject
                                true, //shouldSilence
                                true, //shouldAddToCallLog
                                true // shouldShowNotification
                        );
                    } else {
                        Log.w(this, "silenceCall, unknown call id: %s", callId);
                    }
                    finishCallScreening();
                }
            } finally {
                Binder.restoreCallingIdentity(token);
                Log.endSession();
            }
        }
    }

    private final Context mContext;
    private final CallsManager mCallsManager;
    private final ParcelableCallUtils.Converter mParcelableCallUtilsConverter;
    private final TelecomSystem.SyncRoot mTelecomLock;
    private final SettingsSecureAdapter mSettingsSecureAdapter;

    private Call mCall;
    private CallScreeningFilterResultCallback mCallback;
    private ICallScreeningService mService;
    private ServiceConnection mConnection;
    private String mPackageName;
    private CharSequence mAppName;
    private boolean mHasFinished = false;
    private int mCallScreeningServiceType;

    private CallFilteringResult mResult = new CallFilteringResult(
            true, // shouldAllowCall
            false, //shouldReject
            true, //shouldAddToCallLog
            true // shouldShowNotification
    );

    public CallScreeningServiceFilter(
            Context context,
            CallsManager callsManager,
            PhoneAccountRegistrar phoneAccountRegistrar,
            ParcelableCallUtils.Converter parcelableCallUtilsConverter,
            TelecomSystem.SyncRoot lock,
            SettingsSecureAdapter settingsSecureAdapter) {
        mContext = context;
        mCallsManager = callsManager;
        mParcelableCallUtilsConverter = parcelableCallUtilsConverter;
        mTelecomLock = lock;
        mSettingsSecureAdapter = settingsSecureAdapter;
    }

    public void startCallScreeningFilter(Call call,
            CallScreeningFilterResultCallback callback,
            String packageName,
            CharSequence appName,
            int callScreeningServiceType) {
        if (mHasFinished) {
            Log.w(this, "Attempting to reuse CallScreeningServiceFilter. Ignoring.");
            return;
        }
        Log.addEvent(call, LogUtils.Events.SCREENING_SENT, packageName);
        mCall = call;
        mCallback = callback;
        mPackageName = packageName;
        mAppName = appName;
        mCallScreeningServiceType = callScreeningServiceType;

        mConnection = new CallScreeningServiceConnection();
        if (!CallScreeningServiceHelper.bindCallScreeningService(mContext,
                mCallsManager.getCurrentUserHandle(),
                mPackageName,
                mConnection)) {
            Log.i(this, "Could not bind to call screening service");
            finishCallScreening();
        }
    }

    private void finishCallScreening() {
        if (!mHasFinished) {
            Log.addEvent(mCall, LogUtils.Events.SCREENING_COMPLETED, mResult);
            mCallback.onCallScreeningFilterComplete(mCall, mResult, mPackageName);

            if (mConnection != null) {
                // We still need to call unbind even if the service disconnected.
                try {
                    mContext.unbindService(mConnection);
                } catch (IllegalArgumentException ie) {
                    Log.e(this, ie, "Unbind error");
                }
                mConnection = null;
            }
            mService = null;
            mHasFinished = true;
        }
    }

    private void onServiceBound(ICallScreeningService service) {
        mService = service;
        try {
            boolean isSystemDialer =
                    mCallScreeningServiceType
                            == CallScreeningServiceFilter.CALL_SCREENING_FILTER_TYPE_SYSTEM_DIALER;
            // Important: Only send a minimal subset of the call to the screening service.
            // We will send some of the call extras to the call screening service which the system
            // dialer implements.
            mService.screenCall(new CallScreeningAdapter(),
                    mParcelableCallUtilsConverter.toParcelableCallForScreening(mCall,
                            isSystemDialer));
        } catch (RemoteException e) {
            Log.e(this, e, "Failed to set the call screening adapter.");
            finishCallScreening();
        }
    }

    private boolean isLoggable(ComponentName componentName, boolean shouldAddToCallLog) {
        if (isCarrierCallScreeningApp(componentName)) {
            return shouldAddToCallLog;
        } else if (isDefaultDialer(componentName) || isUserChosenCallScreeningApp(componentName)) {
            return true;
        }

        return shouldAddToCallLog;
    }

    private boolean isCarrierCallScreeningApp(ComponentName componentName) {
        String carrierCallScreeningApp = null;
        CarrierConfigManager configManager = (CarrierConfigManager) mContext
                .getSystemService(Context.CARRIER_CONFIG_SERVICE);
        PersistableBundle configBundle = configManager.getConfig();
        if (configBundle != null) {
            carrierCallScreeningApp = configBundle
                    .getString(CarrierConfigManager.KEY_CARRIER_CALL_SCREENING_APP_STRING);
        }

        if (!TextUtils.isEmpty(carrierCallScreeningApp) && carrierCallScreeningApp
                .equals(componentName.flattenToString())) {
            return true;
        }

        return false;
    }

    private boolean isDefaultDialer(ComponentName componentName) {
        String defaultDialer = TelecomManager.from(mContext).getDefaultDialerPackage();

        if (!TextUtils.isEmpty(defaultDialer) && defaultDialer
                .equals(componentName.getPackageName())) {
            return true;
        }

        return false;
    }

    private boolean isUserChosenCallScreeningApp(ComponentName componentName) {
        String defaultCallScreeningApplication = mSettingsSecureAdapter
                .getStringForUser(mContext.getContentResolver(),
                        Settings.Secure.CALL_SCREENING_DEFAULT_COMPONENT, UserHandle.USER_CURRENT);

        if (!TextUtils.isEmpty(defaultCallScreeningApplication) && defaultCallScreeningApplication
                .equals(componentName.flattenToString())) {
            return true;
        }

        return false;
    }
}
