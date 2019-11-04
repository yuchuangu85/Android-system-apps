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

import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.LocalBluetoothProfile;
import com.android.settingslib.bluetooth.PanProfile;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class BluetoothDeviceProfilePreferenceTest {

    @Mock
    private LocalBluetoothProfile mProfile;
    @Mock
    private CachedBluetoothDevice mCachedDevice;
    private BluetoothDevice mDevice;
    private Context mContext;
    private BluetoothDeviceProfilePreference mPreference;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:AA:BB");
        when(mCachedDevice.getDevice()).thenReturn(mDevice);
        when(mProfile.toString()).thenReturn("key");
        when(mProfile.getNameResource(mDevice)).thenReturn(R.string.bt_profile_name);
        mPreference = new BluetoothDeviceProfilePreference(mContext, mProfile, mCachedDevice);
    }

    @Test
    public void onConstruction_setsProfileStringAsKey() {
        assertThat(mPreference.getKey()).isEqualTo(mProfile.toString());
    }

    @Test
    public void onConstruction_setsProfileNameAsTitle() {
        assertThat(mPreference.getTitle()).isEqualTo(mContext.getString(R.string.bt_profile_name));
    }

    @Test
    public void onAttached_registersDeviceCallback() {
        mPreference.onAttached();

        verify(mCachedDevice).registerCallback(any(CachedBluetoothDevice.Callback.class));
    }

    @Test
    public void onAttached_deviceNotBusy_setsEnabled() {
        when(mCachedDevice.isBusy()).thenReturn(false);

        mPreference.onAttached();

        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void onAttached_deviceBusy_setsNotEnabled() {
        when(mCachedDevice.isBusy()).thenReturn(true);

        mPreference.onAttached();

        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void onAttached_preferred_setsChecked() {
        when(mProfile.isPreferred(mDevice)).thenReturn(true);

        mPreference.onAttached();

        assertThat(mPreference.isChecked()).isTrue();
    }

    @Test
    public void onAttached_notPreferred_setsUnchecked() {
        when(mProfile.isPreferred(mDevice)).thenReturn(false);

        mPreference.onAttached();

        assertThat(mPreference.isChecked()).isFalse();
    }

    @Test
    public void onAttached_panProfile_connected_setsChecked() {
        mProfile = mock(PanProfile.class);
        when(mProfile.getConnectionStatus(mDevice)).thenReturn(STATE_CONNECTED);
        when(mProfile.toString()).thenReturn("key");
        when(mProfile.getNameResource(mDevice)).thenReturn(R.string.bt_profile_name);
        mPreference = new BluetoothDeviceProfilePreference(mContext, mProfile, mCachedDevice);

        mPreference.onAttached();

        assertThat(mPreference.isChecked()).isTrue();
    }

    @Test
    public void onAttached_panProfile_notConnected_setsUnchecked() {
        mProfile = mock(PanProfile.class);
        when(mProfile.getConnectionStatus(mDevice)).thenReturn(STATE_DISCONNECTED);
        when(mProfile.toString()).thenReturn("key");
        when(mProfile.getNameResource(mDevice)).thenReturn(R.string.bt_profile_name);
        mPreference = new BluetoothDeviceProfilePreference(mContext, mProfile, mCachedDevice);

        mPreference.onAttached();

        assertThat(mPreference.isChecked()).isFalse();
    }

    @Test
    public void onDeviceAttributesChanged_refreshesUi() {
        when(mProfile.isPreferred(mDevice)).thenReturn(false);
        when(mCachedDevice.isBusy()).thenReturn(false);
        ArgumentCaptor<CachedBluetoothDevice.Callback> callbackCaptor = ArgumentCaptor.forClass(
                CachedBluetoothDevice.Callback.class);
        mPreference.onAttached();
        verify(mCachedDevice).registerCallback(callbackCaptor.capture());

        assertThat(mPreference.isEnabled()).isTrue();
        assertThat(mPreference.isChecked()).isFalse();

        when(mProfile.isPreferred(mDevice)).thenReturn(true);
        when(mCachedDevice.isBusy()).thenReturn(true);

        callbackCaptor.getValue().onDeviceAttributesChanged();

        assertThat(mPreference.isEnabled()).isFalse();
        assertThat(mPreference.isChecked()).isTrue();
    }

    @Test
    public void onDetached_unregistersDeviceCallback() {
        ArgumentCaptor<CachedBluetoothDevice.Callback> callbackCaptor = ArgumentCaptor.forClass(
                CachedBluetoothDevice.Callback.class);
        mPreference.onAttached();
        verify(mCachedDevice).registerCallback(callbackCaptor.capture());

        mPreference.onDetached();

        verify(mCachedDevice).unregisterCallback(callbackCaptor.getValue());
    }
}
