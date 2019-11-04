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
import androidx.preference.SwitchPreference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowApplication;

import java.util.List;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class AutoTimeZoneTogglePreferenceControllerTest {

    private Context mContext;
    private SwitchPreference mPreference;
    private PreferenceControllerTestHelper<AutoTimeZoneTogglePreferenceController>
            mPreferenceControllerHelper;
    private AutoTimeZoneTogglePreferenceController mController;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mPreference = new SwitchPreference(mContext);
        mPreferenceControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                AutoTimeZoneTogglePreferenceController.class, mPreference);
        mController = mPreferenceControllerHelper.getController();
        mPreferenceControllerHelper.markState(Lifecycle.State.CREATED);
    }

    @Test
    public void testRefreshUi_unchecked() {
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.AUTO_TIME_ZONE, 0);
        mController.refreshUi();
        assertThat(mPreference.isChecked()).isFalse();
    }

    @Test
    public void testRefreshUi_checked() {
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.AUTO_TIME_ZONE, 1);
        mController.refreshUi();
        assertThat(mPreference.isChecked()).isTrue();
    }

    @Test
    public void testOnPreferenceChange_autoTimeZoneSet_shouldSendIntent() {
        mPreference.setChecked(true);
        mController.handlePreferenceChanged(mPreference, true);

        List<Intent> intentsFired = ShadowApplication.getInstance().getBroadcastIntents();
        assertThat(intentsFired.size()).isEqualTo(1);
        Intent intentFired = intentsFired.get(0);
        assertThat(intentFired.getAction()).isEqualTo(Intent.ACTION_TIME_CHANGED);
    }

    @Test
    public void testOnPreferenceChange_autoTimeZoneUnset_shouldSendIntent() {
        mPreference.setChecked(false);
        mController.handlePreferenceChanged(mPreference, false);

        List<Intent> intentsFired = ShadowApplication.getInstance().getBroadcastIntents();
        assertThat(intentsFired.size()).isEqualTo(1);
        Intent intentFired = intentsFired.get(0);
        assertThat(intentFired.getAction()).isEqualTo(Intent.ACTION_TIME_CHANGED);
    }
}
