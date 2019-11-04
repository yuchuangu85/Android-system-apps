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

package com.android.car.settings.display;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.provider.Settings;

import androidx.lifecycle.Lifecycle;
import androidx.preference.SwitchPreference;
import androidx.preference.TwoStatePreference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class AdaptiveBrightnessTogglePreferenceControllerTest {

    private Context mContext;
    private AdaptiveBrightnessTogglePreferenceController mController;
    private TwoStatePreference mTwoStatePreference;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mTwoStatePreference = new SwitchPreference(mContext);
        PreferenceControllerTestHelper<AdaptiveBrightnessTogglePreferenceController>
                preferenceControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                AdaptiveBrightnessTogglePreferenceController.class, mTwoStatePreference);
        mController = preferenceControllerHelper.getController();
        preferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
    }

    @Test
    public void testRefreshUi_manualMode_isNotChecked() {
        Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);

        mController.refreshUi();
        assertThat(mTwoStatePreference.isChecked()).isFalse();
    }

    @Test
    public void testRefreshUi_automaticMode_isChecked() {
        Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);

        mController.refreshUi();
        assertThat(mTwoStatePreference.isChecked()).isTrue();
    }

    @Test
    public void testHandlePreferenceChanged_setFalse() {
        mTwoStatePreference.callChangeListener(false);
        int brightnessMode = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
        assertThat(brightnessMode).isEqualTo(Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
    }

    @Test
    public void testHandlePreferenceChanged_setTrue() {
        mTwoStatePreference.callChangeListener(true);
        int brightnessMode = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        assertThat(brightnessMode).isEqualTo(Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
    }
}
