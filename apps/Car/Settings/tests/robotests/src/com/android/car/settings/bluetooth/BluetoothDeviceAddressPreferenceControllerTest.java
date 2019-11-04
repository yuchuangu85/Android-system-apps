/*
 * Copyright 2018 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowBluetoothAdapter;
import com.android.car.settings.testutils.ShadowBluetoothPan;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

/** Unit test for {@link BluetoothAddressPreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowBluetoothAdapter.class, ShadowBluetoothPan.class})
public class BluetoothDeviceAddressPreferenceControllerTest {

    @After
    public void tearDown() {
        ShadowBluetoothAdapter.reset();
    }

    @Test
    public void refreshUi_setsAddress() {
        // Make sure controller is available.
        Shadows.shadowOf(RuntimeEnvironment.application.getPackageManager()).setSystemFeature(
                FEATURE_BLUETOOTH, /* supported= */ true);
        BluetoothAdapter.getDefaultAdapter().enable();
        getShadowBluetoothAdapter().setState(BluetoothAdapter.STATE_ON);

        String address = "address";
        CachedBluetoothDevice device = mock(CachedBluetoothDevice.class);
        when(device.getAddress()).thenReturn(address);

        // Construct controller.
        Context context = RuntimeEnvironment.application;
        Preference preference = new Preference(context);
        PreferenceControllerTestHelper<BluetoothDeviceAddressPreferenceController>
                controllerHelper = new PreferenceControllerTestHelper<>(context,
                BluetoothDeviceAddressPreferenceController.class);
        controllerHelper.getController().setCachedDevice(device);
        controllerHelper.setPreference(preference);
        controllerHelper.markState(Lifecycle.State.CREATED);

        controllerHelper.getController().refreshUi();

        assertThat(preference.getTitle()).isEqualTo(
                context.getString(R.string.bluetooth_device_mac_address, address));
    }

    private ShadowBluetoothAdapter getShadowBluetoothAdapter() {
        return (ShadowBluetoothAdapter) Shadow.extract(BluetoothAdapter.getDefaultAdapter());
    }
}
