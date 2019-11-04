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

package com.android.car.settings.system;

import static android.content.pm.PackageManager.FEATURE_BLUETOOTH;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.UNSUPPORTED_ON_DEVICE;

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowBluetoothAdapter;
import com.android.car.settings.testutils.ShadowBluetoothPan;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

/** Unit test for {@link BluetoothMacAddressPreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowBluetoothAdapter.class, ShadowBluetoothPan.class})
public class BluetoothMacAddressPreferenceControllerTest {

    private Context mContext;
    private PreferenceControllerTestHelper<BluetoothMacAddressPreferenceController>
            mControllerHelper;
    private Preference mPreference;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;

        // Construct controller.
        mPreference = new Preference(mContext);
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
            BluetoothMacAddressPreferenceController.class, mPreference);
    }

    @After
    public void tearDown() {
        ShadowBluetoothAdapter.reset();
    }

    @Test
    public void refreshUi_setsAddress() {
        Shadows.shadowOf(mContext.getPackageManager()).setSystemFeature(
                FEATURE_BLUETOOTH, /* supported= */ true);

        // Make sure controller is available.
        BluetoothAdapter.getDefaultAdapter().enable();
        getShadowBluetoothAdapter().setState(BluetoothAdapter.STATE_ON);

        String address = "address";
        getShadowBluetoothAdapter().setAddress(address);

        mControllerHelper.markState(Lifecycle.State.CREATED);
        mControllerHelper.getController().refreshUi();

        assertThat(mPreference.getSummary()).isEqualTo(address);
    }

    @Test
    public void getAvailabilityStatus_featureBluetooth_unsupportedOnDevice() {
        Shadows.shadowOf(mContext.getPackageManager()).setSystemFeature(
                FEATURE_BLUETOOTH, /* supported= */ false);

        assertThat(mControllerHelper.getController().getAvailabilityStatus()).isEqualTo(
                UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_enableDefaultAdapter_available() {
        Shadows.shadowOf(mContext.getPackageManager()).setSystemFeature(
                FEATURE_BLUETOOTH, /* supported= */ true);

        assertThat(mControllerHelper.getController().getAvailabilityStatus()).isEqualTo(
                AVAILABLE);
    }

    private ShadowBluetoothAdapter getShadowBluetoothAdapter() {
        return (ShadowBluetoothAdapter) Shadow.extract(BluetoothAdapter.getDefaultAdapter());
    }
}

