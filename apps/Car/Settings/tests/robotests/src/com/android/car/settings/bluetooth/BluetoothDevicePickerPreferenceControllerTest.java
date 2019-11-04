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

package com.android.car.settings.bluetooth;

import static android.content.pm.PackageManager.FEATURE_BLUETOOTH;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothDevicePicker;
import android.bluetooth.BluetoothUuid;
import android.car.userlib.CarUserManagerHelper;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelUuid;

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
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.util.ReflectionHelpers;

import java.util.Arrays;

/** Unit test for {@link BluetoothDevicePickerPreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCarUserManagerHelper.class, ShadowBluetoothAdapter.class,
        ShadowBluetoothPan.class})
public class BluetoothDevicePickerPreferenceControllerTest {

    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;
    @Mock
    private CachedBluetoothDevice mUnbondedCachedDevice;
    @Mock
    private BluetoothDevice mUnbondedDevice;
    @Mock
    private CachedBluetoothDevice mBondedCachedDevice;
    @Mock
    private BluetoothDevice mBondedDevice;
    @Mock
    private CachedBluetoothDeviceManager mCachedDeviceManager;
    private CachedBluetoothDeviceManager mSaveRealCachedDeviceManager;
    private LocalBluetoothManager mLocalBluetoothManager;
    private PreferenceGroup mPreferenceGroup;
    private PreferenceControllerTestHelper<BluetoothDevicePickerPreferenceController>
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
        when(mBondedDevice.getBondState()).thenReturn(BluetoothDevice.BOND_BONDED);
        when(mBondedCachedDevice.getBondState()).thenReturn(BluetoothDevice.BOND_BONDED);
        when(mBondedCachedDevice.getDevice()).thenReturn(mBondedDevice);
        when(mCachedDeviceManager.getCachedDevicesCopy()).thenReturn(
                Arrays.asList(mUnbondedCachedDevice, mBondedCachedDevice));
        // Make bonded device appear first in the list.
        when(mBondedCachedDevice.compareTo(mUnbondedCachedDevice)).thenReturn(-1);
        when(mUnbondedCachedDevice.compareTo(mBondedCachedDevice)).thenReturn(1);

        // Make sure controller is available.
        Shadows.shadowOf(context.getPackageManager()).setSystemFeature(
                FEATURE_BLUETOOTH, /* supported= */ true);
        BluetoothAdapter.getDefaultAdapter().enable();
        getShadowBluetoothAdapter().setState(BluetoothAdapter.STATE_ON);

        mPreferenceGroup = new LogicalPreferenceGroup(context);
        mControllerHelper = new PreferenceControllerTestHelper<>(context,
                BluetoothDevicePickerPreferenceController.class);
    }

    @After
    public void tearDown() {
        ShadowCarUserManagerHelper.reset();
        ShadowBluetoothAdapter.reset();
        ReflectionHelpers.setField(mLocalBluetoothManager, "mCachedDeviceManager",
                mSaveRealCachedDeviceManager);
    }

    @Test
    public void checkInitialized_noLaunchIntentSet_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class,
                () -> mControllerHelper.setPreference(mPreferenceGroup));
    }

    @Test
    public void onStart_appliesFilterType() {
        // Setup device to pass the filter.
        when(mBondedDevice.getUuids()).thenReturn(new ParcelUuid[]{BluetoothUuid.AudioSink});
        Intent launchIntent = createLaunchIntent(/* needsAuth= */ false,
                BluetoothDevicePicker.FILTER_TYPE_AUDIO, "test.package", "TestClass");
        mControllerHelper.getController().setLaunchIntent(launchIntent);
        mControllerHelper.setPreference(mPreferenceGroup);

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        assertThat(((BluetoothDevicePreference) mPreferenceGroup.getPreference(
                0)).getCachedDevice()).isEqualTo(mBondedCachedDevice);
    }

    @Test
    public void onDeviceClicked_bondedDevice_sendsPickedIntent() {
        ComponentName component = new ComponentName("test.package", "TestClass");
        Intent launchIntent = createLaunchIntent(/* needsAuth= */ true,
                BluetoothDevicePicker.FILTER_TYPE_ALL, component.getPackageName(),
                component.getClassName());
        mControllerHelper.getController().setLaunchIntent(launchIntent);
        mControllerHelper.setPreference(mPreferenceGroup);
        mControllerHelper.markState(Lifecycle.State.STARTED);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(0);

        devicePreference.performClick();

        assertThat(ShadowApplication.getInstance().getBroadcastIntents()).hasSize(1);
        Intent pickedIntent = ShadowApplication.getInstance().getBroadcastIntents().get(0);
        assertThat(pickedIntent.getAction()).isEqualTo(
                BluetoothDevicePicker.ACTION_DEVICE_SELECTED);
        assertThat(pickedIntent.getComponent()).isEqualTo(component);
        assertThat((BluetoothDevice) pickedIntent.getParcelableExtra(
                BluetoothDevice.EXTRA_DEVICE)).isEqualTo(mBondedDevice);
    }

    @Test
    public void onDeviceClicked_bondedDevice_goesBack() {
        Intent launchIntent = createLaunchIntent(/* needsAuth= */ true,
                BluetoothDevicePicker.FILTER_TYPE_ALL, "test.package", "TestClass");
        mControllerHelper.getController().setLaunchIntent(launchIntent);
        mControllerHelper.setPreference(mPreferenceGroup);
        mControllerHelper.markState(Lifecycle.State.STARTED);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(0);

        devicePreference.performClick();

        verify(mControllerHelper.getMockFragmentController()).goBack();
    }

    @Test
    public void onDeviceClicked_unbondedDevice_doesNotNeedAuth_sendsPickedIntent() {
        Intent launchIntent = createLaunchIntent(/* needsAuth= */ false,
                BluetoothDevicePicker.FILTER_TYPE_ALL, "test.package", "TestClass");
        mControllerHelper.getController().setLaunchIntent(launchIntent);
        mControllerHelper.setPreference(mPreferenceGroup);
        mControllerHelper.markState(Lifecycle.State.STARTED);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(1);

        devicePreference.performClick();

        Intent pickedIntent = ShadowApplication.getInstance().getBroadcastIntents().get(0);
        assertThat(pickedIntent.getAction()).isEqualTo(
                BluetoothDevicePicker.ACTION_DEVICE_SELECTED);
    }

    @Test
    public void onDeviceClicked_unbondedDevice_needsAuth_startsPairing() {
        Intent launchIntent = createLaunchIntent(/* needsAuth= */ true,
                BluetoothDevicePicker.FILTER_TYPE_ALL, "test.package", "TestClass");
        mControllerHelper.getController().setLaunchIntent(launchIntent);
        mControllerHelper.setPreference(mPreferenceGroup);
        mControllerHelper.markState(Lifecycle.State.STARTED);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(1);

        devicePreference.performClick();

        verify(mUnbondedCachedDevice).startPairing();
    }

    @Test
    public void onDeviceClicked_unbondedDevice_needsAuth_pairingStartFails_resumesScanning() {
        Intent launchIntent = createLaunchIntent(/* needsAuth= */ true,
                BluetoothDevicePicker.FILTER_TYPE_ALL, "test.package", "TestClass");
        mControllerHelper.getController().setLaunchIntent(launchIntent);
        mControllerHelper.setPreference(mPreferenceGroup);
        mControllerHelper.markState(Lifecycle.State.STARTED);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(1);
        when(mUnbondedCachedDevice.startPairing()).thenReturn(false);
        assertThat(BluetoothAdapter.getDefaultAdapter().isDiscovering()).isTrue();

        devicePreference.performClick();

        assertThat(BluetoothAdapter.getDefaultAdapter().isDiscovering()).isTrue();
    }

    @Test
    public void onDeviceBondStateChanged_selectedDeviceBonded_sendsPickedIntent() {
        Intent launchIntent = createLaunchIntent(/* needsAuth= */ true,
                BluetoothDevicePicker.FILTER_TYPE_ALL, "test.package", "TestClass");
        mControllerHelper.getController().setLaunchIntent(launchIntent);
        mControllerHelper.setPreference(mPreferenceGroup);
        mControllerHelper.markState(Lifecycle.State.STARTED);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(1);

        // Select device.
        devicePreference.performClick();
        // Device bonds.
        mControllerHelper.getController().onDeviceBondStateChanged(
                devicePreference.getCachedDevice(), BluetoothDevice.BOND_BONDED);

        Intent pickedIntent = ShadowApplication.getInstance().getBroadcastIntents().get(0);
        assertThat(pickedIntent.getAction()).isEqualTo(
                BluetoothDevicePicker.ACTION_DEVICE_SELECTED);
    }

    @Test
    public void onDeviceBondStateChanged_selectedDeviceBonded_goesBack() {
        Intent launchIntent = createLaunchIntent(/* needsAuth= */ true,
                BluetoothDevicePicker.FILTER_TYPE_ALL, "test.package", "TestClass");
        mControllerHelper.getController().setLaunchIntent(launchIntent);
        mControllerHelper.setPreference(mPreferenceGroup);
        mControllerHelper.markState(Lifecycle.State.STARTED);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(1);

        // Select device.
        devicePreference.performClick();
        // Device bonds.
        mControllerHelper.getController().onDeviceBondStateChanged(
                devicePreference.getCachedDevice(), BluetoothDevice.BOND_BONDED);

        verify(mControllerHelper.getMockFragmentController()).goBack();
    }

    @Test
    public void onDestroy_noDeviceSelected_sendsNullPickedIntent() {
        Intent launchIntent = createLaunchIntent(/* needsAuth= */ true,
                BluetoothDevicePicker.FILTER_TYPE_ALL, "test.package", "TestClass");
        mControllerHelper.getController().setLaunchIntent(launchIntent);
        mControllerHelper.setPreference(mPreferenceGroup);
        mControllerHelper.markState(Lifecycle.State.STARTED);

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_DESTROY);

        Intent pickedIntent = ShadowApplication.getInstance().getBroadcastIntents().get(0);
        assertThat(pickedIntent.getAction()).isEqualTo(
                BluetoothDevicePicker.ACTION_DEVICE_SELECTED);
        assertThat((BluetoothDevice) pickedIntent.getParcelableExtra(
                BluetoothDevice.EXTRA_DEVICE)).isNull();
    }

    private Intent createLaunchIntent(boolean needAuth, int filterType, String packageName,
            String className) {
        Intent intent = new Intent(BluetoothDevicePicker.ACTION_LAUNCH);
        intent.putExtra(BluetoothDevicePicker.EXTRA_NEED_AUTH, needAuth);
        intent.putExtra(BluetoothDevicePicker.EXTRA_FILTER_TYPE, filterType);
        intent.putExtra(BluetoothDevicePicker.EXTRA_LAUNCH_PACKAGE, packageName);
        intent.putExtra(BluetoothDevicePicker.EXTRA_LAUNCH_CLASS, className);
        return intent;
    }

    private ShadowBluetoothAdapter getShadowBluetoothAdapter() {
        return (ShadowBluetoothAdapter) Shadow.extract(BluetoothAdapter.getDefaultAdapter());
    }
}
