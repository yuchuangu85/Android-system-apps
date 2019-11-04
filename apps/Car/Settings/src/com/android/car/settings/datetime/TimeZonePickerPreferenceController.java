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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Settings;

import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.settingslib.datetime.ZoneGetter;

import java.util.Calendar;

/**
 * Business logic for the preference that allows for changing the timezone.
 */
public class TimeZonePickerPreferenceController extends PreferenceController<Preference> {

    private final IntentFilter mIntentFilter;
    private final BroadcastReceiver mTimeChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshUi();
        }
    };

    public TimeZonePickerPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);

        // Listen to ACTION_TIME_CHANGED because it could be sent by the autoTimeZone toggle.
        // Listen to ACTION_TIMEZONE_CHANGED because the change in timezone should update the
        // description of the preference.
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(Intent.ACTION_TIME_CHANGED);
        mIntentFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    /** Starts the broadcast receiver which listens for time changes */
    @Override
    protected void onStartInternal() {
        getContext().registerReceiver(mTimeChangeReceiver, mIntentFilter);
    }

    /** Stops the broadcast receiver which listens for time changes */
    @Override
    protected void onStopInternal() {
        getContext().unregisterReceiver(mTimeChangeReceiver);
    }

    @Override
    protected void updateState(Preference preference) {
        Calendar now = Calendar.getInstance();
        preference.setSummary(ZoneGetter.getTimeZoneOffsetAndName(getContext(), now.getTimeZone(),
                now.getTime()));
        preference.setEnabled(!autoTimezoneIsEnabled());
    }

    private boolean autoTimezoneIsEnabled() {
        return Settings.Global.getInt(getContext().getContentResolver(),
                Settings.Global.AUTO_TIME_ZONE, 0) > 0;
    }
}
