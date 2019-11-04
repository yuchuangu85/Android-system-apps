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

package com.android.car;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.vms.IVmsPublisherClient;
import android.car.vms.IVmsPublisherService;
import android.car.vms.IVmsSubscriberClient;
import android.car.vms.VmsLayer;
import android.car.vms.VmsLayersOffering;
import android.car.vms.VmsSubscriptionState;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.test.filters.SmallTest;

import com.android.car.vms.VmsBrokerService;
import com.android.car.vms.VmsClientManager;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

@SmallTest
public class VmsPublisherServiceTest {
    private static final VmsSubscriptionState SUBSCRIPTION_STATE = new VmsSubscriptionState(12345,
            Collections.emptySet(), Collections.emptySet());
    private static final VmsLayersOffering OFFERING = new VmsLayersOffering(Collections.emptySet(),
            54321);
    private static final VmsLayer LAYER = new VmsLayer(1, 2, 3);
    private static final VmsLayer LAYER2 = new VmsLayer(2, 2, 8);
    private static final VmsLayer LAYER3 = new VmsLayer(3, 2, 8);
    private static final VmsLayer LAYER4 = new VmsLayer(4, 2, 8);

    private static final int PUBLISHER_ID = 54321;
    private static final byte[] PAYLOAD = new byte[]{1, 2, 3, 4};
    private static final byte[] PAYLOAD2 = new byte[]{1, 2, 3, 4, 5, 6};
    private static final byte[] PAYLOAD3 = new byte[]{10, 12, 93, 4, 5, 6, 1, 1, 1};

    @Rule
    public MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Mock
    private Context mContext;
    @Mock
    private VmsBrokerService mBrokerService;
    @Captor
    private ArgumentCaptor<VmsBrokerService.PublisherListener> mProxyCaptor;
    @Mock
    private VmsClientManager mClientManager;

    @Mock
    private IVmsSubscriberClient mSubscriberClient;
    @Mock
    private IVmsSubscriberClient mSubscriberClient2;
    @Mock
    private IVmsSubscriberClient mThrowingSubscriberClient;
    @Mock
    private IVmsSubscriberClient mThrowingSubscriberClient2;

    private VmsPublisherService mPublisherService;
    private MockPublisherClient mPublisherClient;
    private MockPublisherClient mPublisherClient2;

    @Before
    public void setUp() {
        mPublisherService = new VmsPublisherService(mContext, mBrokerService, mClientManager);
        verify(mClientManager).registerConnectionListener(mPublisherService);

        mPublisherClient = new MockPublisherClient();
        mPublisherClient2 = new MockPublisherClient();
        when(mBrokerService.getSubscribersForLayerFromPublisher(LAYER, PUBLISHER_ID))
                .thenReturn(new HashSet<>(Arrays.asList(mSubscriberClient, mSubscriberClient2)));

    }

    @Test
    public void testInit() {
        mPublisherService.init();
    }

    @Test
    public void testOnClientConnected() {
        mPublisherService.onClientConnected("SomeClient", mPublisherClient.asBinder());
        mPublisherService.onClientConnected("SomeOtherClient", mPublisherClient2.asBinder());
        verify(mBrokerService, times(2)).addPublisherListener(mProxyCaptor.capture());

        assertNotNull(mPublisherClient.mPublisherService);
        assertSame(mPublisherClient.mPublisherService, mProxyCaptor.getAllValues().get(0));

        assertNotNull(mPublisherClient2.mPublisherService);
        assertSame(mPublisherClient2.mPublisherService, mProxyCaptor.getAllValues().get(1));
        assertNotSame(mPublisherClient2.mPublisherService, mPublisherClient.mPublisherService);
    }

