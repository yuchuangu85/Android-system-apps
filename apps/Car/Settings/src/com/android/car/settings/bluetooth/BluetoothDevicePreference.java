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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.SystemProperties;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.preference.Preference;

import com.android.car.apps.common.util.Themes;
import com.android.car.settings.R;
import com.android.car.settings.common.ButtonPreference;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;

/**
 * Preference which represents a specific {@link CachedBluetoothDevice}. The title, icon, and
 * summary are kept in sync with the device when the preference is shown. When the device is busy,
 * the preference is disabled. The equality and sort order of this preference is determined by the
 * underlying cached device {@link CachedBluetoothDevice#equals(Object)} and {@link
 * CachedBluetoothDevice#compareTo(CachedBluetoothDevice)}. If two devices are considered equal, the
 * default preference sort ordering is used (see {@link #compareTo(Preference)}.
 */
public class BluetoothDevicePreference extends ButtonPreference {
    private static final String BLUETOOTH_SHOW_DEVICES_WITHOUT_NAMES_PROPERTY =
            "persist.bluetooth.showdeviceswithoutnames";

    private final CachedBluetoothDevice mCachedDevice;
    private final boolean mShowDevicesWithoutNames;
    private final CachedBluetoothDevice.Callback mDeviceCallback = this::refreshUi;

    public BluetoothDevicePreference(Context context, CachedBluetoothDevice cachedDevice) {
        super(context);
        mCachedDevice = cachedDevice;
        mShowDevicesWithoutNames = SystemProperties.getBoolean(
                BLUETOOTH_SHOW_DEVICES_WITHOUT_NAMES_PROPERTY, false);
        // Hide action by default.
        showAction(false);
    }

    /**
     * Returns the {@link CachedBluetoothDevice} represented by this preference.
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
        setTitle(mCachedDevice.getName());
        setSummary(mCachedDevice.getCarConnectionSummary());

        final Pair<Drawable, String> pair = com.android.settingslib.bluetooth.BluetoothUtils
                .getBtClassDrawableWithDescription(getContext(), mCachedDevice);
        if (pair.first != null) {
            setIcon(pair.first);
            getIcon().setTintList(Themes.getAttrColorStateList(getContext(), R.attr.iconColor));
        }

        setEnabled(!mCachedDevice.isBusy());
        setVisible(mShowDevicesWithoutNames || mCachedDevice.hasHumanReadableName());

        // Notify since the ordering may have changed.
        notifyHierarchyChanged();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BluetoothDevicePreference)) {
            return false;
        }
        return mCachedDevice.equals(((BluetoothDevicePreference) o).mCachedDevice);
    }

    @Override
    public int hashCode() {
        return mCachedDevice.hashCode();
    }

    @Override
    public int compareTo(@NonNull Preference another) {
        if (!(another instanceof BluetoothDevicePreference)) {
            // Rely on default sort.
            return super.compareTo(another);
        }

        return mCachedDevice
                .compareTo(((BluetoothDevicePreference) another).mCachedDevice);
    }
}
