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
 * limitations under the License
 */

package com.android.server.telecom.tests;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.IBinder;
import android.os.UserHandle;
import android.telecom.GatewayInfo;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;
import com.android.internal.telecom.ICallRedirectionService;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.PhoneAccountRegistrar;
import com.android.server.telecom.SystemStateHelper;
import com.android.server.telecom.TelecomSystem;
import com.android.server.telecom.Timeouts;

import com.android.server.telecom.callredirection.CallRedirectionProcessor;
import com.android.server.telecom.callredirection.CallRedirectionProcessorHelper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;

import org.junit.Before;

import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class CallRedirectionProcessorTest extends TelecomTestCase {
    @Mock private Context mContext;
    @Mock private CallsManager mCallsManager;
    @Mock private PhoneAccountRegistrar mPhoneAccountRegistrar;
    @Mock private PhoneAccountHandle mPhoneAccountHandle;
    private TelecomSystem.SyncRoot mLock = new TelecomSystem.SyncRoot() { };

    @Mock private Call mCall;

    @Mock private PackageManager mPackageManager;
    @Mock private TelephonyManager mTelephonyManager;
    @Mock private IBinder mBinder;
    @Mock private ICallRedirectionService mCallRedirectionService;

    @Mock private SystemStateHelper mSystemStateHelper;
    @Mock private CallRedirectionProcessorHelper mCallRedirectionProcessorHelper;

    @Mock private Uri mHandle;
    @Mock private GatewayInfo mGatewayInfo;
    @Mock private UserHandle mUserHandle;
    @Mock private ContentResolver mContentResolver;

    @Mock private Timeouts.Adapter mTimeoutsAdapter;

    private static final String USER_DEFINED_PKG_NAME = "user_defined_pkg";
    private static final String USER_DEFINED_CLS_NAME = "user_defined_cls";
    private static final String CARRIER_PKG_NAME = "carrier_pkg";
    private static final String CARRIER_CLS_NAME = "carrier_cls";

    private static final long HANDLER_TIMEOUT_DELAY = 5000;
    private static final long USER_DEFINED_SHORT_TIMEOUT_MS = 1200;
    private static final long CARRIER_SHORT_TIMEOUT_MS = 400;
    private static final long CODE_EXECUTION_DELAY = 500;

    // TODO integerate with a test user-defined service
    private static final ComponentName USER_DEFINED_SERVICE_TEST_COMPONENT_NAME =
            new ComponentName(USER_DEFINED_PKG_NAME, USER_DEFINED_CLS_NAME);
    // TODO integerate with a test carrier service
    private static final ComponentName CARRIER_SERVICE_TEST_COMPONENT_NAME =
            new ComponentName(CARRIER_PKG_NAME, CARRIER_CLS_NAME);

    private static final boolean SPEAKER_PHONE_ON = true;
    private static final int VIDEO_STATE = 0;

    private CallRedirectionProcessor mProcessor;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        when(mCall.getTargetPhoneAccount()).thenReturn(mPhoneAccountHandle);
        when(mCallsManager.getCurrentUserHandle()).thenReturn(UserHandle.CURRENT);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mContext.getContentResolver()).thenReturn(mContentResolver);
        doReturn(mCallRedirectionService).when(mBinder).queryLocalInterface(anyString());
        when(mCallsManager.getSystemStateHelper()).thenReturn(mSystemStateHelper);
        when(mCallsManager.getTimeoutsAdapter()).thenReturn(mTimeoutsAdapter);
        when(mTimeoutsAdapter.getUserDefinedCallRedirectionTimeoutMillis(mContentResolver))
                .thenReturn(USER_DEFINED_SHORT_TIMEOUT_MS);
        when(mTimeoutsAdapter.getCarrierCallRedirectionTimeoutMillis(mContentResolver))
                .thenReturn(CARRIER_SHORT_TIMEOUT_MS);
        when(mCallsManager.getLock()).thenReturn(mLock);
        when(mCallsManager.getCurrentUserHandle()).thenReturn(mUserHandle);
        when(mContext.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(mTelephonyManager);
        when(mTelephonyManager.getNetworkCountryIso()).thenReturn("");
        when(mContext.bindServiceAsUser(nullable(Intent.class), nullable(ServiceConnection.class),
                anyInt(), eq(UserHandle.CURRENT))).thenReturn(true);
    }

    private void setIsInCarMode(boolean isInCarMode) {
        when(mSystemStateHelper.isCarMode()).thenReturn(isInCarMode);
    }

    private void enableUserDefinedCallRedirectionService() {
        when(mCallRedirectionProcessorHelper.getUserDefinedCallRedirectionService()).thenReturn(
                USER_DEFINED_SERVICE_TEST_COMPONENT_NAME);
    }

    private void enableCarrierCallRedirectionService() {
        when(mCallRedirectionProcessorHelper.getCarrierCallRedirectionService(
                any(PhoneAccountHandle.class))).thenReturn(CARRIER_SERVICE_TEST_COMPONENT_NAME);
    }

    private void disableUserDefinedCallRedirectionService() {
        when(mCallRedirectionProcessorHelper.getUserDefinedCallRedirectionService()).thenReturn(
                null);
    }

    private void disableCarrierCallRedirectionService() {
        when(mCallRedirectionProcessorHelper.getCarrierCallRedirectionService(any())).thenReturn(
                null);
    }

    private void startProcessWithNoGateWayInfo() {
        mProcessor = new CallRedirectionProcessor(mContext, mCallsManager, mCall, mHandle,
                mPhoneAccountRegistrar, null, SPEAKER_PHONE_ON, VIDEO_STATE);
        mProcessor.setCallRedirectionServiceHelper(mCallRedirectionProcessorHelper);
    }

    private void startProcessWithGateWayInfo() {
        mProcessor = new CallRedirectionProcessor(mContext, mCallsManager, mCall, mHandle,
                mPhoneAccountRegistrar, mGatewayInfo, SPEAKER_PHONE_ON, VIDEO_STATE);
        mProcessor.setCallRedirectionServiceHelper(mCallRedirectionProcessorHelper);
    }

    @Test
    public void testNoUserDefinedServiceNoCarrierSerivce() {
        startProcessWithNoGateWayInfo();
        disableUserDefinedCallRedirectionService();
        disableCarrierCallRedirectionService();
        mProcessor.performCallRedirection();
        verify(mContext, times(0)).bindServiceAsUser(any(Intent.class),
                any(ServiceConnection.class), anyInt(), any(UserHandle.class));
        verify(mCallsManager, times(1)).onCallRedirectionComplete(eq(mCall), eq(mHandle),
                eq(mPhoneAccountHandle), eq(null), eq(SPEAKER_PHONE_ON), eq(VIDEO_STATE),
                eq(false), eq(CallRedirectionProcessor.UI_TYPE_NO_ACTION));
    }

    @Test
    public void testCarrierServiceTimeoutNoUserDefinedService() throws Exception {
        startProcessWithNoGateWayInfo();
        // To make sure tests are not flaky, clean all the previous handler messages
        waitForHandlerAction(mProcessor.getHandler(), HANDLER_TIMEOUT_DELAY);
        disableUserDefinedCallRedirectionService();
        enableCarrierCallRedirectionService();
        mProcessor.performCallRedirection();
        verify(mContext, times(1)).bindServiceAsUser(any(Intent.class),
                any(ServiceConnection.class), anyInt(), any(UserHandle.class));
        verify(mCallsManager, times(0)).onCallRedirectionComplete(eq(mCall), any(),
                eq(mPhoneAccountHandle), eq(null), eq(SPEAKER_PHONE_ON), eq(VIDEO_STATE),
                eq(false), eq(CallRedirectionProcessor.UI_TYPE_NO_ACTION));
        waitForHandlerActionDelayed(mProcessor.getHandler(), HANDLER_TIMEOUT_DELAY,
                CARRIER_SHORT_TIMEOUT_MS + CODE_EXECUTION_DELAY);
        verify(mCallsManager, times(1)).onCallRedirectionComplete(eq(mCall), any(),
                eq(mPhoneAccountHandle), eq(null), eq(SPEAKER_PHONE_ON), eq(VIDEO_STATE),
                eq(false), eq(CallRedirectionProcessor.UI_TYPE_NO_ACTION));
    }

    @Test
    public void testUserDefinedServiceTimeoutNoCarrierService() throws Exception {
        startProcessWithNoGateWayInfo();
        // To make sure tests are not flaky, clean all the previous handler messages
        waitForHandlerAction(mProcessor.getHandler(), HANDLER_TIMEOUT_DELAY);
        enableUserDefinedCallRedirectionService();
        disableCarrierCallRedirectionService();
        mProcessor.performCallRedirection();
        verify(mContext, times(1)).bindServiceAsUser(any(Intent.class),
                any(ServiceConnection.class), anyInt(), any(UserHandle.class));
        verify(mCallsManager, times(0)).onCallRedirectionComplete(eq(mCall), any(),
                eq(mPhoneAccountHandle), eq(null), eq(SPEAKER_PHONE_ON), eq(VIDEO_STATE),
                eq(false), eq(CallRedirectionProcessor.UI_TYPE_USER_DEFINED_TIMEOUT));

        // Test it is waiting for a User-defined timeout, not a Carrier timeout
        Thread.sleep(CARRIER_SHORT_TIMEOUT_MS + CODE_EXECUTION_DELAY);
        verify(mCallsManager, times(0)).onCallRedirectionComplete(eq(mCall), any(),
                eq(mPhoneAccountHandle), eq(null), eq(SPEAKER_PHONE_ON), eq(VIDEO_STATE),
                eq(false), eq(CallRedirectionProcessor.UI_TYPE_USER_DEFINED_TIMEOUT));

        // Wait for the rest of user-defined timeout time.
        waitForHandlerActionDelayed(mProcessor.getHandler(), HANDLER_TIMEOUT_DELAY,
                USER_DEFINED_SHORT_TIMEOUT_MS - CARRIER_SHORT_TIMEOUT_MS + CODE_EXECUTION_DELAY);
        verify(mCallsManager, times(1)).onCallRedirectionComplete(eq(mCall), any(),
                eq(mPhoneAccountHandle), eq(null), eq(SPEAKER_PHONE_ON), eq(VIDEO_STATE),
                eq(true), eq(CallRedirectionProcessor.UI_TYPE_USER_DEFINED_TIMEOUT));
    }

    @Test
    public void testUserDefinedServiceTimeoutAndCarrierServiceTimeout() throws Exception {
        startProcessWithNoGateWayInfo();
        // To make sure tests are not flaky, clean all the previous handler messages
        waitForHandlerAction(mProcessor.getHandler(), HANDLER_TIMEOUT_DELAY);
        enableUserDefinedCallRedirectionService();
        enableCarrierCallRedirectionService();
        mProcessor.performCallRedirection();

        verify(mContext, times(1)).bindServiceAsUser(any(Intent.class),
                any(ServiceConnection.class), anyInt(), any(UserHandle.class));
        verify(mCallsManager, times(0)).onCallRedirectionComplete(eq(mCall), any(),
                eq(mPhoneAccountHandle), eq(null), eq(SPEAKER_PHONE_ON), eq(VIDEO_STATE),
                eq(false), eq(CallRedirectionProcessor.UI_TYPE_USER_DEFINED_TIMEOUT));

        // Test it is waiting for a User-defined timeout, not a Carrier timeout
        Thread.sleep(CARRIER_SHORT_TIMEOUT_MS + CODE_EXECUTION_DELAY);
        verify(mContext, times(1)).bindServiceAsUser(any(Intent.class),
                any(ServiceConnection.class), anyInt(), any(UserHandle.class));
        verify(mCallsManager, times(0)).onCallRedirectionComplete(eq(mCall), any(),
                eq(mPhoneAccountHandle), eq(null), eq(SPEAKER_PHONE_ON), eq(VIDEO_STATE),
                eq(false), eq(CallRedirectionProcessor.UI_TYPE_USER_DEFINED_TIMEOUT));

        // Wait for the rest of user-defined timeout time.
        waitForHandlerActionDelayed(mProcessor.getHandler(), HANDLER_TIMEOUT_DELAY,
                USER_DEFINED_SHORT_TIMEOUT_MS - CARRIER_SHORT_TIMEOUT_MS + CODE_EXECUTION_DELAY);
        verify(mCallsManager, times(1)).onCallRedirectionComplete(eq(mCall), any(),
                eq(mPhoneAccountHandle), eq(null), eq(SPEAKER_PHONE_ON), eq(VIDEO_STATE),
                eq(true), eq(CallRedirectionProcessor.UI_TYPE_USER_DEFINED_TIMEOUT));

        // Wait for another carrier timeout time, but should not expect any carrier service request
        // is triggered.
        Thread.sleep(CARRIER_SHORT_TIMEOUT_MS + CODE_EXECUTION_DELAY);
        verify(mContext, times(1)).bindServiceAsUser(any(Intent.class),
                any(ServiceConnection.class), anyInt(), any(UserHandle.class));
        verify(mCallsManager, times(1)).onCallRedirectionComplete(eq(mCall), any(),
                eq(mPhoneAccountHandle), eq(null), eq(SPEAKER_PHONE_ON), eq(VIDEO_STATE),
                eq(true), eq(CallRedirectionProcessor.UI_TYPE_USER_DEFINED_TIMEOUT));
    }

    @Test
    public void testProcessGatewayCall() {
        startProcessWithGateWayInfo();
        enableUserDefinedCallRedirectionService();
        enableCarrierCallRedirectionService();
        mProcessor.performCallRedirection();
        verify(mContext, times(1)).bindServiceAsUser(any(Intent.class),
                any(ServiceConnection.class), anyInt(), any(UserHandle.class));
        verify(mCallsManager, times(0)).onCallRedirectionComplete(eq(mCall), any(),
                eq(mPhoneAccountHandle), eq(null), eq(SPEAKER_PHONE_ON), eq(VIDEO_STATE),
                eq(false), eq(CallRedirectionProcessor.UI_TYPE_NO_ACTION));
        waitForHandlerActionDelayed(mProcessor.getHandler(), HANDLER_TIMEOUT_DELAY,
                CARRIER_SHORT_TIMEOUT_MS + CODE_EXECUTION_DELAY);
        verify(mCallsManager, times(1)).onCallRedirectionComplete(eq(mCall), eq(mHandle),
                eq(mPhoneAccountHandle), eq(mGatewayInfo), eq(SPEAKER_PHONE_ON), eq(VIDEO_STATE),
                eq(false), eq(CallRedirectionProcessor.UI_TYPE_NO_ACTION));
    }
}
