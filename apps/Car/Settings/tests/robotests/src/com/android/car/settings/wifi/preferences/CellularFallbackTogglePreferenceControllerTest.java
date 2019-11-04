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

package com.android.car.settings.wifi.preferences;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.provider.Settings;

import androidx.lifecycle.Lifecycle;
import androidx.preference.SwitchPreference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class CellularFallbackTogglePreferenceControllerTest {

    private Context mContext;
    private SwitchPreference mPreference;
    private PreferenceControllerTestHelper<CellularFallbackTogglePreferenceController>
            mPreferenceControllerHelper;
    private CellularFallbackTogglePreferenceController mController;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;

        mPreference = new SwitchPreference(mContext);
        mPreferenceControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                CellularFallbackTogglePreferenceController.class, mPreference);
        mController = mPreferenceControllerHelper.getController();
        mPreferenceControllerHelper.markState(Lifecycle.State.CREATED);
    }

    @Test
    public void refreshUi_unchecked() {
        Settings.Global.putString(mContext.getContentResolver(),
                Settings.Global.NETWORK_AVOID_BAD_WIFI, null);
        mPreference.setChecked(true);

        mController.refreshUi();
        assertThat(mPreference.isChecked()).isFalse();
    }

    @Test
    public void refreshUi_checked() {
        Settings.Global.putString(mContext.getContentResolver(),
                Settings.Global.NETWORK_AVOID_BAD_WIFI, "1");
        mPreference.setChecked(false);

        mController.refreshUi();
        assertThat(mPreference.isChecked()).isTrue();
    }

    @Test
    public void handlePreferenceChanged_toggleFalse_setsNull() {
        Settings.Global.putString(mContext.getContentResolver(),
                Settings.Global.NETWORK_AVOID_BAD_WIFI, "1");

        mPreference.callChangeListener(false);
        assertThat(Settings.Global.getString(mContext.getContentResolver(),
                Settings.Global.NETWORK_AVOID_BAD_WIFI)).isNull();
    }

    @Test
    public void handlePreferenceChanged_toggleTrue_setsEnabled() {
        Settings.Global.putString(mContext.getContentResolver(),
                Settings.Global.NETWORK_AVOID_BAD_WIFI, null);

        mPreference.callChangeListener(true);
        assertThat(Settings.Global.getString(mContext.getContentResolver(),
                Settings.Global.NETWORK_AVOID_BAD_WIFI)).isEqualTo("1");
    }
}
