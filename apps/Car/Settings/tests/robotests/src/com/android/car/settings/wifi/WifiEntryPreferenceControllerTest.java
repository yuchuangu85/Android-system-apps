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

package com.android.car.settings.wifi;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.UNSUPPORTED_ON_DEVICE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.lifecycle.Lifecycle;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.MasterSwitchPreference;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowCarWifiManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

/** Unit test for {@link WifiEntryPreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCarWifiManager.class})
public class WifiEntryPreferenceControllerTest {

    private Context mContext;
    private MasterSwitchPreference mMasterSwitchPreference;
    private PreferenceControllerTestHelper<WifiEntryPreferenceController> mControllerHelper;
    private WifiEntryPreferenceController mController;
    @Mock
    private CarWifiManager mCarWifiManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowCarWifiManager.setInstance(mCarWifiManager);
        mContext = RuntimeEnvironment.application;
        mMasterSwitchPreference = new MasterSwitchPreference(mContext);
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                WifiEntryPreferenceController.class, mMasterSwitchPreference);
        mController = mControllerHelper.getController();
    }

    @After
    public void tearDown() {
        ShadowCarWifiManager.reset();
    }

    @Test
    public void onCreate_setsListener() {
        Shadows.shadowOf(mContext.getPackageManager()).setSystemFeature(
                PackageManager.FEATURE_WIFI, /* supported= */ true);
        assertThat(mMasterSwitchPreference.getSwitchToggleListener()).isNull();

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        assertThat(mMasterSwitchPreference.getSwitchToggleListener()).isNotNull();
    }

    @Test
    public void refreshUi_wifiDisabled_setsSwitchUnchecked() {
        Shadows.shadowOf(mContext.getPackageManager()).setSystemFeature(
                PackageManager.FEATURE_WIFI, /* supported= */ true);
        when(mCarWifiManager.isWifiEnabled()).thenReturn(false);
        mMasterSwitchPreference.setSwitchChecked(true);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        mController.refreshUi();
        assertThat(mMasterSwitchPreference.isSwitchChecked()).isFalse();
    }

    @Test
    public void refreshUi_wifiEnabled_setsSwitchChecked() {
        Shadows.shadowOf(mContext.getPackageManager()).setSystemFeature(
                PackageManager.FEATURE_WIFI, /* supported= */ true);
        when(mCarWifiManager.isWifiEnabled()).thenReturn(true);
        mMasterSwitchPreference.setSwitchChecked(false);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        mController.refreshUi();
        assertThat(mMasterSwitchPreference.isSwitchChecked()).isTrue();
    }

    @Test
    public void getAvailabilityStatus_wifiAvailable_available() {
        Shadows.shadowOf(mContext.getPackageManager()).setSystemFeature(
                PackageManager.FEATURE_WIFI, /* supported= */ true);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_wifiNotAvailable_unsupportedOnDevice() {
        Shadows.shadowOf(mContext.getPackageManager()).setSystemFeature(
                PackageManager.FEATURE_WIFI, /* supported= */ false);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(UNSUPPORTED_ON_DEVICE);
    }
}
