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

import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

import java.util.Calendar;

/**
 * Business logic for the preference which allows for picking the date.
 */
public class DatePickerPreferenceController extends PreferenceController<Preference> {

    private final IntentFilter mIntentFilter;
    private final BroadcastReceiver mTimeChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshUi();
        }
    };

    public DatePickerPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        // Listens to all three actions because they can all affect the date shown on the
        // screen.
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(Intent.ACTION_TIME_CHANGED);
        mIntentFilter.addAction(Intent.ACTION_TIME_TICK);
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
    public void updateState(Preference preference) {
        preference.setSummary(DateFormat.getLongDateFormat(getContext()).format(
                Calendar.getInstance().getTime()));
        preference.setEnabled(!autoDatetimeIsEnabled());
    }

    private boolean autoDatetimeIsEnabled() {
        return Settings.Global.getInt(getContext().getContentResolver(),
                Settings.Global.AUTO_TIME, 0) > 0;
    }
}

