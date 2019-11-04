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
import static android.os.UserManager.DISALLOW_CONFIG_BLUETOOTH;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.car.drivingstate.CarUxRestrictions;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.Intent;

import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowBluetoothAdapter;
import com.android.car.settings.testutils.ShadowBluetoothPan;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;
import com.android.settingslib.bluetooth.BluetoothDeviceFilter;
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

import java.util.Collections;

/** Unit test for {@link BluetoothScanningDevicesGroupPreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCarUserManagerHelper.class, ShadowBluetoothAdapter.class,
        ShadowBluetoothPan.class})
public class BluetoothScanningDevicesGroupPreferenceControllerTest {

    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;
    @Mock
    private CachedBluetoothDevice mCachedDevice;
    @Mock
    private BluetoothDevice mDevice;
    @Mock
    private CachedBluetoothDeviceManager mCachedDeviceManager;
    private CachedBluetoothDeviceManager mSaveRealCachedDeviceManager;
    private LocalBluetoothManager mLocalBluetoothManager;
    private Context mContext;
    private PreferenceGroup mPreferenceGroup;
    private PreferenceControllerTestHelper<TestBluetoothScanningDevicesGroupPreferenceController>
            mControllerHelper;
    private TestBluetoothScanningDevicesGroupPreferenceController mController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowCarUserManagerHelper.setMockInstance(mCarUserManagerHelper);
        mContext = RuntimeEnvironment.application;

        mLocalBluetoothManager = LocalBluetoothManager.getInstance(mContext, /* onInitCallback= */
                null);
        mSaveRealCachedDeviceManager = mLocalBluetoothManager.getCachedDeviceManager();
        ReflectionHelpers.setField(mLocalBluetoothManager, "mCachedDeviceManager",
                mCachedDeviceManager);

        when(mDevice.getBondState()).thenReturn(BluetoothDevice.BOND_NONE);
        when(mCachedDevice.getBondState()).thenReturn(BluetoothDevice.BOND_NONE);
        when(mCachedDevice.getDevice()).thenReturn(mDevice);
        when(mCachedDeviceManager.getCachedDevicesCopy()).thenReturn(
                Collections.singletonList(mCachedDevice));

        // Make sure controller is available.
        Shadows.shadowOf(mContext.getPackageManager()).setSystemFeature(
                FEATURE_BLUETOOTH, /* supported= */ true);
        BluetoothAdapter.getDefaultAdapter().enable();
        getShadowBluetoothAdapter().setState(BluetoothAdapter.STATE_ON);

        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                TestBluetoothScanningDevicesGroupPreferenceController.class, mPreferenceGroup);
        mController = mControllerHelper.getController();
    }

    @After
    public void tearDown() {
        ShadowCarUserManagerHelper.reset();
        ShadowBluetoothAdapter.reset();
        ReflectionHelpers.setField(mLocalBluetoothManager, "mCachedDeviceManager",
                mSaveRealCachedDeviceManager);
    }

    @Test
    public void disallowConfigBluetooth_doesNotStartScanning() {
        when(mCarUserManagerHelper.isCurrentProcessUserHasRestriction(
                DISALLOW_CONFIG_BLUETOOTH)).thenReturn(true);

        mControllerHelper.markState(Lifecycle.State.STARTED);

        assertThat(BluetoothAdapter.getDefaultAdapter().isDiscovering()).isFalse();
        // User can't scan, but they can still see known devices.
        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
    }

    @Test
    public void onScanningStateChanged_scanningEnabled_receiveStopped_restartsScanning() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        assertThat(BluetoothAdapter.getDefaultAdapter().isDiscovering()).isTrue();

        BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
        mController.onScanningStateChanged(/* started= */ false);

        assertThat(BluetoothAdapter.getDefaultAdapter().isDiscovering()).isTrue();
    }

    @Test
    public void onScanningStateChanged_scanningDisabled_receiveStopped_doesNothing() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        // Set a device bonding to disable scanning.
        when(mCachedDevice.getBondState()).thenReturn(BluetoothDevice.BOND_BONDING);
        mController.refreshUi();
        assertThat(BluetoothAdapter.getDefaultAdapter().isDiscovering()).isFalse();

        mController.onScanningStateChanged(/* started= */ false);

        assertThat(BluetoothAdapter.getDefaultAdapter().isDiscovering()).isFalse();
    }

    @Test
    public void onDeviceBondStateChanged_refreshesUi() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        assertThat(BluetoothAdapter.getDefaultAdapter().isDiscovering()).isTrue();

        // Change state to bonding to cancel scanning on refresh.
        when(mCachedDevice.getBondState()).thenReturn(BluetoothDevice.BOND_BONDING);
        when(mDevice.getBondState()).thenReturn(BluetoothDevice.BOND_BONDING);
        mController.onDeviceBondStateChanged(mCachedDevice, BluetoothDevice.BOND_BONDING);

        assertThat(BluetoothAdapter.getDefaultAdapter().isDiscovering()).isFalse();
    }

    @Test
    public void onDeviceClicked_callsInternal() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(0);

        devicePreference.performClick();

        assertThat(mController.getLastClickedDevice()).isEquivalentAccordingToCompareTo(
                devicePreference.getCachedDevice());
    }

    @Test
    public void onDeviceClicked_cancelsScanning() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(0);
        assertThat(BluetoothAdapter.getDefaultAdapter().isDiscovering()).isTrue();

        devicePreference.performClick();

        assertThat(BluetoothAdapter.getDefaultAdapter().isDiscovering()).isFalse();
    }

    @Test
    public void refreshUi_noDeviceBonding_startsScanning() {
        mControllerHelper.markState(Lifecycle.State.STARTED);

        mController.refreshUi();

        assertThat(BluetoothAdapter.getDefaultAdapter().isDiscovering()).isTrue();
    }

    @Test
    public void refreshUi_noDeviceBonding_enablesGroup() {
        mControllerHelper.markState(Lifecycle.State.STARTED);

        mController.refreshUi();

        assertThat(mPreferenceGroup.isEnabled()).isTrue();
    }

    @Test
    public void refreshUi_noDeviceBonding_setsScanModeConnectableDiscoverable() {
        mControllerHelper.markState(Lifecycle.State.STARTED);

        mController.refreshUi();

        assertThat(BluetoothAdapter.getDefaultAdapter().getScanMode()).isEqualTo(
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);
    }

    @Test
    public void refreshUi_deviceBonding_stopsScanning() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        when(mCachedDevice.getBondState()).thenReturn(BluetoothDevice.BOND_BONDING);

        mController.refreshUi();

        assertThat(BluetoothAdapter.getDefaultAdapter().isDiscovering()).isFalse();
    }

    @Test
    public void refreshUi_deviceBonding_disablesGroup() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        when(mCachedDevice.getBondState()).thenReturn(BluetoothDevice.BOND_BONDING);

        mController.refreshUi();

        assertThat(mPreferenceGroup.isEnabled()).isFalse();
    }

    @Test
    public void refreshUi_deviceBonding_setsScanModeConnectable() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        when(mCachedDevice.getBondState()).thenReturn(BluetoothDevice.BOND_BONDING);

        mController.refreshUi();

        assertThat(BluetoothAdapter.getDefaultAdapter().getScanMode()).isEqualTo(
                BluetoothAdapter.SCAN_MODE_CONNECTABLE);
    }

    @Test
    public void onStop_stopsScanning() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        assertThat(BluetoothAdapter.getDefaultAdapter().isDiscovering()).isTrue();

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_STOP);

        assertThat(BluetoothAdapter.getDefaultAdapter().isDiscovering()).isFalse();
    }

    @Test
    public void onStop_clearsNonBondedDevices() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_STOP);

        verify(mCachedDeviceManager).clearNonBondedDevices();
    }

    @Test
    public void onStop_clearsGroup() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        assertThat(mPreferenceGroup.getPreferenceCount()).isGreaterThan(0);

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_STOP);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(0);
    }

    @Test
    public void onStop_setsScanModeConnectable() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_STOP);

        assertThat(BluetoothAdapter.getDefaultAdapter().getScanMode()).isEqualTo(
                BluetoothAdapter.SCAN_MODE_CONNECTABLE);
    }

    @Test
    public void discoverableScanModeTimeout_controllerStarted_resetsDiscoverableScanMode() {
        mControllerHelper.markState(Lifecycle.State.STARTED);

        BluetoothAdapter.getDefaultAdapter().setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE);
        mContext.sendBroadcast(new Intent(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED));

        assertThat(BluetoothAdapter.getDefaultAdapter().getScanMode()).isEqualTo(
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);
    }

    @Test
    public void discoverableScanModeTimeout_controllerStopped_doesNotResetDiscoverableScanMode() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_STOP);

        BluetoothAdapter.getDefaultAdapter().setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE);
        mContext.sendBroadcast(new Intent(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED));

        assertThat(BluetoothAdapter.getDefaultAdapter().getScanMode()).isEqualTo(
                BluetoothAdapter.SCAN_MODE_CONNECTABLE);
    }

    private ShadowBluetoothAdapter getShadowBluetoothAdapter() {
        return (ShadowBluetoothAdapter) Shadow.extract(BluetoothAdapter.getDefaultAdapter());
    }

    private static final class TestBluetoothScanningDevicesGroupPreferenceController extends
            BluetoothScanningDevicesGroupPreferenceController {

        private CachedBluetoothDevice mLastClickedDevice;

        TestBluetoothScanningDevicesGroupPreferenceController(Context context,
                String preferenceKey,
                FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        protected void onDeviceClickedInternal(CachedBluetoothDevice cachedDevice) {
            mLastClickedDevice = cachedDevice;
        }

        CachedBluetoothDevice getLastClickedDevice() {
            return mLastClickedDevice;
        }

        @Override
        protected BluetoothDeviceFilter.Filter getDeviceFilter() {
            return BluetoothDeviceFilter.ALL_FILTER;
        }
    }
}
