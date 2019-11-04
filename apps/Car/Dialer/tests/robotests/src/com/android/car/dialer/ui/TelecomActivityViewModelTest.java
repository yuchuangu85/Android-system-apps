/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car.dialer.ui;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

import com.android.car.dialer.CarDialerRobolectricTestRunner;
import com.android.car.dialer.R;
import com.android.car.dialer.TestDialerApplication;
import com.android.car.dialer.livedata.BluetoothHfpStateLiveData;
import com.android.car.dialer.livedata.BluetoothPairListLiveData;
import com.android.car.dialer.livedata.BluetoothStateLiveData;
import com.android.car.dialer.telecom.UiBluetoothMonitor;
import com.android.car.dialer.telecom.UiCallManager;
import com.android.car.dialer.testutils.ShadowBluetoothAdapterForDialer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.HashSet;

@RunWith(CarDialerRobolectricTestRunner.class)
@Config(shadows = ShadowBluetoothAdapterForDialer.class)
public class TelecomActivityViewModelTest {

    private TelecomActivityViewModel mTelecomActivityViewModel;
    private Context mContext;
    private BluetoothHfpStateLiveData mHfpStateLiveData;
    private BluetoothPairListLiveData mPairedListLiveData;
    private BluetoothStateLiveData mBluetoothStateLiveData;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        ((TestDialerApplication) RuntimeEnvironment.application).initUiCallManager();
    }

    @After
    public void tearDown() {
        UiBluetoothMonitor.get().tearDown();
        UiCallManager.get().tearDown();
    }

    @Test
    public void testDialerAppState_defaultBluetoothAdapterIsNull_bluetoothError() {
        initializeBluetoothMonitor(false);
        initializeViewModel();

        assertThat(mTelecomActivityViewModel.getErrorMessage().getValue()).isEqualTo(
                mContext.getString(R.string.bluetooth_unavailable));
        assertThat(mTelecomActivityViewModel.getDialerAppState().getValue()).isEqualTo(
                TelecomActivityViewModel.DialerAppState.BLUETOOTH_ERROR);
    }

    @Test
    public void testDialerAppState_bluetoothNotEnabled_bluetoothError() {
        initializeBluetoothMonitor(true);
        ShadowBluetoothAdapterForDialer shadowBluetoothAdapter =
                (ShadowBluetoothAdapterForDialer) shadowOf(BluetoothAdapter.getDefaultAdapter());
        shadowBluetoothAdapter.setEnabled(false);
        initializeViewModel();

        assertThat(mBluetoothStateLiveData.getValue()).isEqualTo(
                BluetoothStateLiveData.BluetoothState.DISABLED);
        assertThat(mTelecomActivityViewModel.getErrorMessage().getValue()).isEqualTo(
                mContext.getString(R.string.bluetooth_disabled));
        assertThat(mTelecomActivityViewModel.getDialerAppState().getValue()).isEqualTo(
                TelecomActivityViewModel.DialerAppState.BLUETOOTH_ERROR);
    }

    @Test
    public void testDialerAppState_noPairedDevices_bluetoothError() {
        initializeBluetoothMonitor(true);
        ShadowBluetoothAdapterForDialer shadowBluetoothAdapter =
                (ShadowBluetoothAdapterForDialer) shadowOf(BluetoothAdapter.getDefaultAdapter());
        shadowBluetoothAdapter.setEnabled(true);
        shadowBluetoothAdapter.setBondedDevices(new HashSet<BluetoothDevice>());
        initializeViewModel();

        assertThat(mBluetoothStateLiveData.getValue()).isEqualTo(
                BluetoothStateLiveData.BluetoothState.ENABLED);

        assertThat(mPairedListLiveData.getValue().isEmpty()).isTrue();
        assertThat(mTelecomActivityViewModel.getErrorMessage().getValue()).isEqualTo(
                mContext.getString(R.string.bluetooth_unpaired));
        assertThat(mTelecomActivityViewModel.getDialerAppState().getValue()).isEqualTo(
                TelecomActivityViewModel.DialerAppState.BLUETOOTH_ERROR);
    }

    @Test
    public void testDialerAppState_hfpNoConnected_bluetoothError() {
        initializeBluetoothMonitor(true);
        ShadowBluetoothAdapterForDialer shadowBluetoothAdapter =
                (ShadowBluetoothAdapterForDialer) shadowOf(BluetoothAdapter.getDefaultAdapter());
        shadowBluetoothAdapter.setEnabled(true);
        shadowBluetoothAdapter.setBondedDevices(
                new HashSet<>(Arrays.asList(mock(BluetoothDevice.class))));
        shadowBluetoothAdapter.setProfileConnectionState(BluetoothProfile.HEADSET_CLIENT,
                BluetoothProfile.STATE_DISCONNECTED);
        initializeViewModel();

        assertThat(mBluetoothStateLiveData.getValue()).isEqualTo(
                BluetoothStateLiveData.BluetoothState.ENABLED);
        assertThat(mPairedListLiveData.getValue().isEmpty()).isFalse();

        assertThat(mHfpStateLiveData.getValue() == BluetoothProfile.STATE_DISCONNECTED).isTrue();
        assertThat(mTelecomActivityViewModel.getErrorMessage().getValue()).isEqualTo(
                mContext.getString(R.string.no_hfp));
        assertThat(mTelecomActivityViewModel.getDialerAppState().getValue()).isEqualTo(
                TelecomActivityViewModel.DialerAppState.BLUETOOTH_ERROR);
    }

    @Test
    public void testDialerAppState_bluetoothAllSet_dialerAppStateDefault() {
        initializeBluetoothMonitor(true);
        ShadowBluetoothAdapterForDialer shadowBluetoothAdapter =
                (ShadowBluetoothAdapterForDialer) shadowOf(BluetoothAdapter.getDefaultAdapter());
        shadowBluetoothAdapter.setEnabled(true);
        shadowBluetoothAdapter.setBondedDevices(
                new HashSet<>(Arrays.asList(mock(BluetoothDevice.class))));
        shadowBluetoothAdapter.setProfileConnectionState(BluetoothProfile.HEADSET_CLIENT,
                BluetoothProfile.STATE_CONNECTED);
        initializeViewModel();

        assertThat(mTelecomActivityViewModel.getErrorMessage().getValue()).isEqualTo(
                TelecomActivityViewModel.NO_BT_ERROR);
        assertThat(mTelecomActivityViewModel.getDialerAppState().getValue()).isEqualTo(
                TelecomActivityViewModel.DialerAppState.DEFAULT);
    }

    private void initializeBluetoothMonitor(boolean availability) {
        ShadowBluetoothAdapterForDialer.setBluetoothAvailable(availability);

        UiBluetoothMonitor.init(mContext);
        mHfpStateLiveData = UiBluetoothMonitor.get().getHfpStateLiveData();
        mPairedListLiveData = UiBluetoothMonitor.get().getPairListLiveData();
        mBluetoothStateLiveData = UiBluetoothMonitor.get().getBluetoothStateLiveData();
    }

    private void initializeViewModel() {
        mTelecomActivityViewModel = new TelecomActivityViewModel((Application) mContext);
        // Observers needed so that the liveData's internal initialization is triggered
        mTelecomActivityViewModel.getErrorMessage().observeForever(s -> {
        });
        mTelecomActivityViewModel.getDialerAppState().observeForever(s -> {
        });
    }
}
