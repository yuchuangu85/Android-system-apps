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

package com.android.server.telecom.callredirection;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.telecom.CallRedirectionService;
import android.telecom.GatewayInfo;
import android.telecom.Log;
import android.telecom.Logging.Runnable;
import android.telecom.PhoneAccountHandle;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telecom.ICallRedirectionAdapter;
import com.android.internal.telecom.ICallRedirectionService;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.LogUtils;
import com.android.server.telecom.PhoneAccountRegistrar;
import com.android.server.telecom.TelecomSystem;
import com.android.server.telecom.Timeouts;

/**
 * A single instance of call redirection processor that handles the call redirection with
 * user-defined {@link CallRedirectionService} and carrier {@link CallRedirectionService} for a
 * single call.
 *
 * A user-defined call redirection will be performed firstly and a carrier call redirection will be
 * performed after that; there will be a total of two call redirection cycles.
 *
 * A call redirection cycle is a cycle:
 * 1) Telecom requests a call redirection of a call with a specific {@link CallRedirectionService},
 * 2) Telecom receives the response either from a specific {@link CallRedirectionService} or from
 * the timeout.
 *
 * Telecom should return to {@link CallsManager} at the end of current call redirection
 * cycle, if
 * 1) {@link CallRedirectionService} sends {@link CallRedirectionService#cancelCall()} response
 * before timeout;
 * or 2) Telecom finishes call redirection with carrier {@link CallRedirectionService}.
 */
public class CallRedirectionProcessor implements CallRedirectionCallback {

    private class CallRedirectionAttempt {
        private final ComponentName mComponentName;
        private final String mServiceType;
        private ServiceConnection mConnection;
        private ICallRedirectionService mService;

        private CallRedirectionAttempt(ComponentName componentName, String serviceType) {
            mComponentName = componentName;
            mServiceType = serviceType;
        }

        private void process() {
            Intent intent = new Intent(CallRedirectionService.SERVICE_INTERFACE)
                    .setComponent(mComponentName);
            ServiceConnection connection = new CallRedirectionServiceConnection();
            if (mContext.bindServiceAsUser(
                    intent,
                    connection,
                    Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE
                    | Context.BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS,
                    UserHandle.CURRENT)) {
                Log.d(this, "bindService, found " + mServiceType + " call redirection service,"
                        + " waiting for it to connect");
                mConnection = connection;
            }
        }

        private void onServiceBound(ICallRedirectionService service) {
            mService = service;
            try {
                // Telecom does not perform user interactions for carrier call redirection.
                mService.placeCall(new CallRedirectionAdapter(), mProcessedDestinationUri,
                        mPhoneAccountHandle, mAllowInteractiveResponse
                                && mServiceType.equals(SERVICE_TYPE_USER_DEFINED));
                Log.addEvent(mCall, mServiceType.equals(SERVICE_TYPE_USER_DEFINED)
                        ? LogUtils.Events.REDIRECTION_SENT_USER
                        : LogUtils.Events.REDIRECTION_SENT_CARRIER, mComponentName);
                Log.d(this, "Requested placeCall with [Destination Uri] "
                        + Log.pii(mProcessedDestinationUri)
                        + " [phoneAccountHandle]" + mPhoneAccountHandle);
            } catch (RemoteException e) {
                Log.e(this, e, "Failed to request with the found " + mServiceType + " call"
                        + " redirection service");
                finishCallRedirection();
            }
        }

        private void finishCallRedirection() {
            if (((mServiceType.equals(SERVICE_TYPE_CARRIER)) && mIsCarrierRedirectionPending)
                || ((mServiceType.equals(SERVICE_TYPE_USER_DEFINED))
                    && mIsUserDefinedRedirectionPending)) {
                if (mConnection != null) {
                    // We still need to call unbind even if the service disconnected.
                    mContext.unbindService(mConnection);
                    mConnection = null;
                }
                mService = null;
                onCallRedirectionComplete(mCall);
            }
        }

