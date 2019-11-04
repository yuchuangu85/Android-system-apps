/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.settings.location;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.widget.Switch;

import androidx.annotation.LayoutRes;
import androidx.annotation.XmlRes;

import com.android.car.settings.R;
import com.android.car.settings.common.SettingsFragment;
import com.android.settingslib.Utils;

/**
 * Main page that hosts Location related preferences.
 */
public class LocationSettingsFragment extends SettingsFragment {
    private static final IntentFilter INTENT_FILTER_LOCATION_MODE_CHANGED =
            new IntentFilter(LocationManager.MODE_CHANGED_ACTION);

    private LocationManager mLocationManager;
    private Switch mLocationSwitch;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mLocationSwitch.setChecked(mLocationManager.isLocationEnabled());
        }
    };

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.location_settings_fragment;
    }

    @Override
    @LayoutRes
    protected int getActionBarLayoutId() {
        return R.layout.action_bar_with_toggle;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mLocationManager = (LocationManager) context.getSystemService(Service.LOCATION_SERVICE);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mLocationSwitch = requireActivity().findViewById(R.id.toggle_switch);
    }

    @Override
    public void onStart() {
        super.onStart();
        requireContext().registerReceiver(mReceiver, INTENT_FILTER_LOCATION_MODE_CHANGED);
        updateLocationSwitch();
    }

    @Override
    public void onStop() {
        super.onStop();
        requireContext().unregisterReceiver(mReceiver);
    }

    // Update the location master switch's state upon starting the fragment.
    private void updateLocationSwitch() {
        mLocationSwitch.setChecked(mLocationManager.isLocationEnabled());
        mLocationSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                Utils.updateLocationEnabled(requireContext(), isChecked, UserHandle.myUserId(),
                        Settings.Secure.LOCATION_CHANGER_SYSTEM_SETTINGS));
    }
}
