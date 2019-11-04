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

import static android.os.UserManager.DISALLOW_CONFIG_BLUETOOTH;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.os.UserManager;

import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;

/**
 * Encapsulates common functionality for all {@link BluetoothPreferenceController} instances
 * which display state of a specific {@link CachedBluetoothDevice}. The controller will refresh
 * the UI whenever the device properties change. The controller is not available to users with
 * the {@link UserManager#DISALLOW_CONFIG_BLUETOOTH} restriction.
 *
 * @param <V> the upper bound on the type of {@link Preference} on which the controller expects
 *         to operate.
 */
public abstract class BluetoothDevicePreferenceController<V extends Preference> extends
        BluetoothPreferenceController<V> {

    private final CachedBluetoothDevice.Callback mDeviceCallback = this::refreshUi;
    private CachedBluetoothDevice mCachedDevice;

    public BluetoothDevicePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    /**
     * Sets the {@link CachedBluetoothDevice} which the controller represents. The device must be
     * set or an {@link IllegalStateException} will be thrown when this controller begins its
     * lifecycle.
     */
    public void setCachedDevice(CachedBluetoothDevice device) {
        mCachedDevice = device;
    }

    /**
     * Returns the {@link CachedBluetoothDevice} represented by this controller.
     */
    public CachedBluetoothDevice getCachedDevice() {
        return mCachedDevice;
    }


    @Override
    protected void checkInitialized() {
        if (mCachedDevice == null) {
            throw new IllegalStateException("Must be initialized with a CachedBluetoothDevice");
        }
    }

    @Override
    protected int getAvailabilityStatus() {
        int availabilityStatus = super.getAvailabilityStatus();
        if (availabilityStatus == AVAILABLE) {
            return getCarUserManagerHelper().isCurrentProcessUserHasRestriction(
                    DISALLOW_CONFIG_BLUETOOTH) ? DISABLED_FOR_USER : AVAILABLE;
        }
        return availabilityStatus;
    }

    @Override
    protected void onStartInternal() {
        super.onStartInternal();
        mCachedDevice.registerCallback(mDeviceCallback);
    }

    @Override
    protected void onStopInternal() {
        super.onStopInternal();
        mCachedDevice.unregisterCallback(mDeviceCallback);
    }
}
