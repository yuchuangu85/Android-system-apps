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
package com.android.car.dialer.telecom;

import android.annotation.Nullable;
import android.bluetooth.BluetoothDevice;
import android.car.Car;
import android.car.CarProjectionManager;
import android.car.projection.ProjectionStatus;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.telecom.Call;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import com.android.car.dialer.log.L;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Collections;
import java.util.List;

class ProjectionCallHandler implements InCallServiceImpl.ActiveCallListChangedCallback,
        CarProjectionManager.ProjectionStatusListener {
    private static final String TAG = "CD.ProjectionCallHandler";

    @VisibleForTesting static final String HFP_CLIENT_SCHEME = "hfpc";
    @VisibleForTesting static final String PROJECTION_STATUS_EXTRA_HANDLES_PHONE_UI =
            "android.car.projection.HANDLES_PHONE_UI";
    @VisibleForTesting static final String PROJECTION_STATUS_EXTRA_DEVICE_STATE =
            "android.car.projection.DEVICE_STATE";

    private final CarProjectionManager mCarProjectionManager;
    private final TelecomManager mTelecomManager;

    private int mProjectionState = ProjectionStatus.PROJECTION_STATE_INACTIVE;
    private List<ProjectionStatus> mProjectionDetails = Collections.emptyList();

    ProjectionCallHandler(Context context) {
        this(context.getSystemService(TelecomManager.class),
                (CarProjectionManager)
                        Car.createCar(context).getCarManager(Car.PROJECTION_SERVICE));
    }

    @VisibleForTesting
    ProjectionCallHandler(TelecomManager telecomManager, CarProjectionManager projectionManager) {
        mTelecomManager = telecomManager;
        mCarProjectionManager = projectionManager;
    }

    void start() {
        mCarProjectionManager.registerProjectionStatusListener(this);
    }

    void stop() {
        mCarProjectionManager.unregisterProjectionStatusListener(this);
    }

    @Override
    public void onProjectionStatusChanged(
            int state, String packageName, List<ProjectionStatus> details) {
        mProjectionState = state;
        mProjectionDetails = details;
    }

    @Override
    public boolean onTelecomCallAdded(Call telecomCall) {
        L.d(TAG, "onTelecomCallAdded(%s)", telecomCall);
        if (mProjectionState != ProjectionStatus.PROJECTION_STATE_ACTIVE_BACKGROUND
                && mProjectionState != ProjectionStatus.PROJECTION_STATE_ACTIVE_FOREGROUND) {
            // Nothing's actively projecting, so no need to even check anything else.
            return false;
        }

        if (mTelecomManager.isInEmergencyCall()) {
            L.i(TAG, "Not suppressing UI for projection - in emergency call");
            return false;
        }

        String bluetoothAddress = getHfpBluetoothAddressForCall(telecomCall);
        if (bluetoothAddress == null) {
            // Not an HFP call, so don't suppress UI.
            return false;
        }

        return shouldSuppressCallUiForBluetoothDevice(bluetoothAddress);
    }

    @Override
    public boolean onTelecomCallRemoved(Call telecomCall) {
        return false;
    }

    @Nullable
    private String getHfpBluetoothAddressForCall(Call call) {
        Call.Details details = call.getDetails();
        if (details == null) {
            return null;
        }

        PhoneAccountHandle accountHandle = details.getAccountHandle();
        PhoneAccount account = mTelecomManager.getPhoneAccount(accountHandle);
        if (account == null) {
            return null;
        }

        Uri address = account.getAddress();
        if (address == null || !HFP_CLIENT_SCHEME.equals(address.getScheme())) {
            return null;
        }

        return address.getSchemeSpecificPart();
    }

    private boolean shouldSuppressCallUiForBluetoothDevice(String bluetoothAddress) {
        L.d(TAG, "shouldSuppressCallUiFor(%s)", bluetoothAddress);
        for (ProjectionStatus status : mProjectionDetails) {
            if (!status.isActive()) {
                // Don't suppress UI for packages that aren't actively projecting.
                L.d(TAG, "skip non-projecting package %s", status.getPackageName());
                continue;
            }

            Bundle appExtras = status.getExtras();
            if (!appExtras.getBoolean(PROJECTION_STATUS_EXTRA_HANDLES_PHONE_UI, true)) {
                // Don't suppress UI for apps that say they don't handle phone UI.
                continue;
            }

            for (ProjectionStatus.MobileDevice device : status.getConnectedMobileDevices()) {
                if (!device.isProjecting()) {
                    // Don't suppress UI for devices that aren't foreground.
                    L.d(TAG, "skip non-projecting device %s", device.getName());
                    continue;
                }

                Bundle extras = device.getExtras();
                if (extras.getInt(PROJECTION_STATUS_EXTRA_DEVICE_STATE,
                        ProjectionStatus.PROJECTION_STATE_ACTIVE_FOREGROUND)
                        != ProjectionStatus.PROJECTION_STATE_ACTIVE_FOREGROUND) {
                    L.d(TAG, "skip device %s - not foreground", device.getName());
                    continue;
                }

                Parcelable projectingBluetoothDevice =
                        extras.getParcelable(BluetoothDevice.EXTRA_DEVICE);

                L.d(TAG, "Device %s has BT device %s", device.getName(), projectingBluetoothDevice);

                if (projectingBluetoothDevice == null) {
                    L.i(TAG, "Suppressing in-call UI - device %s is projecting, and does not "
                            + "specify a Bluetooth address", device);
                    return true;
                } else if (!(projectingBluetoothDevice instanceof BluetoothDevice)) {
                    L.e(TAG, "Device %s has bad EXTRA_DEVICE value %s - treating as unspecified",
                            device, projectingBluetoothDevice);
                    return true;
                } else if (bluetoothAddress.equals(
                        ((BluetoothDevice) projectingBluetoothDevice).getAddress())) {
                    L.i(TAG, "Suppressing in-call UI - device %s is projecting, and call is coming "
                            + "from device's Bluetooth address %s", device, bluetoothAddress);
                    return true;
                }
            }
        }

        // No projecting apps want to suppress this device, so let it through.
        return false;
    }
}
