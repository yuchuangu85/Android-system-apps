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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.StringRes;

import com.android.car.settings.R;

/** Dialog for changing the advertised name of the local bluetooth adapter. */
public class LocalRenameDialogFragment extends BluetoothRenameDialogFragment {

    /** Tag identifying the dialog for changing the name of the local Bluetooth adapter. */
    public static final String TAG = "LocalBluetoothRename";

    private BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    private final IntentFilter mFilter = new IntentFilter(
            BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateDeviceName();
        }
    };

    @Override
    public void onStart() {
        super.onStart();
        requireContext().registerReceiver(mReceiver, mFilter);
    }

    @Override
    public void onStop() {
        super.onStop();
        requireContext().unregisterReceiver(mReceiver);
    }

    @Override
    @StringRes
    protected int getDialogTitle() {
        return R.string.bluetooth_rename_vehicle;
    }

    @Override
    protected String getDeviceName() {
        if (mBluetoothAdapter.isEnabled()) {
            return mBluetoothAdapter.getName();
        }
        return null;
    }

    @Override
    protected void setDeviceName(String deviceName) {
        mBluetoothAdapter.setName(deviceName);
    }
}
