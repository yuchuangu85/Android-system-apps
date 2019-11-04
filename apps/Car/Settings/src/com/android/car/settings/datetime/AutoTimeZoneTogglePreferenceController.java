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

package com.android.car.settings.datetime;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import androidx.preference.TwoStatePreference;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

/**
 * Business logic for the toggle which chooses to use the network provided time zone.
 */
public class AutoTimeZoneTogglePreferenceController extends
        PreferenceController<TwoStatePreference> {

    public AutoTimeZoneTogglePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<TwoStatePreference> getPreferenceType() {
        return TwoStatePreference.class;
    }

    @Override
    protected boolean handlePreferenceChanged(TwoStatePreference preference, Object newValue) {
        boolean isAutoTimezoneEnabled = (boolean) newValue;
        Settings.Global.putInt(getContext().getContentResolver(), Settings.Global.AUTO_TIME_ZONE,
                isAutoTimezoneEnabled ? 1 : 0);

        getContext().sendBroadcast(new Intent(Intent.ACTION_TIME_CHANGED));
        return true;
    }

    @Override
    protected void updateState(TwoStatePreference preference) {
        preference.setChecked(isEnabled());
    }

    private boolean isEnabled() {
        return Settings.Global.getInt(getContext().getContentResolver(),
                Settings.Global.AUTO_TIME_ZONE, 0) > 0;
    }
}
