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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;

import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowBluetoothAdapter;
import com.android.car.settings.testutils.ShadowBluetoothPan;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.LocalBluetoothProfile;

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

import java.util.Arrays;
import java.util.Collections;

/** Unit test for {@link BluetoothDeviceProfilesPreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowBluetoothAdapter.class, ShadowBluetoothPan.class})
public class BluetoothDeviceProfilesPreferenceControllerTest {

    @Mock
    private CachedBluetoothDevice mCachedDevice;
    private BluetoothDevice mDevice;
    private PreferenceGroup mPreferenceGroup;
    private BluetoothDeviceProfilesPreferenceController mController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Context context = RuntimeEnvironment.application;
        mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:AA:BB");
        when(mCachedDevice.getDevice()).thenReturn(mDevice);

        // Make sure controller is available.
        Shadows.shadowOf(context.getPackageManager()).setSystemFeature(
                FEATURE_BLUETOOTH, /* supported= */ true);
        BluetoothAdapter.getDefaultAdapter().enable();
        getShadowBluetoothAdapter().setState(BluetoothAdapter.STATE_ON);

        mPreferenceGroup = new PreferenceCategory(context);
        PreferenceControllerTestHelper<BluetoothDeviceProfilesPreferenceController>
                controllerHelper = new PreferenceControllerTestHelper<>(context,
                BluetoothDeviceProfilesPreferenceController.class);
        mController = controllerHelper.getController();
        mController.setCachedDevice(mCachedDevice);
        controllerHelper.setPreference(mPreferenceGroup);
        controllerHelper.markState(Lifecycle.State.STARTED);
    }

    @After
    public void tearDown() {
        ShadowBluetoothAdapter.reset();
    }

    @Test
    public void refreshUi_addsNewProfiles() {
        LocalBluetoothProfile profile1 = mock(LocalBluetoothProfile.class);
        when(profile1.getNameResource(mDevice)).thenReturn(R.string.bt_profile_name);
        when(mCachedDevice.getProfiles()).thenReturn(Collections.singletonList(profile1));

        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);

        LocalBluetoothProfile profile2 = mock(LocalBluetoothProfile.class);
        when(profile2.getNameResource(mDevice)).thenReturn(R.string.bt_profile_name);
        when(mCachedDevice.getProfiles()).thenReturn(Arrays.asList(profile1, profile2));

        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(2);
        BluetoothDeviceProfilePreference profilePreference =
                (BluetoothDeviceProfilePreference) mPreferenceGroup.getPreference(1);
        assertThat(profilePreference.getProfile()).isEqualTo(profile2);
    }

    @Test
    public void refreshUi_removesRemovedProfiles() {
        LocalBluetoothProfile profile1 = mock(LocalBluetoothProfile.class);
        when(profile1.getNameResource(mDevice)).thenReturn(R.string.bt_profile_name);
        LocalBluetoothProfile profile2 = mock(LocalBluetoothProfile.class);
        when(profile2.getNameResource(mDevice)).thenReturn(R.string.bt_profile_name);
        when(mCachedDevice.getProfiles()).thenReturn(Arrays.asList(profile1, profile2));

        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(2);

        when(mCachedDevice.getProfiles()).thenReturn(Collections.singletonList(profile2));
        when(mCachedDevice.getRemovedProfiles()).thenReturn(Collections.singletonList(profile1));

        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        BluetoothDeviceProfilePreference profilePreference =
                (BluetoothDeviceProfilePreference) mPreferenceGroup.getPreference(0);
        assertThat(profilePreference.getProfile()).isEqualTo(profile2);
    }

    @Test
    public void refreshUi_profiles_showsPreference() {
        LocalBluetoothProfile profile = mock(LocalBluetoothProfile.class);
        when(profile.getNameResource(mDevice)).thenReturn(R.string.bt_profile_name);
        when(mCachedDevice.getProfiles()).thenReturn(Collections.singletonList(profile));

        mController.refreshUi();

        assertThat(mPreferenceGroup.isVisible()).isTrue();
    }

    @Test
    public void refreshUi_noProfiles_hidesPreference() {
        when(mCachedDevice.getProfiles()).thenReturn(Collections.emptyList());

        mController.refreshUi();

        assertThat(mPreferenceGroup.isVisible()).isFalse();
    }

    @Test
    public void profileChecked_setsProfilePreferred() {
        LocalBluetoothProfile profile = mock(LocalBluetoothProfile.class);
        when(profile.getNameResource(mDevice)).thenReturn(R.string.bt_profile_name);
        when(mCachedDevice.getProfiles()).thenReturn(Collections.singletonList(profile));
        mController.refreshUi();
        BluetoothDeviceProfilePreference profilePreference =
                (BluetoothDeviceProfilePreference) mPreferenceGroup.getPreference(0);

        assertThat(profilePreference.isChecked()).isFalse();
        profilePreference.performClick();

        verify(profile).setPreferred(mDevice, true);
    }

    @Test
    public void profileChecked_connectsToProfile() {
        LocalBluetoothProfile profile = mock(LocalBluetoothProfile.class);
        when(profile.getNameResource(mDevice)).thenReturn(R.string.bt_profile_name);
        when(mCachedDevice.getProfiles()).thenReturn(Collections.singletonList(profile));
        mController.refreshUi();
        BluetoothDeviceProfilePreference profilePreference =
                (BluetoothDeviceProfilePreference) mPreferenceGroup.getPreference(0);

        assertThat(profilePreference.isChecked()).isFalse();
        profilePreference.performClick();

        verify(mCachedDevice).connectProfile(profile);
    }

    @Test
    public void profileUnchecked_setsProfileNotPreferred() {
        LocalBluetoothProfile profile = mock(LocalBluetoothProfile.class);
        when(profile.getNameResource(mDevice)).thenReturn(R.string.bt_profile_name);
        when(profile.isPreferred(mDevice)).thenReturn(true);
        when(mCachedDevice.getProfiles()).thenReturn(Collections.singletonList(profile));
        mController.refreshUi();
        BluetoothDeviceProfilePreference profilePreference =
                (BluetoothDeviceProfilePreference) mPreferenceGroup.getPreference(0);

        assertThat(profilePreference.isChecked()).isTrue();
        profilePreference.performClick();

        verify(profile).setPreferred(mDevice, false);
    }

    @Test
    public void profileUnchecked_disconnectsFromProfile() {
        LocalBluetoothProfile profile = mock(LocalBluetoothProfile.class);
        when(profile.getNameResource(mDevice)).thenReturn(R.string.bt_profile_name);
        when(profile.isPreferred(mDevice)).thenReturn(true);
        when(mCachedDevice.getProfiles()).thenReturn(Collections.singletonList(profile));
        mController.refreshUi();
        BluetoothDeviceProfilePreference profilePreference =
                (BluetoothDeviceProfilePreference) mPreferenceGroup.getPreference(0);

        assertThat(profilePreference.isChecked()).isTrue();
        profilePreference.performClick();

        verify(mCachedDevice).disconnect(profile);
    }

    private ShadowBluetoothAdapter getShadowBluetoothAdapter() {
        return (ShadowBluetoothAdapter) Shadow.extract(BluetoothAdapter.getDefaultAdapter());
    }
}
