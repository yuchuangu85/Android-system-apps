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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.widget.Button;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.testutils.BaseTestActivity;
import com.android.car.settings.testutils.FragmentController;
import com.android.car.settings.testutils.ShadowBluetoothAdapter;
import com.android.car.settings.testutils.ShadowBluetoothPan;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.CachedBluetoothDeviceManager;
import com.android.settingslib.bluetooth.LocalBluetoothManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

/** Unit test for {@link BluetoothDeviceDetailsFragment}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowBluetoothAdapter.class, ShadowBluetoothPan.class})
public class BluetoothDeviceDetailsFragmentTest {

    @Mock
    private CachedBluetoothDevice mCachedDevice;
    @Mock
    private CachedBluetoothDeviceManager mCachedDeviceManager;
    private CachedBluetoothDeviceManager mSaveRealCachedDeviceManager;
    private LocalBluetoothManager mLocalBluetoothManager;
    private Context mContext;
    private FragmentController<BluetoothDeviceDetailsFragment> mFragmentController;
    private BluetoothDeviceDetailsFragment mFragment;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;

        mLocalBluetoothManager = LocalBluetoothManager.getInstance(mContext, /* onInitCallback= */
                null);
        mSaveRealCachedDeviceManager = mLocalBluetoothManager.getCachedDeviceManager();
        ReflectionHelpers.setField(mLocalBluetoothManager, "mCachedDeviceManager",
                mCachedDeviceManager);

        String address = "00:11:22:33:AA:BB";
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        when(mCachedDeviceManager.findDevice(device)).thenReturn(mCachedDevice);
        when(mCachedDevice.getAddress()).thenReturn(address);

        mFragment = BluetoothDeviceDetailsFragment.newInstance(mCachedDevice);
        mFragmentController = FragmentController.of(mFragment);
    }

    @After
    public void tearDown() {
        ShadowBluetoothAdapter.reset();
        ReflectionHelpers.setField(mLocalBluetoothManager, "mCachedDeviceManager",
                mSaveRealCachedDeviceManager);
    }

    @Test
    public void forgetButtonClicked_unpairsDevice() {
        mFragmentController.setup();

        findForgetButton(mFragment.requireActivity()).performClick();

        verify(mCachedDevice).unpair();
    }

    @Test
    public void forgetButtonClicked_goesBack() {
        mFragmentController.setup();

        findForgetButton(mFragment.requireActivity()).performClick();

        assertThat(
                ((BaseTestActivity) mFragment.requireActivity()).getOnBackPressedFlag()).isTrue();
    }

    @Test
    public void connectionButtonClicked_deviceConnected_disconnectsDevice() {
        when(mCachedDevice.isConnected()).thenReturn(true);
        mFragmentController.setup();

        findConnectionButton(mFragment.requireActivity()).performClick();

        verify(mCachedDevice).disconnect();
    }

    @Test
    public void connectionButtonClicked_deviceNotConnected_connectsDevice() {
        when(mCachedDevice.isConnected()).thenReturn(false);
        mFragmentController.setup();

        findConnectionButton(mFragment.requireActivity()).performClick();

        verify(mCachedDevice).connect(/* connectAllProfiles= */ true);
    }

    @Test
    public void deviceConnected_connectionButtonShowsDisconnect() {
        when(mCachedDevice.isConnected()).thenReturn(true);
        mFragmentController.setup();

        assertThat(findConnectionButton(mFragment.requireActivity()).getText()).isEqualTo(
                mContext.getString(R.string.disconnect));
    }

    @Test
    public void deviceNotConnected_connectionButtonShowsConnect() {
        when(mCachedDevice.isConnected()).thenReturn(false);
        mFragmentController.setup();

        assertThat(findConnectionButton(mFragment.requireActivity()).getText()).isEqualTo(
                mContext.getString(R.string.connect));
    }

    @Test
    public void deviceBusy_connectionButtonDisabled() {
        when(mCachedDevice.isBusy()).thenReturn(true);
        mFragmentController.setup();

        assertThat(findConnectionButton(mFragment.requireActivity()).isEnabled()).isFalse();
    }

    @Test
    public void deviceNotBusy_connectionButtonEnabled() {
        when(mCachedDevice.isBusy()).thenReturn(false);
        mFragmentController.setup();

        assertThat(findConnectionButton(mFragment.requireActivity()).isEnabled()).isTrue();
    }

    @Test
    public void onStart_listensForDeviceAttributesChanges() {
        mFragmentController.create().start();

        verify(mCachedDevice).registerCallback(any(CachedBluetoothDevice.Callback.class));
    }

    @Test
    public void onStop_stopsListeningForDeviceAttributeChanges() {
        ArgumentCaptor<CachedBluetoothDevice.Callback> callbackCaptor = ArgumentCaptor.forClass(
                CachedBluetoothDevice.Callback.class);
        mFragmentController.create().start();
        verify(mCachedDevice).registerCallback(callbackCaptor.capture());

        mFragmentController.stop();

        verify(mCachedDevice).unregisterCallback(callbackCaptor.getValue());
    }

    @Test
    public void deviceAttributesChanged_updatesConnectionButtonState() {
        when(mCachedDevice.isBusy()).thenReturn(true);
        ArgumentCaptor<CachedBluetoothDevice.Callback> callbackCaptor = ArgumentCaptor.forClass(
                CachedBluetoothDevice.Callback.class);
        mFragmentController.create().start();
        assertThat(findConnectionButton(mFragment.requireActivity()).isEnabled()).isFalse();
        verify(mCachedDevice).registerCallback(callbackCaptor.capture());

        when(mCachedDevice.isBusy()).thenReturn(false);
        callbackCaptor.getValue().onDeviceAttributesChanged();

        assertThat(findConnectionButton(mFragment.requireActivity()).isEnabled()).isTrue();
    }

    private Button findForgetButton(Activity activity) {
        return activity.findViewById(R.id.action_button2);
    }

    private Button findConnectionButton(Activity activity) {
        return activity.findViewById(R.id.action_button1);
    }
}
