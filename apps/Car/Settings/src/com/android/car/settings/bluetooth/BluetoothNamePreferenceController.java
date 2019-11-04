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

import android.bluetooth.BluetoothAdapter;
import android.car.drivingstate.CarUxRestrictions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;

/**
 * Displays the name of the local Bluetooth adapter. When the associated preference is clicked, a
 * dialog is shown to allow the user to update the adapter name. If the user has the {@link
 * DISALLOW_CONFIG_BLUETOOTH} restriction, interaction with the preference is disabled.
 */
public class BluetoothNamePreferenceController extends BluetoothPreferenceController<Preference> {

    private final IntentFilter mIntentFilter = new IntentFilter(
            BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshUi();
        }
    };

    public BluetoothNamePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    protected void onStartInternal() {
        super.onStartInternal();
        getContext().registerReceiver(mReceiver, mIntentFilter);
    }

    @Override
    protected void onStopInternal() {
        super.onStopInternal();
        getContext().unregisterReceiver(mReceiver);
    }

    @Override
    protected void updateState(Preference preference) {
        preference.setSelectable(!getCarUserManagerHelper().isCurrentProcessUserHasRestriction(
                DISALLOW_CONFIG_BLUETOOTH));
        preference.setSummary(BluetoothAdapter.getDefaultAdapter().getName());
    }

    @Override
    protected boolean handlePreferenceClicked(Preference preference) {
        getFragmentController().showDialog(new LocalRenameDialogFragment(),
                LocalRenameDialogFragment.TAG);
        return true;
    }
}
