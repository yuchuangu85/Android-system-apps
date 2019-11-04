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

package com.android.car.trust;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.car.encryptionrunner.DummyEncryptionRunner;
import android.car.encryptionrunner.EncryptionRunnerFactory;
import android.car.encryptionrunner.HandshakeMessage;
import android.car.trust.TrustedDeviceInfo;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.RemoteException;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.car.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

/**
 * Unit test for {@link CarTrustAgentEnrollmentService} and {@link CarTrustedDeviceService}.
 */
@RunWith(AndroidJUnit4.class)
public class CarTrustAgentEnrollmentServiceTest {

    private static final long TEST_HANDLE1 = 1L;
    private static final long TEST_HANDLE2 = 2L;
    // Random uuid for test
    private static final UUID TEST_ID1 = UUID.fromString("9a138a69-7c29-400f-9e71-fc29516f9f8b");
    private static final UUID TEST_ID2 = UUID.fromString("3e344860-e688-4cce-8411-16161b61ad57");
    private static final String TEST_TOKEN = "test_escrow_token";
    private static final String ADDRESS = "00:11:22:33:AA:BB";
    private static final String DEFAULT_NAME = "My Device";
    private static final TrustedDeviceInfo DEVICE_INFO1 = new TrustedDeviceInfo(
            TEST_HANDLE1, ADDRESS, DEFAULT_NAME);
    private static final TrustedDeviceInfo DEVICE_INFO2 = new TrustedDeviceInfo(
            TEST_HANDLE2, ADDRESS, DEFAULT_NAME);

    private Context mContext;
    private CarTrustedDeviceService mCarTrustedDeviceService;
    private CarTrustAgentEnrollmentService mCarTrustAgentEnrollmentService;
    private BluetoothDevice mBluetoothDevice;
    private int mUserId;
    @Mock
    private CarTrustAgentBleManager mMockCarTrustAgentBleManager;

    @Mock
    private CarTrustAgentEnrollmentService.CarTrustAgentEnrollmentRequestDelegate mEnrollDelegate =
            new CarTrustAgentEnrollmentService.CarTrustAgentEnrollmentRequestDelegate() {
                @Override
                public void addEscrowToken(byte[] token, int uid) {
                }

                @Override
                public void removeEscrowToken(long handle, int uid) {
                }

                @Override
                public void isEscrowTokenActive(long handle, int uid) {
                }
            };

    @Before
    public void setUp() throws RemoteException {
        MockitoAnnotations.initMocks(this);

        mBluetoothDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(ADDRESS);
        mContext = InstrumentationRegistry.getTargetContext();
        mCarTrustedDeviceService = new CarTrustedDeviceService(mContext);
        mCarTrustAgentEnrollmentService = new CarTrustAgentEnrollmentService(mContext,
                mCarTrustedDeviceService, mMockCarTrustAgentBleManager);
        mCarTrustedDeviceService.init();
        mCarTrustAgentEnrollmentService.init();
        mCarTrustAgentEnrollmentService.setEnrollmentRequestDelegate(mEnrollDelegate);
        mUserId = new CarUserManagerHelper(mContext).getCurrentProcessUserId();
        mCarTrustAgentEnrollmentService.onRemoteDeviceConnected(mBluetoothDevice);
    }

    @After
    public void tearDown() {
        // Need to clear the shared preference
        mCarTrustAgentEnrollmentService.onEscrowTokenRemoved(TEST_HANDLE1, mUserId);
        mCarTrustAgentEnrollmentService.onEscrowTokenRemoved(TEST_HANDLE2, mUserId);
    }

    @Test
    public void testDisableEnrollment_startEnrollmentAdvertisingFail() {
        mCarTrustAgentEnrollmentService.setTrustedDeviceEnrollmentEnabled(false);
        mCarTrustAgentEnrollmentService.startEnrollmentAdvertising();
        verify(mMockCarTrustAgentBleManager, never()).startEnrollmentAdvertising();
    }

