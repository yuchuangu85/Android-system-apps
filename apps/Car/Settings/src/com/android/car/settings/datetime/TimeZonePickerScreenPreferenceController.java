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

import android.app.AlarmManager;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.settingslib.datetime.ZoneGetter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Business logic which will populate the timezone options.
 */
public class TimeZonePickerScreenPreferenceController extends
        PreferenceController<PreferenceGroup> {

    private List<Preference> mZonesList;
    @VisibleForTesting
    AlarmManager mAlarmManager;

    public TimeZonePickerScreenPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mAlarmManager = (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
    }

    @Override
    protected Class<PreferenceGroup> getPreferenceType() {
        return PreferenceGroup.class;
    }

    @Override
    protected void updateState(PreferenceGroup preferenceGroup) {
        if (mZonesList == null) {
            constructTimeZoneList();
        }
        for (Preference zonePreference : mZonesList) {
            preferenceGroup.addPreference(zonePreference);
        }
    }

    private void constructTimeZoneList() {
        // We load all of the time zones on the UI thread. However it shouldn't be very expensive
        // and also shouldn't take a long time. We can revisit this to setup background work and
        // paging, if it becomes an issue.
        List<Map<String, Object>> zones = ZoneGetter.getZonesList(getContext());
        setZonesList(zones);
    }

    @VisibleForTesting
    void setZonesList(List<Map<String, Object>> zones) {
        Collections.sort(zones, new TimeZonesComparator());
        mZonesList = new ArrayList<>();
        for (Map<String, Object> zone : zones) {
            mZonesList.add(createTimeZonePreference(zone));
        }
    }

    /** Construct a time zone preference based on the Map object given by {@link ZoneGetter}. */
    private Preference createTimeZonePreference(Map<String, Object> timeZone) {
        Preference preference = new Preference(getContext());
        preference.setKey(timeZone.get(ZoneGetter.KEY_ID).toString());
        preference.setTitle(timeZone.get(ZoneGetter.KEY_DISPLAY_LABEL).toString());
        preference.setSummary(timeZone.get(ZoneGetter.KEY_OFFSET_LABEL).toString());
        preference.setOnPreferenceClickListener(pref -> {
            mAlarmManager.setTimeZone(timeZone.get(ZoneGetter.KEY_ID).toString());
            getFragmentController().goBack();

            // Note: This is intentionally ACTION_TIME_CHANGED, not ACTION_TIMEZONE_CHANGED.
            // Timezone change is handled by the alarm manager. This broadcast message is used
            // to update the clock and other time related displays that the time has changed due
            // to a change in the timezone.
            getContext().sendBroadcast(new Intent(Intent.ACTION_TIME_CHANGED));
            return true;
        });
        return preference;
    }

    /** Compares the timezone objects returned by {@link ZoneGetter}. */
    private static final class TimeZonesComparator implements Comparator<Map<String, Object>> {

        /** Compares timezones based on 1. offset, 2. display label/name. */
        TimeZonesComparator() {
        }

        @Override
        public int compare(Map<String, Object> map1, Map<String, Object> map2) {
            int timeZoneOffsetCompare = compareWithKey(map1, map2, ZoneGetter.KEY_OFFSET);

            // If equivalent timezone offset, compare based on display label.
            if (timeZoneOffsetCompare == 0) {
                return compareWithKey(map1, map2, ZoneGetter.KEY_DISPLAY_LABEL);
            }

            return timeZoneOffsetCompare;
        }

        private int compareWithKey(Map<String, Object> map1, Map<String, Object> map2,
                String comparisonKey) {
            Object value1 = map1.get(comparisonKey);
            Object value2 = map2.get(comparisonKey);
            if (!isComparable(value1) || !isComparable(value2)) {
                throw new IllegalArgumentException(
                        "Cannot use Map which has values that are not Comparable");
            }
            return ((Comparable) value1).compareTo(value2);
        }

        private boolean isComparable(Object value) {
            return (value != null) && (value instanceof Comparable);
        }
    }
}
