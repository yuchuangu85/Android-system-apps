/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.car.vms.VmsAssociatedLayer;
import android.car.vms.VmsAvailableLayers;
import android.car.vms.VmsLayer;
import android.car.vms.VmsLayerDependency;
import android.car.vms.VmsLayersOffering;
import android.car.vms.VmsSubscriberManager;
import android.car.vms.VmsSubscriptionState;
import android.util.Pair;

import androidx.test.filters.MediumTest;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

@MediumTest
public class VmsPublisherSubscriberTest extends MockedVmsTestBase {
    private static final VmsLayer SUBSCRIPTION_LAYER = new VmsLayer(1, 1, 1);
    private static final VmsLayer SUBSCRIPTION_LAYER_OTHER = new VmsLayer(2, 1, 1);

    private static final byte[] PAYLOAD = {0xa, 0xb};
    private static final byte[] PAYLOAD_OTHER = {0xb, 0xc};

    private static final byte[] PUBLISHER_INFO = {0x0};
    private static final byte[] PUBLISHER_INFO_OTHER = {0x1};

    private static final int UNKNOWN_PUBLISHER = 99999;

    private MockPublisherClient mPublisher;
    private VmsSubscriberManager mSubscriber;
    private MockSubscriberClient mSubscriberClient;

    @Before
    public void setUpClients() {
        mPublisher = getMockPublisherClient();
        mSubscriber = getSubscriberManager();
        mSubscriberClient = getMockSubscriberClient();
    }

    @Test
    public void testPublisherInfo_Unregistered() {
        assertEquals(0, mSubscriber.getPublisherInfo(UNKNOWN_PUBLISHER).length);
    }