    @Test
    public void testOnClientDisconnected() {
        mPublisherService.onClientConnected("SomeClient", mPublisherClient.asBinder());
        mPublisherService.onClientConnected("SomeOtherClient", mPublisherClient2.asBinder());
        verify(mBrokerService, times(2)).addPublisherListener(mProxyCaptor.capture());

        reset(mClientManager, mBrokerService);
        mPublisherService.onClientDisconnected("SomeClient");

        verify(mBrokerService).removeDeadPublisher(mPublisherClient.mToken);
        verify(mBrokerService).removePublisherListener(mProxyCaptor.getAllValues().get(0));
        verifyNoMoreInteractions(mBrokerService);
    }

    @Test
    public void testSetLayersOffering() throws Exception {
        mPublisherService.onClientConnected("SomeClient", mPublisherClient.asBinder());

        mPublisherClient.mPublisherService.setLayersOffering(mPublisherClient.mToken, OFFERING);
        verify(mBrokerService).setPublisherLayersOffering(mPublisherClient.mToken, OFFERING);
    }

    @Test(expected = SecurityException.class)
    public void testSetLayersOffering_InvalidToken() throws Exception {
        mPublisherService.onClientConnected("SomeClient", mPublisherClient.asBinder());

        mPublisherClient.mPublisherService.setLayersOffering(new Binder(), OFFERING);
    }

    @Test(expected = SecurityException.class)
    public void testSetLayersOffering_Disconnected() throws Exception {
        mPublisherService.onClientConnected("SomeClient", mPublisherClient.asBinder());
        mPublisherService.onClientDisconnected("SomeClient");

        mPublisherClient.mPublisherService.setLayersOffering(mPublisherClient.mToken, OFFERING);
    }

    @Test(expected = SecurityException.class)
    public void testSetLayersOffering_PermissionDenied() throws Exception {
        mPublisherService.onClientConnected("SomeClient", mPublisherClient.asBinder());
        when(mContext.checkCallingOrSelfPermission(Car.PERMISSION_VMS_PUBLISHER)).thenReturn(
                PackageManager.PERMISSION_DENIED);

        mPublisherClient.mPublisherService.setLayersOffering(mPublisherClient.mToken, OFFERING);
    }

    @Test
    public void testPublish() throws Exception {
        mPublisherService.onClientConnected("SomeClient", mPublisherClient.asBinder());

        mPublisherClient.mPublisherService.publish(mPublisherClient.mToken, LAYER, PUBLISHER_ID,
                PAYLOAD);
        verify(mSubscriberClient).onVmsMessageReceived(LAYER, PAYLOAD);
        verify(mSubscriberClient2).onVmsMessageReceived(LAYER, PAYLOAD);
    }

    @Test
    public void testPublishNullLayerAndNullPayload() throws Exception {
        mPublisherService.onClientConnected("SomeClient", mPublisherClient.asBinder());

        // We just want to ensure that no exceptions are thrown here.
        mPublisherClient.mPublisherService.publish(mPublisherClient.mToken, null, PUBLISHER_ID,
                null);
    }

    @Test
    public void testPublish_ClientError() throws Exception {
        mPublisherService.onClientConnected("SomeClient", mPublisherClient.asBinder());
        doThrow(new RemoteException()).when(mSubscriberClient).onVmsMessageReceived(LAYER, PAYLOAD);

        mPublisherClient.mPublisherService.publish(mPublisherClient.mToken, LAYER, PUBLISHER_ID,
                PAYLOAD);
        verify(mSubscriberClient).onVmsMessageReceived(LAYER, PAYLOAD);
        verify(mSubscriberClient2).onVmsMessageReceived(LAYER, PAYLOAD);
    }

    @Test(expected = SecurityException.class)
    public void testPublish_InvalidToken() throws Exception {
        mPublisherService.onClientConnected("SomeClient", mPublisherClient.asBinder());

        mPublisherClient.mPublisherService.publish(new Binder(), LAYER, PUBLISHER_ID, PAYLOAD);
    }

