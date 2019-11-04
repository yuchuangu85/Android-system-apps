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

package com.android.car.dialer.telecom;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.CallAudioState;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import com.android.car.dialer.CarDialerRobolectricTestRunner;
import com.android.car.dialer.R;
import com.android.car.dialer.TestDialerApplication;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowContextImpl;
import org.robolectric.shadows.ShadowToast;

import java.util.List;

@RunWith(CarDialerRobolectricTestRunner.class)
public class UiCallManagerTest {

    private static final String TEL_SCHEME = "tel";

    private Context mContext;
    private UiCallManager mUiCallManager;
    @Mock
    private TelecomManager mMockTelecomManager;
    @Mock
    private InCallServiceImpl mMockInCallService;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        mContext = RuntimeEnvironment.application;

        ShadowContextImpl shadowContext = Shadow.extract(((Application) mContext).getBaseContext());
        shadowContext.setSystemService(Context.TELECOM_SERVICE, mMockTelecomManager);
    }

    private void initUiCallManager() {
        ((TestDialerApplication) mContext).setupInCallServiceImpl(mMockInCallService);
        ((TestDialerApplication) mContext).initUiCallManager();

        mUiCallManager = UiCallManager.get();
    }

    private void initUiCallManager_InCallServiceIsNull() {
        ((TestDialerApplication) mContext).setupInCallServiceImpl(null);
        ((TestDialerApplication) mContext).initUiCallManager();

        mUiCallManager = UiCallManager.get();
    }

    @Test
    public void testInit_initTwice_ThrowException() {
        initUiCallManager();

        assertNotNull(mUiCallManager);

        try {
            UiCallManager.init(mContext);
            fail();
        } catch (IllegalStateException e) {
            // This is expected.
        }
    }

    @Test
    public void testPlaceCall() {
        initUiCallManager();

        String[] phoneNumbers = {
                "6505551234", // US Number
                "511", // Special number
                "911", // Emergency number
                "122", // Emergency number
                "#77" // Emergency number
        };

        for (int i = 0; i < phoneNumbers.length; i++) {
            checkPlaceCall(phoneNumbers[i], i + 1);
        }
    }

    private void checkPlaceCall(String phoneNumber, int timesCalled) {
        ArgumentCaptor<Uri> uriCaptor = ArgumentCaptor.forClass(Uri.class);

        assertThat(mUiCallManager.placeCall(phoneNumber)).isTrue();
        verify(mMockTelecomManager, times(timesCalled)).placeCall(uriCaptor.capture(),
                (Bundle) isNull());
        assertThat(uriCaptor.getValue().getScheme()).isEqualTo(TEL_SCHEME);
        assertThat(uriCaptor.getValue().getSchemeSpecificPart()).isEqualTo(phoneNumber);
        assertThat(uriCaptor.getValue().getFragment()).isNull();
    }

    @Test
    public void testPlaceCall_invalidNumber() {
        initUiCallManager();
        String[] phoneNumbers = {
                "xxxxx",
                "51f"
        };

        for (String phoneNumber : phoneNumbers) {
            checkPlaceCallForInvalidNumber(phoneNumber);
        }
    }

    private void checkPlaceCallForInvalidNumber(String phoneNumber) {
        ArgumentCaptor<Uri> uriCaptor = ArgumentCaptor.forClass(Uri.class);

        assertThat(mUiCallManager.placeCall(phoneNumber)).isFalse();
        verify(mMockTelecomManager, never()).placeCall(uriCaptor.capture(), isNull());

        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo(
                mContext.getString(R.string.error_invalid_phone_number));
    }

    @Test
    public void testGetMuted_isMuted() {
        initUiCallManager();

        CallAudioState callAudioState = new CallAudioState(true,
                CallAudioState.ROUTE_BLUETOOTH, CallAudioState.ROUTE_ALL);
        when(mMockInCallService.getCallAudioState()).thenReturn(callAudioState);

        assertThat(mUiCallManager.getMuted()).isTrue();
    }

    @Test
    public void testGetMuted_audioRouteIsNull() {
        initUiCallManager();

        when(mMockInCallService.getCallAudioState()).thenReturn(null);

        assertThat(mUiCallManager.getMuted()).isFalse();
    }

    @Test
    public void testGetMuted_InCallServiceIsNull() {
        initUiCallManager_InCallServiceIsNull();

        assertThat(mUiCallManager.getMuted()).isFalse();
    }

    @Test
    public void testSetMuted() {
        initUiCallManager();

        mUiCallManager.setMuted(true);

        ArgumentCaptor<Boolean> captor = ArgumentCaptor.forClass(Boolean.class);
        verify(mMockInCallService).setMuted(captor.capture());
        assertThat(captor.getValue()).isTrue();
    }

    @Test
    public void testGetSupportedAudioRouteMask() {
        initUiCallManager();

        CallAudioState callAudioState = new CallAudioState(
                true, CallAudioState.ROUTE_BLUETOOTH, CallAudioState.ROUTE_ALL);
        when(mMockInCallService.getCallAudioState()).thenReturn(callAudioState);

        assertThat(mUiCallManager.getSupportedAudioRouteMask()).isEqualTo(CallAudioState.ROUTE_ALL);
    }

    @Test
    public void testGetSupportedAudioRouteMask_InCallServiceIsNull() {
        initUiCallManager_InCallServiceIsNull();

        assertThat(mUiCallManager.getSupportedAudioRouteMask()).isEqualTo(0);
    }

    @Test
    public void testGetSupportedAudioRoute_isBluetoothCall() {
        initUiCallManager();

        PhoneAccountHandle mockPhoneAccountHandle = mock(PhoneAccountHandle.class);
        ComponentName mockComponentName = mock(ComponentName.class);
        when(mockComponentName.getClassName()).thenReturn(
                UiCallManager.HFP_CLIENT_CONNECTION_SERVICE_CLASS_NAME);
        when(mockPhoneAccountHandle.getComponentName()).thenReturn(mockComponentName);
        when(mMockTelecomManager.getUserSelectedOutgoingPhoneAccount())
                .thenReturn(mockPhoneAccountHandle);

        assertThat(mUiCallManager.isBluetoothCall()).isTrue();
        List<Integer> supportedAudioRoute = mUiCallManager.getSupportedAudioRoute();
        assertThat(supportedAudioRoute.get(0)).isEqualTo(CallAudioState.ROUTE_BLUETOOTH);
        assertThat(supportedAudioRoute.get(1)).isEqualTo(CallAudioState.ROUTE_EARPIECE);
    }

    @Test
    public void testGetSupportedAudioRoute_supportedAudioRouteMaskIs0() {
        initUiCallManager();

        // SupportedAudioRouteMask is 0.
        assertThat(mUiCallManager.isBluetoothCall()).isFalse();
        assertThat(mUiCallManager.getSupportedAudioRoute().size()).isEqualTo(0);
    }

    @Test
    public void testGetSupportedAudioRoute_supportedAudioRouteMaskIsRouteAll() {
        initUiCallManager();

        // SupportedAudioRouteMask is CallAudioState.ROUTE_ALL.
        CallAudioState callAudioState = new CallAudioState(
                true, CallAudioState.ROUTE_BLUETOOTH, CallAudioState.ROUTE_ALL);
        when(mMockInCallService.getCallAudioState()).thenReturn(callAudioState);

        assertThat(mUiCallManager.isBluetoothCall()).isFalse();
        assertThat(mUiCallManager.getSupportedAudioRoute().size()).isEqualTo(1);
        assertThat(mUiCallManager.getSupportedAudioRoute().get(0))
                .isEqualTo(CallAudioState.ROUTE_EARPIECE);
    }

    @Test
    public void testGetSupportedAudioRoute_supportedAudioRouteMaskIsRouteSpeaker() {
        initUiCallManager();

        // SupportedAudioRouteMask is CallAudioState.ROUTE_SPEAKER.
        CallAudioState callAudioState = new CallAudioState(
                true, CallAudioState.ROUTE_BLUETOOTH, CallAudioState.ROUTE_SPEAKER);
        when(mMockInCallService.getCallAudioState()).thenReturn(callAudioState);

        assertThat(mUiCallManager.isBluetoothCall()).isFalse();
        assertThat(mUiCallManager.getSupportedAudioRoute().size()).isEqualTo(1);
        assertThat(mUiCallManager.getSupportedAudioRoute().get(0))
                .isEqualTo(CallAudioState.ROUTE_SPEAKER);
    }

    @After
    public void tearDown() {
        mUiCallManager.tearDown();
    }
}