    @Test
    public void testEncryptionHandshake() {
        mCarTrustAgentEnrollmentService.setEncryptionRunner(
                EncryptionRunnerFactory.newDummyRunner());
        assertThat(mCarTrustAgentEnrollmentService.mEnrollmentState).isEqualTo(
                mCarTrustAgentEnrollmentService.ENROLLMENT_STATE_NONE);
        // Have received device unique id.
        UUID uuid = UUID.randomUUID();
        mCarTrustAgentEnrollmentService.onEnrollmentDataReceived(Utils.uuidToBytes(uuid));
        assertThat(mCarTrustAgentEnrollmentService.mEnrollmentState).isEqualTo(
                mCarTrustAgentEnrollmentService.ENROLLMENT_STATE_UNIQUE_ID);
        assertThat(mCarTrustAgentEnrollmentService.mEncryptionState).isEqualTo(
                HandshakeMessage.HandshakeState.UNKNOWN);
        // send device unique id
        verify(mMockCarTrustAgentBleManager).sendEnrollmentMessage(eq(mBluetoothDevice),
                eq(Utils.uuidToBytes(mCarTrustedDeviceService.getUniqueId())), any(), eq(false));

        // Have received handshake request.
        mCarTrustAgentEnrollmentService.onEnrollmentDataReceived(
                DummyEncryptionRunner.INIT.getBytes());
        assertThat(mCarTrustAgentEnrollmentService.mEncryptionState).isEqualTo(
                HandshakeMessage.HandshakeState.IN_PROGRESS);
        // Send response to init handshake request
        verify(mMockCarTrustAgentBleManager).sendEnrollmentMessage(eq(mBluetoothDevice),
                eq(DummyEncryptionRunner.INIT_RESPONSE.getBytes()), any(), eq(false));

        mCarTrustAgentEnrollmentService.onEnrollmentDataReceived(
                DummyEncryptionRunner.CLIENT_RESPONSE.getBytes());
        assertThat(mCarTrustAgentEnrollmentService.mEncryptionState).isEqualTo(
                HandshakeMessage.HandshakeState.VERIFICATION_NEEDED);
        verify(mMockCarTrustAgentBleManager).sendEnrollmentMessage(eq(mBluetoothDevice),
                eq(DummyEncryptionRunner.INIT_RESPONSE.getBytes()), any(), eq(false));

        // Completed the handshake by confirming the verification code.
        mCarTrustAgentEnrollmentService.enrollmentHandshakeAccepted(mBluetoothDevice);
        verify(mMockCarTrustAgentBleManager).sendEnrollmentMessage(eq(mBluetoothDevice),
                eq(mCarTrustAgentEnrollmentService.CONFIRMATION_SIGNAL), any(), eq(false));
        assertThat(mCarTrustAgentEnrollmentService.mEncryptionState).isEqualTo(
                HandshakeMessage.HandshakeState.FINISHED);
        assertThat(mCarTrustAgentEnrollmentService.mEnrollmentState).isEqualTo(
                mCarTrustAgentEnrollmentService.ENROLLMENT_STATE_ENCRYPTION_COMPLETED);
    }

    @Test
    public void testOnEscrowTokenAdded_tokenRequiresActivation() {
        mCarTrustAgentEnrollmentService.onEscrowTokenAdded(TEST_TOKEN.getBytes(), TEST_HANDLE1,
                mUserId);

        // Token has not been activated, the number of enrolled trusted device will remain the same.
        assertThat(mCarTrustAgentEnrollmentService.getEnrolledDeviceInfosForUser(
                mUserId)).isEmpty();
        assertThat(mCarTrustAgentEnrollmentService.isEscrowTokenActive(TEST_HANDLE1,
                mUserId)).isFalse();
    }

    @Test
    public void testOnEscrowTokenActiveStateChange_true_addTrustedDevice() {
        // Set up the service to go through the handshake
        setupEncryptionHandshake(TEST_ID1);

        // Token has been activated and added to the shared preference
        mCarTrustAgentEnrollmentService.onEscrowTokenActiveStateChanged(
                TEST_HANDLE1, /* isTokenActive= */ true, mUserId);

        verify(mMockCarTrustAgentBleManager).sendEnrollmentMessage(eq(mBluetoothDevice), any(),
                any(), eq(true));
        assertThat(mCarTrustAgentEnrollmentService.getEnrolledDeviceInfosForUser(
                mUserId)).containsExactly(DEVICE_INFO1);
        assertThat(mCarTrustAgentEnrollmentService.isEscrowTokenActive(TEST_HANDLE1,
                mUserId)).isTrue();
    }

    @Test
    public void testOnEscrowTokenActiveStateChange_addDuplicateDevice() {
        // Set up the service to go through the handshake
        setupEncryptionHandshake(TEST_ID1);

        // Enroll device
        mCarTrustAgentEnrollmentService.onEscrowTokenActiveStateChanged(
                TEST_HANDLE1, /* isTokenActive= */ true, mUserId);

        // Enroll same device again
        mCarTrustAgentEnrollmentService.onEscrowTokenActiveStateChanged(
                TEST_HANDLE2, /* isTokenActive= */ true, mUserId);
        verify(mEnrollDelegate).removeEscrowToken(eq(TEST_HANDLE1), eq(mUserId));
    }