        private class CallRedirectionServiceConnection implements ServiceConnection {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder service) {
                Log.startSession("CRSC.oSC");
                try {
                    synchronized (mTelecomLock) {
                        Log.addEvent(mCall, mServiceType.equals(SERVICE_TYPE_USER_DEFINED)
                                ? LogUtils.Events.REDIRECTION_BOUND_USER
                                : LogUtils.Events.REDIRECTION_BOUND_CARRIER, componentName);
                        onServiceBound(ICallRedirectionService.Stub.asInterface(service));
                    }
                } finally {
                    Log.endSession();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                Log.startSession("CRSC.oSD");
                try {
                    synchronized (mTelecomLock) {
                        finishCallRedirection();
                    }
                } finally {
                    Log.endSession();
                }
            }
        }

        private class CallRedirectionAdapter extends ICallRedirectionAdapter.Stub {
            @Override
            public void cancelCall() {
                Log.startSession("CRA.cC");
                long token = Binder.clearCallingIdentity();
                try {
                    synchronized (mTelecomLock) {
                        Log.d(this, "Received cancelCall from " +  mServiceType + " call"
                                + " redirection service");
                        mShouldCancelCall = true;
                        finishCallRedirection();
                    }
                } finally {
                    Binder.restoreCallingIdentity(token);
                    Log.endSession();
                }
            }

            @Override
            public void placeCallUnmodified() {
                Log.startSession("CRA.pCU");
                long token = Binder.clearCallingIdentity();
                try {
                    synchronized (mTelecomLock) {
                        Log.d(this, "Received placeCallUnmodified from " +  mServiceType + " call"
                                + " redirection service");
                        finishCallRedirection();
                    }
                } finally {
                    Binder.restoreCallingIdentity(token);
                    Log.endSession();
                }
            }

            @Override
            public void redirectCall(Uri gatewayUri, PhoneAccountHandle targetPhoneAccount,
                                     boolean confirmFirst) {
                Log.startSession("CRA.rC");
                long token = Binder.clearCallingIdentity();
                try {
                    synchronized (mTelecomLock) {
                        mRedirectionGatewayInfo = mCallRedirectionProcessorHelper
                                .getGatewayInfoFromGatewayUri(mComponentName.getPackageName(),
                                        gatewayUri, mDestinationUri);
                        mPhoneAccountHandle = targetPhoneAccount;
                        // If carrier redirects call, we should skip to notify users about
                        // the user-defined call redirection service.
                        mUiAction = (confirmFirst && mServiceType.equals(SERVICE_TYPE_USER_DEFINED)
                                && mAllowInteractiveResponse)
                                ? UI_TYPE_USER_DEFINED_ASK_FOR_CONFIRM : UI_TYPE_NO_ACTION;
                        Log.d(this, "Received redirectCall with [gatewayUri]"
                                + Log.pii(gatewayUri) + " [phoneAccountHandle]"
                                + mPhoneAccountHandle + "[confirmFirst]" + confirmFirst + " from "
                                + mServiceType + " call redirection service");
                        finishCallRedirection();
                    }
                } finally {
                    Binder.restoreCallingIdentity(token);
                    Log.endSession();
                }
            }
        }
    }

    private final Context mContext;
    private final CallsManager mCallsManager;
    private final Call mCall;
    private final boolean mAllowInteractiveResponse;
    private GatewayInfo mRedirectionGatewayInfo;
    private final boolean mSpeakerphoneOn;
    private final int mVideoState;
    private final Timeouts.Adapter mTimeoutsAdapter;
    private final TelecomSystem.SyncRoot mTelecomLock;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private CallRedirectionAttempt mAttempt;
    private CallRedirectionProcessorHelper mCallRedirectionProcessorHelper;

    public static final String SERVICE_TYPE_CARRIER = "carrier";
    public static final String SERVICE_TYPE_USER_DEFINED = "user_defined";
    public static final String UI_TYPE_NO_ACTION = "no_action";
    public static final String UI_TYPE_USER_DEFINED_TIMEOUT = "user_defined_timeout";
    public static final String UI_TYPE_USER_DEFINED_ASK_FOR_CONFIRM
            = "user_defined_ask_for_confirm";

