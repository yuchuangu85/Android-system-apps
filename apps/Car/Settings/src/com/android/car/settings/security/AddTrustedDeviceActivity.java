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

package com.android.car.settings.security;

import android.bluetooth.BluetoothDevice;
import android.car.Car;
import android.car.trust.CarTrustAgentEnrollmentManager;
import android.car.userlib.CarUserManagerHelper;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;

import com.android.car.settings.R;
import com.android.car.settings.common.BaseCarSettingsActivity;
import com.android.car.settings.common.Logger;

/**
 * Activity which manages the enrollment process and communicates between
 * CarTrustAgentEnrollmentService and fragments.
 *
 * <p>The flow when user want to enroll a trusted device should be as follows:
 * <ol>
 * <li> {@link CarTrustAgentEnrollmentManager#setEnrollmentCallback(
 *CarTrustAgentEnrollmentManager.CarTrustAgentEnrollmentCallback)}
 * <li> {@link CarTrustAgentEnrollmentManager#setBleCallback(
 *CarTrustAgentEnrollmentManager.CarTrustAgentBleCallback)}
 * <li> {@link CarTrustAgentEnrollmentManager#startEnrollmentAdvertising()}
 * <li> wait for {@link CarTrustAgentEnrollmentManager.CarTrustAgentBleCallback#
 * onBleEnrollmentDeviceDisconnected(BluetoothDevice)}
 * <li>  {@link CarTrustAgentEnrollmentManager#stopEnrollmentAdvertising()}
 * <li> wait for
 * {@link CarTrustAgentEnrollmentManager.CarTrustAgentEnrollmentCallback#onAuthStringAvailable(
 *BluetoothDevice, String)} to show the pairing code dialog to user
 * <li> {@link CarTrustAgentEnrollmentManager#enrollmentHandshakeAccepted(BluetoothDevice)} after
 * user confirms the pairing code
 * <li> wait for
 * {@link CarTrustAgentEnrollmentManager.CarTrustAgentEnrollmentCallback#onEscrowTokenAdded(long)}
 * <li> {@link #getCheckLockFragment()}, wait user to input the password
 * <li> After user enter the correct password, wait for
 * {@link CarTrustAgentEnrollmentManager.CarTrustAgentEnrollmentCallback#
 * onEscrowTokenActiveStateChanged(long, boolean)}
 * <li> After get the results, finish the activity
 * </ol>
 */
public class AddTrustedDeviceActivity extends BaseCarSettingsActivity implements CheckLockListener {
    private static final Logger LOG = new Logger(AddTrustedDeviceActivity.class);
    private static final String BLUETOOTH_DEVICE_KEY = "bluetoothDevice";
    private static final String CURRENT_HANDLE_KEY = "currentHandle";
    private Car mCar;
    private BluetoothDevice mBluetoothDevice;
    private long mHandle;
    private CarUserManagerHelper mCarUserManagerHelper;
    @Nullable
    private CarTrustAgentEnrollmentManager mCarTrustAgentEnrollmentManager;

    private final CarTrustAgentEnrollmentManager.CarTrustAgentEnrollmentCallback
            mCarTrustAgentEnrollmentCallback =
            new CarTrustAgentEnrollmentManager.CarTrustAgentEnrollmentCallback() {

                @Override
                public void onEnrollmentHandshakeFailure(BluetoothDevice device, int errorCode) {
                    LOG.e("Trust agent service time out");
                }

                @Override
                public void onAuthStringAvailable(BluetoothDevice device, String authString) {
                    ConfirmPairingCodeDialog dialog = ConfirmPairingCodeDialog.newInstance(
                            authString);
                    dialog.setConfirmPairingCodeListener(mConfirmParingCodeListener);
                    showDialog(dialog, ConfirmPairingCodeDialog.TAG);
                }

                @Override
                public void onEscrowTokenAdded(long handle) {
                    // User need to enter the correct authentication of the car to activate the
                    // added token.
                    mHandle = handle;
                    launchFragment(getCheckLockFragment());
                }

                @Override
                public void onEscrowTokenRemoved(long handle) {
                }

                @Override
                public void onEscrowTokenActiveStateChanged(long handle, boolean active) {
                    if (active) {
                        onDeviceAddedSuccessfully();
                    } else {
                        LOG.d(handle + " has not been activated");
                    }
                    finish();
                }
            };

