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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.StringRes;

import com.android.car.settings.R;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.LocalBluetoothManager;

/** Dialog for changing the display name of a remote bluetooth device. */
public class RemoteRenameDialogFragment extends BluetoothRenameDialogFragment {

    /** Tag identifying the dialog for changing the name of a remote Bluetooth device. */
    public static final String TAG = "RemoteDeviceBluetoothRename";

    private static final String KEY_CACHED_DEVICE_ADDRESS = "cached_device";

    private CachedBluetoothDevice mCachedDevice;

    /** Returns a new {@link RemoteRenameDialogFragment} instance for the given {@code device}. */
    public static RemoteRenameDialogFragment newInstance(CachedBluetoothDevice device) {
        Bundle args = new Bundle(1);
        args.putString(KEY_CACHED_DEVICE_ADDRESS, device.getAddress());
        RemoteRenameDialogFragment fragment = new RemoteRenameDialogFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        String deviceAddress = getArguments().getString(KEY_CACHED_DEVICE_ADDRESS);
        LocalBluetoothManager manager = BluetoothUtils.getLocalBtManager(context);
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(
                deviceAddress);
        mCachedDevice = manager.getCachedDeviceManager().findDevice(device);
    }

    @Override
    @StringRes
    protected int getDialogTitle() {
        return R.string.bluetooth_rename_device;
    }

    @Override
    protected String getDeviceName() {
        if (mCachedDevice != null) {
            return mCachedDevice.getName();
        }
        return null;
    }

    @Override
    protected void setDeviceName(String deviceName) {
        if (mCachedDevice != null) {
            mCachedDevice.setName(deviceName);
        }
    }
}
