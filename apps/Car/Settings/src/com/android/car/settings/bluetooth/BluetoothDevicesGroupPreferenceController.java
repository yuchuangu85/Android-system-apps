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

import android.annotation.CallSuper;
import android.bluetooth.BluetoothAdapter;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.common.FragmentController;
import com.android.settingslib.bluetooth.BluetoothCallback;
import com.android.settingslib.bluetooth.BluetoothDeviceFilter;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Manages a group of Bluetooth devices by adding preferences for devices that pass a subclass
 * defined filter and removing preferences for devices that no longer pass. Subclasses are
 * dispatched click events on individual preferences to customize the behavior.
 *
 * <p>Note: {@link #refreshUi()} is called whenever a device is added or removed with {@link
 * #onDeviceAdded(CachedBluetoothDevice)} or {@link #onDeviceDeleted(CachedBluetoothDevice)}.
 * Subclasses should listen to state changes (and possibly override additional {@link
 * BluetoothCallback} methods) and call {@link #refreshUi()} for changes which affect their
 * implementation of {@link #getDeviceFilter()}.
 */
public abstract class BluetoothDevicesGroupPreferenceController extends
        BluetoothPreferenceController<PreferenceGroup> {

    private final Map<CachedBluetoothDevice, BluetoothDevicePreference> mPreferenceMap =
            new HashMap<>();
    private final Preference.OnPreferenceClickListener mDevicePreferenceClickListener =
            preference -> {
                onDeviceClicked(((BluetoothDevicePreference) preference).getCachedDevice());
                return true;
            };

    public BluetoothDevicesGroupPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<PreferenceGroup> getPreferenceType() {
        return PreferenceGroup.class;
    }

    /**
     * Returns a filter for which devices should be included in the group. Devices that do not
     * pass the filter will not be added. Added devices that no longer pass the filter will be
     * removed.
     */
    protected abstract BluetoothDeviceFilter.Filter getDeviceFilter();

    /**
     * Returns a newly created {@link BluetoothDevicePreference} for the given {@link
     * CachedBluetoothDevice}. Subclasses may override this method to customize how devices are
     * represented in the group.
     */
    protected BluetoothDevicePreference createDevicePreference(CachedBluetoothDevice cachedDevice) {
        return new BluetoothDevicePreference(getContext(), cachedDevice);
    }

    /**
     * Called when a preference in the group is clicked.
     *
     * @param cachedDevice the device represented by the clicked preference.
     */
    protected abstract void onDeviceClicked(CachedBluetoothDevice cachedDevice);

    /**
     * Returns a mapping of all {@link CachedBluetoothDevice} instances represented by this group
     * and their associated preferences.
     */
    protected Map<CachedBluetoothDevice, BluetoothDevicePreference> getPreferenceMap() {
        return mPreferenceMap;
    }

    @Override
    @CallSuper
    protected void updateState(PreferenceGroup preferenceGroup) {
        Collection<CachedBluetoothDevice> cachedDevices =
                getBluetoothManager().getCachedDeviceManager().getCachedDevicesCopy();

        Set<CachedBluetoothDevice> devicesToRemove = new HashSet<>(mPreferenceMap.keySet());
        devicesToRemove.removeAll(cachedDevices);
        for (CachedBluetoothDevice deviceToRemove : devicesToRemove) {
            removePreference(deviceToRemove);
        }

        for (CachedBluetoothDevice cachedDevice : cachedDevices) {
            if (getDeviceFilter().matches(cachedDevice.getDevice())) {
                addPreference(cachedDevice);
            } else {
                removePreference(cachedDevice);
            }
        }

        preferenceGroup.setVisible(preferenceGroup.getPreferenceCount() > 0);
    }

    @Override
    public final void onBluetoothStateChanged(int bluetoothState) {
        super.onBluetoothStateChanged(bluetoothState);
        if (bluetoothState == BluetoothAdapter.STATE_TURNING_OFF) {
            // Cleanup the UI so that we don't have stale representations when the adapter turns
            // on again. This can happen if Bluetooth crashes and restarts.
            getPreference().removeAll();
            mPreferenceMap.clear();
        }
    }

    @Override
    public final void onDeviceAdded(CachedBluetoothDevice cachedDevice) {
        refreshUi();
    }

    @Override
    public final void onDeviceDeleted(CachedBluetoothDevice cachedDevice) {
        refreshUi();
    }

    private void addPreference(CachedBluetoothDevice cachedDevice) {
        if (!mPreferenceMap.containsKey(cachedDevice)) {
            BluetoothDevicePreference devicePreference = createDevicePreference(cachedDevice);
            devicePreference.setOnPreferenceClickListener(mDevicePreferenceClickListener);
            mPreferenceMap.put(cachedDevice, devicePreference);
            getPreference().addPreference(devicePreference);
        }
    }

    private void removePreference(CachedBluetoothDevice cachedDevice) {
        if (mPreferenceMap.containsKey(cachedDevice)) {
            getPreference().removePreference(mPreferenceMap.get(cachedDevice));
            mPreferenceMap.remove(cachedDevice);
        }
    }
}
