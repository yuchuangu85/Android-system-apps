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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.content.Context;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowBluetoothAdapter;
import com.android.car.settings.testutils.ShadowBluetoothPan;
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

import java.util.StringJoiner;

/** Unit test for {@link BluetoothDeviceNamePreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowBluetoothAdapter.class, ShadowBluetoothPan.class})
public class BluetoothDeviceNamePreferenceControllerTest {

    @Mock
    private CachedBluetoothDevice mDevice;
    @Mock
    private CachedBluetoothDeviceManager mCachedDeviceManager;
    private CachedBluetoothDeviceManager mSaveRealCachedDeviceManager;
    private LocalBluetoothManager mLocalBluetoothManager;
    private Preference mPreference;
    private PreferenceControllerTestHelper<BluetoothDeviceNamePreferenceController>
            mControllerHelper;
    private BluetoothDeviceNamePreferenceController mController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Context context = RuntimeEnvironment.application;
        mLocalBluetoothManager = LocalBluetoothManager.getInstance(context, /* onInitCallback= */
                null);
        mSaveRealCachedDeviceManager = mLocalBluetoothManager.getCachedDeviceManager();
        ReflectionHelpers.setField(mLocalBluetoothManager, "mCachedDeviceManager",
                mCachedDeviceManager);

        // Make sure controller is available.
        Shadows.shadowOf(context.getPackageManager()).setSystemFeature(
                FEATURE_BLUETOOTH, /* supported= */ true);
        BluetoothAdapter.getDefaultAdapter().enable();
        getShadowBluetoothAdapter().setState(BluetoothAdapter.STATE_ON);

        mPreference = new Preference(context);
        mControllerHelper = new PreferenceControllerTestHelper<>(context,
                BluetoothDeviceNamePreferenceController.class);
        mController = mControllerHelper.getController();
        mController.setCachedDevice(mDevice);
        mControllerHelper.setPreference(mPreference);
        mControllerHelper.markState(Lifecycle.State.CREATED);
    }

    @After
    public void tearDown() {
        ShadowBluetoothAdapter.reset();
        ReflectionHelpers.setField(mLocalBluetoothManager, "mCachedDeviceManager",
                mSaveRealCachedDeviceManager);
    }

    @Test
    public void refreshUi_setsDeviceNameAsTitle() {
        String name = "name";
        when(mDevice.getName()).thenReturn(name);

        mController.refreshUi();

        assertThat(mPreference.getTitle()).isEqualTo(name);
    }

    @Test
    public void refreshUi_setsCarConnectionSummaryAsSummary() {
        String summary = "summary";
        when(mDevice.getCarConnectionSummary()).thenReturn(summary);

        mController.refreshUi();

        assertThat(mPreference.getSummary()).isEqualTo(summary);
    }

    @Test
    public void refreshUi_setsIcon() {
        when(mDevice.getBtClass()).thenReturn(
                new BluetoothClass(BluetoothClass.Device.Major.PHONE));

        mController.refreshUi();

        assertThat(mPreference.getIcon()).isNotNull();
    }

    @Test
    public void refreshUi_hearingAidDevice_setsBatteryStatusesAsSummary() {
        String summary = "summary";
        when(mDevice.getCarConnectionSummary()).thenReturn(summary);
        String otherSummary = "other summary";
        when(mCachedDeviceManager.getSubDeviceSummary(mDevice)).thenReturn("other summary");

        mController.refreshUi();

        String expected = new StringJoiner(System.lineSeparator()).add(summary).add(
                otherSummary).toString();
        assertThat(mPreference.getSummary()).isEqualTo(expected);
    }

    @Test
    public void preferenceClicked_launchesRenameDialog() {
        mControllerHelper.markState(Lifecycle.State.STARTED);

        mPreference.performClick();

        verify(mControllerHelper.getMockFragmentController()).showDialog(
                any(RemoteRenameDialogFragment.class), eq(RemoteRenameDialogFragment.TAG));
    }

    @Test
    public void preferenceClicked_handled() {
        mControllerHelper.markState(Lifecycle.State.STARTED);

        assertThat(
                mPreference.getOnPreferenceClickListener().onPreferenceClick(mPreference)).isTrue();
    }

    private ShadowBluetoothAdapter getShadowBluetoothAdapter() {
        return (ShadowBluetoothAdapter) Shadow.extract(BluetoothAdapter.getDefaultAdapter());
    }
}