    @Test(expected = SecurityException.class)
    public void testPublish_Disconnected() throws Exception {
        mPublisherService.onClientConnected("SomeClient", mPublisherClient.asBinder());
        mPublisherService.onClientDisconnected("SomeClient");

        mPublisherClient.mPublisherService.publish(mPublisherClient.mToken, LAYER, PUBLISHER_ID,
                PAYLOAD);
    }

    @Test(expected = SecurityException.class)
    public void testPublish_PermissionDenied() throws Exception {
        mPublisherService.onClientConnected("SomeClient", mPublisherClient.asBinder());
        when(mContext.checkCallingOrSelfPermission(Car.PERMISSION_VMS_PUBLISHER)).thenReturn(
                PackageManager.PERMISSION_DENIED);

        mPublisherClient.mPublisherService.publish(mPublisherClient.mToken, LAYER, PUBLISHER_ID,
                PAYLOAD);
    }

    @Test
    public void testGetSubscriptions() throws Exception {
        mPublisherService.onClientConnected("SomeClient", mPublisherClient.asBinder());
        when(mBrokerService.getSubscriptionState()).thenReturn(SUBSCRIPTION_STATE);

        assertEquals(SUBSCRIPTION_STATE, mPublisherClient.mPublisherService.getSubscriptions());
    }

    @Test(expected = SecurityException.class)
    public void testGetSubscriptions_Disconnected() throws Exception {
        mPublisherService.onClientConnected("SomeClient", mPublisherClient.asBinder());
        mPublisherService.onClientDisconnected("SomeClient");

        mPublisherClient.mPublisherService.getSubscriptions();
    }

    @Test(expected = SecurityException.class)
    public void testGetSubscriptions_PermissionDenied() throws Exception {
        mPublisherService.onClientConnected("SomeClient", mPublisherClient.asBinder());
        when(mContext.checkCallingOrSelfPermission(Car.PERMISSION_VMS_PUBLISHER)).thenReturn(
                PackageManager.PERMISSION_DENIED);

        mPublisherClient.mPublisherService.getSubscriptions();
    }

    @Test
    public void testGetPublisherId() throws Exception {
        mPublisherService.onClientConnected("SomeClient", mPublisherClient.asBinder());
        when(mBrokerService.getPublisherId(PAYLOAD)).thenReturn(PUBLISHER_ID);

        assertEquals(PUBLISHER_ID, mPublisherClient.mPublisherService.getPublisherId(PAYLOAD));
    }

    @Test(expected = SecurityException.class)
    public void testGetPublisherId_Disconnected() throws Exception {
        mPublisherService.onClientConnected("SomeClient", mPublisherClient.asBinder());
        mPublisherService.onClientDisconnected("SomeClient");

        mPublisherClient.mPublisherService.getPublisherId(PAYLOAD);
    }

    @Test(expected = SecurityException.class)
    public void testGetPublisherId_PermissionDenied() throws Exception {
        mPublisherService.onClientConnected("SomeClient", mPublisherClient.asBinder());
        when(mContext.checkCallingOrSelfPermission(Car.PERMISSION_VMS_PUBLISHER)).thenReturn(
                PackageManager.PERMISSION_DENIED);

        mPublisherClient.mPublisherService.getPublisherId(PAYLOAD);
    }

    @Test
    public void testOnSubscriptionChange() {
        mPublisherService.onClientConnected("SomeClient", mPublisherClient.asBinder());
        mPublisherService.onClientConnected("SomeOtherClient", mPublisherClient2.asBinder());
        verify(mBrokerService, times(2)).addPublisherListener(mProxyCaptor.capture());

        mProxyCaptor.getAllValues().get(0).onSubscriptionChange(SUBSCRIPTION_STATE);

        assertEquals(SUBSCRIPTION_STATE, mPublisherClient.mSubscriptionState);
        assertNull(mPublisherClient2.mSubscriptionState);
    }

