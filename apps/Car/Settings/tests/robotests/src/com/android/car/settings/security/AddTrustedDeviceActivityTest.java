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

package com.android.car.settings.security;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.car.Car;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.car.trust.CarTrustAgentEnrollmentManager;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.os.Bundle;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.testutils.ShadowCar;
import com.android.car.settings.testutils.ShadowLockPatternUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * Unit tests for {@link AddTrustedDeviceActivity}.
 */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCar.class, ShadowLockPatternUtils.class})
public class AddTrustedDeviceActivityTest {
    private static final String ADDRESS = "00:11:22:33:AA:BB";
    private static final String BLUETOOTH_DEVICE_KEY = "bluetoothDevice";
    private static final String CURRENT_HANDLE_KEY = "currentHandle";
    private Context mContext;
    private ActivityController<AddTrustedDeviceActivity> mActivityController;
    private AddTrustedDeviceActivity mActivity;
    @Mock
    private CarTrustAgentEnrollmentManager mMockCarTrustAgentEnrollmentManager;
    @Mock
    private CarUxRestrictionsManager mMockCarUxRestrictionsManager;
    private CarUserManagerHelper mCarUserManagerHelper;
    private BluetoothDevice mBluetoothDevice;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        CarUxRestrictions noSetupRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* time= */ 0).build();
        when(mMockCarUxRestrictionsManager.getCurrentCarUxRestrictions())
                .thenReturn(noSetupRestrictions);
        ShadowCar.setCarManager(Car.CAR_UX_RESTRICTION_SERVICE, mMockCarUxRestrictionsManager);
        mContext = RuntimeEnvironment.application;
        ShadowCar.setCarManager(Car.CAR_TRUST_AGENT_ENROLLMENT_SERVICE,
                mMockCarTrustAgentEnrollmentManager);
        mCarUserManagerHelper = new CarUserManagerHelper(mContext);
        mBluetoothDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(ADDRESS);
        mActivityController = ActivityController.of(new AddTrustedDeviceActivity());
        mActivity = mActivityController.get();
        ShadowLockPatternUtils.setPasswordQuality(DevicePolicyManager.PASSWORD_QUALITY_NUMERIC);
        mActivityController.create();
    }

    @After
    public void tearDown() {
        ShadowCar.reset();
        ShadowLockPatternUtils.reset();
    }

    @Test
    public void onStart_no_saveInstanceState_startAdvertising() {
        mActivityController.start();
        verify(mMockCarTrustAgentEnrollmentManager).startEnrollmentAdvertising();
    }

    @Test
    public void onStart_saveInstanceState_deviceConnected_doNotStartAdvertising() {
        // Recreate with saved state (e.g. during config change).
        Bundle outState = new Bundle();
        outState.putParcelable(BLUETOOTH_DEVICE_KEY, mBluetoothDevice);
        mActivityController = ActivityController.of(new AddTrustedDeviceActivity());
        mActivityController.setup(outState);
        verify(mMockCarTrustAgentEnrollmentManager, never()).startEnrollmentAdvertising();
    }

    @Test
    public void onStart_saveInstanceState_deviceNotConnected_startAdvertising() {
        // Recreate with saved state (e.g. during config change).
        Bundle outState = new Bundle();
        outState.putParcelable(BLUETOOTH_DEVICE_KEY, null);
        mActivityController = ActivityController.of(new AddTrustedDeviceActivity());
        mActivityController.setup(outState);
        verify(mMockCarTrustAgentEnrollmentManager).startEnrollmentAdvertising();
    }

    @Test
    public void onStart_has_activated_handle_finish() {
        mActivityController.start().postCreate(null).resume();
        ArgumentCaptor<CarTrustAgentEnrollmentManager.CarTrustAgentEnrollmentCallback> callBack =
                ArgumentCaptor.forClass(
                        CarTrustAgentEnrollmentManager.CarTrustAgentEnrollmentCallback.class);
        verify(mMockCarTrustAgentEnrollmentManager).setEnrollmentCallback(callBack.capture());

        callBack.getValue().onEscrowTokenAdded(1);
        mActivityController.stop();

        when(mMockCarTrustAgentEnrollmentManager.isEscrowTokenActive(1,
                mCarUserManagerHelper.getCurrentProcessUserId())).thenReturn(true);
        Bundle outState = new Bundle();
        outState.putLong(CURRENT_HANDLE_KEY, 1);
        outState.putParcelable(BLUETOOTH_DEVICE_KEY, mBluetoothDevice);
        mActivityController = ActivityController.of(new AddTrustedDeviceActivity());
        mActivityController.setup(outState).start();

        assertThat(mActivityController.get().isFinishing()).isTrue();
    }

    @Test
    public void onAuthStringAvailable_createDialog() {
        mActivityController.start().postCreate(null).resume();
        ArgumentCaptor<CarTrustAgentEnrollmentManager.CarTrustAgentEnrollmentCallback>
                enrollmentCallBack =
                ArgumentCaptor.forClass(
                        CarTrustAgentEnrollmentManager.CarTrustAgentEnrollmentCallback.class);
        verify(mMockCarTrustAgentEnrollmentManager).setEnrollmentCallback(
                enrollmentCallBack.capture());
        enrollmentCallBack.getValue().onAuthStringAvailable(mBluetoothDevice, "123");

        assertThat(mActivity.findDialogByTag(ConfirmPairingCodeDialog.TAG)).isNotNull();
    }

    @Test
    public void onLockVerified_showAddTrustedDeviceProgressFragment() {
        mActivityController.start().postCreate(null).resume();

        mActivity.launchFragment(ConfirmLockPinPasswordFragment.newPinInstance());
        mActivity.onLockVerified("lock".getBytes());

        assertThat(mActivity.getSupportFragmentManager().findFragmentById(R.id.fragment_container))
                .isInstanceOf(AddTrustedDeviceProgressFragment.class);
    }

    @Test
    public void onEscrowTokenAdded_showCheckLockFragment() {
        mActivityController.start().postCreate(null).resume();
        ArgumentCaptor<CarTrustAgentEnrollmentManager.CarTrustAgentEnrollmentCallback> callBack =
                ArgumentCaptor.forClass(
                        CarTrustAgentEnrollmentManager.CarTrustAgentEnrollmentCallback.class);
        verify(mMockCarTrustAgentEnrollmentManager).setEnrollmentCallback(callBack.capture());

        callBack.getValue().onEscrowTokenAdded(1);

        assertThat(mActivityController.get().getInitialFragment()).isInstanceOf(
                ConfirmLockPinPasswordFragment.class);
    }

    @Test
    public void onBluetoothDeviceDisconnected_finish() {
        mActivityController.start().postCreate(null).resume();
        ArgumentCaptor<CarTrustAgentEnrollmentManager.CarTrustAgentBleCallback>
                bleCallBack = ArgumentCaptor.forClass(
                CarTrustAgentEnrollmentManager.CarTrustAgentBleCallback.class);
        verify(mMockCarTrustAgentEnrollmentManager).setBleCallback(bleCallBack.capture());

        bleCallBack.getValue().onBleEnrollmentDeviceDisconnected(mBluetoothDevice);

        assertThat(mActivity.isFinishing()).isTrue();
    }

    @Test
    public void onPairingCodeDialogConfirmed_handshakeAccepted() {
        mActivityController.start().postCreate(null).resume();
        ArgumentCaptor<CarTrustAgentEnrollmentManager.CarTrustAgentBleCallback> bleCallBack =
                ArgumentCaptor.forClass(
                        CarTrustAgentEnrollmentManager.CarTrustAgentBleCallback.class);
        verify(mMockCarTrustAgentEnrollmentManager).setBleCallback(bleCallBack.capture());

        bleCallBack.getValue().onBleEnrollmentDeviceConnected(mBluetoothDevice);
        mActivity.mConfirmParingCodeListener.onConfirmPairingCode();
        verify(mMockCarTrustAgentEnrollmentManager).enrollmentHandshakeAccepted(mBluetoothDevice);

    }

    @Test
    public void onStart_onEscrowTokenActiveStateChanged_activated_finish() {
        mActivityController.start().postCreate(null).resume();
        ArgumentCaptor<CarTrustAgentEnrollmentManager.CarTrustAgentEnrollmentCallback> callBack =
                ArgumentCaptor.forClass(
                        CarTrustAgentEnrollmentManager.CarTrustAgentEnrollmentCallback.class);
        verify(mMockCarTrustAgentEnrollmentManager).setEnrollmentCallback(callBack.capture());

        ArgumentCaptor<CarTrustAgentEnrollmentManager.CarTrustAgentBleCallback> bleCallBack =
                ArgumentCaptor.forClass(
                        CarTrustAgentEnrollmentManager.CarTrustAgentBleCallback.class);
        verify(mMockCarTrustAgentEnrollmentManager).setBleCallback(bleCallBack.capture());

        bleCallBack.getValue().onBleEnrollmentDeviceConnected(mBluetoothDevice);
        callBack.getValue().onEscrowTokenActiveStateChanged(1, true);

        assertThat(mActivity.isFinishing()).isTrue();
    }

    @Test
    public void configurationNotChanged_terminateEnrollmentHandshake() {
        mActivityController.start();

        mActivityController.pause();

        verify(mMockCarTrustAgentEnrollmentManager).terminateEnrollmentHandshake();
    }
}
