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
package com.android.car.settings.location;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.UNSUPPORTED_ON_DEVICE;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;

import androidx.lifecycle.Lifecycle;
import androidx.preference.SwitchPreference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class BluetoothScanningPreferenceControllerTest {

    private Context mContext;
    private SwitchPreference mPreference;
    private BluetoothScanningPreferenceController mController;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mPreference = new SwitchPreference(mContext);
        PreferenceControllerTestHelper<BluetoothScanningPreferenceController> controllerHelper =
                new PreferenceControllerTestHelper<>(mContext,
                        BluetoothScanningPreferenceController.class, mPreference);
        mController = controllerHelper.getController();
        Shadows.shadowOf(mContext.getPackageManager()).setSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE, /* supported= */ true);
        controllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
    }

    @Test
    public void getAvailabilityStatus_hasBluetoothFeature_available() {
        Shadows.shadowOf(mContext.getPackageManager()).setSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE, /* supported= */ true);
        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_noBluetoothFeature_unsupported() {
        Shadows.shadowOf(mContext.getPackageManager()).setSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE, /* supported= */ false);
        assertThat(mController.getAvailabilityStatus()).isEqualTo(UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void refreshUi_bluetoothScanningEnabled_shouldCheckPreference() {
        mPreference.setChecked(false);
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.BLE_SCAN_ALWAYS_AVAILABLE, 1);
        mController.refreshUi();

        assertThat(mPreference.isChecked()).isTrue();
    }

    @Test
    public void refreshUi_bluetoothScanningDisabled_shouldUncheckPreference() {
        mPreference.setChecked(true);
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.BLE_SCAN_ALWAYS_AVAILABLE, 0);
        mController.refreshUi();

        assertThat(mPreference.isChecked()).isFalse();
    }

    @Test
    public void handlePreferenceChanged_preferenceChecked_shouldEnableBluetoothScanning() {
        mPreference.callChangeListener(true);

        assertThat(Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.BLE_SCAN_ALWAYS_AVAILABLE, 0)).isEqualTo(1);
    }

    @Test
    public void handlePreferenceChanged_preferenceUnchecked_shouldDisableBluetoothScanning() {
        mPreference.callChangeListener(false);

        assertThat(Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.BLE_SCAN_ALWAYS_AVAILABLE, 1)).isEqualTo(0);
    }
}