    @Test
    public void testDump_getPacketCount() throws Exception {
        mPublisherService.onClientConnected("SomeClient", mPublisherClient.asBinder());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintWriter printWriter = new PrintWriter(outputStream);

        mPublisherClient.mPublisherService.publish(mPublisherClient.mToken, LAYER, PUBLISHER_ID,
                PAYLOAD);
        mPublisherService.dump(printWriter);

        printWriter.flush();
        String dumpString = outputStream.toString();
        String expectedPacketCountString = String.format(VmsPublisherService.PACKET_COUNT_FORMAT,
                LAYER, 1L);
        String expectedPacketSizeString = String.format(VmsPublisherService.PACKET_SIZE_FORMAT,
                LAYER, PAYLOAD.length);
        assertThat(dumpString.contains(expectedPacketCountString)).isTrue();
        assertThat(dumpString.contains(expectedPacketSizeString)).isTrue();
    }

    @Test
    public void testDump_getPacketCounts() throws Exception {
        mPublisherService.onClientConnected("SomeClient", mPublisherClient.asBinder());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintWriter printWriter = new PrintWriter(outputStream);

        mPublisherClient.mPublisherService.publish(mPublisherClient.mToken, LAYER, PUBLISHER_ID,
                PAYLOAD);
        mPublisherClient.mPublisherService.publish(mPublisherClient.mToken, LAYER2, PUBLISHER_ID,
                PAYLOAD);
        mPublisherClient.mPublisherService.publish(mPublisherClient.mToken, LAYER, PUBLISHER_ID,
                PAYLOAD2);
        mPublisherClient.mPublisherService.publish(mPublisherClient.mToken, LAYER, PUBLISHER_ID,
                PAYLOAD);
        mPublisherClient.mPublisherService.publish(mPublisherClient.mToken, LAYER3, PUBLISHER_ID,
                PAYLOAD);
        mPublisherClient.mPublisherService.publish(mPublisherClient.mToken, LAYER, PUBLISHER_ID,
                PAYLOAD3);
        mPublisherClient.mPublisherService.publish(mPublisherClient.mToken, LAYER, PUBLISHER_ID,
                PAYLOAD3);
        mPublisherClient.mPublisherService.publish(mPublisherClient.mToken, LAYER2, PUBLISHER_ID,
                PAYLOAD3);
        mPublisherClient.mPublisherService.publish(mPublisherClient.mToken, LAYER, PUBLISHER_ID,
                PAYLOAD3);
        mPublisherClient.mPublisherService.publish(mPublisherClient.mToken, LAYER3, PUBLISHER_ID,
                PAYLOAD);
        mPublisherService.dump(printWriter);

        printWriter.flush();
        String dumpString = outputStream.toString();

        // LAYER called 6 times with PAYLOAD 2 times, PAYLOAD2 1 time, PAYLOAD3 3 times
        String expectedPacketCountString1 = String.format(VmsPublisherService.PACKET_COUNT_FORMAT,
                LAYER, 6L);
        String expectedPacketSizeString1 = String.format(VmsPublisherService.PACKET_SIZE_FORMAT,
                LAYER, 2 * PAYLOAD.length + PAYLOAD2.length + 3 * PAYLOAD3.length);

        // LAYER2 called 2 times with PAYLOAD 1 time, PAYLOAD2 0 time, PAYLOAD3 1 times
        String expectedPacketCountString2 = String.format(VmsPublisherService.PACKET_COUNT_FORMAT,
                LAYER2, 2L);
        String expectedPacketSizeString2 = String.format(VmsPublisherService.PACKET_SIZE_FORMAT,
                LAYER2, PAYLOAD.length + PAYLOAD3.length);

        // LAYER3 called 2 times with PAYLOAD 2 times, PAYLOAD2 0 time, PAYLOAD3 0 times
        String expectedPacketCountString3 = String.format(VmsPublisherService.PACKET_COUNT_FORMAT,
                LAYER3, 2L);
        String expectedPacketSizeString3 = String.format(VmsPublisherService.PACKET_SIZE_FORMAT,
                LAYER3, 2 * PAYLOAD.length);

        assertThat(dumpString.contains(expectedPacketCountString1)).isTrue();
        assertThat(dumpString.contains(expectedPacketSizeString1)).isTrue();
        assertThat(dumpString.contains(expectedPacketCountString2)).isTrue();
        assertThat(dumpString.contains(expectedPacketSizeString2)).isTrue();
        assertThat(dumpString.contains(expectedPacketCountString3)).isTrue();
        assertThat(dumpString.contains(expectedPacketSizeString3)).isTrue();
    }

