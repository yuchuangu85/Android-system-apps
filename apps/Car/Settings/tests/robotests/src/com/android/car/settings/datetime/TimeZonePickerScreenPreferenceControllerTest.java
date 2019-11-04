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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.settingslib.datetime.ZoneGetter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowApplication;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class TimeZonePickerScreenPreferenceControllerTest {

    private PreferenceGroup mPreferenceGroup;
    private PreferenceControllerTestHelper<TimeZonePickerScreenPreferenceController>
            mPreferenceControllerHelper;
    private TimeZonePickerScreenPreferenceController mController;
    @Mock
    private AlarmManager mAlarmManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Context context = RuntimeEnvironment.application;
        mPreferenceGroup = new LogicalPreferenceGroup(context);
        mPreferenceControllerHelper = new PreferenceControllerTestHelper<>(context,
                TimeZonePickerScreenPreferenceController.class, mPreferenceGroup);
        mController = mPreferenceControllerHelper.getController();

        // Test setup.
        mController.mAlarmManager = mAlarmManager;
    }

    @Test
    public void testOnCreate_hasElements() {
        List<Map<String, Object>> testTimeZones = new ArrayList<>();
        testTimeZones.add(
                createTimeZoneMap("testKey1", "Midway", "GMT-11:00", -1100));
        testTimeZones.add(
                createTimeZoneMap("testKey2", "Tijuana", "GMT-07:00", -700));
        testTimeZones.add(
                createTimeZoneMap("testKey3", "Coordinated Universal Time", "GMT+00:00", 0));
        testTimeZones.add(
                createTimeZoneMap("testKey4", "Kabul", "GMT+04:30", 430));
        mController.setZonesList(testTimeZones);
        mPreferenceControllerHelper.markState(Lifecycle.State.CREATED);
        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(4);
    }

    @Test
    public void testOnPreferenceClick_setTimeZoneCalled() {
        List<Map<String, Object>> testTimeZone = new ArrayList<>();
        testTimeZone.add(createTimeZoneMap("testKey", "London", "GMT+01:00", 100));
        mController.setZonesList(testTimeZone);
        mPreferenceControllerHelper.markState(Lifecycle.State.CREATED);
        Preference preference = mPreferenceGroup.findPreference("testKey");
        preference.performClick();
        verify(mAlarmManager).setTimeZone("testKey");
    }

    @Test
    public void testOnPreferenceClick_fragmentControllerGoBack() {
        List<Map<String, Object>> testTimeZone = new ArrayList<>();
        testTimeZone.add(createTimeZoneMap("testKey", "London", "GMT+01:00", 100));
        mController.setZonesList(testTimeZone);
        mPreferenceControllerHelper.markState(Lifecycle.State.CREATED);
        Preference preference = mPreferenceGroup.findPreference("testKey");
        preference.performClick();
        verify(mPreferenceControllerHelper.getMockFragmentController()).goBack();
    }

    @Test
    public void testOnPreferenceClick_broadcastFired() {
        List<Map<String, Object>> testTimeZone = new ArrayList<>();
        testTimeZone.add(createTimeZoneMap("testKey", "London", "GMT+01:00", 100));
        mController.setZonesList(testTimeZone);
        mPreferenceControllerHelper.markState(Lifecycle.State.CREATED);
        Preference preference = mPreferenceGroup.findPreference("testKey");
        preference.performClick();

        List<Intent> intentsFired = ShadowApplication.getInstance().getBroadcastIntents();
        assertThat(intentsFired.size()).isEqualTo(1);
        Intent intentFired = intentsFired.get(0);
        assertThat(intentFired.getAction()).isEqualTo(Intent.ACTION_TIME_CHANGED);
    }

    @Test
    public void testTimeZonesComparator() {
        List<Map<String, Object>> testTimeZones = new ArrayList<>();
        testTimeZones.add(createTimeZoneMap("testKey1", "Oral",
                "GMT+05:00", 500));
        testTimeZones.add(createTimeZoneMap("testKey2", "Kathmandu",
                "GMT+05:45", 545));
        testTimeZones.add(createTimeZoneMap("testKey3", "Brazzaville",
                "GMT+01:00", 100));
        testTimeZones.add(createTimeZoneMap("testKey4", "Casablanca",
                "GMT+01:00", 100));
        testTimeZones.add(createTimeZoneMap("testKey5", "Nuuk",
                "GMT-02:00", -200));
        testTimeZones.add(createTimeZoneMap("testKey6", "St. John's",
                "GMT-02:30", -230));
        mController.setZonesList(testTimeZones);
        mPreferenceControllerHelper.markState(Lifecycle.State.CREATED);

        List<String> computedOrder = new ArrayList<>();
        for (int i = 0; i < mPreferenceGroup.getPreferenceCount(); i++) {
            computedOrder.add(mPreferenceGroup.getPreference(i).getTitle().toString());
        }

        assertThat(computedOrder).containsExactly("St. John's", "Nuuk", "Brazzaville", "Casablanca",
                "Oral", "Kathmandu");
    }

    private Map<String, Object> createTimeZoneMap(String key, String timeZone, String offset,
            int offsetValue) {
        Map<String, Object> map = new HashMap<>();
        map.put(ZoneGetter.KEY_ID, key);
        map.put(ZoneGetter.KEY_DISPLAY_LABEL, timeZone);
        map.put(ZoneGetter.KEY_OFFSET_LABEL, offset);
        map.put(ZoneGetter.KEY_OFFSET, offsetValue);
        return map;
    }
}
