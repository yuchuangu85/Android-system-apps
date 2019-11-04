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

package com.android.car.dialer.testutils;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;

import androidx.annotation.Nullable;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowApplication;

import java.util.HashMap;
import java.util.Map;

/**
 * Derived from {@link org.robolectric.shadows.ShadowBluetoothAdapter}
 *
 * Needed for Bluetooth related tests because the default ShadowBluetooth Adapter does not include
 * an implementation of setProfileConnectionState.
 */
@Implements(value = BluetoothAdapter.class)
public class ShadowBluetoothAdapterForDialer extends
        org.robolectric.shadows.ShadowBluetoothAdapter {

    private static boolean bluetoothAvailable = true;
    private Map<Integer, Integer> profileConnectionStateData = new HashMap<>();

    @Nullable
    @Implementation
    public static synchronized BluetoothAdapter getDefaultAdapter() {
        if (!bluetoothAvailable) {
            return null;
        }
        return (BluetoothAdapter) ShadowApplication.getInstance().getBluetoothAdapter();
    }

    /**
     * Sets if the default Bluetooth Adapter is null
     */
    public static void setBluetoothAvailable(boolean available) {
        bluetoothAvailable = available;
    }
}
