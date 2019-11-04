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
package com.android.car.dialer.telecom;

import static com.android.car.dialer.telecom.ProjectionCallHandler.HFP_CLIENT_SCHEME;
import static com.android.car.dialer.telecom.ProjectionCallHandler.PROJECTION_STATUS_EXTRA_DEVICE_STATE;
import static com.android.car.dialer.telecom.ProjectionCallHandler.PROJECTION_STATUS_EXTRA_HANDLES_PHONE_UI;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.car.CarProjectionManager;
import android.car.projection.ProjectionStatus;
import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.telecom.Call;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
public class ProjectionCallHandlerTest {
    private static final String HFP_ADDRESS = "00:11:22:33:44:55";
    private static final String NON_HFP_ADDRESS = "AA:BB:CC:DD:EE:FF";

    private static PhoneAccount.Builder phoneAccountBuilder(String name) {
        return PhoneAccount.builder(
                new PhoneAccountHandle(
                        ComponentName.unflattenFromString("package.name/.class"),
                        name),
                name);
    }

    private final PhoneAccount mHfpAccount = phoneAccountBuilder("HFP")
            .setAddress(Uri.fromParts(HFP_CLIENT_SCHEME, HFP_ADDRESS, null))
            .build();

    private final PhoneAccount mNonHfpAccount = phoneAccountBuilder("Non-HFP")
            .setAddress(Uri.fromParts("tel", "1234567890", null))
            .build();

    private final PhoneAccount mAccountWithNoAddress = phoneAccountBuilder("No Address").build();

    private Context mContext;
    private TelecomManager mTelecomManager;
    private CarProjectionManager mCarProjectionManager;

    private ProjectionCallHandler mProjectionCallHandler;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mTelecomManager = spy(mContext.getSystemService(TelecomManager.class));
        mCarProjectionManager = mock(CarProjectionManager.class);

        when(mTelecomManager.isInEmergencyCall()).thenReturn(false);

        mTelecomManager.registerPhoneAccount(mHfpAccount);
        mTelecomManager.registerPhoneAccount(mNonHfpAccount);
        mTelecomManager.registerPhoneAccount(mAccountWithNoAddress);

