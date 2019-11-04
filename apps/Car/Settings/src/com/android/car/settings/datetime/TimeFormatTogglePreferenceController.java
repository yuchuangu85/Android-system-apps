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
import android.text.format.DateFormat;

import androidx.preference.TwoStatePreference;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

import java.util.Calendar;

/**
 * Business logic for toggle which chooses between 12 hour or 24 hour formats.
 */
public class TimeFormatTogglePreferenceController extends PreferenceController<TwoStatePreference> {
    public static final String HOURS_12 = "12";
    public static final String HOURS_24 = "24";

    private static final int DEMO_MONTH = 11;
    private static final int DEMO_DAY_OF_MONTH = 31;
    private static final int DEMO_HOUR_OF_DAY = 13;
    private static final int DEMO_MINUTE = 0;
    private static final int DEMO_SECOND = 0;
    private final Calendar mTimeFormatDemoDate = Calendar.getInstance();
    private final BroadcastReceiver mTimeChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshUi();
        }
    };

    public TimeFormatTogglePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<TwoStatePreference> getPreferenceType() {
        return TwoStatePreference.class;
    }

    /** Starts the broadcast receiver which listens for time changes */
    @Override
    protected void onStartInternal() {
        // Listens to ACTION_TIME_CHANGED because the description needs to be changed based on
        // the ACTION_TIME_CHANGED intent that this toggle sends.
        getContext().registerReceiver(mTimeChangeReceiver,
                new IntentFilter(Intent.ACTION_TIME_CHANGED));
    }

    /** Stops the broadcast receiver which listens for time changes */
    @Override
    protected void onStopInternal() {
        getContext().unregisterReceiver(mTimeChangeReceiver);
    }

    @Override
    protected void updateState(TwoStatePreference preference) {
        Calendar now = Calendar.getInstance();
        mTimeFormatDemoDate.setTimeZone(now.getTimeZone());
        // We use December 31st because it's unambiguous when demonstrating the date format.
        // We use 13:00 so we can demonstrate the 12/24 hour options.
        mTimeFormatDemoDate.set(now.get(Calendar.YEAR), DEMO_MONTH, DEMO_DAY_OF_MONTH,
                DEMO_HOUR_OF_DAY, DEMO_MINUTE, DEMO_SECOND);
        preference.setSummary(
                DateFormat.getTimeFormat(getContext()).format(mTimeFormatDemoDate.getTime()));
        preference.setChecked(is24Hour());
    }

    @Override
    protected boolean handlePreferenceChanged(TwoStatePreference preference, Object newValue) {
        boolean isUse24HourFormatEnabled = (boolean) newValue;
        Settings.System.putString(getContext().getContentResolver(),
                Settings.System.TIME_12_24,
                isUse24HourFormatEnabled ? HOURS_24 : HOURS_12);
        Intent timeChanged = new Intent(Intent.ACTION_TIME_CHANGED);
        int timeFormatPreference =
                isUse24HourFormatEnabled ? Intent.EXTRA_TIME_PREF_VALUE_USE_24_HOUR
                        : Intent.EXTRA_TIME_PREF_VALUE_USE_12_HOUR;
        timeChanged.putExtra(Intent.EXTRA_TIME_PREF_24_HOUR_FORMAT,
                timeFormatPreference);
        getContext().sendBroadcast(timeChanged);
        return true;
    }

    private boolean is24Hour() {
        return DateFormat.is24HourFormat(getContext());
    }
}
