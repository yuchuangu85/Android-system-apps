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

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.provider.CallLog;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.CallerInfo;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallScreeningServiceHelper;
import com.android.server.telecom.CallerInfoLookupHelper;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.ParcelableCallUtils;
import com.android.server.telecom.PhoneAccountRegistrar;
import com.android.server.telecom.RoleManagerAdapter;
import com.android.server.telecom.TelecomServiceImpl;
import com.android.server.telecom.TelecomSystem;
import com.android.server.telecom.callfiltering.CallFilterResultCallback;
import com.android.server.telecom.callfiltering.CallFilteringResult;
import com.android.server.telecom.callfiltering.CallScreeningServiceController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class CallScreeningServiceControllerTest extends TelecomTestCase {

    @Mock Context mContext;
    @Mock Call mCall;
    @Mock private CallFilterResultCallback mCallback;
    @Mock CallsManager mCallsManager;
    @Mock RoleManagerAdapter mRoleManagerAdapter;
    @Mock CarrierConfigManager mCarrierConfigManager;
    @Mock private TelecomManager mTelecomManager;
    @Mock PackageManager mPackageManager;
    @Mock ParcelableCallUtils.Converter mParcelableCallUtilsConverter;
    @Mock PhoneAccountRegistrar mPhoneAccountRegistrar;
    @Mock private CallerInfoLookupHelper mCallerInfoLookupHelper;

    CallScreeningServiceHelper.AppLabelProxy mAppLabelProxy =
            new CallScreeningServiceHelper.AppLabelProxy() {
        @Override
        public CharSequence getAppLabel(String packageName) {
            return APP_NAME;
        }
    };

    private ResolveInfo mResolveInfo;
    private TelecomServiceImpl.SettingsSecureAdapter mSettingsSecureAdapter =
            spy(new CallScreeningServiceFilterTest.SettingsSecureAdapterFake());
    private TelecomSystem.SyncRoot mLock = new TelecomSystem.SyncRoot() { };

    private static final String CALL_ID = "u89prgt9ps78y5";
    private static final Uri TEST_HANDLE = Uri.parse("tel:1235551234");
    private static final String DEFAULT_DIALER_PACKAGE = "com.android.dialer";
    private static final String PKG_NAME = "com.android.services.telecom.tests";
    private static final String CLS_NAME = "CallScreeningService";
    private static final String APP_NAME = "Screeny McScreenface";
    private static final ComponentName CARRIER_DEFINED_CALL_SCREENING = new ComponentName(
            "com.android.carrier", "com.android.carrier.callscreeningserviceimpl");
    private static final ComponentName DEFAULT_DIALER_CALL_SCREENING = new ComponentName(
            "com.android.dialer", "com.android.dialer.callscreeningserviceimpl");
    private static final ComponentName USER_CHOSEN_CALL_SCREENING = new ComponentName(
            "com.android.userchosen", "com.android.userchosen.callscreeningserviceimpl");

    private static final CallFilteringResult PASS_RESULT = new CallFilteringResult(
            true, // shouldAllowCall
            false, // shouldReject
            true, // shouldAddToCallLog
            true // shouldShowNotification
    );

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
        when(mRoleManagerAdapter.getCallCompanionApps()).thenReturn(Collections.emptyList());
        when(mRoleManagerAdapter.getDefaultCallScreeningApp()).thenReturn(null);
        when(mRoleManagerAdapter.getCarModeDialerApp()).thenReturn(null);
        when(mCallsManager.getRoleManagerAdapter()).thenReturn(mRoleManagerAdapter);
        when(mCallsManager.getCurrentUserHandle()).thenReturn(UserHandle.CURRENT);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mCall.getId()).thenReturn(CALL_ID);

        setCarrierDefinedCallScreeningApplication();
        when(TelecomManager.from(mContext)).thenReturn(mTelecomManager);
        when(mTelecomManager.getDefaultDialerPackage()).thenReturn(DEFAULT_DIALER_PACKAGE);

        mResolveInfo =  new ResolveInfo() {{
            serviceInfo = new ServiceInfo();
            serviceInfo.packageName = PKG_NAME;
            serviceInfo.name = CLS_NAME;
            serviceInfo.permission = Manifest.permission.BIND_SCREENING_SERVICE;
        }};

        when(mPackageManager.queryIntentServicesAsUser(nullable(Intent.class), anyInt(), anyInt()))
                .thenReturn(Collections.singletonList(mResolveInfo));
        when(mParcelableCallUtilsConverter.toParcelableCall(
                eq(mCall), anyBoolean(), eq(mPhoneAccountRegistrar))).thenReturn(null);
        when(mContext.bindServiceAsUser(nullable(Intent.class), nullable(ServiceConnection.class),
                anyInt(), eq(UserHandle.CURRENT))).thenReturn(true);
        when(mCall.getHandle()).thenReturn(TEST_HANDLE);
    }

    @SmallTest
    @Test
    public void testAllAllowCall() {
        when(mRoleManagerAdapter.getDefaultCallScreeningApp()).thenReturn(
                USER_CHOSEN_CALL_SCREENING.getPackageName());
        CallScreeningServiceController controller = new CallScreeningServiceController(mContext,
                mCallsManager, mPhoneAccountRegistrar,
                mParcelableCallUtilsConverter, mLock,
                mSettingsSecureAdapter, mCallerInfoLookupHelper, mAppLabelProxy);

        controller.startFilterLookup(mCall, mCallback);

        controller.onCallScreeningFilterComplete(mCall, PASS_RESULT,
                CARRIER_DEFINED_CALL_SCREENING.getPackageName());

        CallerInfoLookupHelper.OnQueryCompleteListener queryListener = verifyLookupStart();
        CallerInfo callerInfo = new CallerInfo();
        callerInfo.contactExists = false;
        queryListener.onCallerInfoQueryComplete(TEST_HANDLE, callerInfo);

        controller.onCallScreeningFilterComplete(mCall, PASS_RESULT,
                DEFAULT_DIALER_CALL_SCREENING.getPackageName());
        controller.onCallScreeningFilterComplete(mCall, PASS_RESULT, USER_CHOSEN_CALL_SCREENING
                .getPackageName());

        verify(mContext, times(3)).bindServiceAsUser(any(Intent.class), any(ServiceConnection
                        .class),
                eq(Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE),
                eq(UserHandle.CURRENT));

        verify(mCallback).onCallFilteringComplete(eq(mCall), eq(PASS_RESULT));
    }

    @SmallTest
    @Test
    public void testCarrierAllowCallAndContactExists() {
        CallScreeningServiceController controller = new CallScreeningServiceController(mContext,
                mCallsManager,
                mPhoneAccountRegistrar, mParcelableCallUtilsConverter, mLock,
                mSettingsSecureAdapter, mCallerInfoLookupHelper, mAppLabelProxy);

        controller.startFilterLookup(mCall, mCallback);

        controller.onCallScreeningFilterComplete(mCall, PASS_RESULT,
                CARRIER_DEFINED_CALL_SCREENING.getPackageName());

        CallerInfoLookupHelper.OnQueryCompleteListener queryListener = verifyLookupStart();
        CallerInfo callerInfo = new CallerInfo();
        callerInfo.contactExists = true;
        queryListener.onCallerInfoQueryComplete(TEST_HANDLE, callerInfo);

        verify(mContext, times(1)).bindServiceAsUser(any(Intent.class), any(ServiceConnection
                        .class),
                eq(Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE),
                eq(UserHandle.CURRENT));

        verify(mCallback).onCallFilteringComplete(eq(mCall), eq(PASS_RESULT));
    }

    @SmallTest
    @Test
    public void testCarrierCallScreeningRejectCall() {
        CallScreeningServiceController controller = new CallScreeningServiceController(mContext,
                mCallsManager,
                mPhoneAccountRegistrar, mParcelableCallUtilsConverter, mLock,
                mSettingsSecureAdapter, mCallerInfoLookupHelper, mAppLabelProxy);

        controller.startFilterLookup(mCall, mCallback);

        controller.onCallScreeningFilterComplete(mCall, new CallFilteringResult(
                false, // shouldAllowCall
                true, // shouldReject
                false, // shouldAddToCallLog
                true, // shouldShowNotification
                CallLog.Calls.BLOCK_REASON_CALL_SCREENING_SERVICE,
                APP_NAME,
                CARRIER_DEFINED_CALL_SCREENING.flattenToString()
        ), CARRIER_DEFINED_CALL_SCREENING.getPackageName());

        verify(mContext, times(1)).bindServiceAsUser(any(Intent.class),
                any(ServiceConnection.class),
                eq(Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE),
                eq(UserHandle.CURRENT));

        verify(mCallback).onCallFilteringComplete(eq(mCall), eq(new CallFilteringResult(
                false, // shouldAllowCall
                true, // shouldReject
                false, // shouldAddToCallLog
                true, // shouldShowNotification
                CallLog.Calls.BLOCK_REASON_CALL_SCREENING_SERVICE, //callBlockReason
                APP_NAME, //callScreeningAppName
                CARRIER_DEFINED_CALL_SCREENING.flattenToString() //callScreeningComponentName
        )));
    }

    @SmallTest
    @Test
    public void testDefaultDialerRejectCall() {
        when(mRoleManagerAdapter.getDefaultCallScreeningApp()).thenReturn(
                USER_CHOSEN_CALL_SCREENING.getPackageName());
        CallScreeningServiceController controller = new CallScreeningServiceController(mContext,
                mCallsManager,
                mPhoneAccountRegistrar, mParcelableCallUtilsConverter, mLock,
                mSettingsSecureAdapter, mCallerInfoLookupHelper, mAppLabelProxy);

        controller.startFilterLookup(mCall, mCallback);

        controller.onCallScreeningFilterComplete(mCall, PASS_RESULT,
                CARRIER_DEFINED_CALL_SCREENING.getPackageName());

        CallerInfoLookupHelper.OnQueryCompleteListener queryListener = verifyLookupStart();
        CallerInfo callerInfo = new CallerInfo();
        callerInfo.contactExists = false;
        queryListener.onCallerInfoQueryComplete(TEST_HANDLE, callerInfo);

        controller.onCallScreeningFilterComplete(mCall, new CallFilteringResult(
                false, // shouldAllowCall
                true, // shouldReject
                false, // shouldAddToCallLog
                true, // shouldShowNotification
                CallLog.Calls.BLOCK_REASON_CALL_SCREENING_SERVICE,
                APP_NAME,
                DEFAULT_DIALER_CALL_SCREENING.flattenToString()
        ), DEFAULT_DIALER_CALL_SCREENING.getPackageName());

        verify(mContext, times(3)).bindServiceAsUser(any(Intent.class),
                any(ServiceConnection.class),
                eq(Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE),
                eq(UserHandle.CURRENT));

        verify(mCallback).onCallFilteringComplete(eq(mCall), eq(new CallFilteringResult(
                false, // shouldAllowCall
                true, // shouldReject
                true, // shouldAddToCallLog (we don't allow services to skip call log)
                true, // shouldShowNotification
                CallLog.Calls.BLOCK_REASON_CALL_SCREENING_SERVICE, //callBlockReason
                APP_NAME, //callScreeningAppName
                DEFAULT_DIALER_CALL_SCREENING.flattenToString() //callScreeningComponentName
        )));
    }

    @SmallTest
    @Test
    public void testUserChosenRejectCall() {
        when(mRoleManagerAdapter.getDefaultCallScreeningApp()).thenReturn(
                USER_CHOSEN_CALL_SCREENING.getPackageName());
        CallScreeningServiceController controller = new CallScreeningServiceController(mContext,
                mCallsManager,
                mPhoneAccountRegistrar, mParcelableCallUtilsConverter, mLock,
                mSettingsSecureAdapter, mCallerInfoLookupHelper, mAppLabelProxy);

        controller.startFilterLookup(mCall, mCallback);

        controller.onCallScreeningFilterComplete(mCall, PASS_RESULT,
                CARRIER_DEFINED_CALL_SCREENING.getPackageName());

        CallerInfoLookupHelper.OnQueryCompleteListener queryListener = verifyLookupStart();
        CallerInfo callerInfo = new CallerInfo();
        callerInfo.contactExists = false;
        queryListener.onCallerInfoQueryComplete(TEST_HANDLE, callerInfo);

        controller.onCallScreeningFilterComplete(mCall, PASS_RESULT,
                DEFAULT_DIALER_CALL_SCREENING.getPackageName());
        controller.onCallScreeningFilterComplete(mCall, new CallFilteringResult(
                false, // shouldAllowCall
                true, // shouldReject
                false, // shouldAddToCallLog
                true, // shouldShowNotification
                CallLog.Calls.BLOCK_REASON_CALL_SCREENING_SERVICE,
                APP_NAME,
                USER_CHOSEN_CALL_SCREENING.flattenToString()
        ), USER_CHOSEN_CALL_SCREENING.getPackageName());

        verify(mContext, times(3)).bindServiceAsUser(any(Intent.class),
                any(ServiceConnection.class),
                eq(Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE),
                eq(UserHandle.CURRENT));

        verify(mCallback).onCallFilteringComplete(eq(mCall), eq(new CallFilteringResult(
                false, // shouldAllowCall
                true, // shouldReject
                true, // shouldAddToCallLog (we don't allow services to skip call log)
                true, // shouldShowNotification
                CallLog.Calls.BLOCK_REASON_CALL_SCREENING_SERVICE, //callBlockReason
                APP_NAME, //callScreeningAppName
                USER_CHOSEN_CALL_SCREENING.flattenToString() //callScreeningComponentName
        )));
    }

    private CallerInfoLookupHelper.OnQueryCompleteListener verifyLookupStart() {
        return verifyLookupStart(TEST_HANDLE);
    }

    private CallerInfoLookupHelper.OnQueryCompleteListener verifyLookupStart(Uri handle) {

        ArgumentCaptor<CallerInfoLookupHelper.OnQueryCompleteListener> captor =
                ArgumentCaptor.forClass(CallerInfoLookupHelper.OnQueryCompleteListener.class);
        verify(mCallerInfoLookupHelper).startLookup(eq(handle), captor.capture());
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
