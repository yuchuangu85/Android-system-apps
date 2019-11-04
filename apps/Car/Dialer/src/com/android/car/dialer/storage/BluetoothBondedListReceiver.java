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

package com.android.car.dialer.storage;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.Collections;
import java.util.Set;

/**
 * Broadcast receiver that monitors the bluetooth device unpair event and removes entries for
 * devices that has been unpaired.
 */
public class BluetoothBondedListReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() != BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
            return;
        }

        if (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                == BluetoothDevice.BOND_NONE) {
            FavoriteNumberRepository favoriteNumberRepository =
                    FavoriteNumberRepository.getRepository(context);
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter == null ? Collections.emptySet()
                    : bluetoothAdapter.getBondedDevices();
            favoriteNumberRepository.cleanup(pairedDevices);
        }
    }
}
