/*
 * Copyright (C) 2019 The Android Open Source Project
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
import android.provider.Settings;

import androidx.preference.TwoStatePreference;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

/**
 * Controls wifi tethering auto off configuration
 */
public class WifiTetherAutoOffPreferenceController extends
        PreferenceController<TwoStatePreference> {

    public WifiTetherAutoOffPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<TwoStatePreference> getPreferenceType() {
        return TwoStatePreference.class;
    }

    @Override
    protected void updateState(TwoStatePreference preference) {
        boolean settingsOn = Settings.Global.getInt(getContext().getContentResolver(),
                Settings.Global.SOFT_AP_TIMEOUT_ENABLED, 1) != 0;
        preference.setChecked(settingsOn);
    }

    @Override
    protected boolean handlePreferenceChanged(TwoStatePreference preference, Object newValue) {
        boolean settingsOn = (Boolean) newValue;
        Settings.Global.putInt(getContext().getContentResolver(),
                Settings.Global.SOFT_AP_TIMEOUT_ENABLED, settingsOn ? 1 : 0);
        return true;
    }
}
