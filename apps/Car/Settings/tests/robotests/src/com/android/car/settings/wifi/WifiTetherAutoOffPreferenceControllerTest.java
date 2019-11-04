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

package com.android.car.settings.wifi;

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
public class WifiTetherAutoOffPreferenceControllerTest {

    private Context mContext;
    private TwoStatePreference mTwoStatePreference;
    private PreferenceControllerTestHelper<WifiTetherAutoOffPreferenceController> mControllerHelper;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mTwoStatePreference = new SwitchPreference(mContext);
        mControllerHelper =
                new PreferenceControllerTestHelper<WifiTetherAutoOffPreferenceController>(mContext,
                        WifiTetherAutoOffPreferenceController.class, mTwoStatePreference);
    }

    @Test
    public void onStart_tetherAutoOff_on_shouldReturnSwitchStateOn() {
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.SOFT_AP_TIMEOUT_ENABLED, 1);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        assertThat(mTwoStatePreference.isChecked()).isTrue();
    }

    @Test
    public void onStart_tetherAutoOff_off_shouldReturnSwitchStateOff() {
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.SOFT_AP_TIMEOUT_ENABLED, 0);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        assertThat(mTwoStatePreference.isChecked()).isFalse();
    }

    @Test
    public void onSwitchOn_shouldReturnAutoOff_on() {
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.SOFT_AP_TIMEOUT_ENABLED, 0);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mTwoStatePreference.performClick();

        assertThat(Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.SOFT_AP_TIMEOUT_ENABLED, 0))
                .isEqualTo(1);
    }

    @Test
    public void onSwitchOff_shouldReturnAutoOff_off() {
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.SOFT_AP_TIMEOUT_ENABLED, 1);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mTwoStatePreference.performClick();

        assertThat(Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.SOFT_AP_TIMEOUT_ENABLED, 1))
                .isEqualTo(0);
    }
}
