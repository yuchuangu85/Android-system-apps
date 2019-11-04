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

import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;

import android.content.Context;

import androidx.preference.SwitchPreference;

import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.LocalBluetoothProfile;
import com.android.settingslib.bluetooth.PanProfile;

/**
 * Preference that represents a {@link LocalBluetoothProfile} for a {@link CachedBluetoothDevice}.
 */
public class BluetoothDeviceProfilePreference extends SwitchPreference {

    private final LocalBluetoothProfile mProfile;
    private final CachedBluetoothDevice mCachedDevice;
    private final CachedBluetoothDevice.Callback mDeviceCallback = this::refreshUi;

    public BluetoothDeviceProfilePreference(Context context, LocalBluetoothProfile profile,
            CachedBluetoothDevice cachedDevice) {
        super(context);
        mProfile = profile;
        mCachedDevice = cachedDevice;
        setKey(profile.toString());
        setTitle(profile.getNameResource(cachedDevice.getDevice()));
    }

    /**
     * Returns the {@link LocalBluetoothProfile} represented by this preference.
     */
    public LocalBluetoothProfile getProfile() {
        return mProfile;
    }

    /**
     * Returns the {@link CachedBluetoothDevice} used to construct this preference.
     */
    public CachedBluetoothDevice getCachedDevice() {
        return mCachedDevice;
    }

    @Override
    public void onAttached() {
        super.onAttached();
        mCachedDevice.registerCallback(mDeviceCallback);
        refreshUi();
    }

    @Override
    public void onDetached() {
        super.onDetached();
        mCachedDevice.unregisterCallback(mDeviceCallback);
    }

    private void refreshUi() {
        setEnabled(!mCachedDevice.isBusy());
        if (mProfile instanceof PanProfile) {
            setChecked(
                    mProfile.getConnectionStatus(mCachedDevice.getDevice()) == STATE_CONNECTED);
        } else {
            setChecked(mProfile.isPreferred(mCachedDevice.getDevice()));
        }
    }
}
