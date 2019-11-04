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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowBluetoothAdapter;
import com.android.car.settings.testutils.ShadowBluetoothPan;
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

import java.util.Arrays;
import java.util.Collections;

/** Unit test for {@link BluetoothDevicesGroupPreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowBluetoothAdapter.class, ShadowBluetoothPan.class})
public class BluetoothDevicesGroupPreferenceControllerTest {

    @Mock
    private BluetoothDeviceFilter.Filter mFilter;
    @Mock
    private CachedBluetoothDevice mCachedDevice1;
    @Mock
    private CachedBluetoothDevice mCachedDevice2;
    @Mock
    private CachedBluetoothDeviceManager mCachedDeviceManager;
    private CachedBluetoothDeviceManager mSaveRealCachedDeviceManager;
    private LocalBluetoothManager mLocalBluetoothManager;
    private BluetoothDevice mDevice1;
    private PreferenceGroup mPreferenceGroup;
    private TestBluetoothDevicesGroupPreferenceController mController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Context context = RuntimeEnvironment.application;

        mLocalBluetoothManager = LocalBluetoothManager.getInstance(context, /* onInitCallback= */
                null);
        mSaveRealCachedDeviceManager = mLocalBluetoothManager.getCachedDeviceManager();
        ReflectionHelpers.setField(mLocalBluetoothManager, "mCachedDeviceManager",
                mCachedDeviceManager);

        mDevice1 = BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:AA:BB");
        when(mCachedDevice1.getDevice()).thenReturn(mDevice1);
        BluetoothDevice device2 = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(
                "BB:AA:33:22:11:00");
        when(mCachedDevice2.getDevice()).thenReturn(device2);

        // Make sure controller is available.
        Shadows.shadowOf(context.getPackageManager()).setSystemFeature(
                FEATURE_BLUETOOTH, /* supported= */ true);
        BluetoothAdapter.getDefaultAdapter().enable();
        getShadowBluetoothAdapter().setState(BluetoothAdapter.STATE_ON);

        mPreferenceGroup = new PreferenceCategory(context);
        PreferenceControllerTestHelper<TestBluetoothDevicesGroupPreferenceController>
                controllerHelper = new PreferenceControllerTestHelper<>(context,
                TestBluetoothDevicesGroupPreferenceController.class);
        mController = controllerHelper.getController();
        mController.setDeviceFilter(mFilter);
        controllerHelper.setPreference(mPreferenceGroup);
        controllerHelper.markState(Lifecycle.State.STARTED);
    }

    @After
    public void tearDown() {
        ShadowBluetoothAdapter.reset();
        ReflectionHelpers.setField(mLocalBluetoothManager, "mCachedDeviceManager",
                mSaveRealCachedDeviceManager);
    }

    @Test
    public void refreshUi_filterMatch_addsToGroup() {
        when(mCachedDeviceManager.getCachedDevicesCopy()).thenReturn(
                Collections.singletonList(mCachedDevice1));
        when(mFilter.matches(mDevice1)).thenReturn(true);

        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(0);
        assertThat(devicePreference.getCachedDevice()).isEqualTo(mCachedDevice1);
    }

    @Test
    public void refreshUi_filterMatch_addsToPreferenceMap() {
        when(mCachedDeviceManager.getCachedDevicesCopy()).thenReturn(
                Collections.singletonList(mCachedDevice1));
        when(mFilter.matches(mDevice1)).thenReturn(true);

        mController.refreshUi();

        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(0);
        assertThat(mController.getPreferenceMap()).containsEntry(devicePreference.getCachedDevice(),
                devicePreference);
    }

    @Test
    public void refreshUi_filterMismatch_removesFromGroup() {
        when(mCachedDeviceManager.getCachedDevicesCopy()).thenReturn(
                Collections.singletonList(mCachedDevice1));
        when(mFilter.matches(mDevice1)).thenReturn(true);
        mController.refreshUi();
        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(0);
        assertThat(devicePreference.getCachedDevice()).isEqualTo(mCachedDevice1);

        when(mFilter.matches(mDevice1)).thenReturn(false);
        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(0);
    }

    @Test
    public void refreshUi_filterMismatch_removesFromPreferenceMap() {
        when(mCachedDeviceManager.getCachedDevicesCopy()).thenReturn(
                Collections.singletonList(mCachedDevice1));
        when(mFilter.matches(mDevice1)).thenReturn(true);
        mController.refreshUi();
        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(0);
        assertThat(devicePreference.getCachedDevice()).isEqualTo(mCachedDevice1);

        when(mFilter.matches(mDevice1)).thenReturn(false);
        mController.refreshUi();

        assertThat(mController.getPreferenceMap()).doesNotContainKey(mCachedDevice1);
    }

    @Test
    public void refreshUi_noDevices_hidesGroup() {
        mController.refreshUi();

        assertThat(mPreferenceGroup.isVisible()).isFalse();
    }

    @Test
    public void refreshUi_devices_showsGroup() {
        when(mCachedDeviceManager.getCachedDevicesCopy()).thenReturn(
                Collections.singletonList(mCachedDevice1));
        when(mFilter.matches(mDevice1)).thenReturn(true);

        mController.refreshUi();

        assertThat(mPreferenceGroup.isVisible()).isTrue();
    }

    @Test
    public void onBluetoothStateChanged_turningOff_clearsPreferences() {
        when(mCachedDeviceManager.getCachedDevicesCopy()).thenReturn(
                Collections.singletonList(mCachedDevice1));
        when(mFilter.matches(mDevice1)).thenReturn(true);
        mController.refreshUi();
        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);

        mController.onBluetoothStateChanged(BluetoothAdapter.STATE_TURNING_OFF);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(0);
        assertThat(mController.getPreferenceMap()).isEmpty();
    }

    @Test
    public void onDeviceAdded_refreshesUi() {
        when(mCachedDeviceManager.getCachedDevicesCopy()).thenReturn(
                Collections.singletonList(mCachedDevice1));
        when(mFilter.matches(mDevice1)).thenReturn(true);

        mController.onDeviceAdded(mCachedDevice1);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(0);
        assertThat(devicePreference.getCachedDevice()).isEqualTo(mCachedDevice1);
    }

    @Test
    public void onDeviceDeleted_refreshesUi() {
        when(mCachedDeviceManager.getCachedDevicesCopy()).thenReturn(
                Arrays.asList(mCachedDevice1, mCachedDevice2));
        when(mFilter.matches(any(BluetoothDevice.class))).thenReturn(true);
        mController.refreshUi();
        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(2);

        when(mCachedDeviceManager.getCachedDevicesCopy()).thenReturn(
                Collections.singletonList(mCachedDevice2));
        mController.onDeviceDeleted(mCachedDevice1);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(0);
        assertThat(devicePreference.getCachedDevice()).isEqualTo(mCachedDevice2);
    }

    @Test
    public void onDeviceDeleted_lastDevice_hidesGroup() {
        when(mCachedDeviceManager.getCachedDevicesCopy()).thenReturn(
                Collections.singletonList(mCachedDevice1));
        when(mFilter.matches(any(BluetoothDevice.class))).thenReturn(true);
        mController.refreshUi();

        when(mCachedDeviceManager.getCachedDevicesCopy()).thenReturn(Collections.emptyList());
        mController.onDeviceDeleted(mCachedDevice1);

        assertThat(mPreferenceGroup.isVisible()).isFalse();
    }

    @Test
    public void preferenceClicked_callsOnDeviceClicked() {
        when(mCachedDeviceManager.getCachedDevicesCopy()).thenReturn(
                Arrays.asList(mCachedDevice1, mCachedDevice2));
        when(mFilter.matches(any(BluetoothDevice.class))).thenReturn(true);
        mController.refreshUi();
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(1);

        devicePreference.performClick();

        assertThat(mController.getClickedDevice()).isEqualTo(devicePreference.getCachedDevice());
    }

    @Test
    public void preferenceClicked_handled() {
        when(mCachedDeviceManager.getCachedDevicesCopy()).thenReturn(
                Arrays.asList(mCachedDevice1, mCachedDevice2));
        when(mFilter.matches(any(BluetoothDevice.class))).thenReturn(true);
        mController.refreshUi();
        BluetoothDevicePreference devicePreference =
                (BluetoothDevicePreference) mPreferenceGroup.getPreference(1);

        assertThat(devicePreference.getOnPreferenceClickListener().onPreferenceClick(
                devicePreference)).isTrue();
    }

    private ShadowBluetoothAdapter getShadowBluetoothAdapter() {
        return (ShadowBluetoothAdapter) Shadow.extract(BluetoothAdapter.getDefaultAdapter());
    }

    /** Concrete impl of {@link BluetoothDevicesGroupPreferenceController} for testing. */
    private static class TestBluetoothDevicesGroupPreferenceController extends
            BluetoothDevicesGroupPreferenceController {

        private BluetoothDeviceFilter.Filter mFilter;
        private CachedBluetoothDevice mClickedDevice;

        TestBluetoothDevicesGroupPreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        protected BluetoothDeviceFilter.Filter getDeviceFilter() {
            return mFilter;
        }

        void setDeviceFilter(BluetoothDeviceFilter.Filter filter) {
            mFilter = filter;
        }

        @Override
        protected void onDeviceClicked(CachedBluetoothDevice cachedDevice) {
            mClickedDevice = cachedDevice;
        }

        CachedBluetoothDevice getClickedDevice() {
            return mClickedDevice;
        }
    }
}
