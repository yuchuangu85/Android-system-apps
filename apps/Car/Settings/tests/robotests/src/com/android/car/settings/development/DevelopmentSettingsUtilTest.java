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

package com.android.car.settings.development;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.provider.Settings;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;
import com.android.car.settings.testutils.ShadowLocalBroadcastManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCarUserManagerHelper.class, ShadowLocalBroadcastManager.class})
public class DevelopmentSettingsUtilTest {

    private Context mContext;
    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowCarUserManagerHelper.setMockInstance(mCarUserManagerHelper);
        mContext = RuntimeEnvironment.application;
        when(mCarUserManagerHelper.isCurrentProcessAdminUser()).thenReturn(true);
        when(mCarUserManagerHelper.isCurrentProcessDemoUser()).thenReturn(false);
    }

    @After
    public void tearDown() {
        ShadowCarUserManagerHelper.reset();
        ShadowLocalBroadcastManager.reset();
    }

    @Test
    public void isEnabled_settingsOff_isAdmin_notDemo_shouldReturnFalse() {
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0);

        assertThat(DevelopmentSettingsUtil.isDevelopmentSettingsEnabled(mContext,
                mCarUserManagerHelper)).isFalse();
    }

    @Test
    public void isEnabled_settingsOn_isAdmin_notDemo_shouldReturnTrue() {
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);

        assertThat(DevelopmentSettingsUtil.isDevelopmentSettingsEnabled(mContext,
                mCarUserManagerHelper)).isTrue();
    }

    @Test
    public void isEnabled_settingsOn_notAdmin_notDemo_shouldReturnFalse() {
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);
        when(mCarUserManagerHelper.isCurrentProcessAdminUser()).thenReturn(false);
        when(mCarUserManagerHelper.isCurrentProcessDemoUser()).thenReturn(false);

        assertThat(DevelopmentSettingsUtil.isDevelopmentSettingsEnabled(mContext,
                mCarUserManagerHelper)).isFalse();
    }

    @Test
    public void isEnabled_settingsOn_notAdmin_isDemo_shouldReturnTrue() {
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);
        when(mCarUserManagerHelper.isCurrentProcessAdminUser()).thenReturn(false);
        when(mCarUserManagerHelper.isCurrentProcessDemoUser()).thenReturn(true);

        assertThat(DevelopmentSettingsUtil.isDevelopmentSettingsEnabled(mContext,
                mCarUserManagerHelper)).isTrue();
    }

    @Test
    public void isEnabled_settingsOff_notAdmin_isDemo_shouldReturnFalse() {
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0);
        when(mCarUserManagerHelper.isCurrentProcessAdminUser()).thenReturn(false);
        when(mCarUserManagerHelper.isCurrentProcessDemoUser()).thenReturn(true);

        assertThat(DevelopmentSettingsUtil.isDevelopmentSettingsEnabled(mContext,
                mCarUserManagerHelper)).isFalse();
    }

    @Test
    public void setDevelopmentSettingsEnabled_setTrue() {
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0);

        DevelopmentSettingsUtil.setDevelopmentSettingsEnabled(mContext, true);

        assertThat(Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)).isEqualTo(1);
    }

    @Test
    public void setDevelopmentSettingsEnabled_setFalse() {
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);

        DevelopmentSettingsUtil.setDevelopmentSettingsEnabled(mContext, false);

        assertThat(Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1)).isEqualTo(0);
    }

    @Test
    public void isDeviceProvisioned_true() {
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.DEVICE_PROVISIONED,
                1);
        assertThat(DevelopmentSettingsUtil.isDeviceProvisioned(mContext)).isTrue();
    }

    @Test
    public void isDeviceProvisioned_false() {
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.DEVICE_PROVISIONED,
                0);
        assertThat(DevelopmentSettingsUtil.isDeviceProvisioned(mContext)).isFalse();
    }
}
