/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.CallLog;
import android.telecom.CallScreeningService;
import android.telecom.ParcelableCall;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telecom.ICallScreeningAdapter;
import com.android.internal.telecom.ICallScreeningService;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.ParcelableCallUtils;
import com.android.server.telecom.PhoneAccountRegistrar;
import com.android.server.telecom.TelecomServiceImpl;
import com.android.server.telecom.callfiltering.CallFilteringResult;
import com.android.server.telecom.callfiltering.CallScreeningServiceFilter;
import com.android.server.telecom.TelecomSystem;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class CallScreeningServiceFilterTest extends TelecomTestCase {
    @Mock Context mContext;
    @Mock CallsManager mCallsManager;
    @Mock PhoneAccountRegistrar mPhoneAccountRegistrar;
    @Mock ParcelableCallUtils.Converter mParcelableCallUtilsConverter;
    private TelecomSystem.SyncRoot mLock = new TelecomSystem.SyncRoot() { };

    @Mock Call mCall;
    @Mock CallScreeningServiceFilter.CallScreeningFilterResultCallback mCallback;

    @Mock PackageManager mPackageManager;
    @Mock IBinder mBinder;
    @Mock ICallScreeningService mCallScreeningService;
    @Mock CarrierConfigManager mCarrierConfigManager;
    @Mock private TelecomManager mTelecomManager;
    private TelecomServiceImpl.SettingsSecureAdapter mSettingsSecureAdapter =
        spy(new SettingsSecureAdapterFake());

    private static final String PKG_NAME = "com.android.services.telecom.tests";
    private static final String APP_NAME = "TeleTestApp";
    private static final String CLS_NAME = "CallScreeningService";
    private static final ComponentName COMPONENT_NAME = new ComponentName(PKG_NAME, CLS_NAME);
    private static final String CALL_ID = "u89prgt9ps78y5";
    private static final String DEFAULT_DIALER_PACKAGE = "com.android.dialer";
    private static final ComponentName CARRIER_DEFINED_CALL_SCREENING = new ComponentName(
        "com.android.carrier", "com.android.carrier.callscreeningserviceimpl");
    private static final String CARRIER_DEFINED_CALL_SCREENING_APP_NAME = "GMob";
    private static final ComponentName DEFAULT_DIALER_CALL_SCREENING = new ComponentName(
        "com.android.dialer", "com.android.dialer.callscreeningserviceimpl");
    private static final String DEFAULT_DIALER_APP_NAME = "Dialer";
    private static final ComponentName USER_CHOSEN_CALL_SCREENING = new ComponentName(
        "com.android.userchosen", "com.android.userchosen.callscreeningserviceimpl");
    private static final String USER_CHOSEN_CALL_SCREENING_APP_NAME = "UserChosen";
    private ResolveInfo mResolveInfo;

    private static final CallFilteringResult PASS_RESULT = new CallFilteringResult(
            true, // shouldAllowCall
            false, // shouldReject
            true, // shouldAddToCallLog
            true // shouldShowNotification
    );

    private static final CallFilteringResult PASS_RESULT_WITH_SILENCE = new CallFilteringResult(
            true, // shouldAllowCall
            false, // shouldReject
            true, // shouldSilence
            true, // shouldAddToCallLog
            true // shouldShowNotification
    );

    private CallScreeningServiceFilter mFilter;

    public static class SettingsSecureAdapterFake implements
        TelecomServiceImpl.SettingsSecureAdapter {
        @Override
        public void putStringForUser(ContentResolver resolver, String name, String value,
            int userHandle) {

        }

        @Override
        public String getStringForUser(ContentResolver resolver, String name, int userHandle) {
            return USER_CHOSEN_CALL_SCREENING.flattenToString();
        }
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        when(mCallsManager.getCurrentUserHandle()).thenReturn(UserHandle.CURRENT);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mCall.getId()).thenReturn(CALL_ID);
        doReturn(mCallScreeningService).when(mBinder).queryLocalInterface(anyString());

        mResolveInfo =  new ResolveInfo() {{
            serviceInfo = new ServiceInfo();
            serviceInfo.packageName = PKG_NAME;
            serviceInfo.name = CLS_NAME;
            serviceInfo.permission = Manifest.permission.BIND_SCREENING_SERVICE;
        }};

        mFilter = new CallScreeningServiceFilter(mContext, mCallsManager, mPhoneAccountRegistrar,
                mParcelableCallUtilsConverter, mLock, mSettingsSecureAdapter);

        when(mPackageManager.queryIntentServicesAsUser(nullable(Intent.class), anyInt(), anyInt()))
                .thenReturn(Collections.singletonList(mResolveInfo));
        when(mParcelableCallUtilsConverter.toParcelableCall(
                eq(mCall), anyBoolean(), eq(mPhoneAccountRegistrar))).thenReturn(null);
        when(mContext.bindServiceAsUser(nullable(Intent.class), nullable(ServiceConnection.class),
                anyInt(), eq(UserHandle.CURRENT))).thenReturn(true);
    }

    @SmallTest
    @Test
    public void testNoPackageName() {
        mFilter.startCallScreeningFilter(mCall, mCallback, null, null,
                CallScreeningServiceFilter.CALL_SCREENING_FILTER_TYPE_USER_SELECTED);
        verify(mCallback).onCallScreeningFilterComplete(eq(mCall), eq(PASS_RESULT), eq(null));
    }

    @SmallTest
    @Test
    public void testNoResolveEntries() {
        when(mPackageManager.queryIntentServicesAsUser(nullable(Intent.class), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());
        mFilter.startCallScreeningFilter(mCall, mCallback, PKG_NAME, APP_NAME,
                CallScreeningServiceFilter.CALL_SCREENING_FILTER_TYPE_USER_SELECTED);
        verify(mCallback).onCallScreeningFilterComplete(eq(mCall), eq(PASS_RESULT), eq(PKG_NAME));
    }

    @SmallTest
    @Test
    public void testBadResolveEntry() {
        mResolveInfo.serviceInfo = null;
        mFilter.startCallScreeningFilter(mCall, mCallback, PKG_NAME, APP_NAME,
                CallScreeningServiceFilter.CALL_SCREENING_FILTER_TYPE_USER_SELECTED);
        verify(mCallback).onCallScreeningFilterComplete(eq(mCall), eq(PASS_RESULT), eq(PKG_NAME));
    }

    @SmallTest
    @Test
    public void testPermissionlessFilterService() {
        mResolveInfo.serviceInfo.permission = null;
        mFilter.startCallScreeningFilter(mCall, mCallback, PKG_NAME, APP_NAME,
                CallScreeningServiceFilter.CALL_SCREENING_FILTER_TYPE_USER_SELECTED);
        verify(mCallback).onCallScreeningFilterComplete(eq(mCall), eq(PASS_RESULT), eq(PKG_NAME));
    }

    @SmallTest
    @Test
    public void testContextFailToBind() {
        when(mContext.bindServiceAsUser(nullable(Intent.class), nullable(ServiceConnection.class),
                anyInt(), eq(UserHandle.CURRENT))).thenReturn(false);
        mFilter.startCallScreeningFilter(mCall, mCallback, PKG_NAME, APP_NAME,
                CallScreeningServiceFilter.CALL_SCREENING_FILTER_TYPE_USER_SELECTED);
        verify(mCallback).onCallScreeningFilterComplete(eq(mCall), eq(PASS_RESULT), eq(PKG_NAME));
    }

    @SmallTest
    @Test
    public void testExceptionInScreeningService() throws Exception {
        doThrow(new RemoteException()).when(mCallScreeningService).screenCall(
                nullable(ICallScreeningAdapter.class), nullable(ParcelableCall.class));
        mFilter.startCallScreeningFilter(mCall, mCallback, PKG_NAME, APP_NAME,
                CallScreeningServiceFilter.CALL_SCREENING_FILTER_TYPE_USER_SELECTED);
        ServiceConnection serviceConnection = verifyBindingIntent();
        serviceConnection.onServiceConnected(COMPONENT_NAME, mBinder);
        verify(mCallback).onCallScreeningFilterComplete(eq(mCall), eq(PASS_RESULT), eq(PKG_NAME));
    }

    @SmallTest
    @Test
    public void testAllowCall() throws Exception {
        mFilter.startCallScreeningFilter(mCall, mCallback, PKG_NAME, APP_NAME,
                CallScreeningServiceFilter.CALL_SCREENING_FILTER_TYPE_USER_SELECTED);
        ServiceConnection serviceConnection = verifyBindingIntent();
        serviceConnection.onServiceConnected(COMPONENT_NAME, mBinder);
        ICallScreeningAdapter csAdapter = getCallScreeningAdapter();
        csAdapter.allowCall(CALL_ID);
        verify(mCallback).onCallScreeningFilterComplete(eq(mCall), eq(PASS_RESULT), eq(PKG_NAME));
    }

    @SmallTest
    @Test
    public void testSilenceCall() throws Exception {
        mFilter.startCallScreeningFilter(mCall, mCallback, PKG_NAME, APP_NAME,
                CallScreeningServiceFilter.CALL_SCREENING_FILTER_TYPE_USER_SELECTED);
        ServiceConnection serviceConnection = verifyBindingIntent();
        serviceConnection.onServiceConnected(COMPONENT_NAME, mBinder);
        ICallScreeningAdapter csAdapter = getCallScreeningAdapter();
        csAdapter.silenceCall(CALL_ID);
        verify(mCallback).onCallScreeningFilterComplete(eq(mCall), eq(PASS_RESULT_WITH_SILENCE),
                eq(PKG_NAME));
    }

    @SmallTest
    @Test
    public void testDisallowCallForCarrierDefined() throws Exception {
        mResolveInfo.serviceInfo.packageName = CARRIER_DEFINED_CALL_SCREENING.getPackageName();
        mResolveInfo.serviceInfo.name = CARRIER_DEFINED_CALL_SCREENING.getClassName();
        setCarrierDefinedCallScreeningApplication();
        when(TelecomManager.from(mContext)).thenReturn(mTelecomManager);
        when(mTelecomManager.getDefaultDialerPackage()).thenReturn(DEFAULT_DIALER_PACKAGE);

        mFilter.startCallScreeningFilter(mCall, mCallback,
                CARRIER_DEFINED_CALL_SCREENING.getPackageName(),
                CARRIER_DEFINED_CALL_SCREENING_APP_NAME,
                CallScreeningServiceFilter.CALL_SCREENING_FILTER_TYPE_CARRIER);
        ServiceConnection serviceConnection = verifyBindingIntent();
        serviceConnection.onServiceConnected(COMPONENT_NAME, mBinder);
        ICallScreeningAdapter csAdapter = getCallScreeningAdapter();
        csAdapter.disallowCall(CALL_ID,
                true, // shouldReject
                false, // shouldAddToCallLog
                true, // shouldShowNotification
                CARRIER_DEFINED_CALL_SCREENING
        );
        verify(mCallback).onCallScreeningFilterComplete(eq(mCall), eq(new CallFilteringResult(
                false, // shouldAllowCall
                true, // shouldReject
                false, // shouldAddToCallLog
                true, // shouldShowNotification
                CallLog.Calls.BLOCK_REASON_CALL_SCREENING_SERVICE, //callBlockReason
                CARRIER_DEFINED_CALL_SCREENING_APP_NAME, //callScreeningAppName
                CARRIER_DEFINED_CALL_SCREENING.flattenToString() //callScreeningComponentName
        )), eq(CARRIER_DEFINED_CALL_SCREENING.getPackageName()));
    }

    @SmallTest
    @Test
    public void testDisallowCallForDefaultDialer() throws Exception {
        mResolveInfo.serviceInfo.packageName = DEFAULT_DIALER_CALL_SCREENING.getPackageName();
        mResolveInfo.serviceInfo.name = DEFAULT_DIALER_CALL_SCREENING.getClassName();
        setCarrierDefinedCallScreeningApplication();
        when(TelecomManager.from(mContext)).thenReturn(mTelecomManager);
        when(mTelecomManager.getDefaultDialerPackage()).thenReturn(DEFAULT_DIALER_PACKAGE);

        mFilter.startCallScreeningFilter(mCall, mCallback,
                DEFAULT_DIALER_CALL_SCREENING.getPackageName(),
                DEFAULT_DIALER_APP_NAME,
                CallScreeningServiceFilter.CALL_SCREENING_FILTER_TYPE_DEFAULT_DIALER);
        ServiceConnection serviceConnection = verifyBindingIntent();
        serviceConnection.onServiceConnected(COMPONENT_NAME, mBinder);
        ICallScreeningAdapter csAdapter = getCallScreeningAdapter();
        csAdapter.disallowCall(CALL_ID,
            true, // shouldReject
            false, // shouldAddToCallLog
            true, // shouldShowNotification
            DEFAULT_DIALER_CALL_SCREENING
        );
        verify(mCallback).onCallScreeningFilterComplete(eq(mCall), eq(new CallFilteringResult(
            false, // shouldAllowCall
            true, // shouldReject
            true, // shouldAddToCallLog
            true, // shouldShowNotification
            CallLog.Calls.BLOCK_REASON_CALL_SCREENING_SERVICE, //callBlockReason
            DEFAULT_DIALER_APP_NAME, //callScreeningAppName
            DEFAULT_DIALER_CALL_SCREENING.flattenToString() //callScreeningComponentName
        )), eq(DEFAULT_DIALER_CALL_SCREENING.getPackageName()));
    }

    @SmallTest
    @Test
    public void testDisallowCallForUserChosen() throws Exception {
        mResolveInfo.serviceInfo.packageName = USER_CHOSEN_CALL_SCREENING.getPackageName();
        mResolveInfo.serviceInfo.name = USER_CHOSEN_CALL_SCREENING.getClassName();
        setCarrierDefinedCallScreeningApplication();
        when(TelecomManager.from(mContext)).thenReturn(mTelecomManager);
        when(mTelecomManager.getDefaultDialerPackage()).thenReturn(DEFAULT_DIALER_PACKAGE);

        mFilter.startCallScreeningFilter(mCall, mCallback,
                USER_CHOSEN_CALL_SCREENING.getPackageName(),
                USER_CHOSEN_CALL_SCREENING_APP_NAME,
                CallScreeningServiceFilter.CALL_SCREENING_FILTER_TYPE_USER_SELECTED);
        ServiceConnection serviceConnection = verifyBindingIntent();
        serviceConnection.onServiceConnected(COMPONENT_NAME, mBinder);
        ICallScreeningAdapter csAdapter = getCallScreeningAdapter();
        csAdapter.disallowCall(CALL_ID,
            true, // shouldReject
            false, // shouldAddToCallLog
            true, // shouldShowNotification
            USER_CHOSEN_CALL_SCREENING
        );
        verify(mCallback).onCallScreeningFilterComplete(eq(mCall), eq(new CallFilteringResult(
            false, // shouldAllowCall
            true, // shouldReject
            true, // shouldAddToCallLog
            true, // shouldShowNotification
            CallLog.Calls.BLOCK_REASON_CALL_SCREENING_SERVICE, //callBlockReason
            USER_CHOSEN_CALL_SCREENING_APP_NAME, //callScreeningAppName
            USER_CHOSEN_CALL_SCREENING.flattenToString() //callScreeningComponentName
        )), eq(USER_CHOSEN_CALL_SCREENING.getPackageName()));
    }

    private ServiceConnection verifyBindingIntent() {
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<ServiceConnection> serviceCaptor =
                ArgumentCaptor.forClass(ServiceConnection.class);
        verify(mContext).bindServiceAsUser(intentCaptor.capture(), serviceCaptor.capture(),
                eq(Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE),
                eq(UserHandle.CURRENT));

        Intent capturedIntent = intentCaptor.getValue();
        assertEquals(CallScreeningService.SERVICE_INTERFACE, capturedIntent.getAction());
        assertEquals(mResolveInfo.serviceInfo.packageName, capturedIntent.getPackage());
        assertEquals(new ComponentName(mResolveInfo.serviceInfo.packageName, mResolveInfo
                .serviceInfo.name), capturedIntent.getComponent());

        return serviceCaptor.getValue();
    }

    private ICallScreeningAdapter getCallScreeningAdapter() throws Exception {
        ArgumentCaptor<ICallScreeningAdapter> captor =
                ArgumentCaptor.forClass(ICallScreeningAdapter.class);
        verify(mCallScreeningService).screenCall(captor.capture(), nullable(ParcelableCall.class));
        return captor.getValue();
    }

    private void setCarrierDefinedCallScreeningApplication() {
        String carrierDefined = CARRIER_DEFINED_CALL_SCREENING.flattenToString();
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(CarrierConfigManager.KEY_CARRIER_CALL_SCREENING_APP_STRING,
            carrierDefined);
        when(mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE))
            .thenReturn(mCarrierConfigManager);
        when(mCarrierConfigManager.getConfig()).thenReturn(bundle);
    }
}