    @Test
    public void testDumpNoListeners_getPacketFailureCount() throws Exception {
        mPublisherService.onClientConnected("SomeClient", mPublisherClient.asBinder());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintWriter printWriter = new PrintWriter(outputStream);

        // Layer 2 has no listeners and should therefore result in a packet failure to be recorded.
        mPublisherClient.mPublisherService.publish(mPublisherClient.mToken, LAYER2, PUBLISHER_ID,
                PAYLOAD);
        mPublisherService.dump(printWriter);

        printWriter.flush();
        String dumpString = outputStream.toString();

        String expectedPacketFailureString = String.format(
                VmsPublisherService.PACKET_FAILURE_COUNT_FORMAT,
                LAYER2, "SomeClient", "", 1L);
        String expectedPacketFailureSizeString = String.format(
                VmsPublisherService.PACKET_FAILURE_SIZE_FORMAT,
                LAYER2, "SomeClient", "", PAYLOAD.length);

        assertThat(dumpString.contains(expectedPacketFailureString)).isTrue();
        assertThat(dumpString.contains(expectedPacketFailureSizeString)).isTrue();
    }

    @Test
    public void testDumpNoListeners_getPacketFailureCounts() throws Exception {
        // LAYER2 and LAYER3 both have no listeners
        when(mBrokerService.getSubscribersForLayerFromPublisher(LAYER2, PUBLISHER_ID))
                .thenReturn(new HashSet<>());
        when(mBrokerService.getSubscribersForLayerFromPublisher(LAYER3, PUBLISHER_ID))
                .thenReturn(new HashSet<>());

        mPublisherService.onClientConnected("SomeClient", mPublisherClient.asBinder());
        mPublisherService.onClientConnected("SomeClient2", mPublisherClient2.asBinder());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintWriter printWriter = new PrintWriter(outputStream);

        // Layer 2 has no listeners and should therefore result in a packet failure to be recorded.
        mPublisherClient.mPublisherService.publish(mPublisherClient.mToken, LAYER2, PUBLISHER_ID,
                PAYLOAD);
        mPublisherClient2.mPublisherService.publish(mPublisherClient2.mToken, LAYER3, PUBLISHER_ID,
                PAYLOAD);

        mPublisherService.dump(printWriter);

        printWriter.flush();
        String dumpString = outputStream.toString();

        String expectedPacketFailureString = String.format(
                VmsPublisherService.PACKET_FAILURE_COUNT_FORMAT,
                LAYER2, "SomeClient", "", 1L);
        String expectedPacketFailureString2 = String.format(
                VmsPublisherService.PACKET_FAILURE_COUNT_FORMAT,
                LAYER3, "SomeClient2", "", 1L);
        String expectedPacketFailureSizeString = String.format(
                VmsPublisherService.PACKET_FAILURE_SIZE_FORMAT,
                LAYER2, "SomeClient", "", PAYLOAD.length);
        String expectedPacketFailureSizeString2 = String.format(
                VmsPublisherService.PACKET_FAILURE_SIZE_FORMAT,
                LAYER3, "SomeClient2", "", PAYLOAD.length);

        assertThat(dumpString.contains(expectedPacketFailureString)).isTrue();
        assertThat(dumpString.contains(expectedPacketFailureSizeString)).isTrue();
        assertThat(dumpString.contains(expectedPacketFailureString2)).isTrue();
        assertThat(dumpString.contains(expectedPacketFailureSizeString2)).isTrue();
    }

