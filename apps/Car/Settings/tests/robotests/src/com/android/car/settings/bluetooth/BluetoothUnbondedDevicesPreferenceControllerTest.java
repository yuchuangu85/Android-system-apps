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
import static android.os.UserManager.DISALLOW_CONFIG_BLUETOOTH;

import static com.android.car.settings.common.PreferenceController.DISABLED_FOR_USER;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;

import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowBluetoothAdapter;
import com.android.car.settings.testutils.ShadowBluetoothPan;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.CachedBluetoothDeviceManager;
import com.android.settingslib.bluetooth.LocalBluetoothManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;

import java.util.Arrays;

/** Unit test for {@link BluetoothUnbondedDevicesPreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCarUserManagerHelper.class, ShadowBluetoothAdapter.class,
        ShadowBluetoothPan.class})
public class BluetoothUnbondedDevicesPreferenceControllerTest {

    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;
    @Mock
    private CachedBluetoothDevice mUnbondedCachedDevice;
    @Mock
    private BluetoothDevice mUnbondedDevice;
    @Mock
    private CachedBluetoothDeviceManager mCachedDeviceManager;
    private CachedBluetoothDeviceManager mSaveRealCachedDeviceManager;
    private LocalBluetoothManager mLocalBluetoothManager;
    private PreferenceGroup mPreferenceGroup;
    private PreferenceControllerTestHelper<BluetoothUnbondedDevicesPreferenceController>
            mControllerHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowCarUserManagerHelper.setMockInstance(mCarUserManagerHelper);
        Context context = RuntimeEnvironment.application;

        mLocalBluetoothManager = LocalBluetoothManager.getInstance(context, /* onInitCallback= */
                null);
        mSaveRealCachedDeviceManager = mLocalBluetoothManager.getCachedDeviceManager();
        ReflectionHelpers.setField(mLocalBluetoothManager, "mCachedDeviceManager",
                mCachedDeviceManager);

        when(mUnbondedDevice.getBondState()).thenReturn(BluetoothDevice.BOND_NONE);
        when(mUnbondedCachedDevice.getBondState()).thenReturn(BluetoothDevice.BOND_NONE);
        when(mUnbondedCachedDevice.getDevice()).thenReturn(mUnbondedDevice);
        BluetoothDevice bondedDevice = mock(BluetoothDevice.class);
        when(bondedDevice.getBondState()).thenReturn(BluetoothDevice.BOND_BONDED);
        CachedBluetoothDevice bondedCachedDevice = mock(CachedBluetoothDevice.class);
        when(bondedCachedDevice.getDevice()).thenReturn(bondedDevice);
        when(mCachedDeviceManager.getCachedDevicesCopy()).thenReturn(
                Arrays.asList(mUnbondedCachedDevice, bondedCachedDevice));

        // Make sure controller is available.
        Shadows.shadowOf(context.getPackageManager()).setSystemFeature(
                FEATURE_BLUETOOTH, /* supported= */ true);
        BluetoothAdapter.getDefaultAdapter().enable();
        getShadowBluetoothAdapter().setState(BluetoothAdapter.STATE_ON);

        mPreferenceGroup = new LogicalPreferenceGroup(context);
        mControllerHelper = new PreferenceControllerTestHelper<>(context,
                BluetoothUnbondedDevicesPreferenceController.class, mPreferenceGroup);
    }

    @After
    public void tearDown() {
        ShadowCarUserManagerHelper.reset();
        ShadowBluetoothAdapter.reset();
        ReflectionHelpers.setField(mLocalBluetoothManager, "mCachedDeviceManager",
                mSaveRealCachedDeviceManager);
    }

    @Test
    public void showsOnlyUnbondedDevices() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(0);
        assertThat(devicePreference.getCachedDevice()).isEqualTo(mUnbondedCachedDevice);
    }

    @Test
    public void onDeviceClicked_startsPairing() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(0);

        devicePreference.performClick();

        verify(mUnbondedCachedDevice).startPairing();
    }

    @Test
    public void onDeviceClicked_pairingStartFails_resumesScanning() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(0);
        when(mUnbondedCachedDevice.startPairing()).thenReturn(false);
        assertThat(BluetoothAdapter.getDefaultAdapter().isDiscovering()).isTrue();

        devicePreference.performClick();

        assertThat(BluetoothAdapter.getDefaultAdapter().isDiscovering()).isTrue();
    }

    @Test
    public void onDeviceClicked_requestsPhonebookAccess() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        when(mUnbondedCachedDevice.startPairing()).thenReturn(true);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(0);

        devicePreference.performClick();

        verify(mUnbondedDevice).setPhonebookAccessPermission(BluetoothDevice.ACCESS_ALLOWED);
    }

    @Test
    public void onDeviceClicked_requestsMessageAccess() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        when(mUnbondedCachedDevice.startPairing()).thenReturn(true);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(0);

        devicePreference.performClick();

        verify(mUnbondedDevice).setMessageAccessPermission(BluetoothDevice.ACCESS_ALLOWED);
    }

    @Test
    public void getAvailabilityStatus_disallowConfigBluetooth_disabledForUser() {
        when(mCarUserManagerHelper.isCurrentProcessUserHasRestriction(
                DISALLOW_CONFIG_BLUETOOTH)).thenReturn(true);

        assertThat(mControllerHelper.getController().getAvailabilityStatus()).isEqualTo(
                DISABLED_FOR_USER);
    }

    private ShadowBluetoothAdapter getShadowBluetoothAdapter() {
        return (ShadowBluetoothAdapter) Shadow.extract(BluetoothAdapter.getDefaultAdapter());
    }
}
