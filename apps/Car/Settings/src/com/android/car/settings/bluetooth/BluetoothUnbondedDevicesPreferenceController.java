/*
 * Copyright 2018 The Android Open Source Project
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

import static android.os.UserManager.DISALLOW_CONFIG_BLUETOOTH;

import android.bluetooth.BluetoothDevice;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.settingslib.bluetooth.BluetoothDeviceFilter;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;

/**
 * Displays a list of unbonded (unpaired) Bluetooth devices. This controller also sets the
 * Bluetooth adapter to discovery mode and begins scanning for discoverable devices for as long as
 * the preference group is shown. Clicking on a device will start the pairing process. Discovery
 * and scanning are halted while a device is pairing. Users with the {@link
 * DISALLOW_CONFIG_BLUETOOTH} restriction cannot pair devices.
 */
public class BluetoothUnbondedDevicesPreferenceController extends
        BluetoothScanningDevicesGroupPreferenceController {

    private static final Logger LOG = new Logger(
            BluetoothUnbondedDevicesPreferenceController.class);

    public BluetoothUnbondedDevicesPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected BluetoothDeviceFilter.Filter getDeviceFilter() {
        return BluetoothDeviceFilter.UNBONDED_DEVICE_FILTER;
    }

    @Override
    protected void onDeviceClickedInternal(CachedBluetoothDevice cachedDevice) {
        if (cachedDevice.startPairing()) {
            LOG.d("startPairing");
            // Indicate that this client (vehicle) would like access to contacts (PBAP) and messages
            // (MAP) if there is a server which permits it (usually a phone).
            cachedDevice.getDevice().setPhonebookAccessPermission(BluetoothDevice.ACCESS_ALLOWED);
            cachedDevice.getDevice().setMessageAccessPermission(BluetoothDevice.ACCESS_ALLOWED);
        } else {
            BluetoothUtils.showError(getContext(), cachedDevice.getName(),
                    R.string.bluetooth_pairing_error_message);
            refreshUi();
        }
    }

    @Override
    protected int getAvailabilityStatus() {
        int availabilityStatus = super.getAvailabilityStatus();
        if (availabilityStatus == AVAILABLE
                && getCarUserManagerHelper().isCurrentProcessUserHasRestriction(
                DISALLOW_CONFIG_BLUETOOTH)) {
            return DISABLED_FOR_USER;
        }
        return availabilityStatus;
    }
}
