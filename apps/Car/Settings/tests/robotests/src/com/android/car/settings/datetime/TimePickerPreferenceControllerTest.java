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

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class TimePickerPreferenceControllerTest {

    private Context mContext;
    private Preference mPreference;
    private PreferenceControllerTestHelper<TimePickerPreferenceController>
            mPreferenceControllerHelper;
    private TimePickerPreferenceController mController;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mPreference = new SwitchPreference(mContext);
        mPreferenceControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                TimePickerPreferenceController.class, mPreference);
        mController = mPreferenceControllerHelper.getController();
        mPreferenceControllerHelper.markState(Lifecycle.State.CREATED);
    }

    @Test
    public void testRefreshUi_disabled() {
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.AUTO_TIME, 1);
        mController.refreshUi();
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void testRefreshUi_enabled() {
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.AUTO_TIME, 0);
        mController.refreshUi();
        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void testRefreshUi_fromBroadcastReceiver_disabled() {
        mPreferenceControllerHelper.markState(Lifecycle.State.STARTED);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.AUTO_TIME, 1);
        mContext.sendBroadcast(new Intent(Intent.ACTION_TIME_CHANGED));
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void testRefreshUi_fromBroadcastReceiver_enabled() {
        mPreferenceControllerHelper.markState(Lifecycle.State.STARTED);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.AUTO_TIME, 0);
        mContext.sendBroadcast(new Intent(Intent.ACTION_TIME_CHANGED));
        assertThat(mPreference.isEnabled()).isTrue();
    }
}
