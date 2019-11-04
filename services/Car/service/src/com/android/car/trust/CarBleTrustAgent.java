/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car.trust;

import static com.android.car.trust.EventLog.BLUETOOTH_STATE_CHANGED;
import static com.android.car.trust.EventLog.USER_UNLOCKED;
import static com.android.car.trust.EventLog.logUnlockEvent;

import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.car.trust.TrustedDeviceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.UserHandle;
import android.os.UserManager;
import android.service.trust.TrustAgentService;
import android.util.Log;

import com.android.car.CarLocalServices;
import com.android.car.Utils;
import com.android.car.trust.CarTrustAgentEnrollmentService.CarTrustAgentEnrollmentRequestDelegate;
import com.android.car.trust.CarTrustAgentUnlockService.CarTrustAgentUnlockDelegate;

import java.util.List;

/**
 * A BluetoothLE (BLE) based {@link TrustAgentService} that uses the escrow token unlock APIs.
 * <p>
 * This trust agent runs during direct boot and interacts with {@link CarTrustedDeviceService}
 * to listen for remote devices to trigger an unlock.
 * <p>
 * The system {@link com.android.server.trust.TrustManagerService} binds to this agent and uses
 * the data it receives from this agent to authorize a user in lieu of the PIN/Pattern/Password
 * credentials.
 */
public class CarBleTrustAgent extends TrustAgentService {
    private static final String TAG = CarBleTrustAgent.class.getSimpleName();
    private boolean mIsDeviceLocked;
    private CarTrustedDeviceService mCarTrustedDeviceService;
    private CarTrustAgentEnrollmentService mCarTrustAgentEnrollmentService;
    private CarTrustAgentUnlockService mCarTrustAgentUnlockService;

