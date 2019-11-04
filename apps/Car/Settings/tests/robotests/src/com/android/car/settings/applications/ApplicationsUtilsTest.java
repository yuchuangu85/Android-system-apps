/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.car.settings.applications;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.admin.DevicePolicyManager;
import android.car.userlib.CarUserManagerHelper;
import android.content.ComponentName;
import android.content.pm.UserInfo;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.testutils.ShadowDefaultDialerManager;
import com.android.car.settings.testutils.ShadowSmsApplication;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Collections;

/** Unit test for {@link ApplicationsUtils}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowDefaultDialerManager.class, ShadowSmsApplication.class})
public class ApplicationsUtilsTest {

    private static final String PACKAGE_NAME = "com.android.car.settings.test";

    @After
    public void tearDown() {
        ShadowDefaultDialerManager.reset();
        ShadowSmsApplication.reset();
    }

    @Test
    public void isKeepEnabledPackage_defaultDialerApplication_returnsTrue() {
        ShadowDefaultDialerManager.setDefaultDialerApplication(PACKAGE_NAME);

        assertThat(ApplicationsUtils.isKeepEnabledPackage(RuntimeEnvironment.application,
                PACKAGE_NAME)).isTrue();
    }

    @Test
    public void isKeepEnabledPackage_defaultSmsApplication_returnsTrue() {
        ShadowSmsApplication.setDefaultSmsApplication(new ComponentName(PACKAGE_NAME, "cls"));

        assertThat(ApplicationsUtils.isKeepEnabledPackage(RuntimeEnvironment.application,
                PACKAGE_NAME)).isTrue();
    }

    @Test
    public void isKeepEnabledPackage_returnsFalse() {
        assertThat(ApplicationsUtils.isKeepEnabledPackage(RuntimeEnvironment.application,
                PACKAGE_NAME)).isFalse();
    }

    @Test
    public void isProfileOrDeviceOwner_profileOwner_returnsTrue() {
        UserInfo userInfo = new UserInfo();
        userInfo.id = 123;
        DevicePolicyManager dpm = mock(DevicePolicyManager.class);
        CarUserManagerHelper um = mock(CarUserManagerHelper.class);
        when(um.getAllUsers()).thenReturn(Collections.singletonList(userInfo));
        when(dpm.getProfileOwnerAsUser(userInfo.id)).thenReturn(
                new ComponentName(PACKAGE_NAME, "cls"));

        assertThat(ApplicationsUtils.isProfileOrDeviceOwner(PACKAGE_NAME, dpm, um)).isTrue();
    }

    @Test
    public void isProfileOrDeviceOwner_deviceOwner_returnsTrue() {
        DevicePolicyManager dpm = mock(DevicePolicyManager.class);
        when(dpm.isDeviceOwnerAppOnAnyUser(PACKAGE_NAME)).thenReturn(true);

        assertThat(ApplicationsUtils.isProfileOrDeviceOwner(PACKAGE_NAME, dpm,
                mock(CarUserManagerHelper.class))).isTrue();
    }

    @Test
    public void isProfileOrDeviceOwner_returnsFalse() {
        assertThat(ApplicationsUtils.isProfileOrDeviceOwner(PACKAGE_NAME,
                mock(DevicePolicyManager.class), mock(CarUserManagerHelper.class))).isFalse();
    }

}
