/*
 * Copyright 2019 The Android Open Source Project
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

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothDevicePicker;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.settingslib.bluetooth.BluetoothDeviceFilter;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;

/**
 * Displays a list of Bluetooth devices for the user to select. When a device is selected, a
 * {@link BluetoothDevicePicker#ACTION_DEVICE_SELECTED} broadcast is sent containing {@link
 * BluetoothDevice#EXTRA_DEVICE}.
 *
 * <p>This is useful to other application to obtain a device without needing to implement the UI.
 * The activity hosting this controller should be launched with an intent as detailed in {@link
 * BluetoothDevicePicker#ACTION_LAUNCH}. This controller will filter devices as specified by {@link
 * BluetoothDevicePicker#EXTRA_FILTER_TYPE} and deliver the broadcast to the specified {@link
 * BluetoothDevicePicker#EXTRA_LAUNCH_PACKAGE} {@link BluetoothDevicePicker#EXTRA_LAUNCH_CLASS}
 * component.  If authentication is required ({@link BluetoothDevicePicker#EXTRA_NEED_AUTH}), this
 * controller will initiate pairing with the device and send the selected broadcast once the device
 * successfully pairs. If no device is selected and this controller is destroyed, a broadcast with
 * a {@code null} {@link BluetoothDevice#EXTRA_DEVICE} is sent.
 */
public class BluetoothDevicePickerPreferenceController extends
        BluetoothScanningDevicesGroupPreferenceController {

    private static final Logger LOG = new Logger(BluetoothDevicePickerPreferenceController.class);

    private BluetoothDeviceFilter.Filter mFilter;

    private boolean mNeedAuth;
    private String mLaunchPackage;
    private String mLaunchClass;

    private CachedBluetoothDevice mSelectedDevice;

    public BluetoothDevicePickerPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    /**
     * Sets the intent with which {@link BluetoothDevicePickerActivity} was launched. The intent
     * may contain {@link BluetoothDevicePicker} extras to customize the selection list and specify
     * the destination of the selected device. See {@link BluetoothDevicePicker#ACTION_LAUNCH}.
     */
    public void setLaunchIntent(Intent intent) {
        mNeedAuth = intent.getBooleanExtra(BluetoothDevicePicker.EXTRA_NEED_AUTH, false);
        mFilter = BluetoothDeviceFilter.getFilter(
                intent.getIntExtra(BluetoothDevicePicker.EXTRA_FILTER_TYPE,
                        BluetoothDevicePicker.FILTER_TYPE_ALL));
        mLaunchPackage = intent.getStringExtra(BluetoothDevicePicker.EXTRA_LAUNCH_PACKAGE);
        mLaunchClass = intent.getStringExtra(BluetoothDevicePicker.EXTRA_LAUNCH_CLASS);
    }

    @Override
    protected void checkInitialized() {
        if (mFilter == null) {
            throw new IllegalStateException("launch intent must be set");
        }
    }

    @Override
    protected BluetoothDeviceFilter.Filter getDeviceFilter() {
        return mFilter;
    }

    @Override
    protected void onDeviceClickedInternal(CachedBluetoothDevice cachedDevice) {
        mSelectedDevice = cachedDevice;
        BluetoothUtils.persistSelectedDeviceInPicker(getContext(), cachedDevice.getAddress());

        if (cachedDevice.getBondState() == BluetoothDevice.BOND_BONDED || !mNeedAuth) {
            sendDevicePickedIntent(cachedDevice.getDevice());
            getFragmentController().goBack();
            return;
        }

        if (cachedDevice.startPairing()) {
            LOG.d("startPairing");
        } else {
            BluetoothUtils.showError(getContext(), cachedDevice.getName(),
                    R.string.bluetooth_pairing_error_message);
            refreshUi();
        }
    }

    @Override
    protected void onStartInternal() {
        super.onStartInternal();
        mSelectedDevice = null;
    }

    @Override
    protected void onDestroyInternal() {
        super.onDestroyInternal();
        if (mSelectedDevice == null) {
            // Notify that no device was selected.
            sendDevicePickedIntent(null);
        }
    }

    @Override
    public void onDeviceBondStateChanged(CachedBluetoothDevice cachedDevice, int bondState) {
        super.onDeviceBondStateChanged(cachedDevice, bondState);
        if (bondState == BluetoothDevice.BOND_BONDED && cachedDevice.equals(mSelectedDevice)) {
            sendDevicePickedIntent(mSelectedDevice.getDevice());
            getFragmentController().goBack();
        }
    }

    private void sendDevicePickedIntent(BluetoothDevice device) {
        LOG.d("sendDevicePickedIntent device: " + device + " package: " + mLaunchPackage
                + " class: " + mLaunchClass);
        Intent intent = new Intent(BluetoothDevicePicker.ACTION_DEVICE_SELECTED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        if (mLaunchPackage != null && mLaunchClass != null) {
            intent.setClassName(mLaunchPackage, mLaunchClass);
        }
        getContext().sendBroadcast(intent);
    }
}
