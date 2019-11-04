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

package com.android.car.settings.bluetooth;

import android.annotation.NonNull;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.UserManager;
import android.text.TextUtils;

import androidx.annotation.VisibleForTesting;

import com.android.car.settings.R;
import com.android.car.settings.common.Logger;
import com.android.settingslib.bluetooth.BluetoothDiscoverableTimeoutReceiver;
import com.android.settingslib.bluetooth.LocalBluetoothAdapter;
import com.android.settingslib.bluetooth.LocalBluetoothManager;

/**
 * This {@link Activity} handles requests to toggle Bluetooth by collecting user
 * consent and waiting until the state change is completed. It can also be used to make the device
 * explicitly discoverable for a given amount of time.
 */
public class BluetoothRequestPermissionActivity extends Activity {
    private static final Logger LOG = new Logger(BluetoothRequestPermissionActivity.class);

    @VisibleForTesting
    static final int REQUEST_UNKNOWN = 0;
    @VisibleForTesting
    static final int REQUEST_ENABLE = 1;
    @VisibleForTesting
    static final int REQUEST_DISABLE = 2;
    @VisibleForTesting
    static final int REQUEST_ENABLE_DISCOVERABLE = 3;

    private static final int DISCOVERABLE_TIMEOUT_TWO_MINUTES = 120;
    private static final int DISCOVERABLE_TIMEOUT_ONE_HOUR = 3600;

    @VisibleForTesting
    static final int DEFAULT_DISCOVERABLE_TIMEOUT = DISCOVERABLE_TIMEOUT_TWO_MINUTES;
    @VisibleForTesting
    static final int MAX_DISCOVERABLE_TIMEOUT = DISCOVERABLE_TIMEOUT_ONE_HOUR;

    private AlertDialog mDialog;
    private int mRequest;
    private int mTimeout = DEFAULT_DISCOVERABLE_TIMEOUT;

    @NonNull
    private CharSequence mAppLabel;
    private LocalBluetoothAdapter mLocalBluetoothAdapter;
    private LocalBluetoothManager mLocalBluetoothManager;
    private StateChangeReceiver mReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mRequest = parseIntent();
        if (mRequest == REQUEST_UNKNOWN) {
            finishWithResult(RESULT_CANCELED);
            return;
        }

        mLocalBluetoothManager = LocalBluetoothManager.getInstance(
                getApplicationContext(), /* onInitCallback= */ null);
        if (mLocalBluetoothManager == null) {
            LOG.e("Bluetooth is not supported on this device");
            finishWithResult(RESULT_CANCELED);
        }

        mLocalBluetoothAdapter = mLocalBluetoothManager.getBluetoothAdapter();