    private PhoneAccountHandle mPhoneAccountHandle;
    private Uri mDestinationUri;
    /**
     * Try to send the implemented service with processed destination uri by formatting it to E.164
     * and removing post dial digits.
     */
    private Uri mProcessedDestinationUri;

    /**
     * Indicates if Telecom should cancel the call when the whole call redirection finishes.
     */
    private boolean mShouldCancelCall = false;
    /**
     * Indicates Telecom should handle different types of UI if need.
     */
    private String mUiAction = UI_TYPE_NO_ACTION;
    /**
     * Indicates if Telecom is waiting for a callback from a user-defined
     * {@link CallRedirectionService}.
     */
    private boolean mIsUserDefinedRedirectionPending = false;
    /**
     * Indicates if Telecom is waiting for a callback from a carrier
     * {@link CallRedirectionService}.
     */
    private boolean mIsCarrierRedirectionPending = false;

    public CallRedirectionProcessor(
            Context context,
            CallsManager callsManager,
            Call call,
            Uri handle,
            PhoneAccountRegistrar phoneAccountRegistrar,
            GatewayInfo gatewayInfo,
            boolean speakerphoneOn,
            int videoState) {
        mContext = context;
        mCallsManager = callsManager;
        mCall = call;
        mDestinationUri = handle;
        mPhoneAccountHandle = call.getTargetPhoneAccount();
        mRedirectionGatewayInfo = gatewayInfo;
        mSpeakerphoneOn = speakerphoneOn;
        mVideoState = videoState;
        mTimeoutsAdapter = callsManager.getTimeoutsAdapter();
        mTelecomLock = callsManager.getLock();
        /**
         * The current rule to decide whether the implemented {@link CallRedirectionService} should
         * allow interactive responses with users is only based on whether it is in car mode.
         */
        mAllowInteractiveResponse = !callsManager.getSystemStateHelper().isCarMode();
        mCallRedirectionProcessorHelper = new CallRedirectionProcessorHelper(
                context, callsManager, phoneAccountRegistrar);
        mProcessedDestinationUri = mCallRedirectionProcessorHelper.formatNumberForRedirection(
                mDestinationUri);
    }

    @Override
    public void onCallRedirectionComplete(Call call) {
        // synchronized on mTelecomLock to enter into Telecom.
        mHandler.post(new Runnable("CRP.oCRC", mTelecomLock) {
            @Override
            public void loggedRun() {
                if (mIsUserDefinedRedirectionPending) {
                    Log.addEvent(mCall, LogUtils.Events.REDIRECTION_COMPLETED_USER);
                    mIsUserDefinedRedirectionPending = false;
                    if (mShouldCancelCall) {
                        mCallsManager.onCallRedirectionComplete(mCall, mDestinationUri,
                                mPhoneAccountHandle, mRedirectionGatewayInfo, mSpeakerphoneOn,
                                mVideoState, mShouldCancelCall, mUiAction);
                    } else {
                        performCarrierCallRedirection();
                    }
                }
                if (mIsCarrierRedirectionPending) {
                    Log.addEvent(mCall, LogUtils.Events.REDIRECTION_COMPLETED_CARRIER);
                    mIsCarrierRedirectionPending = false;
                    mCallsManager.onCallRedirectionComplete(mCall, mDestinationUri,
                            mPhoneAccountHandle, mRedirectionGatewayInfo, mSpeakerphoneOn,
                            mVideoState, mShouldCancelCall, mUiAction);
                }
            }
        }.prepare());
    }

    /**
     * The entry to perform call redirection of the call from (@link CallsManager)
     */
    public void performCallRedirection() {
        // If the Gateway Info is set with intent, only request with carrier call redirection.
        if (mRedirectionGatewayInfo != null) {
            performCarrierCallRedirection();
        } else {
            performUserDefinedCallRedirection();
        }
    }

