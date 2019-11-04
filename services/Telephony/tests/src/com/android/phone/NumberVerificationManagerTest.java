/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.phone;

import static junit.framework.TestCase.assertFalse;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.telephony.NumberVerificationCallback;
import android.telephony.PhoneNumberRange;
import android.telephony.ServiceState;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.INumberVerificationCallback;
import com.android.internal.telephony.Phone;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public class NumberVerificationManagerTest {
    private static final PhoneNumberRange SAMPLE_RANGE =
            new PhoneNumberRange("1", "650555", "0000", "8999");
    private static final long DEFAULT_VERIFICATION_TIMEOUT = 100;
    @Mock private Phone mPhone1;
    @Mock private Phone mPhone2;
    @Mock private Call mRingingCall;
    @Mock private Call mForegroundCall;
    @Mock private Call mBackgroundCall;
    @Mock private INumberVerificationCallback mCallback;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ServiceState ss = mock(ServiceState.class);
        when(ss.getVoiceRegState()).thenReturn(ServiceState.STATE_IN_SERVICE);
        when(mPhone1.getServiceState()).thenReturn(ss);
        when(mPhone1.getForegroundCall()).thenReturn(mForegroundCall);
        when(mPhone1.getRingingCall()).thenReturn(mRingingCall);
        when(mPhone1.getBackgroundCall()).thenReturn(mBackgroundCall);
        when(mPhone2.getServiceState()).thenReturn(ss);
        when(mPhone2.getForegroundCall()).thenReturn(mForegroundCall);
        when(mPhone2.getRingingCall()).thenReturn(mRingingCall);
        when(mPhone2.getBackgroundCall()).thenReturn(mBackgroundCall);

        when(mForegroundCall.getState()).thenReturn(Call.State.IDLE);
        when(mRingingCall.getState()).thenReturn(Call.State.IDLE);
        when(mBackgroundCall.getState()).thenReturn(Call.State.IDLE);
    }

    @Test
    public void testConcurrentRequestFailure() throws Exception {
        NumberVerificationManager manager =
                new NumberVerificationManager(() -> new Phone[]{mPhone1});
        manager.requestVerification(SAMPLE_RANGE, mCallback, DEFAULT_VERIFICATION_TIMEOUT);
        manager.requestVerification(SAMPLE_RANGE, mCallback, DEFAULT_VERIFICATION_TIMEOUT);
        verify(mCallback, times(1)).onVerificationFailed(
                NumberVerificationCallback.REASON_CONCURRENT_REQUESTS);
    }

    @Test
    public void testEcbmFailure() throws Exception {
        NumberVerificationManager manager =
                new NumberVerificationManager(() -> new Phone[]{mPhone1});
        when(mPhone1.isInEcm()).thenReturn(true);

        manager.requestVerification(SAMPLE_RANGE, mCallback, DEFAULT_VERIFICATION_TIMEOUT);
        verify(mCallback, times(1)).onVerificationFailed(
                NumberVerificationCallback.REASON_IN_ECBM);
    }

    @Test
    public void testEmergencyCallFailure() throws Exception {
        NumberVerificationManager manager =
                new NumberVerificationManager(() -> new Phone[]{mPhone1});
        when(mPhone1.isInEmergencyCall()).thenReturn(true);

        manager.requestVerification(SAMPLE_RANGE, mCallback, DEFAULT_VERIFICATION_TIMEOUT);
        verify(mCallback, times(1)).onVerificationFailed(
                NumberVerificationCallback.REASON_IN_EMERGENCY_CALL);
    }

    @Test
    public void testNoPhoneInServiceFailure() throws Exception {
        ServiceState ss = mock(ServiceState.class);
        when(ss.getVoiceRegState()).thenReturn(ServiceState.STATE_POWER_OFF);
        when(mPhone1.getServiceState()).thenReturn(ss);
        when(mPhone2.getServiceState()).thenReturn(ss);
        NumberVerificationManager manager =
                new NumberVerificationManager(() -> new Phone[]{mPhone1, mPhone2});

        manager.requestVerification(SAMPLE_RANGE, mCallback, DEFAULT_VERIFICATION_TIMEOUT);
        verify(mCallback, times(1)).onVerificationFailed(
                NumberVerificationCallback.REASON_NETWORK_NOT_AVAILABLE);
    }

    @Test
    public void testAllLinesFullFailure() throws Exception {
        NumberVerificationManager manager =
                new NumberVerificationManager(() -> new Phone[]{mPhone1, mPhone2});
        when(mRingingCall.getState()).thenReturn(Call.State.ALERTING);

        manager.requestVerification(SAMPLE_RANGE, mCallback, DEFAULT_VERIFICATION_TIMEOUT);
        verify(mCallback, times(1)).onVerificationFailed(
                NumberVerificationCallback.REASON_TOO_MANY_CALLS);
    }

    private void verifyDefaultRangeMatching(NumberVerificationManager manager) throws Exception {
        String testNumber = "6505550000";
        assertTrue(manager.checkIncomingCall(testNumber));
        verify(mCallback).onCallReceived(testNumber);
    }

    @Test
    public void testVerificationWorksWithOnePhoneInService() throws Exception {
        ServiceState ss = mock(ServiceState.class);
        when(ss.getVoiceRegState()).thenReturn(ServiceState.STATE_POWER_OFF);
        when(mPhone1.getServiceState()).thenReturn(ss);
        NumberVerificationManager manager =
                new NumberVerificationManager(() -> new Phone[]{mPhone1, mPhone2});

        manager.requestVerification(SAMPLE_RANGE, mCallback, DEFAULT_VERIFICATION_TIMEOUT);
        verify(mCallback, never()).onVerificationFailed(anyInt());
        verifyDefaultRangeMatching(manager);
    }

    @Test
    public void testVerificationWorksWithOnePhoneFull() throws Exception {
        Call fakeCall = mock(Call.class);
        when(fakeCall.getState()).thenReturn(Call.State.ACTIVE);
        when(mPhone1.getForegroundCall()).thenReturn(fakeCall);
        when(mPhone1.getRingingCall()).thenReturn(fakeCall);
        when(mPhone1.getBackgroundCall()).thenReturn(fakeCall);
        NumberVerificationManager manager =
                new NumberVerificationManager(() -> new Phone[]{mPhone1, mPhone2});

        manager.requestVerification(SAMPLE_RANGE, mCallback, DEFAULT_VERIFICATION_TIMEOUT);
        verify(mCallback, never()).onVerificationFailed(anyInt());
        verifyDefaultRangeMatching(manager);
    }

    @Test
    public void testDoubleVerificationFailure() throws Exception {
        NumberVerificationManager manager =
                new NumberVerificationManager(() -> new Phone[]{mPhone1, mPhone2});
        manager.requestVerification(SAMPLE_RANGE, mCallback, DEFAULT_VERIFICATION_TIMEOUT);
        verifyDefaultRangeMatching(manager);
        assertFalse(manager.checkIncomingCall("this doesn't even matter"));
    }
}