    @Test
    public void testDumpRemoteException_getPacketFailureCount() throws Exception {
        // The listener on LAYER3 will throw on LAYER3 and PAYLOAD
        Mockito.doThrow(new RemoteException()).when(mThrowingSubscriberClient).onVmsMessageReceived(
                LAYER3, PAYLOAD);
        when(mBrokerService.getSubscribersForLayerFromPublisher(LAYER3, PUBLISHER_ID))
                .thenReturn(new HashSet<>(Arrays.asList(mThrowingSubscriberClient)));
        when(mBrokerService.getPackageName(mThrowingSubscriberClient)).thenReturn("Thrower");

        mPublisherService.onClientConnected("SomeClient", mPublisherClient.asBinder());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintWriter printWriter = new PrintWriter(outputStream);

        // Layer 2 has no listeners and should therefore result in a packet failure to be recorded.
        mPublisherClient.mPublisherService.publish(mPublisherClient.mToken, LAYER3, PUBLISHER_ID,
                PAYLOAD);

        mPublisherService.dump(printWriter);

        printWriter.flush();
        String dumpString = outputStream.toString();

        String expectedPacketFailureString = String.format(
                VmsPublisherService.PACKET_FAILURE_COUNT_FORMAT,
                LAYER3, "SomeClient", "Thrower", 1L);
        String expectedPacketFailureSizeString = String.format(
                VmsPublisherService.PACKET_FAILURE_SIZE_FORMAT,
                LAYER3, "SomeClient", "Thrower", PAYLOAD.length);

        assertThat(dumpString.contains(expectedPacketFailureString)).isTrue();
        assertThat(dumpString.contains(expectedPacketFailureSizeString)).isTrue();
    }

