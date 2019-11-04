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
import static android.os.UserManager.DISALLOW_CONFIG_BLUETOOTH;

import android.bluetooth.BluetoothAdapter;
import android.car.drivingstate.CarUxRestrictions;
import android.car.userlib.CarUserManagerHelper;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;

import androidx.lifecycle.LifecycleObserver;
import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

/**
 * Controls a preference that, when clicked, launches the page for pairing new Bluetooth devices.
 * The associated preference for this controller should define the fragment attribute or an intent
 * to launch for the Bluetooth device pairing page. If the adapter is not enabled, a click will
 * enable Bluetooth. The summary message is updated to indicate this effect to the user.
 */
public class PairNewDevicePreferenceController extends PreferenceController<Preference> implements
        LifecycleObserver {

    private final CarUserManagerHelper mCarUserManagerHelper;
    private final IntentFilter mIntentFilter = new IntentFilter(
            BluetoothAdapter.ACTION_STATE_CHANGED);
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshUi();
        }
    };

    public PairNewDevicePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mCarUserManagerHelper = new CarUserManagerHelper(context);
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    protected void checkInitialized() {
        if (getPreference().getIntent() == null && getPreference().getFragment() == null) {
            throw new IllegalStateException(
                    "Preference should declare fragment or intent for page to pair new devices");
        }
    }

    @Override
    protected int getAvailabilityStatus() {
        if (!getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            return UNSUPPORTED_ON_DEVICE;
        }
        return isUserRestricted() ? DISABLED_FOR_USER : AVAILABLE;
    }

    private boolean isUserRestricted() {
        return mCarUserManagerHelper.isCurrentProcessUserHasRestriction(DISALLOW_BLUETOOTH)
                || mCarUserManagerHelper.isCurrentProcessUserHasRestriction(
                DISALLOW_CONFIG_BLUETOOTH);
    }

    @Override
    protected void onStartInternal() {
        getContext().registerReceiver(mReceiver, mIntentFilter);
    }

    @Override
    protected void onStopInternal() {
        getContext().unregisterReceiver(mReceiver);
    }

    @Override
    protected void updateState(Preference preference) {
        preference.setSummary(
                BluetoothAdapter.getDefaultAdapter().isEnabled() ? "" : getContext().getString(
                        R.string.bluetooth_pair_new_device_summary));
    }

    @Override
    protected boolean handlePreferenceClicked(Preference preference) {
        // Enable the adapter if it is not on (user is notified via summary message).
        BluetoothAdapter.getDefaultAdapter().enable();
        return false; // Don't handle so that preference framework will launch pairing fragment.
    }
}
