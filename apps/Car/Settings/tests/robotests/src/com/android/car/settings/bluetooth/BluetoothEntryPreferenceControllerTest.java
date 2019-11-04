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

package com.android.car.settings.bluetooth;

import static android.content.pm.PackageManager.FEATURE_BLUETOOTH;
import static android.os.UserManager.DISALLOW_BLUETOOTH;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.DISABLED_FOR_USER;
import static com.android.car.settings.common.PreferenceController.UNSUPPORTED_ON_DEVICE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import android.car.userlib.CarUserManagerHelper;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

/** Unit test for {@link BluetoothEntryPreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCarUserManagerHelper.class})
public class BluetoothEntryPreferenceControllerTest {

    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;
    private BluetoothEntryPreferenceController mController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowCarUserManagerHelper.setMockInstance(mCarUserManagerHelper);
        mController = new PreferenceControllerTestHelper<>(RuntimeEnvironment.application,
                BluetoothEntryPreferenceController.class).getController();
    }

    @After
    public void tearDown() {
        ShadowCarUserManagerHelper.reset();
    }

    @Test
    public void getAvailabilityStatus_bluetoothAvailable_available() {
        Shadows.shadowOf(RuntimeEnvironment.application.getPackageManager()).setSystemFeature(
                FEATURE_BLUETOOTH, /* supported= */ true);
        when(mCarUserManagerHelper.isCurrentProcessUserHasRestriction(any())).thenReturn(false);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_bluetoothAvailable_disallowBluetooth_disabledForUser() {
        Shadows.shadowOf(RuntimeEnvironment.application.getPackageManager()).setSystemFeature(
                FEATURE_BLUETOOTH, /* supported= */ true);
        when(mCarUserManagerHelper.isCurrentProcessUserHasRestriction(
                DISALLOW_BLUETOOTH)).thenReturn(true);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(DISABLED_FOR_USER);
    }

    @Test
    public void getAvailabilityStatus_bluetoothNotAvailable_unsupportedOnDevice() {
        Shadows.shadowOf(RuntimeEnvironment.application.getPackageManager()).setSystemFeature(
                FEATURE_BLUETOOTH, /* supported= */ false);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(UNSUPPORTED_ON_DEVICE);
    }
}
