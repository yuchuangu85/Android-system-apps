/*
 * Copyright (C) 2015 The Android Open Source Project
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
import android.content.Context;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Binder;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telephony.SubscriptionManager;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.server.telecom.Call;
import com.android.server.telecom.CallIdMapper;
import com.android.server.telecom.ConnectionServiceFocusManager;
import com.android.server.telecom.ConnectionServiceRepository;
import com.android.server.telecom.ConnectionServiceWrapper;
import com.android.server.telecom.CreateConnectionProcessor;
import com.android.server.telecom.CreateConnectionResponse;
import com.android.server.telecom.PhoneAccountRegistrar;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.HashMap;

import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit testing for CreateConnectionProcessor as well as CreateConnectionTimeout classes.
 */
@RunWith(JUnit4.class)
public class CreateConnectionProcessorTest extends TelecomTestCase {

    private static final String TEST_PACKAGE = "com.android.server.telecom.tests";
    private static final String TEST_CLASS =
            "com.android.server.telecom.tests.MockConnectionService";

    @Mock
    ConnectionServiceRepository mMockConnectionServiceRepository;
    @Mock
    PhoneAccountRegistrar mMockAccountRegistrar;
    @Mock
    CreateConnectionResponse mMockCreateConnectionResponse;
    @Mock
    Call mMockCall;
    @Mock
    ConnectionServiceFocusManager mConnectionServiceFocusManager;

    CreateConnectionProcessor mTestCreateConnectionProcessor;

    private ArrayList<PhoneAccount> phoneAccounts;
    private HashMap<Integer,Integer> mSubToSlot;
    private HashMap<PhoneAccount,Integer> mAccountToSub;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        mContext = mComponentContextFixture.getTestDouble().getApplicationContext();

        when(mMockCall.getConnectionServiceFocusManager()).thenReturn(
                mConnectionServiceFocusManager);
        doAnswer(new Answer<Void>() {
                     @Override
                     public Void answer(InvocationOnMock invocation) {
                         Object[] args = invocation.getArguments();
                         ConnectionServiceFocusManager.CallFocus focus =
                                 (ConnectionServiceFocusManager.CallFocus) args[0];
                         ConnectionServiceFocusManager.RequestFocusCallback callback =
                                 (ConnectionServiceFocusManager.RequestFocusCallback) args[1];
                         callback.onRequestFocusDone(focus);
                         return null;
                     }
                 }
                ).when(mConnectionServiceFocusManager).requestFocus(any(), any());

        mTestCreateConnectionProcessor = new CreateConnectionProcessor(mMockCall,
                mMockConnectionServiceRepository, mMockCreateConnectionResponse,
                mMockAccountRegistrar, mContext);