    private void performUserDefinedCallRedirection() {
        Log.d(this, "performUserDefinedCallRedirection");
        ComponentName componentName =
                mCallRedirectionProcessorHelper.getUserDefinedCallRedirectionService();
        if (componentName != null) {
            mAttempt = new CallRedirectionAttempt(componentName, SERVICE_TYPE_USER_DEFINED);
            mAttempt.process();
            mIsUserDefinedRedirectionPending = true;
            processTimeoutForCallRedirection(SERVICE_TYPE_USER_DEFINED);
        } else {
            Log.i(this, "There are no user-defined call redirection services installed on this"
                    + " device.");
            performCarrierCallRedirection();
        }
    }

    private void performCarrierCallRedirection() {
        Log.d(this, "performCarrierCallRedirection");
        ComponentName componentName =
                mCallRedirectionProcessorHelper.getCarrierCallRedirectionService(
                        mPhoneAccountHandle);
        if (componentName != null) {
            mAttempt = new CallRedirectionAttempt(componentName, SERVICE_TYPE_CARRIER);
            mAttempt.process();
            mIsCarrierRedirectionPending = true;
            processTimeoutForCallRedirection(SERVICE_TYPE_CARRIER);
        } else {
            Log.i(this, "There are no carrier call redirection services installed on this"
                    + " device.");
            mCallsManager.onCallRedirectionComplete(mCall, mDestinationUri,
                    mPhoneAccountHandle, mRedirectionGatewayInfo, mSpeakerphoneOn, mVideoState,
                    mShouldCancelCall, mUiAction);
        }
    }

    private void processTimeoutForCallRedirection(String serviceType) {
        long timeout = serviceType.equals(SERVICE_TYPE_USER_DEFINED) ?
            mTimeoutsAdapter.getUserDefinedCallRedirectionTimeoutMillis(
                mContext.getContentResolver()) : mTimeoutsAdapter
            .getCarrierCallRedirectionTimeoutMillis(mContext.getContentResolver());

        mHandler.postDelayed(new Runnable("CRP.pTFCR", null) {
            @Override
            public void loggedRun() {
                boolean isCurrentRedirectionPending =
                        serviceType.equals(SERVICE_TYPE_USER_DEFINED) ?
                                mIsUserDefinedRedirectionPending : mIsCarrierRedirectionPending;
                if (isCurrentRedirectionPending) {
                    Log.i(this, serviceType + " call redirection has timed out.");
                    Log.addEvent(mCall, serviceType.equals(SERVICE_TYPE_USER_DEFINED)
                            ? LogUtils.Events.REDIRECTION_TIMED_OUT_USER
                            : LogUtils.Events.REDIRECTION_TIMED_OUT_CARRIER);
                    if (serviceType.equals(SERVICE_TYPE_USER_DEFINED)) {
                        mUiAction = UI_TYPE_USER_DEFINED_TIMEOUT;
                        mShouldCancelCall = true;
                    }
                    onCallRedirectionComplete(mCall);
                }
            }
        }.prepare(), timeout);
    }

    /**
     * Checks if Telecom can make call redirection with any available call redirection service.
     *
     * @return {@code true} if it can; {@code false} otherwise.
     */
    public boolean canMakeCallRedirectionWithService() {
        boolean canMakeCallRedirectionWithService =
                mCallRedirectionProcessorHelper.getUserDefinedCallRedirectionService() != null
                        || mCallRedirectionProcessorHelper.getCarrierCallRedirectionService(
                                mPhoneAccountHandle) != null;
        Log.i(this, "Can make call redirection with any available service: "
                + canMakeCallRedirectionWithService);
        return canMakeCallRedirectionWithService;
    }

    /**
     * Returns the handler, for testing purposes.
     */
    @VisibleForTesting
    public Handler getHandler() {
        return mHandler;
    }

    /**
     * Set CallRedirectionProcessorHelper for testing purposes.
     */
    @VisibleForTesting
    public void setCallRedirectionServiceHelper(
            CallRedirectionProcessorHelper callRedirectionProcessorHelper) {
        mCallRedirectionProcessorHelper = callRedirectionProcessorHelper;
    }
}
