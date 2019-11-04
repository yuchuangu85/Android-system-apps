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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothClass;
import android.content.Context;
import android.os.SystemProperties;

import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;

/** Unit test for {@link BluetoothDevicePreference}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
public class BluetoothDevicePreferenceTest {

    @Mock
    private CachedBluetoothDevice mCachedDevice;
    private Context mContext;
    private BluetoothDevicePreference mPreference;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        mPreference = new BluetoothDevicePreference(mContext, mCachedDevice);
    }

    @Test
    public void actionIsHiddenByDefault() {
        assertThat(mPreference.isActionShown()).isFalse();
    }

    @Test
    public void onAttached_registersDeviceCallback() {
        mPreference.onAttached();

        verify(mCachedDevice).registerCallback(any(CachedBluetoothDevice.Callback.class));
    }

    @Test
    public void onAttached_setsDeviceNameAsTitle() {
        String name = "name";
        when(mCachedDevice.getName()).thenReturn(name);

        mPreference.onAttached();

        assertThat(mPreference.getTitle()).isEqualTo(name);
    }

    @Test
    public void onAttached_setsCarConnectionSummaryAsSummary() {
        String summary = "summary";
        when(mCachedDevice.getCarConnectionSummary()).thenReturn(summary);

        mPreference.onAttached();

        assertThat(mPreference.getSummary()).isEqualTo(summary);
    }

    @Test
    public void onAttached_setsIcon() {
        when(mCachedDevice.getBtClass()).thenReturn(
                new BluetoothClass(BluetoothClass.Device.Major.PHONE));

        mPreference.onAttached();

        assertThat(mPreference.getIcon()).isNotNull();
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
    public void onAttached_deviceNameNotHumanReadable_setsHidden() {
        when(mCachedDevice.hasHumanReadableName()).thenReturn(false);

        mPreference.onAttached();

        assertThat(mPreference.isVisible()).isFalse();
    }

    @Test
    public void onAttached_deviceNameNotHumanReadable_showWithoutNamesTrue_setsShown() {
        SystemProperties.set("persist.bluetooth.showdeviceswithoutnames", Boolean.TRUE.toString());
        when(mCachedDevice.hasHumanReadableName()).thenReturn(false);
        mPreference = new BluetoothDevicePreference(mContext, mCachedDevice);

        mPreference.onAttached();

        assertThat(mPreference.isVisible()).isTrue();
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

    @Test
    public void onDeviceAttributesChanged_refreshesUi() {
        String name = "name";
        when(mCachedDevice.getName()).thenReturn(name);
        String summary = "summary";
        when(mCachedDevice.getCarConnectionSummary()).thenReturn(summary);
        when(mCachedDevice.isBusy()).thenReturn(false);
        ArgumentCaptor<CachedBluetoothDevice.Callback> callbackCaptor = ArgumentCaptor.forClass(
                CachedBluetoothDevice.Callback.class);
        mPreference.onAttached();
        verify(mCachedDevice).registerCallback(callbackCaptor.capture());

        assertThat(mPreference.getTitle()).isEqualTo(name);
        assertThat(mPreference.getSummary()).isEqualTo(summary);
        assertThat(mPreference.isEnabled()).isTrue();

        String updatedName = "updatedName";
        when(mCachedDevice.getName()).thenReturn(updatedName);
        String updatedSummary = "updatedSummary";
        when(mCachedDevice.getCarConnectionSummary()).thenReturn(updatedSummary);
        when(mCachedDevice.isBusy()).thenReturn(true);

        callbackCaptor.getValue().onDeviceAttributesChanged();

        assertThat(mPreference.getTitle()).isEqualTo(updatedName);
        assertThat(mPreference.getSummary()).isEqualTo(updatedSummary);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void equals_devicesEqual_returnsTrue() {
        BluetoothDevicePreference otherPreference = new BluetoothDevicePreference(mContext,
                mCachedDevice);

        assertThat(mPreference.equals(otherPreference)).isTrue();
    }

    @Test
    public void equals_devicesNotEqual_returnsFalse() {
        BluetoothDevicePreference otherPreference = new BluetoothDevicePreference(mContext,
                mock(CachedBluetoothDevice.class));

        assertThat(mPreference.equals(otherPreference)).isFalse();
    }

    @Test
    public void compareTo_sameType_usesDeviceCompareTo() {
        CachedBluetoothDevice otherDevice = mock(CachedBluetoothDevice.class);
        BluetoothDevicePreference otherPreference = new BluetoothDevicePreference(mContext,
                otherDevice);
        when(mCachedDevice.compareTo(otherDevice)).thenReturn(1);
        when(otherDevice.compareTo(mCachedDevice)).thenReturn(-1);

        assertThat(mPreference.compareTo(otherPreference)).isEqualTo(1);
        assertThat(otherPreference.compareTo(mPreference)).isEqualTo(-1);
    }

    @Test
    public void compareTo_differentType_fallsBackToDefaultCompare() {
        mPreference.setOrder(1);
        Preference otherPreference = new Preference(mContext);
        otherPreference.setOrder(2);

        assertThat(mPreference.compareTo(otherPreference)).isEqualTo(-1);
        verify(mCachedDevice, never()).compareTo(any());
    }
}