    @Test
    public void testDumpRemoteException_getPacketFailureCounts() throws Exception {
        // The listeners will throw on LAYER3 or LAYER4 and PAYLOAD
        Mockito.doThrow(new RemoteException()).when(mThrowingSubscriberClient).onVmsMessageReceived(
                LAYER3, PAYLOAD);
        Mockito.doThrow(new RemoteException()).when(mThrowingSubscriberClient).onVmsMessageReceived(
                LAYER4, PAYLOAD);
        Mockito.doThrow(new RemoteException()).when(
                mThrowingSubscriberClient2).onVmsMessageReceived(LAYER3, PAYLOAD);
        Mockito.doThrow(new RemoteException()).when(
                mThrowingSubscriberClient2).onVmsMessageReceived(LAYER4, PAYLOAD);

        when(mBrokerService.getSubscribersForLayerFromPublisher(LAYER3, PUBLISHER_ID))
                .thenReturn(new HashSet<>(
                        Arrays.asList(mThrowingSubscriberClient, mThrowingSubscriberClient2)));
        when(mBrokerService.getSubscribersForLayerFromPublisher(LAYER4, PUBLISHER_ID))
                .thenReturn(new HashSet<>(
                        Arrays.asList(mThrowingSubscriberClient, mThrowingSubscriberClient2)));

        when(mBrokerService.getPackageName(mThrowingSubscriberClient)).thenReturn("Thrower");
        when(mBrokerService.getPackageName(mThrowingSubscriberClient2)).thenReturn("Thrower2");

        mPublisherService.onClientConnected("SomeClient", mPublisherClient.asBinder());
        mPublisherService.onClientConnected("SomeClient2", mPublisherClient2.asBinder());

        // Layer 2 has no listeners and should therefore result in a packet failure to be recorded.
        mPublisherClient.mPublisherService.publish(mPublisherClient.mToken, LAYER3, PUBLISHER_ID,
                PAYLOAD);
        mPublisherClient.mPublisherService.publish(mPublisherClient.mToken, LAYER3, PUBLISHER_ID,
                PAYLOAD);
        mPublisherClient.mPublisherService.publish(mPublisherClient.mToken, LAYER4, PUBLISHER_ID,
                PAYLOAD);
        mPublisherClient.mPublisherService.publish(mPublisherClient.mToken, LAYER4, PUBLISHER_ID,
                PAYLOAD);

        mPublisherClient2.mPublisherService.publish(mPublisherClient2.mToken, LAYER3, PUBLISHER_ID,
                PAYLOAD);
        mPublisherClient2.mPublisherService.publish(mPublisherClient2.mToken, LAYER3, PUBLISHER_ID,
                PAYLOAD);
        mPublisherClient2.mPublisherService.publish(mPublisherClient2.mToken, LAYER4, PUBLISHER_ID,
                PAYLOAD);
        mPublisherClient2.mPublisherService.publish(mPublisherClient2.mToken, LAYER4, PUBLISHER_ID,
                PAYLOAD);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintWriter printWriter = new PrintWriter(outputStream);
        mPublisherService.dump(printWriter);

        printWriter.flush();
        String dumpString = outputStream.toString();

        List<String> expectedStrings = Arrays.asList(
                String.format(VmsPublisherService.PACKET_FAILURE_COUNT_FORMAT, LAYER3, "SomeClient",
                        "Thrower", 2L),
                String.format(VmsPublisherService.PACKET_FAILURE_COUNT_FORMAT, LAYER3, "SomeClient",
                        "Thrower2", 2L),
                String.format(VmsPublisherService.PACKET_FAILURE_COUNT_FORMAT, LAYER4, "SomeClient",
                        "Thrower", 2L),
                String.format(VmsPublisherService.PACKET_FAILURE_COUNT_FORMAT, LAYER4, "SomeClient",
                        "Thrower2", 2L),
                String.format(VmsPublisherService.PACKET_FAILURE_COUNT_FORMAT, LAYER3,
                        "SomeClient2",
                        "Thrower", 2L),
                String.format(VmsPublisherService.PACKET_FAILURE_COUNT_FORMAT, LAYER3,
                        "SomeClient2",
                        "Thrower2", 2L),
                String.format(VmsPublisherService.PACKET_FAILURE_COUNT_FORMAT, LAYER4,
                        "SomeClient2",
                        "Thrower", 2L),
                String.format(VmsPublisherService.PACKET_FAILURE_COUNT_FORMAT, LAYER4,
                        "SomeClient2",
                        "Thrower2", 2L),

                String.format(VmsPublisherService.PACKET_FAILURE_SIZE_FORMAT, LAYER3, "SomeClient",
                        "Thrower", 2 * PAYLOAD.length),
                String.format(VmsPublisherService.PACKET_FAILURE_SIZE_FORMAT, LAYER3, "SomeClient",
                        "Thrower2", 2 * PAYLOAD.length),
                String.format(VmsPublisherService.PACKET_FAILURE_SIZE_FORMAT, LAYER4, "SomeClient",
                        "Thrower", 2 * PAYLOAD.length),
                String.format(VmsPublisherService.PACKET_FAILURE_SIZE_FORMAT, LAYER4, "SomeClient",
                        "Thrower2", 2 * PAYLOAD.length),
                String.format(VmsPublisherService.PACKET_FAILURE_SIZE_FORMAT, LAYER3, "SomeClient2",
                        "Thrower", 2 * PAYLOAD.length),
                String.format(VmsPublisherService.PACKET_FAILURE_SIZE_FORMAT, LAYER3, "SomeClient2",
                        "Thrower2", 2 * PAYLOAD.length),
                String.format(VmsPublisherService.PACKET_FAILURE_SIZE_FORMAT, LAYER4, "SomeClient2",
                        "Thrower", 2 * PAYLOAD.length),
                String.format(VmsPublisherService.PACKET_FAILURE_SIZE_FORMAT, LAYER4, "SomeClient2",
                        "Thrower2", 2 * PAYLOAD.length));

        for (String expected : expectedStrings) {
            assertThat(dumpString.contains(expected)).isTrue();
        }
    }