        mAccountToSub = new HashMap<>();
        phoneAccounts = new ArrayList<>();
        mSubToSlot = new HashMap<>();
        mTestCreateConnectionProcessor.setTelephonyManagerAdapter(
                new CreateConnectionProcessor.ITelephonyManagerAdapter() {
                    @Override
                    public int getSubIdForPhoneAccount(Context context, PhoneAccount account) {
                        return mAccountToSub.getOrDefault(account,
                                SubscriptionManager.INVALID_SIM_SLOT_INDEX);
                    }

                    @Override
                    public int getSlotIndex(int subId) {
                        return mSubToSlot.getOrDefault(subId,
                                SubscriptionManager.INVALID_SIM_SLOT_INDEX);
                    }
                });
        when(mMockAccountRegistrar.getAllPhoneAccountsOfCurrentUser()).thenReturn(phoneAccounts);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        mTestCreateConnectionProcessor = null;
        super.tearDown();
    }

    @SmallTest
    @Test
    public void testSimPhoneAccountSuccess() throws Exception {
        PhoneAccountHandle pAHandle = getNewTargetPhoneAccountHandle("tel_acct");
        setTargetPhoneAccount(mMockCall, pAHandle);
        when(mMockCall.isEmergencyCall()).thenReturn(false);
        // No Connection Manager in this case
        when(mMockAccountRegistrar.getSimCallManagerFromCall(any(Call.class))).thenReturn(null);
        ConnectionServiceWrapper service = makeConnectionServiceWrapper();

        mTestCreateConnectionProcessor.process();

        verify(mMockCall).setConnectionManagerPhoneAccount(eq(pAHandle));
        verify(mMockCall).setTargetPhoneAccount(eq(pAHandle));
        verify(mMockCall).setConnectionService(eq(service));
        verify(service).createConnection(eq(mMockCall), any(CreateConnectionResponse.class));
        // Notify successful connection to call
        CallIdMapper mockCallIdMapper = mock(CallIdMapper.class);
        mTestCreateConnectionProcessor.handleCreateConnectionSuccess(mockCallIdMapper, null);
        verify(mMockCreateConnectionResponse).handleCreateConnectionSuccess(mockCallIdMapper, null);
    }

    @SmallTest
    @Test
    public void testbadPhoneAccount() throws Exception {
        PhoneAccountHandle pAHandle = null;
        when(mMockCall.isEmergencyCall()).thenReturn(false);
        when(mMockCall.getTargetPhoneAccount()).thenReturn(pAHandle);
        givePhoneAccountBindPermission(pAHandle);
        // No Connection Manager in this case
        when(mMockAccountRegistrar.getSimCallManagerFromCall(any(Call.class))).thenReturn(null);
        ConnectionServiceWrapper service = makeConnectionServiceWrapper();

        mTestCreateConnectionProcessor.process();

        verify(service, never()).createConnection(eq(mMockCall),
                any(CreateConnectionResponse.class));
        verify(mMockCreateConnectionResponse).handleCreateConnectionFailure(
                eq(new DisconnectCause(DisconnectCause.ERROR)));
    }

    @SmallTest
    @Test
    public void testConnectionManagerSuccess() throws Exception {
        PhoneAccountHandle pAHandle = getNewTargetPhoneAccountHandle("tel_acct");
        setTargetPhoneAccount(mMockCall, pAHandle);
        when(mMockCall.isEmergencyCall()).thenReturn(false);
        // Include a Connection Manager
        PhoneAccountHandle callManagerPAHandle = getNewConnectionMangerHandleForCall(mMockCall,
                "cm_acct");
        ConnectionServiceWrapper service = makeConnectionServiceWrapper();
        // Make sure the target phone account has the correct permissions
        PhoneAccount mFakeTargetPhoneAccount = makeQuickAccount("cm_acct",
                PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION);
        when(mMockAccountRegistrar.getPhoneAccountUnchecked(pAHandle)).thenReturn(
                mFakeTargetPhoneAccount);

        mTestCreateConnectionProcessor.process();

        verify(mMockCall).setConnectionManagerPhoneAccount(eq(callManagerPAHandle));
        verify(mMockCall).setTargetPhoneAccount(eq(pAHandle));
        verify(mMockCall).setConnectionService(eq(service));
        verify(service).createConnection(eq(mMockCall),
                any(CreateConnectionResponse.class));
        // Notify successful connection to call
        CallIdMapper mockCallIdMapper = mock(CallIdMapper.class);
        mTestCreateConnectionProcessor.handleCreateConnectionSuccess(mockCallIdMapper, null);
        verify(mMockCreateConnectionResponse).handleCreateConnectionSuccess(mockCallIdMapper, null);
    }

    @SmallTest
    @Test
    public void testConnectionManagerFailedFallToSim() throws Exception {
        PhoneAccountHandle pAHandle = getNewTargetPhoneAccountHandle("tel_acct");
        setTargetPhoneAccount(mMockCall, pAHandle);
        when(mMockCall.isEmergencyCall()).thenReturn(false);
        // Include a Connection Manager
        PhoneAccountHandle callManagerPAHandle = getNewConnectionMangerHandleForCall(mMockCall,
                "cm_acct");
        ConnectionServiceWrapper service = makeConnectionServiceWrapper();
        when(mMockCall.getConnectionManagerPhoneAccount()).thenReturn(callManagerPAHandle);
        PhoneAccount mFakeTargetPhoneAccount = makeQuickAccount("cm_acct",
                PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION);
        when(mMockAccountRegistrar.getPhoneAccountUnchecked(pAHandle)).thenReturn(
                mFakeTargetPhoneAccount);
        when(mMockCall.getConnectionService()).thenReturn(service);
        // Put CreateConnectionProcessor in correct state to fail with ConnectionManager
        mTestCreateConnectionProcessor.process();
        reset(mMockCall);
        reset(service);

        when(mMockCall.getConnectionServiceFocusManager()).thenReturn(
                mConnectionServiceFocusManager);
        // Notify that the ConnectionManager has denied the call.
        when(mMockCall.getConnectionManagerPhoneAccount()).thenReturn(callManagerPAHandle);
        when(mMockCall.getConnectionService()).thenReturn(service);
        mTestCreateConnectionProcessor.handleCreateConnectionFailure(
                new DisconnectCause(DisconnectCause.CONNECTION_MANAGER_NOT_SUPPORTED));

        // Verify that the Sim Phone Account is used correctly
        verify(mMockCall).setConnectionManagerPhoneAccount(eq(pAHandle));
        verify(mMockCall).setTargetPhoneAccount(eq(pAHandle));
        verify(mMockCall).setConnectionService(eq(service));
        verify(service).createConnection(eq(mMockCall), any(CreateConnectionResponse.class));
        // Notify successful connection to call
        CallIdMapper mockCallIdMapper = mock(CallIdMapper.class);
        mTestCreateConnectionProcessor.handleCreateConnectionSuccess(mockCallIdMapper, null);
        verify(mMockCreateConnectionResponse).handleCreateConnectionSuccess(mockCallIdMapper, null);
    }

    @SmallTest
    @Test
    public void testConnectionManagerFailedDoNotFallToSim() throws Exception {
        PhoneAccountHandle pAHandle = getNewTargetPhoneAccountHandle("tel_acct");
        setTargetPhoneAccount(mMockCall, pAHandle);
        when(mMockCall.isEmergencyCall()).thenReturn(false);
        // Include a Connection Manager
        PhoneAccountHandle callManagerPAHandle = getNewConnectionMangerHandleForCall(mMockCall,
                "cm_acct");
        ConnectionServiceWrapper service = makeConnectionServiceWrapper();
        when(mMockCall.getConnectionManagerPhoneAccount()).thenReturn(callManagerPAHandle);
        PhoneAccount mFakeTargetPhoneAccount = makeQuickAccount("cm_acct",
                PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION);
        when(mMockAccountRegistrar.getPhoneAccountUnchecked(pAHandle)).thenReturn(
                mFakeTargetPhoneAccount);
        when(mMockCall.getConnectionService()).thenReturn(service);
        // Put CreateConnectionProcessor in correct state to fail with ConnectionManager
        mTestCreateConnectionProcessor.process();
        reset(mMockCall);
        reset(service);

        when(mMockCall.getConnectionServiceFocusManager()).thenReturn(
                mConnectionServiceFocusManager);
        // Notify that the ConnectionManager has rejected the call.
        when(mMockCall.getConnectionManagerPhoneAccount()).thenReturn(callManagerPAHandle);
        when(mMockCall.getConnectionService()).thenReturn(service);
        when(service.isServiceValid("createConnection")).thenReturn(true);
        mTestCreateConnectionProcessor.handleCreateConnectionFailure(
                new DisconnectCause(DisconnectCause.OTHER));

        // Verify call connection rejected
        verify(mMockCreateConnectionResponse).handleCreateConnectionFailure(
                new DisconnectCause(DisconnectCause.OTHER));
    }

    /**
     * Ensure that the non-emergency capable PhoneAccount and the SIM manager is not chosen to place
     * the emergency call if there is an emergency capable PhoneAccount available as well.
     */
    @SmallTest
    @Test
    public void testEmergencyCall() throws Exception {
        when(mMockCall.isEmergencyCall()).thenReturn(true);
        // Put in a regular phone account as the target to be sure it doesn't call that
        PhoneAccount regularAccount = makePhoneAccount("tel_acct1",
                PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION);
        mapToSubSlot(regularAccount, 1 /*subId*/, 0 /*slotId*/);
        setTargetPhoneAccount(mMockCall, regularAccount.getAccountHandle());
        phoneAccounts.add(regularAccount);
        // Include a Connection Manager to be sure it doesn't call that
        PhoneAccount callManagerPA = createNewConnectionManagerPhoneAccountForCall(mMockCall,
                "cm_acct", 0);
        phoneAccounts.add(callManagerPA);
        ConnectionServiceWrapper service = makeConnectionServiceWrapper();
        PhoneAccount emergencyPhoneAccount = makeEmergencyPhoneAccount("tel_emer", 0);
        mapToSubSlot(emergencyPhoneAccount, 2 /*subId*/, 1 /*slotId*/);
        phoneAccounts.add(emergencyPhoneAccount);
        PhoneAccountHandle emergencyPhoneAccountHandle = emergencyPhoneAccount.getAccountHandle();

        mTestCreateConnectionProcessor.process();

        verify(mMockCall).setConnectionManagerPhoneAccount(eq(emergencyPhoneAccountHandle));
        verify(mMockCall).setTargetPhoneAccount(eq(emergencyPhoneAccountHandle));
        verify(mMockCall).setConnectionService(eq(service));
        verify(service).createConnection(eq(mMockCall), any(CreateConnectionResponse.class));
        // Notify successful connection to call
        CallIdMapper mockCallIdMapper = mock(CallIdMapper.class);
        mTestCreateConnectionProcessor.handleCreateConnectionSuccess(mockCallIdMapper, null);
        verify(mMockCreateConnectionResponse).handleCreateConnectionSuccess(mockCallIdMapper, null);
    }

    /**
     * 1) Ensure that if there is a non-SIM PhoneAccount, it is not chosen as the Phone Account to
     * dial the emergency call.
     * 2) Register multiple emergency capable PhoneAccounts. Since there is not preference, we
     * default to sending on the lowest slot.
     */
    @SmallTest
    @Test
    public void testEmergencyCallMultiSimNoPreferred() throws Exception {
        when(mMockCall.isEmergencyCall()).thenReturn(true);
        // Put in a non-SIM phone account as the target to be sure it doesn't call that.
        PhoneAccount regularAccount = makePhoneAccount("tel_acct1", 0);
        setTargetPhoneAccount(mMockCall, regularAccount.getAccountHandle());
        // Include a Connection Manager to be sure it doesn't call that
        PhoneAccount callManagerPA = createNewConnectionManagerPhoneAccountForCall(mMockCall,
                "cm_acct", 0);
        phoneAccounts.add(callManagerPA);
        ConnectionServiceWrapper service = makeConnectionServiceWrapper();
        PhoneAccount emergencyPhoneAccount1 = makeEmergencyPhoneAccount("tel_emer1", 0);
        phoneAccounts.add(emergencyPhoneAccount1);
        mapToSubSlot(emergencyPhoneAccount1, 1 /*subId*/, 1 /*slotId*/);
        PhoneAccount emergencyPhoneAccount2 = makeEmergencyPhoneAccount("tel_emer2", 0);
        phoneAccounts.add(emergencyPhoneAccount2);
        mapToSubSlot(emergencyPhoneAccount2, 2 /*subId*/, 0 /*slotId*/);
        PhoneAccountHandle emergencyPhoneAccountHandle2 = emergencyPhoneAccount2.getAccountHandle();

        mTestCreateConnectionProcessor.process();

        // We did not set a preference
        verify(mMockCall).setConnectionManagerPhoneAccount(eq(emergencyPhoneAccountHandle2));
        verify(mMockCall).setTargetPhoneAccount(eq(emergencyPhoneAccountHandle2));
        verify(mMockCall).setConnectionService(eq(service));
        verify(service).createConnection(eq(mMockCall), any(CreateConnectionResponse.class));
        // Notify successful connection to call
        CallIdMapper mockCallIdMapper = mock(CallIdMapper.class);
        mTestCreateConnectionProcessor.handleCreateConnectionSuccess(mockCallIdMapper, null);
        verify(mMockCreateConnectionResponse).handleCreateConnectionSuccess(mockCallIdMapper, null);
    }

    /**
     * Ensure that the call goes out on the PhoneAccount that has the CAPABILITY_EMERGENCY_PREFERRED
     * capability, even if the user specifically chose the other emergency capable PhoneAccount.
     */
    @SmallTest
    @Test
    public void testEmergencyCallMultiSimTelephonyPreferred() throws Exception {
        when(mMockCall.isEmergencyCall()).thenReturn(true);
        ConnectionServiceWrapper service = makeConnectionServiceWrapper();
        PhoneAccount emergencyPhoneAccount1 = makeEmergencyPhoneAccount("tel_emer1", 0);
        mapToSubSlot(emergencyPhoneAccount1, 1 /*subId*/, 0 /*slotId*/);
        setTargetPhoneAccount(mMockCall, emergencyPhoneAccount1.getAccountHandle());
        phoneAccounts.add(emergencyPhoneAccount1);
        PhoneAccount emergencyPhoneAccount2 = makeEmergencyPhoneAccount("tel_emer2",
                PhoneAccount.CAPABILITY_EMERGENCY_PREFERRED);
        mapToSubSlot(emergencyPhoneAccount2, 2 /*subId*/, 1 /*slotId*/);
        phoneAccounts.add(emergencyPhoneAccount2);
        PhoneAccountHandle emergencyPhoneAccountHandle2 = emergencyPhoneAccount2.getAccountHandle();

        mTestCreateConnectionProcessor.process();

        // Make sure the telephony preferred SIM is the one that is chosen.
        verify(mMockCall).setConnectionManagerPhoneAccount(eq(emergencyPhoneAccountHandle2));
        verify(mMockCall).setTargetPhoneAccount(eq(emergencyPhoneAccountHandle2));
        verify(mMockCall).setConnectionService(eq(service));
        verify(service).createConnection(eq(mMockCall), any(CreateConnectionResponse.class));
        // Notify successful connection to call
        CallIdMapper mockCallIdMapper = mock(CallIdMapper.class);
        mTestCreateConnectionProcessor.handleCreateConnectionSuccess(mockCallIdMapper, null);
        verify(mMockCreateConnectionResponse).handleCreateConnectionSuccess(mockCallIdMapper, null);
    }

    /**
     * If there is no phone account with CAPABILITY_EMERGENCY_PREFERRED capability, choose the user
     * chosen target account.
     */
    @SmallTest
    @Test
    public void testEmergencyCallMultiSimUserPreferred() throws Exception {
        when(mMockCall.isEmergencyCall()).thenReturn(true);
        // Include a Connection Manager to be sure it doesn't call that
        PhoneAccount callManagerPA = createNewConnectionManagerPhoneAccountForCall(mMockCall,
                "cm_acct", 0);
        phoneAccounts.add(callManagerPA);
        ConnectionServiceWrapper service = makeConnectionServiceWrapper();
        PhoneAccount emergencyPhoneAccount1 = makeEmergencyPhoneAccount("tel_emer1", 0);
        mapToSubSlot(emergencyPhoneAccount1, 1 /*subId*/, 0 /*slotId*/);
        phoneAccounts.add(emergencyPhoneAccount1);
        PhoneAccount emergencyPhoneAccount2 = makeEmergencyPhoneAccount("tel_emer2", 0);
        // Make this the user preferred account
        mapToSubSlot(emergencyPhoneAccount2, 2 /*subId*/, 1 /*slotId*/);
        setTargetPhoneAccount(mMockCall, emergencyPhoneAccount2.getAccountHandle());
        phoneAccounts.add(emergencyPhoneAccount2);
        PhoneAccountHandle emergencyPhoneAccountHandle2 = emergencyPhoneAccount2.getAccountHandle();

        mTestCreateConnectionProcessor.process();

        // Make sure the user preferred SIM is the one that is chosen.
        verify(mMockCall).setConnectionManagerPhoneAccount(eq(emergencyPhoneAccountHandle2));
        verify(mMockCall).setTargetPhoneAccount(eq(emergencyPhoneAccountHandle2));
        verify(mMockCall).setConnectionService(eq(service));
        verify(service).createConnection(eq(mMockCall), any(CreateConnectionResponse.class));
        // Notify successful connection to call
        CallIdMapper mockCallIdMapper = mock(CallIdMapper.class);
        mTestCreateConnectionProcessor.handleCreateConnectionSuccess(mockCallIdMapper, null);
        verify(mMockCreateConnectionResponse).handleCreateConnectionSuccess(mockCallIdMapper, null);
    }

    /**
     * If the user preferred PhoneAccount is associated with an invalid slot, place on the other,
     * valid slot.
     */
    @SmallTest
    @Test
    public void testEmergencyCallMultiSimUserPreferredInvalidSlot() throws Exception {
        when(mMockCall.isEmergencyCall()).thenReturn(true);
        // Include a Connection Manager to be sure it doesn't call that
        PhoneAccount callManagerPA = createNewConnectionManagerPhoneAccountForCall(mMockCall,
                "cm_acct", 0);
        phoneAccounts.add(callManagerPA);
        ConnectionServiceWrapper service = makeConnectionServiceWrapper();
        PhoneAccount emergencyPhoneAccount1 = makeEmergencyPhoneAccount("tel_emer1", 0);
        // make this the user preferred account
        setTargetPhoneAccount(mMockCall, emergencyPhoneAccount1.getAccountHandle());
        mapToSubSlot(emergencyPhoneAccount1, 1 /*subId*/,
                SubscriptionManager.INVALID_SIM_SLOT_INDEX /*slotId*/);
        phoneAccounts.add(emergencyPhoneAccount1);
        PhoneAccount emergencyPhoneAccount2 = makeEmergencyPhoneAccount("tel_emer2", 0);
        mapToSubSlot(emergencyPhoneAccount2, 2 /*subId*/, 1 /*slotId*/);
        phoneAccounts.add(emergencyPhoneAccount2);
        PhoneAccountHandle emergencyPhoneAccountHandle2 = emergencyPhoneAccount2.getAccountHandle();

        mTestCreateConnectionProcessor.process();

        // Make sure the valid SIM is the one that is chosen.
        verify(mMockCall).setConnectionManagerPhoneAccount(eq(emergencyPhoneAccountHandle2));
        verify(mMockCall).setTargetPhoneAccount(eq(emergencyPhoneAccountHandle2));
        verify(mMockCall).setConnectionService(eq(service));
        verify(service).createConnection(eq(mMockCall), any(CreateConnectionResponse.class));
        // Notify successful connection to call
        CallIdMapper mockCallIdMapper = mock(CallIdMapper.class);
        mTestCreateConnectionProcessor.handleCreateConnectionSuccess(mockCallIdMapper, null);
        verify(mMockCreateConnectionResponse).handleCreateConnectionSuccess(mockCallIdMapper, null);
    }

    /**
     * If a PhoneAccount is associated with an invalid slot, place on the other, valid slot.
     */
    @SmallTest
    @Test
    public void testEmergencyCallMultiSimNoPreferenceInvalidSlot() throws Exception {
        when(mMockCall.isEmergencyCall()).thenReturn(true);
        // Include a Connection Manager to be sure it doesn't call that
        PhoneAccount callManagerPA = createNewConnectionManagerPhoneAccountForCall(mMockCall,
                "cm_acct", 0);
        phoneAccounts.add(callManagerPA);
        ConnectionServiceWrapper service = makeConnectionServiceWrapper();
        PhoneAccount emergencyPhoneAccount1 = makeEmergencyPhoneAccount("tel_emer1", 0);
        mapToSubSlot(emergencyPhoneAccount1, 1 /*subId*/,
                SubscriptionManager.INVALID_SIM_SLOT_INDEX /*slotId*/);
        phoneAccounts.add(emergencyPhoneAccount1);
        PhoneAccount emergencyPhoneAccount2 = makeEmergencyPhoneAccount("tel_emer2", 0);
        mapToSubSlot(emergencyPhoneAccount2, 2 /*subId*/, 1 /*slotId*/);
        phoneAccounts.add(emergencyPhoneAccount2);
        PhoneAccountHandle emergencyPhoneAccountHandle2 = emergencyPhoneAccount2.getAccountHandle();

        mTestCreateConnectionProcessor.process();

        // Make sure the valid SIM is the one that is chosen.
        verify(mMockCall).setConnectionManagerPhoneAccount(eq(emergencyPhoneAccountHandle2));
        verify(mMockCall).setTargetPhoneAccount(eq(emergencyPhoneAccountHandle2));
        verify(mMockCall).setConnectionService(eq(service));
        verify(service).createConnection(eq(mMockCall), any(CreateConnectionResponse.class));
        // Notify successful connection to call
        CallIdMapper mockCallIdMapper = mock(CallIdMapper.class);
        mTestCreateConnectionProcessor.handleCreateConnectionSuccess(mockCallIdMapper, null);
        verify(mMockCreateConnectionResponse).handleCreateConnectionSuccess(mockCallIdMapper, null);
    }

    @SmallTest
    @Test
    public void testEmergencyCallSimFailToConnectionManager() throws Exception {
        when(mMockCall.isEmergencyCall()).thenReturn(true);
        when(mMockCall.getHandle()).thenReturn(Uri.parse(""));
        // Put in a regular phone account to be sure it doesn't call that
        PhoneAccount regularAccount = makePhoneAccount("tel_acct1",
                PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION);
        mapToSubSlot(regularAccount, 1 /*subId*/, 0 /*slotId*/);
        setTargetPhoneAccount(mMockCall, regularAccount.getAccountHandle());
        phoneAccounts.add(regularAccount);
        when(mMockAccountRegistrar.getOutgoingPhoneAccountForSchemeOfCurrentUser(
                nullable(String.class))).thenReturn(regularAccount.getAccountHandle());
        // Include a normal Connection Manager to be sure it doesn't call that
        PhoneAccount callManagerPA = createNewConnectionManagerPhoneAccountForCall(mMockCall,
                "cm_acct", 0);
        phoneAccounts.add(callManagerPA);
        // Include a connection Manager for the user with the capability to make calls
        PhoneAccount emerCallManagerPA = getNewEmergencyConnectionManagerPhoneAccount("cm_acct",
                PhoneAccount.CAPABILITY_PLACE_EMERGENCY_CALLS);
        ConnectionServiceWrapper service = makeConnectionServiceWrapper();
        PhoneAccount emergencyPhoneAccount = makeEmergencyPhoneAccount("tel_emer", 0);
        phoneAccounts.add(emergencyPhoneAccount);
        mapToSubSlot(regularAccount, 2 /*subId*/, 1 /*slotId*/);
        mTestCreateConnectionProcessor.process();
        reset(mMockCall);
        reset(service);

        when(mMockCall.getConnectionServiceFocusManager()).thenReturn(
                mConnectionServiceFocusManager);
        when(mMockCall.isEmergencyCall()).thenReturn(true);

        // When Notify SIM connection fails, fall back to connection manager
        mTestCreateConnectionProcessor.handleCreateConnectionFailure(new DisconnectCause(
                DisconnectCause.REJECTED));

        verify(mMockCall).setConnectionManagerPhoneAccount(
                eq(emerCallManagerPA.getAccountHandle()));
        verify(mMockCall).setTargetPhoneAccount(eq(regularAccount.getAccountHandle()));
        verify(mMockCall).setConnectionService(eq(service));
        verify(service).createConnection(eq(mMockCall), any(CreateConnectionResponse.class));
    }

    private PhoneAccount makeEmergencyPhoneAccount(String id, int capabilities) {
        final PhoneAccount emergencyPhoneAccount = makeQuickAccount(id, capabilities |
                PhoneAccount.CAPABILITY_PLACE_EMERGENCY_CALLS |
                        PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION);
        PhoneAccountHandle emergencyPhoneAccountHandle = emergencyPhoneAccount.getAccountHandle();
        givePhoneAccountBindPermission(emergencyPhoneAccountHandle);
        when(mMockAccountRegistrar.getPhoneAccountUnchecked(emergencyPhoneAccountHandle))
                .thenReturn(emergencyPhoneAccount);
        return emergencyPhoneAccount;
    }

    private PhoneAccount makePhoneAccount(String id, int capabilities) {
        final PhoneAccount phoneAccount = makeQuickAccount(id, capabilities);
        PhoneAccountHandle phoneAccountHandle = phoneAccount.getAccountHandle();
        givePhoneAccountBindPermission(phoneAccountHandle);
        when(mMockAccountRegistrar.getPhoneAccountUnchecked(
                phoneAccount.getAccountHandle())).thenReturn(phoneAccount);
        return phoneAccount;
    }

    private void mapToSubSlot(PhoneAccount account, int subId, int slotId) {
        mAccountToSub.put(account, subId);
        mSubToSlot.put(subId, slotId);
    }

    private void givePhoneAccountBindPermission(PhoneAccountHandle handle) {
        when(mMockAccountRegistrar.phoneAccountRequiresBindPermission(eq(handle))).thenReturn(true);
    }

    private PhoneAccountHandle getNewConnectionMangerHandleForCall(Call call, String id) {
        PhoneAccountHandle callManagerPAHandle = makeQuickAccountHandle(id);
        when(mMockAccountRegistrar.getSimCallManagerFromCall(eq(call))).thenReturn(
                callManagerPAHandle);
        givePhoneAccountBindPermission(callManagerPAHandle);
        return callManagerPAHandle;
    }

    private PhoneAccountHandle getNewTargetPhoneAccountHandle(String id) {
        PhoneAccountHandle pAHandle = makeQuickAccountHandle(id);
        givePhoneAccountBindPermission(pAHandle);
        return pAHandle;
    }

    private void setTargetPhoneAccount(Call call, PhoneAccountHandle pAHandle) {
        when(call.getTargetPhoneAccount()).thenReturn(pAHandle);
    }

    private PhoneAccount createNewConnectionManagerPhoneAccountForCall(Call call, String id,
            int capability) {
        PhoneAccount callManagerPA = makeQuickAccount(id, capability);
        when(mMockAccountRegistrar.getSimCallManagerFromCall(eq(call))).thenReturn(
                callManagerPA.getAccountHandle());
        givePhoneAccountBindPermission(callManagerPA.getAccountHandle());
        when(mMockAccountRegistrar.getPhoneAccountUnchecked(
                callManagerPA.getAccountHandle())).thenReturn(callManagerPA);
        return callManagerPA;
    }

    private PhoneAccount getNewEmergencyConnectionManagerPhoneAccount(String id, int capability) {
        PhoneAccount callManagerPA = makeQuickAccount(id, capability);
        when(mMockAccountRegistrar.getSimCallManagerOfCurrentUser()).thenReturn(
                callManagerPA.getAccountHandle());
        givePhoneAccountBindPermission(callManagerPA.getAccountHandle());
        when(mMockAccountRegistrar.getPhoneAccountUnchecked(
                callManagerPA.getAccountHandle())).thenReturn(callManagerPA);
        return callManagerPA;
    }

    private static ComponentName makeQuickConnectionServiceComponentName() {
        return new ComponentName(TEST_PACKAGE, TEST_CLASS);
    }

    private ConnectionServiceWrapper makeConnectionServiceWrapper() {
        ConnectionServiceWrapper wrapper = mock(ConnectionServiceWrapper.class);
        when(mMockConnectionServiceRepository.getService(
                eq(makeQuickConnectionServiceComponentName()),
                eq(Binder.getCallingUserHandle()))).thenReturn(wrapper);
        return wrapper;
    }

    private static PhoneAccountHandle makeQuickAccountHandle(String id) {
        return new PhoneAccountHandle(makeQuickConnectionServiceComponentName(), id,
                Binder.getCallingUserHandle());
    }

    private PhoneAccount.Builder makeQuickAccountBuilder(String id, int idx) {
        return new PhoneAccount.Builder(makeQuickAccountHandle(id), "label" + idx);
    }

    private PhoneAccount makeQuickAccount(String id, int idx) {
        return makeQuickAccountBuilder(id, idx)
                .setAddress(Uri.parse("http://foo.com/" + idx))
                .setSubscriptionAddress(Uri.parse("tel:555-000" + idx))
                .setCapabilities(idx)
                .setIcon(Icon.createWithResource(
                        "com.android.server.telecom.tests", R.drawable.stat_sys_phone_call))
                .setShortDescription("desc" + idx)
                .setIsEnabled(true)
                .build();
    }
}