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

import static android.os.UserManager.DISALLOW_BLUETOOTH;

import android.bluetooth.BluetoothAdapter;
import android.car.drivingstate.CarUxRestrictions;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.CallSuper;
import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.settingslib.bluetooth.BluetoothCallback;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.LocalBluetoothManager;

/**
 * Abstract {@link PreferenceController} that listens to {@link BluetoothCallback} events and
 * exposes the interface methods for override. It implements a default
 * {@link #getAvailabilityStatus()} which is {@link #AVAILABLE} when the  system supports
 * Bluetooth, the current user is not restricted by {@link DISALLOW_BLUETOOTH}, and the default
 * Bluetooth adapter is enabled.
 *
 * @param <V> the upper bound on the type of {@link Preference} on which the controller expects
 *         to operate.
 */
public abstract class BluetoothPreferenceController<V extends Preference> extends
        PreferenceController<V> implements BluetoothCallback {

    private final CarUserManagerHelper mCarUserManagerHelper;
    private final LocalBluetoothManager mBluetoothManager;

    public BluetoothPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mCarUserManagerHelper = new CarUserManagerHelper(context);
        mBluetoothManager = BluetoothUtils.getLocalBtManager(context);
    }

    /** Returns a {@link CarUserManagerHelper} constructed from the controller context. */
    protected final CarUserManagerHelper getCarUserManagerHelper() {
        return mCarUserManagerHelper;
    }

    /**
     * Returns a {@link LocalBluetoothManager} retrieved with the controller context. This is
     * not {@code null} unless {@link #getAvailabilityStatus()} returns
     * {@link #UNSUPPORTED_ON_DEVICE}.
     */
    protected final LocalBluetoothManager getBluetoothManager() {
        return mBluetoothManager;
    }

    @Override
    protected int getAvailabilityStatus() {
        if (!getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            return UNSUPPORTED_ON_DEVICE;
        }
        if (mCarUserManagerHelper.isCurrentProcessUserHasRestriction(DISALLOW_BLUETOOTH)) {
            return DISABLED_FOR_USER;
        }
        return BluetoothAdapter.getDefaultAdapter().isEnabled() ? AVAILABLE
                : CONDITIONALLY_UNAVAILABLE;
    }

    @Override
    @CallSuper
    protected void onStartInternal() {
        mBluetoothManager.getEventManager().registerCallback(this);
    }

    @Override
    @CallSuper
    protected void onStopInternal() {
        mBluetoothManager.getEventManager().unregisterCallback(this);
    }

    @Override
    @CallSuper
    public void onBluetoothStateChanged(int bluetoothState) {
        refreshUi();
    }

    @Override
    public void onScanningStateChanged(boolean started) {
    }

    @Override
    public void onDeviceAdded(CachedBluetoothDevice cachedDevice) {
    }

    @Override
    public void onDeviceDeleted(CachedBluetoothDevice cachedDevice) {
    }

    @Override
    public void onDeviceBondStateChanged(CachedBluetoothDevice cachedDevice, int bondState) {
    }

    @Override
    public void onConnectionStateChanged(CachedBluetoothDevice cachedDevice, int state) {
    }

    @Override
    public void onActiveDeviceChanged(CachedBluetoothDevice activeDevice, int bluetoothProfile) {
    }

    @Override
    public void onAudioModeChanged() {
    }
}