    @Test
    public void testPublisherInfo_Registered() {
        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);
        assertArrayEquals(PUBLISHER_INFO, mSubscriber.getPublisherInfo(publisherId));
    }

    @Test
    public void testPublisherId_AlreadyRegistered() {
        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);
        int publisherId2 = mPublisher.getPublisherId(PUBLISHER_INFO);
        assertEquals(publisherId, publisherId2);
    }

    @Test
    public void testPublisherId_MultiplePublishers() {
        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);
        int publisherId2 = mPublisher.getPublisherId(PUBLISHER_INFO_OTHER);
        assertNotEquals(publisherId, publisherId2);
        assertArrayEquals(PUBLISHER_INFO, mSubscriber.getPublisherInfo(publisherId));
        assertArrayEquals(PUBLISHER_INFO_OTHER, mSubscriber.getPublisherInfo(publisherId2));
    }

    @Test
    public void testLayerAvailability_Default() {
        VmsAvailableLayers availableLayers = mSubscriber.getAvailableLayers();
        assertEquals(Collections.emptySet(), availableLayers.getAssociatedLayers());
        assertEquals(0, availableLayers.getSequence());
    }

    @Test
    public void testLayerAvailability() {
        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);
        mPublisher.setLayersOffering(new VmsLayersOffering(Collections.singleton(
                new VmsLayerDependency(SUBSCRIPTION_LAYER, Collections.emptySet())), publisherId));

        assertLayerAvailability(1,
                new VmsAssociatedLayer(SUBSCRIPTION_LAYER, Collections.singleton(publisherId)));
    }

    @Test
    public void testLayerAvailability_Overwrite() {
        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);
        mPublisher.setLayersOffering(new VmsLayersOffering(Collections.singleton(
                new VmsLayerDependency(SUBSCRIPTION_LAYER, Collections.emptySet())), publisherId));
        mPublisher.setLayersOffering(new VmsLayersOffering(Collections.singleton(
                new VmsLayerDependency(SUBSCRIPTION_LAYER_OTHER, Collections.emptySet())),
                publisherId));

        assertLayerAvailability(2,
                new VmsAssociatedLayer(SUBSCRIPTION_LAYER_OTHER,
                        Collections.singleton(publisherId)));
    }

    @Test
    public void testLayerAvailability_MultiplePublishers_SameLayer() {
        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);
        int publisherId2 = mPublisher.getPublisherId(PUBLISHER_INFO_OTHER);
        mPublisher.setLayersOffering(new VmsLayersOffering(Collections.singleton(
                new VmsLayerDependency(SUBSCRIPTION_LAYER, Collections.emptySet())), publisherId));
        mPublisher.setLayersOffering(new VmsLayersOffering(Collections.singleton(
                new VmsLayerDependency(SUBSCRIPTION_LAYER, Collections.emptySet())), publisherId2));

        assertLayerAvailability(2,
                new VmsAssociatedLayer(SUBSCRIPTION_LAYER,
                        new HashSet<>(Arrays.asList(publisherId, publisherId2))));
    }

    @Test
    public void testLayerAvailability_MultiplePublishers_MultipleLayers() {
        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);
        int publisherId2 = mPublisher.getPublisherId(PUBLISHER_INFO_OTHER);
        mPublisher.setLayersOffering(new VmsLayersOffering(Collections.singleton(
                new VmsLayerDependency(SUBSCRIPTION_LAYER, Collections.emptySet())), publisherId));
        mPublisher.setLayersOffering(new VmsLayersOffering(Collections.singleton(
                new VmsLayerDependency(SUBSCRIPTION_LAYER_OTHER, Collections.emptySet())),
                publisherId2));

        assertLayerAvailability(2,
                new VmsAssociatedLayer(SUBSCRIPTION_LAYER, Collections.singleton(publisherId)),
                new VmsAssociatedLayer(SUBSCRIPTION_LAYER_OTHER,
                        Collections.singleton(publisherId2)));
    }

    @Test
    public void testLayerAvailability_MultiplePublishers_Remove() {
        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);
        int publisherId2 = mPublisher.getPublisherId(PUBLISHER_INFO_OTHER);
        mPublisher.setLayersOffering(new VmsLayersOffering(Collections.singleton(
                new VmsLayerDependency(SUBSCRIPTION_LAYER, Collections.emptySet())), publisherId));
        mPublisher.setLayersOffering(new VmsLayersOffering(Collections.singleton(
                new VmsLayerDependency(SUBSCRIPTION_LAYER, Collections.emptySet())), publisherId2));

        mPublisher.setLayersOffering(new VmsLayersOffering(Collections.emptySet(), publisherId2));

        assertLayerAvailability(3,
                new VmsAssociatedLayer(SUBSCRIPTION_LAYER, Collections.singleton(publisherId)));
    }

    @Test
    public void testLayerAvailability_MultiplePublishers_Overwrite() {
        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);
        int publisherId2 = mPublisher.getPublisherId(PUBLISHER_INFO_OTHER);
        mPublisher.setLayersOffering(new VmsLayersOffering(Collections.singleton(
                new VmsLayerDependency(SUBSCRIPTION_LAYER, Collections.emptySet())), publisherId));
        mPublisher.setLayersOffering(new VmsLayersOffering(Collections.singleton(
                new VmsLayerDependency(SUBSCRIPTION_LAYER, Collections.emptySet())), publisherId2));

        mPublisher.setLayersOffering(new VmsLayersOffering(Collections.singleton(
                new VmsLayerDependency(SUBSCRIPTION_LAYER_OTHER, Collections.emptySet())),
                publisherId2));

        assertLayerAvailability(3,
                new VmsAssociatedLayer(SUBSCRIPTION_LAYER, Collections.singleton(publisherId)),
                new VmsAssociatedLayer(SUBSCRIPTION_LAYER_OTHER,
                        Collections.singleton(publisherId2)));
    }

    @Test
    public void testStartMonitoring() {
        mSubscriber.startMonitoring();
        assertNull(mPublisher.receiveSubscriptionState());

        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);
        mPublisher.publish(SUBSCRIPTION_LAYER, publisherId, PAYLOAD);
        assertDataMessage(SUBSCRIPTION_LAYER, PAYLOAD);
    }

    @Test
    public void testStartMonitoring_AfterPublish() {
        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);
        mPublisher.publish(SUBSCRIPTION_LAYER, publisherId, PAYLOAD);

        mSubscriber.startMonitoring();
        assertNull(mSubscriberClient.receiveMessage());
    }

    @Test
    public void testStartMonitoring_MultipleLayers() {
        mSubscriber.startMonitoring();

        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);
        mPublisher.publish(SUBSCRIPTION_LAYER, publisherId, PAYLOAD);
        assertDataMessage(SUBSCRIPTION_LAYER, PAYLOAD);

        mPublisher.publish(SUBSCRIPTION_LAYER_OTHER, publisherId, PAYLOAD_OTHER);
        assertDataMessage(SUBSCRIPTION_LAYER_OTHER, PAYLOAD_OTHER);
    }

    @Test
    public void testStartMonitoring_MultiplePublishers() {
        mSubscriber.startMonitoring();

        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);
        mPublisher.publish(SUBSCRIPTION_LAYER, publisherId, PAYLOAD);
        assertDataMessage(SUBSCRIPTION_LAYER, PAYLOAD);

        int publisherId2 = mPublisher.getPublisherId(PUBLISHER_INFO_OTHER);
        mPublisher.publish(SUBSCRIPTION_LAYER, publisherId2, PAYLOAD_OTHER);
        assertDataMessage(SUBSCRIPTION_LAYER, PAYLOAD_OTHER);
    }

    @Test
    public void testStopMonitoring() {
        mSubscriber.startMonitoring();
        mSubscriber.stopMonitoring();
        assertNull(mPublisher.receiveSubscriptionState());

        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);
        mPublisher.publish(SUBSCRIPTION_LAYER, publisherId, PAYLOAD);
        assertNull(mSubscriberClient.receiveMessage());
    }

    @Test
    public void testSubscribe() {
        mSubscriber.subscribe(SUBSCRIPTION_LAYER);
        assertSubscriptionState(1, SUBSCRIPTION_LAYER);

        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);
        mPublisher.publish(SUBSCRIPTION_LAYER, publisherId, PAYLOAD);
        assertDataMessage(SUBSCRIPTION_LAYER, PAYLOAD);
    }

    @Test
    public void testSubscribe_AfterPublish() {
        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);

        mPublisher.publish(SUBSCRIPTION_LAYER, publisherId, PAYLOAD);
        mSubscriber.subscribe(SUBSCRIPTION_LAYER);

        assertSubscriptionState(1, SUBSCRIPTION_LAYER);
        assertNull(mSubscriberClient.receiveMessage());
    }

    @Test
    public void testSubscribe_MultipleLayers() {
        mSubscriber.subscribe(SUBSCRIPTION_LAYER);
        mSubscriber.subscribe(SUBSCRIPTION_LAYER_OTHER);
        assertSubscriptionState(2, SUBSCRIPTION_LAYER, SUBSCRIPTION_LAYER_OTHER);

        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);
        mPublisher.publish(SUBSCRIPTION_LAYER, publisherId, PAYLOAD);
        assertDataMessage(SUBSCRIPTION_LAYER, PAYLOAD);

        mPublisher.publish(SUBSCRIPTION_LAYER_OTHER, publisherId, PAYLOAD_OTHER);
        assertDataMessage(SUBSCRIPTION_LAYER_OTHER, PAYLOAD_OTHER);
    }

    @Test
    public void testSubscribe_MultiplePublishers() {
        mSubscriber.subscribe(SUBSCRIPTION_LAYER);
        assertSubscriptionState(1, SUBSCRIPTION_LAYER);

        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);
        mPublisher.publish(SUBSCRIPTION_LAYER, publisherId, PAYLOAD);
        assertDataMessage(SUBSCRIPTION_LAYER, PAYLOAD);

        int publisherId2 = mPublisher.getPublisherId(PUBLISHER_INFO_OTHER);
        mPublisher.publish(SUBSCRIPTION_LAYER, publisherId2, PAYLOAD_OTHER);
        assertDataMessage(SUBSCRIPTION_LAYER, PAYLOAD_OTHER);
    }

    @Test
    public void testSubscribe_MultipleLayers_MultiplePublishers() {
        mSubscriber.subscribe(SUBSCRIPTION_LAYER);
        mSubscriber.subscribe(SUBSCRIPTION_LAYER_OTHER);
        assertSubscriptionState(2, SUBSCRIPTION_LAYER, SUBSCRIPTION_LAYER_OTHER);

        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);
        mPublisher.publish(SUBSCRIPTION_LAYER, publisherId, PAYLOAD);
        assertDataMessage(SUBSCRIPTION_LAYER, PAYLOAD);

        int publisherId2 = mPublisher.getPublisherId(PUBLISHER_INFO_OTHER);
        mPublisher.publish(SUBSCRIPTION_LAYER_OTHER, publisherId2, PAYLOAD_OTHER);
        assertDataMessage(SUBSCRIPTION_LAYER_OTHER, PAYLOAD_OTHER);
    }

    @Test
    public void testSubscribe_ClearCallback() {
        mSubscriber.subscribe(SUBSCRIPTION_LAYER);
        mSubscriber.clearVmsSubscriberClientCallback();

        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);
        mPublisher.publish(SUBSCRIPTION_LAYER, publisherId, PAYLOAD);
        assertNull(mSubscriberClient.receiveMessage());
    }

    @Test(expected = IllegalStateException.class)
    public void testSubscribe_NoCallback() {
        mSubscriber.clearVmsSubscriberClientCallback();
        mSubscriber.subscribe(SUBSCRIPTION_LAYER);
    }

    @Test
    public void testUnsubscribe() {
        mSubscriber.subscribe(SUBSCRIPTION_LAYER);
        mSubscriber.unsubscribe(SUBSCRIPTION_LAYER);
        assertSubscriptionState(2, Collections.emptySet(), Collections.emptySet());

        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);
        mPublisher.publish(SUBSCRIPTION_LAYER, publisherId, PAYLOAD);
        assertNull(mSubscriberClient.receiveMessage());
    }

    @Test
    public void testUnsubscribe_MultipleLayers() {
        mSubscriber.subscribe(SUBSCRIPTION_LAYER);
        mSubscriber.subscribe(SUBSCRIPTION_LAYER_OTHER);
        mSubscriber.unsubscribe(SUBSCRIPTION_LAYER);
        assertSubscriptionState(3, SUBSCRIPTION_LAYER_OTHER);

        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);
        mPublisher.publish(SUBSCRIPTION_LAYER, publisherId, PAYLOAD);
        assertNull(mSubscriberClient.receiveMessage());

        mPublisher.publish(SUBSCRIPTION_LAYER_OTHER, publisherId, PAYLOAD_OTHER);
        assertDataMessage(SUBSCRIPTION_LAYER_OTHER, PAYLOAD_OTHER);
    }

    @Test
    public void testUnsubscribe_MultiplePublishers() {
        mSubscriber.subscribe(SUBSCRIPTION_LAYER);
        mSubscriber.subscribe(SUBSCRIPTION_LAYER_OTHER);
        mSubscriber.unsubscribe(SUBSCRIPTION_LAYER);
        assertSubscriptionState(3, SUBSCRIPTION_LAYER_OTHER);

        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);
        mPublisher.publish(SUBSCRIPTION_LAYER, publisherId, PAYLOAD);
        assertNull(mSubscriberClient.receiveMessage());

        int publisherId2 = mPublisher.getPublisherId(PUBLISHER_INFO_OTHER);
        mPublisher.publish(SUBSCRIPTION_LAYER, publisherId2, PAYLOAD_OTHER);
        assertNull(mSubscriberClient.receiveMessage());
    }

    @Test
    public void testUnsubscribe_NotSubscribed() {
        mSubscriber.unsubscribe(SUBSCRIPTION_LAYER);
        assertNull(mPublisher.receiveSubscriptionState());

        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);
        mPublisher.publish(SUBSCRIPTION_LAYER, publisherId, PAYLOAD);
        assertNull(mSubscriberClient.receiveMessage());
    }

    @Test
    public void testSubscribeToPublisher() {
        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);

        mSubscriber.subscribe(SUBSCRIPTION_LAYER, publisherId);
        assertSubscriptionState(1,
                new VmsAssociatedLayer(SUBSCRIPTION_LAYER, Collections.singleton(publisherId)));

        mPublisher.publish(SUBSCRIPTION_LAYER, publisherId, PAYLOAD);
        assertDataMessage(SUBSCRIPTION_LAYER, PAYLOAD);
    }

    @Test
    public void testSubscribeToPublisher_AfterPublish() {
        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);

        mPublisher.publish(SUBSCRIPTION_LAYER, publisherId, PAYLOAD);
        mSubscriber.subscribe(SUBSCRIPTION_LAYER, publisherId);

        assertSubscriptionState(1,
                new VmsAssociatedLayer(SUBSCRIPTION_LAYER, Collections.singleton(publisherId)));

        assertNull(mSubscriberClient.receiveMessage());
    }

    @Test
    public void testSubscribeToPublisher_MultipleLayers() {
        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);

        mSubscriber.subscribe(SUBSCRIPTION_LAYER, publisherId);
        mSubscriber.subscribe(SUBSCRIPTION_LAYER_OTHER, publisherId);

        assertSubscriptionState(2,
                new VmsAssociatedLayer(SUBSCRIPTION_LAYER, Collections.singleton(publisherId)),
                new VmsAssociatedLayer(SUBSCRIPTION_LAYER_OTHER,
                        Collections.singleton(publisherId)));

        mPublisher.publish(SUBSCRIPTION_LAYER, publisherId, PAYLOAD);
        assertDataMessage(SUBSCRIPTION_LAYER, PAYLOAD);

        mPublisher.publish(SUBSCRIPTION_LAYER_OTHER, publisherId, PAYLOAD_OTHER);
        assertDataMessage(SUBSCRIPTION_LAYER_OTHER, PAYLOAD_OTHER);
    }

    @Test
    public void testSubscribeToPublisher_MultiplePublishers() {
        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);
        int publisherId2 = mPublisher.getPublisherId(PUBLISHER_INFO_OTHER);

        mSubscriber.subscribe(SUBSCRIPTION_LAYER, publisherId);
        assertSubscriptionState(1,
                new VmsAssociatedLayer(SUBSCRIPTION_LAYER, Collections.singleton(publisherId)));

        mPublisher.publish(SUBSCRIPTION_LAYER, publisherId, PAYLOAD);
        assertDataMessage(SUBSCRIPTION_LAYER, PAYLOAD);

        mPublisher.publish(SUBSCRIPTION_LAYER, publisherId2, PAYLOAD_OTHER);
        assertNull(mSubscriberClient.receiveMessage());
    }

    @Test
    public void testSubscribeToPublisher_MultipleLayers_MultiplePublishers() {
        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);
        int publisherId2 = mPublisher.getPublisherId(PUBLISHER_INFO_OTHER);

        mSubscriber.subscribe(SUBSCRIPTION_LAYER, publisherId);
        mSubscriber.subscribe(SUBSCRIPTION_LAYER_OTHER, publisherId2);
        assertSubscriptionState(2,
                new VmsAssociatedLayer(SUBSCRIPTION_LAYER, Collections.singleton(publisherId)),
                new VmsAssociatedLayer(SUBSCRIPTION_LAYER_OTHER,
                        Collections.singleton(publisherId2)));

        mPublisher.publish(SUBSCRIPTION_LAYER, publisherId, PAYLOAD);
        assertDataMessage(SUBSCRIPTION_LAYER, PAYLOAD);

        mPublisher.publish(SUBSCRIPTION_LAYER_OTHER, publisherId2, PAYLOAD_OTHER);
        assertDataMessage(SUBSCRIPTION_LAYER_OTHER, PAYLOAD_OTHER);
    }

    @Test
    public void testSubscribeToPublisher_UnknownPublisher() {
        mSubscriber.subscribe(SUBSCRIPTION_LAYER, UNKNOWN_PUBLISHER);

        assertSubscriptionState(1,
                new VmsAssociatedLayer(SUBSCRIPTION_LAYER,
                        Collections.singleton(UNKNOWN_PUBLISHER)));
    }

    @Test
    public void testSubscribeToPublisher_ClearCallback() {
        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);

        mSubscriber.subscribe(SUBSCRIPTION_LAYER, publisherId);
        mSubscriber.clearVmsSubscriberClientCallback();

        mPublisher.publish(SUBSCRIPTION_LAYER, publisherId, PAYLOAD);
        assertNull(mSubscriberClient.receiveMessage());
    }

    @Test(expected = IllegalStateException.class)
    public void testSubscribeToPublisher_NoCallback() {
        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);

        mSubscriber.clearVmsSubscriberClientCallback();
        mSubscriber.subscribe(SUBSCRIPTION_LAYER, publisherId);
    }

    @Test
    public void testUnsubscribeToPublisher() {
        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);

        mSubscriber.subscribe(SUBSCRIPTION_LAYER, publisherId);
        mSubscriber.unsubscribe(SUBSCRIPTION_LAYER, publisherId);
        assertSubscriptionState(2, Collections.emptySet(), Collections.emptySet());

        mPublisher.publish(SUBSCRIPTION_LAYER, publisherId, PAYLOAD);
        assertNull(mSubscriberClient.receiveMessage());
    }

    @Test
    public void testUnsubscribeToPublisher_MultipleLayers() {
        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);

        mSubscriber.subscribe(SUBSCRIPTION_LAYER, publisherId);
        mSubscriber.subscribe(SUBSCRIPTION_LAYER_OTHER, publisherId);
        mSubscriber.unsubscribe(SUBSCRIPTION_LAYER, publisherId);
        assertSubscriptionState(3,
                new VmsAssociatedLayer(SUBSCRIPTION_LAYER_OTHER,
                        Collections.singleton(publisherId)));

        mPublisher.publish(SUBSCRIPTION_LAYER, publisherId, PAYLOAD);
        assertNull(mSubscriberClient.receiveMessage());

        mPublisher.publish(SUBSCRIPTION_LAYER_OTHER, publisherId, PAYLOAD_OTHER);
        assertDataMessage(SUBSCRIPTION_LAYER_OTHER, PAYLOAD_OTHER);
    }

    @Test
    public void testUnsubscribeToPublisher_MultiplePublishers() {
        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);
        int publisherId2 = mPublisher.getPublisherId(PUBLISHER_INFO_OTHER);

        mSubscriber.subscribe(SUBSCRIPTION_LAYER, publisherId);
        mSubscriber.subscribe(SUBSCRIPTION_LAYER, publisherId2);
        mSubscriber.unsubscribe(SUBSCRIPTION_LAYER, publisherId);
        assertSubscriptionState(3,
                new VmsAssociatedLayer(SUBSCRIPTION_LAYER, Collections.singleton(publisherId2)));

        mPublisher.publish(SUBSCRIPTION_LAYER, publisherId, PAYLOAD);
        assertNull(mSubscriberClient.receiveMessage());

        mPublisher.publish(SUBSCRIPTION_LAYER, publisherId2, PAYLOAD_OTHER);
        assertDataMessage(SUBSCRIPTION_LAYER, PAYLOAD_OTHER);
    }

    @Test
    public void testUnsubscribeToPublisher_NotSubscribed() {
        int publisherId = mPublisher.getPublisherId(PUBLISHER_INFO);

        mSubscriber.unsubscribe(SUBSCRIPTION_LAYER, publisherId);
        assertNull(mPublisher.receiveSubscriptionState());

        mPublisher.publish(SUBSCRIPTION_LAYER, publisherId, PAYLOAD);
        assertNull(mSubscriberClient.receiveMessage());
    }

    @Test
    public void testUnsubscribeToPublisher_UnknownPublisher() {
        mSubscriber.unsubscribe(SUBSCRIPTION_LAYER, UNKNOWN_PUBLISHER);
        assertNull(mPublisher.receiveSubscriptionState());
    }

    private void assertLayerAvailability(int sequenceNumber,
            VmsAssociatedLayer... associatedLayers) {
        VmsAvailableLayers availableLayers = receiveWithSequence(
                getMockSubscriberClient()::receiveLayerAvailability,
                VmsAvailableLayers::getSequence,
                sequenceNumber);
        assertEquals(availableLayers, mSubscriber.getAvailableLayers());
        assertEquals(new HashSet<>(Arrays.asList(associatedLayers)),
                availableLayers.getAssociatedLayers());
    }

    private void assertSubscriptionState(int sequenceNumber, VmsLayer... layers) {
        assertSubscriptionState(sequenceNumber, new HashSet<>(Arrays.asList(layers)),
                Collections.emptySet());
    }

    private void assertSubscriptionState(int sequenceNumber,
            VmsAssociatedLayer... associatedLayers) {
        assertSubscriptionState(sequenceNumber, Collections.emptySet(),
                new HashSet<>(Arrays.asList(associatedLayers)));
    }

    private void assertSubscriptionState(int sequenceNumber, Set<VmsLayer> layers,
            Set<VmsAssociatedLayer> associatedLayers) {
        VmsSubscriptionState subscriptionState = receiveWithSequence(
                mPublisher::receiveSubscriptionState,
                VmsSubscriptionState::getSequenceNumber,
                sequenceNumber);
        assertEquals(layers, subscriptionState.getLayers());
        assertEquals(associatedLayers, subscriptionState.getAssociatedLayers());
    }

    private static <T> T receiveWithSequence(Supplier<T> supplierFunction,
            ToIntFunction<T> sequenceNumberFn, int sequenceNumber) {
        T obj = null;
        for (int seq = 1; seq <= sequenceNumber; seq++) {
            obj = supplierFunction.get();
            assertNotNull(obj);
            assertEquals(seq, sequenceNumberFn.applyAsInt(obj));
        }
        return obj;
    }

    private void assertDataMessage(VmsLayer layer, byte[] payload) {
        Pair<VmsLayer, byte[]> message = mSubscriberClient.receiveMessage();
        assertEquals(layer, message.first);
        assertArrayEquals(payload, message.second);
    }
}
