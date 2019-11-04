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

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.common.FragmentController;
import com.android.settingslib.bluetooth.LocalBluetoothProfile;

/**
 * Displays toggles for Bluetooth profiles supported by a device. Toggling a profile on will set it
 * as preferred and attempt a connection. Toggling a profile off will disconnect the profile. If no
 * profiles are supported, the preference is hidden.
 */
public class BluetoothDeviceProfilesPreferenceController extends
        BluetoothDevicePreferenceController<PreferenceGroup> {

    private final Preference.OnPreferenceChangeListener mProfileChangeListener =
            (preference, newValue) -> {
                boolean isChecked = (boolean) newValue;
                BluetoothDeviceProfilePreference profilePref =
                        (BluetoothDeviceProfilePreference) preference;
                LocalBluetoothProfile profile = profilePref.getProfile();
                profile.setPreferred(profilePref.getCachedDevice().getDevice(), isChecked);
                if (isChecked) {
                    getCachedDevice().connectProfile(profile);
                } else {
                    getCachedDevice().disconnect(profile);
                }
                return true;
            };

    public BluetoothDeviceProfilesPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<PreferenceGroup> getPreferenceType() {
        return PreferenceGroup.class;
    }

    @Override
    protected void updateState(PreferenceGroup preferenceGroup) {
        for (LocalBluetoothProfile profile : getCachedDevice().getProfiles()) {
            Preference profilePref = preferenceGroup.findPreference(profile.toString());
            if (profilePref == null) {
                profilePref = new BluetoothDeviceProfilePreference(getContext(), profile,
                        getCachedDevice());
                profilePref.setOnPreferenceChangeListener(mProfileChangeListener);
                preferenceGroup.addPreference(profilePref);
            }
        }
        for (LocalBluetoothProfile removedProfile : getCachedDevice().getRemovedProfiles()) {
            Preference prefToRemove = preferenceGroup.findPreference(removedProfile.toString());
            if (prefToRemove != null) {
                preferenceGroup.removePreference(prefToRemove);
            }
        }
        preferenceGroup.setVisible(preferenceGroup.getPreferenceCount() > 0);
    }
}