    @Override
    public void onCreate() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onCreate()");
        }
        super.onCreate();
        // Registering for more granular BLE specific state changes as against Bluetooth state
        // changes, helps with reducing latency in getting notified.
        IntentFilter intentFilter = new IntentFilter(BluetoothAdapter.ACTION_BLE_STATE_CHANGED);
        registerReceiver(mBluetoothBroadcastReceiver, intentFilter);

        // TODO(b/129144535) handle scenarios where CarService crashed.  Maybe retrieve this
        //  every time we need instead of caching.
        mCarTrustedDeviceService = CarLocalServices.getService(CarTrustedDeviceService.class);
        if (mCarTrustedDeviceService == null) {
            Log.e(TAG, "Cannot retrieve the Trusted device Service");
            return;
        }
        mCarTrustAgentEnrollmentService =
                mCarTrustedDeviceService.getCarTrustAgentEnrollmentService();
        setEnrollmentRequestDelegate();
        mCarTrustAgentUnlockService = mCarTrustedDeviceService.getCarTrustAgentUnlockService();
        setUnlockRequestDelegate();
        setManagingTrust(true);
    }

    @Override
    public void onDestroy() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Car Trust agent shutting down");
        }
        super.onDestroy();
        mCarTrustAgentEnrollmentService = null;
        if (mBluetoothBroadcastReceiver != null) {
            unregisterReceiver(mBluetoothBroadcastReceiver);
        }
    }

    // Overriding TrustAgentService methods
    @Override
    public void onDeviceLocked() {
        int uid = ActivityManager.getCurrentUser();
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onDeviceLocked Current user: " + uid);
        }
        super.onDeviceLocked();
        mIsDeviceLocked = true;
        if (BluetoothAdapter.getDefaultAdapter().getState() == BluetoothAdapter.STATE_OFF) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Not starting Unlock Advertising yet, since Bluetooth Adapter is off");
            }
            return;
        }
        if (!hasTrustedDevice(uid)) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Not starting Unlock Advertising yet, since current user: "
                        + uid + "has no trusted device");
            }
            return;
        }
        if (mCarTrustAgentUnlockService != null) {
            mCarTrustAgentUnlockService.startUnlockAdvertising();
        }
    }

    @Override
    public void onDeviceUnlocked() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onDeviceUnlocked Current user: " + ActivityManager.getCurrentUser());
        }
        super.onDeviceUnlocked();
        mIsDeviceLocked = false;
        if (BluetoothAdapter.getDefaultAdapter().getState() == BluetoothAdapter.STATE_OFF) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Not stopping Unlock Advertising, since Bluetooth Adapter is off");
            }
            return;
        }
        if (mCarTrustAgentUnlockService != null) {
            mCarTrustAgentUnlockService.stopUnlockAdvertising();

        }
    }

    @Override
    public void onEscrowTokenRemoved(long handle, boolean successful) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onEscrowTokenRemoved handle: " + Long.toHexString(handle));
        }
        if (mCarTrustAgentEnrollmentService == null) {
            return;
        }
        if (successful) {
            mCarTrustAgentEnrollmentService.onEscrowTokenRemoved(handle,
                    ActivityManager.getCurrentUser());
        }
    }

    @Override
    public void onEscrowTokenStateReceived(long handle, int tokenState) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onEscrowTokenStateReceived: " + Long.toHexString(handle) + " state: "
                    + tokenState);
        }
        if (mCarTrustAgentEnrollmentService == null) {
            return;
        }
        mCarTrustAgentEnrollmentService.onEscrowTokenActiveStateChanged(handle,
                tokenState == TOKEN_STATE_ACTIVE, ActivityManager.getCurrentUser());
    }

    @Override
    public void onEscrowTokenAdded(byte[] token, long handle, UserHandle user) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onEscrowTokenAdded handle: " + Long.toHexString(handle) + " token: "
                    + Utils.byteArrayToHexString(token));
        }
        if (mCarTrustAgentEnrollmentService == null) {
            return;
        }
        mCarTrustAgentEnrollmentService.onEscrowTokenAdded(token, handle, user.getIdentifier());
    }

    private void setEnrollmentRequestDelegate() {
        if (mCarTrustAgentEnrollmentService == null) {
            return;
        }
        mCarTrustAgentEnrollmentService.setEnrollmentRequestDelegate(mEnrollDelegate);
    }

    private void setUnlockRequestDelegate() {
        if (mCarTrustAgentUnlockService == null) {
            return;
        }
        mCarTrustAgentUnlockService.setUnlockRequestDelegate(mUnlockDelegate);
    }

    /**
     *
     * @param uid User id
     * @return if the user has trusted device
     */
    private boolean hasTrustedDevice(int uid) {
        List<TrustedDeviceInfo> trustedDeviceInfos = mCarTrustAgentEnrollmentService
                .getEnrolledDeviceInfosForUser(uid);
        return trustedDeviceInfos != null && trustedDeviceInfos.size() > 0;
    }

    private void unlockUserInternally(int uid, byte[] token, long handle) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "About to unlock user: " + uid);
            UserManager um = (UserManager) getSystemService(Context.USER_SERVICE);
            if (um.isUserUnlocked(UserHandle.of(uid))) {
                Log.d(TAG, "User currently unlocked");
            } else {
                Log.d(TAG, "User currently locked");
            }
        }
        unlockUserWithToken(handle, token, UserHandle.of(uid));
        grantTrust("Granting trust from escrow token",
                0, FLAG_GRANT_TRUST_DISMISS_KEYGUARD);
    }

    private final BroadcastReceiver mBluetoothBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null && BluetoothAdapter.ACTION_BLE_STATE_CHANGED.equals(
                    intent.getAction())) {
                onBluetoothStateChanged(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1));
            }
        }
    };

    private void onBluetoothStateChanged(int state) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onBluetoothStateChanged: " + state);
        }
        if (!mIsDeviceLocked) {
            return;
        }
        logUnlockEvent(BLUETOOTH_STATE_CHANGED, state);
        switch (state) {
            case BluetoothAdapter.STATE_BLE_ON:
                int uid = ActivityManager.getCurrentUser();
                if (mCarTrustAgentUnlockService != null && hasTrustedDevice(uid)) {
                    mCarTrustAgentUnlockService.startUnlockAdvertising();
                }
                break;
            case BluetoothAdapter.STATE_OFF:
                Log.e(TAG, "Bluetooth Adapter Off in lock screen");
                if (mCarTrustedDeviceService != null) {
                    mCarTrustedDeviceService.cleanupBleService();
                }
                break;
            default:
                break;
        }
    }

    // Implementing Delegates for Enrollment and Unlock.  The CarBleTrustAgent acts as the interface
    // between the Trust Agent framework and the Car Service.  The Car service handles communicating
    // with the peer device part and the framework handles the actual authentication.  The
    // CarBleTrustAgent abstracts these 2 pieces from each other.
    /**
     * Implementation of the {@link CarTrustAgentEnrollmentRequestDelegate}
     */
    private final CarTrustAgentEnrollmentRequestDelegate mEnrollDelegate =
            new CarTrustAgentEnrollmentRequestDelegate() {
                @Override
                public void addEscrowToken(byte[] token, int uid) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG,
                                "addEscrowToken. uid: " + uid + " token: "
                                        + Utils.byteArrayToHexString(
                                        token));
                    }
                    CarBleTrustAgent.this.addEscrowToken(token, UserHandle.of(uid));
                }

                @Override
                public void removeEscrowToken(long handle, int uid) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG,
                                "removeEscrowToken. uid: " + ActivityManager.getCurrentUser()
                                        + " handle: " + handle);
                    }
                    CarBleTrustAgent.this.removeEscrowToken(handle,
                            UserHandle.of(uid));
                }

                @Override
                public void isEscrowTokenActive(long handle, int uid) {
                    CarBleTrustAgent.this.isEscrowTokenActive(handle, UserHandle.of(uid));
                }
            };

    /**
     * Implementation of the {@link CarTrustAgentUnlockDelegate}
     */
    private final CarTrustAgentUnlockDelegate mUnlockDelegate = new CarTrustAgentUnlockDelegate() {
        /**
         * Pass the user and token credentials to authenticate with the LockSettingsService.
         *
         * @param user   user being authorized
         * @param token  escrow token for the user
         * @param handle the handle corresponding to the escrow token
         */
        @Override
        public void onUnlockDataReceived(int user, byte[] token, long handle) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onUnlockDataReceived:" + user + " token: " + Long.toHexString(
                        Utils.bytesToLong(token)) + " handle: " + Long.toHexString(handle));
            }
            if (ActivityManager.getCurrentUser() != user) {
                // Current behavior is to only authenticate the user we have booted into.
                // TODO(b/129029418) Make identification & Auth vs Auth-only a
                // configurable option
                Log.e(TAG, "Expected User: " + ActivityManager.getCurrentUser()
                        + " Presented User: " + user);
                return;
            } else {
                unlockUserInternally(user, token, handle);
                logUnlockEvent(USER_UNLOCKED);
            }

        }
    };
}
