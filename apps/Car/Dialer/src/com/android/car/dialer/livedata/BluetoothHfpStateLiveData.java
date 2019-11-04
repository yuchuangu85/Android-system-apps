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
import android.bluetooth.BluetoothHeadsetClient;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.android.car.dialer.log.L;

import androidx.lifecycle.LiveData;

/**
 * Provides the connectivity state of HFP Bluetooth profile. States can be one of:
 * <ul>
 * <li>{@link BluetoothProfile#STATE_DISCONNECTED},
 * <li>{@link BluetoothProfile#STATE_CONNECTING},
 * <li>{@link BluetoothProfile#STATE_CONNECTED},
 * <li>{@link BluetoothProfile#STATE_DISCONNECTING}
 * </ul>
 */
public class BluetoothHfpStateLiveData extends LiveData<Integer> {
    private static final String TAG = "CD.BluetoothHfpStateLiveData";

    private final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private final Context mContext;
    private final IntentFilter mIntentFilter = new IntentFilter();

    private BroadcastReceiver mBluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateState();
        }
    };

    /** Creates a new {@link BluetoothHfpStateLiveData}. Call on main thread. */
    public BluetoothHfpStateLiveData(Context context) {
        mContext = context;
        mIntentFilter.addAction(BluetoothHeadsetClient.ACTION_CONNECTION_STATE_CHANGED);
    }

    @Override
    protected void onActive() {
        if (mBluetoothAdapter != null) {
            updateState();
            mContext.registerReceiver(mBluetoothStateReceiver, mIntentFilter);
        }
    }

    @Override
    protected void onInactive() {
        if (mBluetoothAdapter != null) {
            mContext.unregisterReceiver(mBluetoothStateReceiver);
        }
    }

    private void updateState() {
        int state = mBluetoothAdapter.getProfileConnectionState(
                BluetoothProfile.HEADSET_CLIENT);
        if (getValue() == null || state != getValue()) {
            L.d(TAG, "updateState to %s", state);
            setValue(state);
        }
    }
}
