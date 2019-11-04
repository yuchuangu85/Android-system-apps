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

package com.android.car.settings.security;

import static android.content.pm.PackageManager.FEATURE_BLUETOOTH;
import static android.os.UserManager.DISALLOW_BLUETOOTH;
import static android.os.UserManager.DISALLOW_CONFIG_BLUETOOTH;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.DISABLED_FOR_USER;
import static com.android.car.settings.common.PreferenceController.UNSUPPORTED_ON_DEVICE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothAdapter;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowBluetoothAdapter;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;
import com.android.car.settings.testutils.ShadowLockPatternUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;


/**
 * Unit tests for {@link AddTrustedDevicePreferenceController}.
 */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowLockPatternUtils.class, ShadowBluetoothAdapter.class,
        ShadowCarUserManagerHelper.class})
public class AddTrustedDevicePreferenceControllerTest {

    private Context mContext;
    private PreferenceControllerTestHelper<AddTrustedDevicePreferenceController>
            mPreferenceControllerHelper;
    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;
    private Preference mPreference;
    private AddTrustedDevicePreferenceController mController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        ShadowCarUserManagerHelper.setMockInstance(mCarUserManagerHelper);
        mPreference = new Preference(mContext);
        mPreferenceControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                AddTrustedDevicePreferenceController.class, mPreference);
        mController = mPreferenceControllerHelper.getController();
        Shadows.shadowOf(RuntimeEnvironment.application.getPackageManager()).setSystemFeature(
                FEATURE_BLUETOOTH, /* supported= */ true);
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
    }

    @After
    public void tearDown() {
        ShadowLockPatternUtils.reset();
        ShadowBluetoothAdapter.reset();
        ShadowCarUserManagerHelper.reset();
    }

    @Test
    public void refreshUi_hasPassword_preferenceEnabled() {
        ShadowLockPatternUtils.setPasswordQuality(DevicePolicyManager.PASSWORD_QUALITY_SOMETHING);
        when(mCarUserManagerHelper.isCurrentProcessUserHasRestriction(
                DISALLOW_BLUETOOTH)).thenReturn(false);

        mController.refreshUi();

        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void refreshUi_noPassword_preferenceDisabled() {
        ShadowLockPatternUtils.setPasswordQuality(DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED);
        when(mCarUserManagerHelper.isCurrentProcessUserHasRestriction(
                DISALLOW_BLUETOOTH)).thenReturn(false);

        mController.refreshUi();

        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void refreshUi_bluetoothAdapterEnabled_setsEmptySummary() {
        ShadowLockPatternUtils.setPasswordQuality(DevicePolicyManager.PASSWORD_QUALITY_SOMETHING);
        when(mCarUserManagerHelper.isCurrentProcessUserHasRestriction(
                DISALLOW_BLUETOOTH)).thenReturn(false);
        BluetoothAdapter.getDefaultAdapter().enable();

        mController.refreshUi();

        assertThat(mPreference.getSummary().toString()).isEmpty();
    }

    @Test
    public void refreshUi_bluetoothAdapterDisabled_setsTurnOnToAddSummary() {
        BluetoothAdapter.getDefaultAdapter().disable();

        mController.refreshUi();

        assertThat(mPreference.getSummary()).isEqualTo(
                mContext.getString(R.string.add_device_summary));
    }

    @Test
    public void onPreferenceClicked_hasPassword_enableBluetooth() {
        BluetoothAdapter.getDefaultAdapter().disable();
        ShadowLockPatternUtils.setPasswordQuality(DevicePolicyManager.PASSWORD_QUALITY_SOMETHING);
        mController.refreshUi();

        mPreference.performClick();

        assertThat(BluetoothAdapter.getDefaultAdapter().isEnabled()).isTrue();
    }

    @Test
    public void getAvailabilityStatus_bluetoothNotAvailable_unsupportedOnDevice() {
        Shadows.shadowOf(RuntimeEnvironment.application.getPackageManager()).setSystemFeature(
                FEATURE_BLUETOOTH, /* supported= */ false);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_disallowBluetoothUserRestriction_disabledForUser() {
        Shadows.shadowOf(RuntimeEnvironment.application.getPackageManager()).setSystemFeature(
                FEATURE_BLUETOOTH, /* supported= */ true);
        when(mCarUserManagerHelper.isCurrentProcessUserHasRestriction(
                DISALLOW_BLUETOOTH)).thenReturn(true);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(DISABLED_FOR_USER);
    }

    @Test
    public void getAvailabilityStatus_disallowConfigBluetoothUserRestriction_disabledForUser() {
        Shadows.shadowOf(RuntimeEnvironment.application.getPackageManager()).setSystemFeature(
                FEATURE_BLUETOOTH, /* supported= */ true);
        when(mCarUserManagerHelper.isCurrentProcessUserHasRestriction(
                DISALLOW_CONFIG_BLUETOOTH)).thenReturn(true);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(DISABLED_FOR_USER);
    }

    @Test
    public void getAvailabilityStatus_available() {
        Shadows.shadowOf(RuntimeEnvironment.application.getPackageManager()).setSystemFeature(
                FEATURE_BLUETOOTH, /* supported= */ true);
        // No user restrictions.

        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }
}