    @Test
    public void testOnEscrowTokenActiveStateChange_false_doNotAddTrustedDevice() {
        // Set up the service to go through the handshake
        setupEncryptionHandshake(TEST_ID1);

        // Token activation fail
        mCarTrustAgentEnrollmentService.onEscrowTokenActiveStateChanged(
                TEST_HANDLE1, /* isTokenActive= */ false, mUserId);

        assertThat(mCarTrustAgentEnrollmentService.getEnrolledDeviceInfosForUser(
                mUserId)).isEmpty();
    }

    @Test
    public void testOnEscrowTokenRemoved_removeOneTrustedDevice() {
        setupEncryptionHandshake(TEST_ID1);
        SharedPreferences sharedPrefs = mCarTrustedDeviceService.getSharedPrefs();
        mCarTrustAgentEnrollmentService.onEscrowTokenActiveStateChanged(
                TEST_HANDLE1, /* isTokenActive= */ true,
                mUserId);

        assertThat(mCarTrustAgentEnrollmentService.getEnrolledDeviceInfosForUser(
                mUserId)).containsExactly(DEVICE_INFO1);
        assertThat(mCarTrustedDeviceService.getUserHandleByTokenHandle(TEST_HANDLE1)).isEqualTo(
                mUserId);
        assertThat(sharedPrefs.getLong(TEST_ID1.toString(), -1)).isEqualTo(TEST_HANDLE1);

        mCarTrustAgentEnrollmentService.onEscrowTokenActiveStateChanged(
                TEST_HANDLE2, /* isTokenActive= */ true,
                mUserId);

        assertThat(mCarTrustAgentEnrollmentService.getEnrolledDeviceInfosForUser(
                mUserId)).containsExactly(DEVICE_INFO1, DEVICE_INFO2);
        assertThat(mCarTrustedDeviceService.getUserHandleByTokenHandle(TEST_HANDLE2)).isEqualTo(
                mUserId);
        assertThat(sharedPrefs.getLong(TEST_ID1.toString(), -1)).isEqualTo(TEST_HANDLE2);

        // Remove all handles
        mCarTrustAgentEnrollmentService.onEscrowTokenRemoved(TEST_HANDLE1, mUserId);

        assertThat(mCarTrustAgentEnrollmentService.getEnrolledDeviceInfosForUser(
                mUserId)).containsExactly(DEVICE_INFO2);
        assertThat(mCarTrustedDeviceService.getUserHandleByTokenHandle(TEST_HANDLE1)).isEqualTo(-1);
        assertThat(mCarTrustedDeviceService.getUserHandleByTokenHandle(TEST_HANDLE2)).isEqualTo(
                mUserId);
        assertThat(sharedPrefs.getLong(TEST_ID1.toString(), -1)).isEqualTo(TEST_HANDLE2);

        mCarTrustAgentEnrollmentService.onEscrowTokenRemoved(TEST_HANDLE2, mUserId);

        assertThat(mCarTrustAgentEnrollmentService.getEnrolledDeviceInfosForUser(
            mUserId)).isEmpty();
        assertThat(mCarTrustedDeviceService.getUserHandleByTokenHandle(TEST_HANDLE2)).isEqualTo(-1);
        assertThat(sharedPrefs.getLong(TEST_ID1.toString(), -1)).isEqualTo(-1);
    }

    @Test
    public void testGetUserHandleByTokenHandle_nonExistentHandle() {
        assertThat(mCarTrustedDeviceService.getUserHandleByTokenHandle(TEST_HANDLE1)).isEqualTo(-1);
    }

    @Test
    public void testGetUserHandleByTokenHandle_existingHandle() {
        setupEncryptionHandshake(TEST_ID1);
        mCarTrustAgentEnrollmentService.onEscrowTokenActiveStateChanged(
                TEST_HANDLE1, /* isTokenActive= */ true, mUserId);
        assertThat(mCarTrustedDeviceService.getUserHandleByTokenHandle(TEST_HANDLE1)).isEqualTo(
                mUserId);
    }

    private void setupEncryptionHandshake(UUID uuid) {
        mCarTrustAgentEnrollmentService.setEncryptionRunner(
                EncryptionRunnerFactory.newDummyRunner());
        mCarTrustAgentEnrollmentService.onEnrollmentDataReceived(Utils.uuidToBytes(uuid));
        mCarTrustAgentEnrollmentService.onEnrollmentDataReceived(
                DummyEncryptionRunner.INIT.getBytes());
        mCarTrustAgentEnrollmentService.onEnrollmentDataReceived(
                DummyEncryptionRunner.CLIENT_RESPONSE.getBytes());
        mCarTrustAgentEnrollmentService.enrollmentHandshakeAccepted(mBluetoothDevice);
    }
}
