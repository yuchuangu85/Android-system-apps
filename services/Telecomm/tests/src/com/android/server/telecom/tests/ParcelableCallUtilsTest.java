package com.android.server.telecom.tests;

import static com.android.server.telecom.TelecomSystem.*;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.telecom.Connection;
import android.telecom.GatewayInfo;
import android.telecom.ParcelableCall;
import android.telecom.PhoneAccountHandle;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.server.telecom.Call;
import com.android.server.telecom.CallerInfoLookupHelper;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.ClockProxy;
import com.android.server.telecom.ConnectionServiceRepository;
import com.android.server.telecom.ParcelableCallUtils;
import com.android.server.telecom.PhoneAccountRegistrar;
import com.android.server.telecom.PhoneNumberUtilsAdapter;
import com.android.server.telecom.TelecomSystem;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public class ParcelableCallUtilsTest extends TelecomTestCase {

    private SyncRoot mLock = new SyncRoot() {};
    @Mock private ClockProxy mClockProxy;
    @Mock private CallsManager mCallsManager;
    @Mock private CallerInfoLookupHelper mCallerInfoLookupHelper;
    @Mock private PhoneNumberUtilsAdapter mPhoneNumberUtilsAdapter;
    @Mock private PhoneAccountRegistrar mPhoneAccountRegistrar;
    private Call mCall;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        when(mClockProxy.currentTimeMillis()).thenReturn(System.currentTimeMillis());
        when(mClockProxy.elapsedRealtime()).thenReturn(SystemClock.elapsedRealtime());
        when(mCallsManager.getCallerInfoLookupHelper()).thenReturn(mCallerInfoLookupHelper);
        when(mCallsManager.getPhoneAccountRegistrar()).thenReturn(mPhoneAccountRegistrar);
        when(mPhoneAccountRegistrar.getPhoneAccountUnchecked(any())).thenReturn(null);
        when(mPhoneNumberUtilsAdapter.isLocalEmergencyNumber(any(), any())).thenReturn(false);
        mCall = new Call("1",
                null /* context */,
                mCallsManager,
                mLock,
                null /* ConnectionServiceRepository */,
                mPhoneNumberUtilsAdapter,
                Uri.fromParts("tel", "6505551212", null),
                null /* GatewayInfo */,
                null /* connectionMgr */,
                new PhoneAccountHandle(
                        ComponentName.unflattenFromString("com.test/Class"), "test"),
                Call.CALL_DIRECTION_INCOMING,
                false /* shouldAttachToExistingConnection */,
                false /* isConference */,
                mClockProxy /* ClockProxy */);
    }

    @SmallTest
    @Test
    public void testParcelForNonSystemDialer() {
        mCall.putExtras(Call.SOURCE_CONNECTION_SERVICE, getSomeExtras());
        ParcelableCall call = ParcelableCallUtils.toParcelableCall(mCall,
                false /* includevideoProvider */,
                null /* phoneAccountRegistrar */,
                false /* supportsExternalCalls */,
                false /* includeRttCall */,
                false /* isForSystemDialer */);

        Bundle parceledExtras = call.getExtras();
        assertFalse(parceledExtras.containsKey(Connection.EXTRA_SIP_INVITE));
        assertFalse(parceledExtras.containsKey("SomeExtra"));
        assertTrue(parceledExtras.containsKey(Connection.EXTRA_CALL_SUBJECT));
    }

    @SmallTest
    @Test
    public void testParcelForSystemDialer() {
        mCall.putExtras(Call.SOURCE_CONNECTION_SERVICE, getSomeExtras());
        ParcelableCall call = ParcelableCallUtils.toParcelableCall(mCall,
                false /* includevideoProvider */,
                null /* phoneAccountRegistrar */,
                false /* supportsExternalCalls */,
                false /* includeRttCall */,
                true /* isForSystemDialer */);

        Bundle parceledExtras = call.getExtras();
        assertTrue(parceledExtras.containsKey(Connection.EXTRA_SIP_INVITE));
        assertTrue(parceledExtras.containsKey("SomeExtra"));
        assertTrue(parceledExtras.containsKey(Connection.EXTRA_CALL_SUBJECT));
    }

    @SmallTest
    @Test
    public void testParcelForSystemCallScreening() {
        mCall.putExtras(Call.SOURCE_CONNECTION_SERVICE, getSomeExtras());
        ParcelableCall call = ParcelableCallUtils.toParcelableCallForScreening(mCall,
                true /* isPartOfSystemDialer */);

        Bundle parceledExtras = call.getExtras();
        assertTrue(parceledExtras.containsKey(Connection.EXTRA_SIP_INVITE));
        assertFalse(parceledExtras.containsKey("SomeExtra"));
        assertFalse(parceledExtras.containsKey(Connection.EXTRA_CALL_SUBJECT));
    }

    @SmallTest
    @Test
    public void testParcelForSystemNonSystemCallScreening() {
        mCall.putExtras(Call.SOURCE_CONNECTION_SERVICE, getSomeExtras());
        ParcelableCall call = ParcelableCallUtils.toParcelableCallForScreening(mCall,
                false /* isPartOfSystemDialer */);

        Bundle parceledExtras = call.getExtras();
        assertFalse(parceledExtras.containsKey(Connection.EXTRA_SIP_INVITE));
        assertFalse(parceledExtras.containsKey("SomeExtra"));
        assertFalse(parceledExtras.containsKey(Connection.EXTRA_CALL_SUBJECT));
    }

    private Bundle getSomeExtras() {
        Bundle extras = new Bundle();
        extras.putString(Connection.EXTRA_SIP_INVITE, "scary data");
        extras.putString("SomeExtra", "Extra Extra");
        extras.putString(Connection.EXTRA_CALL_SUBJECT, "Blah");
        return extras;
    }
}