    @Test
    public void testDump_getAllMetrics() throws Exception {

        // LAYER3 has no subscribers
        when(mBrokerService.getSubscribersForLayerFromPublisher(LAYER3, PUBLISHER_ID))
                .thenReturn(new HashSet<>(Arrays.asList()));

        // LAYER4 has a subscriber that will always throw
        Mockito.doThrow(new RemoteException()).when(mThrowingSubscriberClient).onVmsMessageReceived(
                LAYER4, PAYLOAD);

        when(mBrokerService.getSubscribersForLayerFromPublisher(LAYER4, PUBLISHER_ID))
                .thenReturn(new HashSet<>(
                        Arrays.asList(mThrowingSubscriberClient)));

        when(mBrokerService.getPackageName(mThrowingSubscriberClient)).thenReturn("Thrower");

        mPublisherService.onClientConnected("SomeClient", mPublisherClient.asBinder());
        mPublisherClient.mPublisherService.publish(mPublisherClient.mToken, LAYER, PUBLISHER_ID,
                PAYLOAD);
        mPublisherClient.mPublisherService.publish(mPublisherClient.mToken, LAYER, PUBLISHER_ID,
                PAYLOAD2);
        mPublisherClient.mPublisherService.publish(mPublisherClient.mToken, LAYER3, PUBLISHER_ID,
                PAYLOAD3);
        mPublisherClient.mPublisherService.publish(mPublisherClient.mToken, LAYER4, PUBLISHER_ID,
                PAYLOAD);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintWriter printWriter = new PrintWriter(outputStream);
        mPublisherService.dump(printWriter);

        printWriter.flush();
        String dumpString = outputStream.toString();

        List<String> expectedStrings = Arrays.asList(
                String.format(VmsPublisherService.PACKET_COUNT_FORMAT, LAYER, 2),
                String.format(VmsPublisherService.PACKET_COUNT_FORMAT, LAYER3, 1),
                String.format(VmsPublisherService.PACKET_COUNT_FORMAT, LAYER4, 1),
                String.format(VmsPublisherService.PACKET_SIZE_FORMAT, LAYER,
                        PAYLOAD.length + PAYLOAD2.length),
                String.format(VmsPublisherService.PACKET_SIZE_FORMAT, LAYER3, PAYLOAD3.length),
                String.format(VmsPublisherService.PACKET_SIZE_FORMAT, LAYER4, PAYLOAD.length),
                String.format(VmsPublisherService.PACKET_FAILURE_COUNT_FORMAT, LAYER3, "SomeClient",
                        "",
                        1),
                String.format(VmsPublisherService.PACKET_FAILURE_SIZE_FORMAT, LAYER3, "SomeClient",
                        "",
                        PAYLOAD3.length),
                String.format(VmsPublisherService.PACKET_FAILURE_COUNT_FORMAT, LAYER4, "SomeClient",
                        "Thrower", 1),
                String.format(VmsPublisherService.PACKET_FAILURE_SIZE_FORMAT, LAYER4, "SomeClient",
                        "Thrower", PAYLOAD.length)
        );

        for (String expected : expectedStrings) {
            assertThat(dumpString.contains(expected)).isTrue();
        }
    }


    @Test
    public void testRelease() {
        mPublisherService.release();
    }

    private class MockPublisherClient extends IVmsPublisherClient.Stub {
        private IBinder mToken;
        private IVmsPublisherService mPublisherService;
        private VmsSubscriptionState mSubscriptionState;

        @Override
        public void setVmsPublisherService(IBinder token, IVmsPublisherService service) {
            assertNotNull(token);
            assertNotNull(service);
            if (mToken != null) {
                throw new IllegalArgumentException("Publisher service set multiple times");
            }
            this.mToken = token;
            this.mPublisherService = service;
        }

        @Override
        public void onVmsSubscriptionChange(VmsSubscriptionState subscriptionState) {
            assertNotNull(subscriptionState);
            this.mSubscriptionState = subscriptionState;
        }
    }
}