        int btState = mLocalBluetoothAdapter.getState();
        switch (mRequest) {
            case REQUEST_DISABLE:
                switch (btState) {
                    case BluetoothAdapter.STATE_OFF:
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        proceedAndFinish();
                        break;

                    case BluetoothAdapter.STATE_ON:
                    case BluetoothAdapter.STATE_TURNING_ON:
                        mDialog = createRequestDisableBluetoothDialog();
                        mDialog.show();
                        break;

                    default:
                        LOG.e("Unknown adapter state: " + btState);
                        finishWithResult(RESULT_CANCELED);
                        break;
                }
                break;
            case REQUEST_ENABLE:
                switch (btState) {
                    case BluetoothAdapter.STATE_OFF:
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        mDialog = createRequestEnableBluetoothDialog();
                        mDialog.show();
                        break;
                    case BluetoothAdapter.STATE_ON:
                    case BluetoothAdapter.STATE_TURNING_ON:
                        proceedAndFinish();
                        break;
                    default:
                        LOG.e("Unknown adapter state: " + btState);
                        finishWithResult(RESULT_CANCELED);
                        break;
                }
                break;
            case REQUEST_ENABLE_DISCOVERABLE:
                switch (btState) {
                    case BluetoothAdapter.STATE_OFF:
                    case BluetoothAdapter.STATE_TURNING_OFF:
                    case BluetoothAdapter.STATE_TURNING_ON:
                        /*
                         * Strictly speaking STATE_TURNING_ON belong with STATE_ON; however, BT
                         * may not be ready when the user clicks yes and we would fail to turn on
                         * discovery mode. We still show the dialog and handle this case via the
                         * broadcast receiver.
                         */
                        mDialog = createRequestEnableBluetoothDialogWithTimeout(mTimeout);
                        mDialog.show();
                        break;
                    case BluetoothAdapter.STATE_ON:
                        mDialog = createDiscoverableConfirmDialog(mTimeout);
                        mDialog.show();
                        break;
                    default:
                        LOG.e("Unknown adapter state: " + btState);
                        finishWithResult(RESULT_CANCELED);
                        break;
                }
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
        }
    }

    private void proceedAndFinish() {
        if (mRequest == REQUEST_ENABLE_DISCOVERABLE) {
            finishWithResult(setDiscoverable(mTimeout));
        } else {
            finishWithResult(RESULT_OK);
        }
    }

    // Returns the code that should be used to finish the activity.
    private int setDiscoverable(int timeoutSeconds) {
        if (!mLocalBluetoothAdapter.setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE,
                timeoutSeconds)) {
            return RESULT_CANCELED;
        }

        // If already in discoverable mode, this will extend the timeout.
        long endTime = System.currentTimeMillis() + (long) timeoutSeconds * 1000;
        BluetoothUtils.persistDiscoverableEndTimestamp(/* context= */ this, endTime);
        if (timeoutSeconds > 0) {
            BluetoothDiscoverableTimeoutReceiver.setDiscoverableAlarm(/* context= */ this, endTime);
        }

        int returnCode = timeoutSeconds;
        return returnCode < RESULT_FIRST_USER ? RESULT_FIRST_USER : returnCode;
    }

    private void finishWithResult(int result) {
        if (mDialog != null) {
            mDialog.dismiss();
        }
        setResult(result);
        finish();
    }

    private int parseIntent() {
        int request;
        Intent intent = getIntent();
        if (intent == null) {
            return REQUEST_UNKNOWN;
        }

        switch (intent.getAction()) {
            case BluetoothAdapter.ACTION_REQUEST_ENABLE:
                request = REQUEST_ENABLE;
                break;
            case BluetoothAdapter.ACTION_REQUEST_DISABLE:
                request = REQUEST_DISABLE;
                break;
            case BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE:
                request = REQUEST_ENABLE_DISCOVERABLE;
                mTimeout = intent.getIntExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,
                        DEFAULT_DISCOVERABLE_TIMEOUT);

                if (mTimeout < 1 || mTimeout > MAX_DISCOVERABLE_TIMEOUT) {
                    mTimeout = DEFAULT_DISCOVERABLE_TIMEOUT;
                }
                break;
            default:
                LOG.e("Error: this activity may be started only with intent "
                        + BluetoothAdapter.ACTION_REQUEST_ENABLE);
                return REQUEST_UNKNOWN;
        }

        String packageName = getCallingPackage();
        if (TextUtils.isEmpty(packageName)) {
            packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
        }
        if (!TextUtils.isEmpty(packageName)) {
            try {
                ApplicationInfo applicationInfo = getPackageManager().getApplicationInfo(
                        packageName, 0);
                mAppLabel = applicationInfo.loadLabel(getPackageManager());
            } catch (PackageManager.NameNotFoundException e) {
                LOG.e("Couldn't find app with package name " + packageName);
                return REQUEST_UNKNOWN;
            }
        }

        return request;
    }

    private AlertDialog createWaitingDialog() {
        int message = mRequest == REQUEST_DISABLE ? R.string.bluetooth_turning_off
                : R.string.bluetooth_turning_on;

        return new AlertDialog.Builder(/* context= */ this)
                .setMessage(message)
                .setCancelable(false).setOnCancelListener(
                        dialog -> finishWithResult(RESULT_CANCELED))
                .create();
    }

    // Assumes {@code timeoutSeconds} > 0.
    private AlertDialog createDiscoverableConfirmDialog(int timeoutSeconds) {
        String message = mAppLabel != null
                ? getString(R.string.bluetooth_ask_discovery, mAppLabel, timeoutSeconds)
                : getString(R.string.bluetooth_ask_discovery_no_name, timeoutSeconds);

        return new AlertDialog.Builder(/* context= */ this)
                .setMessage(message)
                .setPositiveButton(R.string.allow, (dialog, which) -> proceedAndFinish())
                .setNegativeButton(R.string.deny,
                        (dialog, which) -> finishWithResult(RESULT_CANCELED))
                .setOnCancelListener(dialog -> finishWithResult(RESULT_CANCELED))
                .create();
    }

    private AlertDialog createRequestEnableBluetoothDialog() {
        String message = mAppLabel != null
                ? getString(R.string.bluetooth_ask_enablement, mAppLabel)
                : getString(R.string.bluetooth_ask_enablement_no_name);

        return new AlertDialog.Builder(/* context= */ this)
                .setMessage(message)
                .setPositiveButton(R.string.allow, this::onConfirmEnableBluetooth)
                .setNegativeButton(R.string.deny,
                        (dialog, which) -> finishWithResult(RESULT_CANCELED))
                .setOnCancelListener(dialog -> finishWithResult(RESULT_CANCELED))
                .create();
    }

    // Assumes {@code timeoutSeconds} > 0.
    private AlertDialog createRequestEnableBluetoothDialogWithTimeout(int timeoutSeconds) {
        String message = mAppLabel != null
                ? getString(R.string.bluetooth_ask_enablement_and_discovery, mAppLabel,
                        timeoutSeconds)
                : getString(R.string.bluetooth_ask_enablement_and_discovery_no_name,
                        timeoutSeconds);

        return new AlertDialog.Builder(/* context= */ this)
                .setMessage(message)
                .setPositiveButton(R.string.allow, this::onConfirmEnableBluetooth)
                .setNegativeButton(R.string.deny,
                        (dialog, which) -> finishWithResult(RESULT_CANCELED))
                .setOnCancelListener(dialog -> finishWithResult(RESULT_CANCELED))
                .create();
    }

    private void onConfirmEnableBluetooth(DialogInterface dialog, int which) {
        UserManager userManager = getSystemService(UserManager.class);
        if (userManager.hasUserRestriction(UserManager.DISALLOW_BLUETOOTH)) {
            // If Bluetooth is disallowed, don't try to enable it, show policy
            // transparency message instead.
            DevicePolicyManager dpm = getSystemService(DevicePolicyManager.class);
            Intent intent = dpm.createAdminSupportIntent(
                    UserManager.DISALLOW_BLUETOOTH);
            if (intent != null) {
                startActivity(intent);
            }
            return;
        }

        mLocalBluetoothAdapter.enable();

        int desiredState = BluetoothAdapter.STATE_ON;
        if (mLocalBluetoothAdapter.getState() == desiredState) {
            proceedAndFinish();
        } else {
            // Register this receiver to listen for state change after the enabling has started.
            mReceiver = new StateChangeReceiver(desiredState);
            registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

            if (mRequest == REQUEST_ENABLE) {
                // Show dialog while waiting for enabling to complete.
                mDialog = createWaitingDialog();
            } else {
                mDialog = createDiscoverableConfirmDialog(mTimeout);
            }
            mDialog.show();
        }
    }

    private AlertDialog createRequestDisableBluetoothDialog() {
        String message = mAppLabel != null
                ? getString(R.string.bluetooth_ask_disablement, mAppLabel)
                : getString(R.string.bluetooth_ask_disablement_no_name);

        return new AlertDialog.Builder(/* context= */ this)
                .setMessage(message)
                .setPositiveButton(R.string.allow, this::onConfirmDisableBluetooth)
                .setNegativeButton(R.string.deny,
                        (dialog, which) -> finishWithResult(RESULT_CANCELED))
                .setOnCancelListener(dialog -> finishWithResult(RESULT_CANCELED))
                .create();
    }

    private void onConfirmDisableBluetooth(DialogInterface dialog, int which) {
        mLocalBluetoothAdapter.disable();

        int desiredState = BluetoothAdapter.STATE_OFF;
        if (mLocalBluetoothAdapter.getState() == desiredState) {
            proceedAndFinish();
        } else {
            // Register this receiver to listen for state change after the disabling has started.
            mReceiver = new StateChangeReceiver(desiredState);
            registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

            // Show dialog while waiting for disabling to complete.
            mDialog = createWaitingDialog();
            mDialog.show();
        }
    }

    @VisibleForTesting
    int getRequestType() {
        return mRequest;
    }

    @VisibleForTesting
    int getTimeout() {
        return mTimeout;
    }

    @VisibleForTesting
    AlertDialog getCurrentDialog() {
        return mDialog;
    }

    /**
     * Listens for bluetooth state changes and finishes the activity if changed to the desired
     * state. If the desired bluetooth state is not received in time, the activity is finished with
     * {@link Activity#RESULT_CANCELED}.
     */
    private final class StateChangeReceiver extends BroadcastReceiver {
        private static final long TOGGLE_TIMEOUT_MILLIS = 10000; // 10 sec
        private final int mDesiredState;

        StateChangeReceiver(int desiredState) {
            mDesiredState = desiredState;

            getWindow().getDecorView().postDelayed(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    finishWithResult(RESULT_CANCELED);
                }
            }, TOGGLE_TIMEOUT_MILLIS);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }

            int currentState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                    BluetoothDevice.ERROR);
            if (mDesiredState == currentState) {
                proceedAndFinish();
            }
        }
    }
}
