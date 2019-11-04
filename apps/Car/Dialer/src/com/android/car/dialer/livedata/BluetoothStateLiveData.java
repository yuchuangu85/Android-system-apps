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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.IntDef;
import androidx.lifecycle.LiveData;

import com.android.car.dialer.log.L;

/**
 * Provides the device Bluetooth availability. Updates client with {@link BluetoothState}.
 */
public class BluetoothStateLiveData extends LiveData<Integer> {
    private static final String TAG = "CD.BluetoothStateLiveData";

    @IntDef({
            BluetoothState.UNKNOWN,
            BluetoothState.DISABLED,
            BluetoothState.ENABLED,
    })
    public @interface BluetoothState {
        /** Bluetooth is not supported on the current device */
        int UNKNOWN = 0;
        /** Bluetooth is disabled */
        int DISABLED = 1;
        /** Bluetooth is enabled */
        int ENABLED = 2;
    }

    private final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private final Context mContext;
    private final IntentFilter mIntentFilter = new IntentFilter();

    private BroadcastReceiver mBluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateState();
        }
    };

    /** Creates a new {@link BluetoothStateLiveData}. Call on main thread. */
    public BluetoothStateLiveData(Context context) {
        mContext = context;
        mIntentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
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
        @BluetoothState int state = BluetoothState.UNKNOWN;
        if (mBluetoothAdapter != null) {
            state = mBluetoothAdapter.isEnabled() ? BluetoothState.ENABLED
                    : BluetoothState.DISABLED;
        }

        if (getValue() == null || state != getValue()) {
            L.d(TAG, "updateState to %s", state);
            setValue(state);
        }
    }
}