        mProjectionCallHandler = new ProjectionCallHandler(mTelecomManager, mCarProjectionManager);
    }

    @Test
    public void startAndStop_registerAndUnregisterProjectionStatusListener() {
        mProjectionCallHandler.start();
        verify(mCarProjectionManager).registerProjectionStatusListener(mProjectionCallHandler);

        mProjectionCallHandler.stop();
        verify(mCarProjectionManager).unregisterProjectionStatusListener(mProjectionCallHandler);
    }

    @Test
    public void noProjectionApps_doesNotSuppress() {
        assertThat(shouldSuppressCallFor(mHfpAccount)).isFalse();
    }

    @Test
    public void projectionApp_inactive_doesNotSuppress() {
        sendProjectionStatus(
                projectionStatusBuilder(ProjectionStatus.PROJECTION_STATE_INACTIVE)
                        .build());

        assertThat(shouldSuppressCallFor(mHfpAccount)).isFalse();
    }

    @Test
    public void projectionApp_readyToProject_doesNotSuppress() {
        sendProjectionStatus(
                projectionStatusBuilder(ProjectionStatus.PROJECTION_STATE_READY_TO_PROJECT)
                        .build());

        assertThat(shouldSuppressCallFor(mHfpAccount)).isFalse();
    }

    @Test
    public void activeApp_noProjectingDevices_doesNotSuppress() {
        sendProjectionStatus(
                projectionStatusBuilder(ProjectionStatus.PROJECTION_STATE_ACTIVE_FOREGROUND)
                        .addMobileDevice(
                                mobileDeviceBuilder().setProjecting(false).build())
                        .build());

        assertThat(shouldSuppressCallFor(mHfpAccount)).isFalse();
    }

    @Test
    public void activeApp_withProjectingDevice_suppresses() {
        sendProjectionStatus(
                projectionStatusBuilder(ProjectionStatus.PROJECTION_STATE_ACTIVE_FOREGROUND)
                        .addMobileDevice(
                                mobileDeviceBuilder().setProjecting(true).build())
                        .build());

        assertThat(shouldSuppressCallFor(mHfpAccount)).isTrue();
    }

    @Test
    public void activeApp_thatHandlesPhoneUi_suppresses() {
        Bundle extras = new Bundle();
        extras.putBoolean(PROJECTION_STATUS_EXTRA_HANDLES_PHONE_UI, true);

        sendProjectionStatus(suppressableStatusBuilder().setExtras(extras).build());

        assertThat(shouldSuppressCallFor(mHfpAccount)).isTrue();
    }

    @Test
    public void activeApp_thatDoesNotHandlePhoneUi_doesNotSuppress() {
        Bundle extras = new Bundle();
        extras.putBoolean(PROJECTION_STATUS_EXTRA_HANDLES_PHONE_UI, false);

        sendProjectionStatus(suppressableStatusBuilder().setExtras(extras).build());

        assertThat(shouldSuppressCallFor(mHfpAccount)).isFalse();
    }

    @Test
    public void activeApp_withProjectingDevice_withBackgroundStateExtra_doesNotSuppress() {
        Bundle deviceExtras = new Bundle();
        deviceExtras.putInt(
                PROJECTION_STATUS_EXTRA_DEVICE_STATE,
                ProjectionStatus.PROJECTION_STATE_ACTIVE_BACKGROUND);

        sendProjectionStatus(suppressableStatus(deviceExtras));

        assertThat(shouldSuppressCallFor(mHfpAccount)).isFalse();
    }

    @Test
    public void activeApp_withProjectingDevice_withForegroundStateExtra_suppresses() {
        Bundle deviceExtras = new Bundle();
        deviceExtras.putInt(
                PROJECTION_STATUS_EXTRA_DEVICE_STATE,
                ProjectionStatus.PROJECTION_STATE_ACTIVE_FOREGROUND);

        sendProjectionStatus(suppressableStatus(deviceExtras));

        assertThat(shouldSuppressCallFor(mHfpAccount)).isTrue();
    }

    @Test
    public void activeApp_withInvalidBluetoothDeviceExtra_suppresses() {
        Bundle deviceExtras = new Bundle();
        deviceExtras.putParcelable(BluetoothDevice.EXTRA_DEVICE, mock(Parcelable.class));

        sendProjectionStatus(suppressableStatus(deviceExtras));

        assertThat(shouldSuppressCallFor(mHfpAccount)).isTrue();
    }

    @Test
    public void activeApp_withNonMatchingBluetoothDeviceExtra_doesNotSuppress() {
        Bundle deviceExtras = new Bundle();
        deviceExtras.putParcelable(
                BluetoothDevice.EXTRA_DEVICE,
                BluetoothAdapter.getDefaultAdapter().getRemoteDevice(NON_HFP_ADDRESS));

        sendProjectionStatus(suppressableStatus(deviceExtras));

        assertThat(shouldSuppressCallFor(mHfpAccount)).isFalse();
    }

    @Test
    public void activeApp_withMatchingBluetoothDeviceExtra_suppresses() {
        Bundle deviceExtras = new Bundle();
        deviceExtras.putParcelable(
                BluetoothDevice.EXTRA_DEVICE,
                BluetoothAdapter.getDefaultAdapter().getRemoteDevice(HFP_ADDRESS));

        sendProjectionStatus(suppressableStatus(deviceExtras));

        assertThat(shouldSuppressCallFor(mHfpAccount)).isTrue();
    }

    @Test
    public void emergencyCall_isNotSuppressed() {
        sendProjectionStatus(suppressableStatus());

        when(mTelecomManager.isInEmergencyCall()).thenReturn(true);

        assertThat(shouldSuppressCallFor(mHfpAccount)).isFalse();
    }

    @Test
    public void call_fromTelAccount_isNotSuppressed() {
        sendProjectionStatus(suppressableStatus());

        assertThat(shouldSuppressCallFor(mNonHfpAccount)).isFalse();
    }

    @Test
    public void call_fromAccount_withNoAddress_isNotSuppressed() {
        sendProjectionStatus(suppressableStatus());

        assertThat(shouldSuppressCallFor(mAccountWithNoAddress)).isFalse();
    }

    @Test
    public void call_fromAccount_withInvalidPhoneAccountHandle_isNotSuppressed() {
        sendProjectionStatus(suppressableStatus());
        mTelecomManager.unregisterPhoneAccount(mHfpAccount.getAccountHandle());

        assertThat(shouldSuppressCallFor(mHfpAccount)).isFalse();
    }

    @Test
    public void call_withNullDetails_isNotSuppressed() {
        sendProjectionStatus(suppressableStatus());

        Call call = createCall(mHfpAccount);
        when(call.getDetails()).thenReturn(null);

        assertThat(mProjectionCallHandler.onTelecomCallAdded(call)).isFalse();
    }

    private Call createCall(PhoneAccount account) {
        Call.Details details = mock(Call.Details.class);
        when(details.getAccountHandle()).thenReturn(account.getAccountHandle());

        Call call = mock(Call.class);
        when(call.getDetails()).thenReturn(details);

        return call;
    }

    private boolean shouldSuppressCallFor(PhoneAccount account) {
        return mProjectionCallHandler.onTelecomCallAdded(createCall(account));
    }

    private ProjectionStatus.Builder projectionStatusBuilder(int state) {
        return ProjectionStatus.builder(mContext.getPackageName(), state);
    }

    private ProjectionStatus.MobileDevice.Builder mobileDeviceBuilder() {
        return ProjectionStatus.MobileDevice.builder(0, "device");
    }

    private ProjectionStatus suppressableStatus() {
        return suppressableStatus(null);
    }

    private ProjectionStatus suppressableStatus(@Nullable Bundle deviceExtras) {
        return suppressableStatusBuilder(deviceExtras).build();
    }

    private ProjectionStatus.Builder suppressableStatusBuilder() {
        return suppressableStatusBuilder(null);
    }

    private ProjectionStatus.Builder suppressableStatusBuilder(@Nullable Bundle deviceExtras) {
        return projectionStatusBuilder(ProjectionStatus.PROJECTION_STATE_ACTIVE_FOREGROUND)
                .addMobileDevice(
                        mobileDeviceBuilder().setProjecting(true).setExtras(deviceExtras).build());
    }

    private void sendProjectionStatus(ProjectionStatus status) {
        mProjectionCallHandler.onProjectionStatusChanged(
                status.getState(),
                status.getPackageName(),
                Collections.singletonList(status));
    }
}
