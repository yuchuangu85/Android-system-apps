/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License.
 */
package com.android.car.dialer.telecom;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadsetClient;
import android.bluetooth.BluetoothHeadsetClientCall;
import android.bluetooth.BluetoothProfile;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.CallAudioState.CallAudioRoute;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.widget.Toast;

import com.android.car.dialer.R;
import com.android.car.dialer.log.L;
import com.android.car.telephony.common.TelecomUtils;
import com.android.internal.annotations.VisibleForTesting;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import com.google.i18n.phonenumbers.PhoneNumberUtil.ValidationResult;
import com.google.i18n.phonenumbers.Phonenumber;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The entry point for all interactions between UI and telecom.
 */
public class UiCallManager {
    private static String TAG = "CD.TelecomMgr";

    @VisibleForTesting
    static final String HFP_CLIENT_CONNECTION_SERVICE_CLASS_NAME
            = "com.android.bluetooth.hfpclient.connserv.HfpClientConnectionService";
    private static UiCallManager sUiCallManager;

    private Context mContext;

    private TelecomManager mTelecomManager;
    private InCallServiceImpl mInCallService;
    private BluetoothHeadsetClient mBluetoothHeadsetClient;

    /**
     * Initialized a globally accessible {@link UiCallManager} which can be retrieved by
     * {@link #get}. If this function is called a second time before calling {@link #tearDown()},
     * an exception will be thrown.
     *
     * @param applicationContext Application context.
     */
    public static UiCallManager init(Context applicationContext) {
        if (sUiCallManager == null) {
            sUiCallManager = new UiCallManager(applicationContext);
        } else {
            throw new IllegalStateException("UiCallManager has been initialized.");
        }
        return sUiCallManager;
    }

    /**
     * Gets the global {@link UiCallManager} instance. Make sure
     * {@link #init(Context)} is called before calling this method.
     */
    public static UiCallManager get() {
        if (sUiCallManager == null) {
            throw new IllegalStateException(
                    "Call UiCallManager.init(Context) before calling this function");
        }
        return sUiCallManager;
    }

    /**
     * This is used only for testing
     */
    @VisibleForTesting
    public static void set(UiCallManager uiCallManager) {
        sUiCallManager = uiCallManager;
    }

