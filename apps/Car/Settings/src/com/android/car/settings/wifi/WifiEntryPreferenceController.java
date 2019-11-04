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

package com.android.car.settings.wifi;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.MasterSwitchPreference;
import com.android.car.settings.common.PreferenceController;

/**
 * Controller which determines if the top level entry into Wi-Fi settings should be displayed
 * based on device capabilities.
 */
public class WifiEntryPreferenceController extends PreferenceController<MasterSwitchPreference> {

    private CarWifiManager mCarWifiManager;

    public WifiEntryPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mCarWifiManager = new CarWifiManager(context);
    }

    @Override
    protected Class<MasterSwitchPreference> getPreferenceType() {
        return MasterSwitchPreference.class;
    }

    @Override
    protected void onCreateInternal() {
        getPreference().setSwitchToggleListener((preference, isChecked) -> {
            if (isChecked != mCarWifiManager.isWifiEnabled()) {
                mCarWifiManager.setWifiEnabled(isChecked);
            }
        });
    }

    @Override
    protected int getAvailabilityStatus() {
        return WifiUtil.isWifiAvailable(getContext()) ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    protected void updateState(MasterSwitchPreference preference) {
        preference.setSwitchChecked(mCarWifiManager.isWifiEnabled());
    }
}
