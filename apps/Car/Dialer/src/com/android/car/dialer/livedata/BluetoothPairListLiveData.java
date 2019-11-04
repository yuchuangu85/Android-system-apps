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
 * limitations under the License.
 */

package com.android.car.dialer.livedata;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.MainThread;
import androidx.lifecycle.LiveData;

import com.android.car.dialer.log.L;

import java.util.Set;

/**
 * Provides a list of paired Bluetooth devices.
 */
public class BluetoothPairListLiveData extends LiveData<Set<BluetoothDevice>> {
    private static final String TAG = "CD.BluetoothPairListLiveData";

    private final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private final Context mContext;
    private final IntentFilter mIntentFilter = new IntentFilter();

    private BroadcastReceiver mBluetoothPairListReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateList();
        }
    };

    /** Creates a new {@link BluetoothPairListLiveData}. Call on main thread. */
    @MainThread
    public BluetoothPairListLiveData(Context context) {
        mContext = context;
        mIntentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
    }

    @Override
    protected void onActive() {
        if (mBluetoothAdapter != null) {
            updateList();
            mContext.registerReceiver(mBluetoothPairListReceiver, mIntentFilter);
        }
    }

    @Override
    protected void onInactive() {
        if (mBluetoothAdapter != null) {
            mContext.unregisterReceiver(mBluetoothPairListReceiver);
        }
    }

    private void updateList() {
        Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();
        L.d(TAG, "updateList to %s", devices);
        setValue(devices);
    }
}