    private UiCallManager(Context context) {
        L.d(TAG, "SetUp");
        mContext = context;

        mTelecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        Intent intent = new Intent(context, InCallServiceImpl.class);
        intent.setAction(InCallServiceImpl.ACTION_LOCAL_BIND);
        context.bindService(intent, mInCallServiceConnection, Context.BIND_AUTO_CREATE);

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            adapter.getProfileProxy(mContext, new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    if (profile == BluetoothProfile.HEADSET_CLIENT) {
                        mBluetoothHeadsetClient = (BluetoothHeadsetClient) proxy;
                    }
                }

                @Override
                public void onServiceDisconnected(int profile) {
                }
            }, BluetoothProfile.HEADSET_CLIENT);
        }
    }

    private final ServiceConnection mInCallServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            L.d(TAG, "onServiceConnected: %s, service: %s", name, binder);
            mInCallService = ((InCallServiceImpl.LocalBinder) binder).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            L.d(TAG, "onServiceDisconnected: %s", name);
            mInCallService = null;
        }
    };

    /**
     * Tears down the {@link UiCallManager}. Calling this function will null out the global
     * accessible {@link UiCallManager} instance. Remember to re-initialize the
     * {@link UiCallManager}.
     */
    public void tearDown() {
        if (mInCallService != null) {
            mContext.unbindService(mInCallServiceConnection);
            mInCallService = null;
        }
        // Clear out the mContext reference to avoid memory leak.
        mContext = null;
        sUiCallManager = null;
    }

    public boolean getMuted() {
        L.d(TAG, "getMuted");
        if (mInCallService == null) {
            return false;
        }
        CallAudioState audioState = mInCallService.getCallAudioState();
        return audioState != null && audioState.isMuted();
    }

    public void setMuted(boolean muted) {
        L.d(TAG, "setMuted: " + muted);
        if (mInCallService == null) {
            return;
        }
        mInCallService.setMuted(muted);
    }

    public int getSupportedAudioRouteMask() {
        L.d(TAG, "getSupportedAudioRouteMask");

        CallAudioState audioState = getCallAudioStateOrNull();
        return audioState != null ? audioState.getSupportedRouteMask() : 0;
    }

    public List<Integer> getSupportedAudioRoute() {
        List<Integer> audioRouteList = new ArrayList<>();

        boolean isBluetoothPhoneCall = isBluetoothCall();
        if (isBluetoothPhoneCall) {
            // if this is bluetooth phone call, we can only select audio route between vehicle
            // and phone.
            // Vehicle speaker route.
            audioRouteList.add(CallAudioState.ROUTE_BLUETOOTH);
            // Headset route.
            audioRouteList.add(CallAudioState.ROUTE_EARPIECE);
        } else {
            // Most likely we are making phone call with on board SIM card.
            int supportedAudioRouteMask = getSupportedAudioRouteMask();

            if ((supportedAudioRouteMask & CallAudioState.ROUTE_EARPIECE) != 0) {
                audioRouteList.add(CallAudioState.ROUTE_EARPIECE);
            } else if ((supportedAudioRouteMask & CallAudioState.ROUTE_BLUETOOTH) != 0) {
                audioRouteList.add(CallAudioState.ROUTE_BLUETOOTH);
            } else if ((supportedAudioRouteMask & CallAudioState.ROUTE_WIRED_HEADSET) != 0) {
                audioRouteList.add(CallAudioState.ROUTE_WIRED_HEADSET);
            } else if ((supportedAudioRouteMask & CallAudioState.ROUTE_SPEAKER) != 0) {
                audioRouteList.add(CallAudioState.ROUTE_SPEAKER);
            }
        }

        return audioRouteList;
    }

    public boolean isBluetoothCall() {
        PhoneAccountHandle phoneAccountHandle =
                mTelecomManager.getUserSelectedOutgoingPhoneAccount();
        if (phoneAccountHandle != null && phoneAccountHandle.getComponentName() != null) {
            return HFP_CLIENT_CONNECTION_SERVICE_CLASS_NAME.equals(
                    phoneAccountHandle.getComponentName().getClassName());
        } else {
            return false;
        }
    }

    /**
     * Returns the current audio route.
     * The available routes are defined in {@link CallAudioState}.
     */
    public int getAudioRoute() {
        if (isBluetoothCall()
                && mBluetoothHeadsetClient != null
                && !mBluetoothHeadsetClient.getConnectedDevices().isEmpty()) {
            // TODO: Make this handle multiple devices
            BluetoothDevice device = mBluetoothHeadsetClient.getConnectedDevices().get(0);
            int audioState = mBluetoothHeadsetClient.getAudioState(device);

            if (audioState == BluetoothHeadsetClient.STATE_AUDIO_CONNECTED) {
                return CallAudioState.ROUTE_BLUETOOTH;
            } else {
                return CallAudioState.ROUTE_EARPIECE;
            }
        } else {
            CallAudioState audioState = getCallAudioStateOrNull();
            int audioRoute = audioState != null ? audioState.getRoute() : 0;
            L.d(TAG, "getAudioRoute " + audioRoute);
            return audioRoute;
        }
    }

    /**
     * Re-route the audio out phone of the ongoing phone call.
     */
    public void setAudioRoute(@CallAudioRoute int audioRoute) {
        if (mBluetoothHeadsetClient != null && isBluetoothCall()) {
            for (BluetoothDevice device : mBluetoothHeadsetClient.getConnectedDevices()) {
                List<BluetoothHeadsetClientCall> currentCalls =
                        mBluetoothHeadsetClient.getCurrentCalls(device);
                if (currentCalls != null && !currentCalls.isEmpty()) {
                    if (audioRoute == CallAudioState.ROUTE_BLUETOOTH) {
                        mBluetoothHeadsetClient.connectAudio(device);
                    } else if ((audioRoute & CallAudioState.ROUTE_WIRED_OR_EARPIECE) != 0) {
                        mBluetoothHeadsetClient.disconnectAudio(device);
                    }
                }
            }
        }
        // TODO: Implement routing audio if current call is not a bluetooth call.
    }

    private CallAudioState getCallAudioStateOrNull() {
        return mInCallService != null ? mInCallService.getCallAudioState() : null;
    }

    /**
     * Places call through TelecomManager
     *
     * @return {@code true} if a call is successfully placed, false if number is invalid.
     */
    public boolean placeCall(String number) {
        if (isValidNumber(number)) {
            Uri uri = Uri.fromParts("tel", number, null);
            L.d(TAG, "android.telecom.TelecomManager#placeCall: %s", number);
            mTelecomManager.placeCall(uri, null);
            return true;
        } else {
            L.d(TAG, "invalid number dialed", number);
            Toast.makeText(mContext, R.string.error_invalid_phone_number,
                    Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /**
     * Runs basic validation check of a phone number, to verify it is the correct length
     * in an internationalized way. Further validation on whether the number actually exists
     * is left for the phone carrier.
     */
    private boolean isValidNumber(String number) {
        Phonenumber.PhoneNumber phoneNumber = TelecomUtils.createI18nPhoneNumber(mContext,
                number);
        if (phoneNumber != null) {
            for (PhoneNumberType type : PhoneNumberType.values()) {
                ValidationResult result =
                        PhoneNumberUtil.getInstance().isPossibleNumberForTypeWithReason(phoneNumber,
                                type);
                if (result != ValidationResult.TOO_SHORT && result != ValidationResult.TOO_LONG) {
                    return true;
                }
            }
        }
        return false;
    }

    public void callVoicemail() {
        L.d(TAG, "callVoicemail");

        String voicemailNumber = TelecomUtils.getVoicemailNumber(mContext);
        if (TextUtils.isEmpty(voicemailNumber)) {
            L.w(TAG, "Unable to get voicemail number.");
            return;
        }
        placeCall(voicemailNumber);
    }

    /** Check if emergency call is supported by any phone account. */
    public boolean isEmergencyCallSupported() {
        List<PhoneAccountHandle> phoneAccountHandleList =
                mTelecomManager.getCallCapablePhoneAccounts();
        for (PhoneAccountHandle phoneAccountHandle : phoneAccountHandleList) {
            PhoneAccount phoneAccount = mTelecomManager.getPhoneAccount(phoneAccountHandle);
            L.d(TAG, "phoneAccount: %s", phoneAccount);
            if (phoneAccount != null && phoneAccount.hasCapabilities(
                    PhoneAccount.CAPABILITY_PLACE_EMERGENCY_CALLS)) {
                return true;
            }
        }
        return false;
    }


    /** Return the current active call list from delegated {@link InCallServiceImpl} */
    public List<Call> getCallList() {
        return mInCallService == null ? Collections.emptyList() : mInCallService.getCalls();
    }
}