    private final CarTrustAgentEnrollmentManager.CarTrustAgentBleCallback
            mCarTrustAgentBleCallback =
            new CarTrustAgentEnrollmentManager.CarTrustAgentBleCallback() {
                @Override
                public void onBleEnrollmentDeviceConnected(BluetoothDevice device) {
                    mBluetoothDevice = device;
                    mCarTrustAgentEnrollmentManager.stopEnrollmentAdvertising();
                }

                @Override
                public void onBleEnrollmentDeviceDisconnected(BluetoothDevice device) {
                    Toast.makeText(AddTrustedDeviceActivity.this, getResources().getString(
                            R.string.trusted_device_disconnected_toast),
                            Toast.LENGTH_SHORT).show();
                    mBluetoothDevice = null;
                    finish();
                }

                @Override
                public void onEnrollmentAdvertisingStarted() {
                }

                @Override
                public void onEnrollmentAdvertisingFailed() {
                    finish();
                }
            };

    @VisibleForTesting
    final ConfirmPairingCodeDialog.ConfirmPairingCodeListener mConfirmParingCodeListener =
            new ConfirmPairingCodeDialog.ConfirmPairingCodeListener() {
                public void onConfirmPairingCode() {
                    mCarTrustAgentEnrollmentManager.enrollmentHandshakeAccepted(mBluetoothDevice);
                }

                public void onDialogCancelled() {
                    finish();
                }
            };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mCar = Car.createCar(this);
        mCarTrustAgentEnrollmentManager = (CarTrustAgentEnrollmentManager) mCar.getCarManager(
                Car.CAR_TRUST_AGENT_ENROLLMENT_SERVICE);
        if (mCarTrustAgentEnrollmentManager == null) {
            LOG.e("CarTrustAgentEnrollmentManager is null");
            finish();
        }
        mCarUserManagerHelper = new CarUserManagerHelper(this);
        if (savedInstanceState != null) {
            mBluetoothDevice = savedInstanceState.getParcelable(BLUETOOTH_DEVICE_KEY);
            mHandle = savedInstanceState.getLong(CURRENT_HANDLE_KEY);
        }
        ConfirmPairingCodeDialog dialog =
                (ConfirmPairingCodeDialog) findDialogByTag(ConfirmPairingCodeDialog.TAG);
        if (dialog != null) {
            dialog.setConfirmPairingCodeListener(mConfirmParingCodeListener);
        }
    }


    @Override
    protected void onStart() {
        super.onStart();
        if (mHandle != 0) {
            if (mCarTrustAgentEnrollmentManager.isEscrowTokenActive(mHandle,
                    mCarUserManagerHelper.getCurrentProcessUserId())) {
                onDeviceAddedSuccessfully();
                finish();
            }
        }
        if (mBluetoothDevice == null) {
            mCarTrustAgentEnrollmentManager.startEnrollmentAdvertising();
        }
        mCarTrustAgentEnrollmentManager.setEnrollmentCallback(mCarTrustAgentEnrollmentCallback);
        mCarTrustAgentEnrollmentManager.setBleCallback(mCarTrustAgentBleCallback);

    }

    @Override
    protected void onPause() {
        super.onPause();
        // When activity is pausing not because of a configuration change
        if (getChangingConfigurations() == 0) {
            mCarTrustAgentEnrollmentManager.terminateEnrollmentHandshake();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mCarTrustAgentEnrollmentManager.setBleCallback(null);
        mCarTrustAgentEnrollmentManager.setEnrollmentCallback(null);
        mCarTrustAgentEnrollmentManager.stopEnrollmentAdvertising();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putParcelable(BLUETOOTH_DEVICE_KEY, mBluetoothDevice);
        savedInstanceState.putLong(CURRENT_HANDLE_KEY, mHandle);
    }


    @Override
    @Nullable
    protected Fragment getInitialFragment() {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(
                R.id.fragment_container);
        return currentFragment == null ? new AddTrustedDeviceProgressFragment() : currentFragment;
    }

    private Fragment getCheckLockFragment() {
        return ConfirmPasswordFragmentFactory.getFragment(/* context= */ this);
    }

    @Override
    public void onLockVerified(byte[] lock) {
        getSupportFragmentManager().popBackStack();
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    private void onDeviceAddedSuccessfully() {
        Toast.makeText(this,
                getResources().getString(R.string.trusted_device_success_enrollment_toast),
                Toast.LENGTH_LONG).show();
    }
}
