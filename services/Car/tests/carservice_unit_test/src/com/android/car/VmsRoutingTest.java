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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.car.vms.IVmsSubscriberClient;
import android.car.vms.VmsAssociatedLayer;
import android.car.vms.VmsAvailableLayers;
import android.car.vms.VmsLayer;
import android.car.vms.VmsSubscriptionState;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

@SmallTest
public class VmsRoutingTest {
    private static final VmsLayer LAYER_1 = new VmsLayer(1, 1, 2);
    private static final VmsLayer LAYER_2 = new VmsLayer(1, 3, 3);
    private static final VmsLayer[] LAYERS = {LAYER_1, LAYER_2};
    private static final int PUBLISHER_ID_1 = 123;
    private static final int PUBLISHER_ID_2 = 456;
    private static final int[] PUBLISHER_IDS = {PUBLISHER_ID_1, PUBLISHER_ID_2};

    private VmsRouting mRouting;
    private IVmsSubscriberClient mSubscriber;
    private IVmsSubscriberClient mSubscriberRewrapped;
    private IVmsSubscriberClient mSubscriber2;

    @Before
    public void setUp() throws Exception {
        mRouting = new VmsRouting();
        mSubscriber = new MockVmsSubscriber();
        mSubscriberRewrapped = IVmsSubscriberClient.Stub.asInterface(mSubscriber.asBinder());
        mSubscriber2 = new MockVmsSubscriber();
    }

    @Test
    public void testDefaultSubscriptionState() {
        assertNoSubscribers();

        assertEquals(
                new VmsSubscriptionState(0, Collections.emptySet(), Collections.emptySet()),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testAddPassiveSubscriber() {
        mRouting.addSubscription(mSubscriber);

        // Receives messages for all layers and publishers
        assertSubscribers(LAYER_1, mSubscriber);
        assertSubscribers(LAYER_2, mSubscriber);

        assertEquals(
                new VmsSubscriptionState(0, Collections.emptySet(), Collections.emptySet()),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testAddPassiveSubscriber_MultipleTimes() {
        mRouting.addSubscription(mSubscriber);
        mRouting.addSubscription(mSubscriber);

        assertSubscribers(LAYER_1, mSubscriber);
        assertSubscribers(LAYER_2, mSubscriber);

        assertEquals(
                new VmsSubscriptionState(0, Collections.emptySet(), Collections.emptySet()),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testAddPassiveSubscriber_MultipleTimes_Rewrapped() {
        mRouting.addSubscription(mSubscriber);
        mRouting.addSubscription(mSubscriberRewrapped);

        assertSubscribers(LAYER_1, mSubscriber);
        assertSubscribers(LAYER_2, mSubscriber);

        assertEquals(
                new VmsSubscriptionState(0, Collections.emptySet(), Collections.emptySet()),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testAddPassiveSubscriber_MultipleSubscribers() {
        mRouting.addSubscription(mSubscriber);
        mRouting.addSubscription(mSubscriber2);

        assertSubscribers(LAYER_1, mSubscriber, mSubscriber2);
        assertSubscribers(LAYER_2, mSubscriber, mSubscriber2);

        assertEquals(
                new VmsSubscriptionState(0, Collections.emptySet(), Collections.emptySet()),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testRemovePassiveSubscriber() {
        mRouting.addSubscription(mSubscriber);
        mRouting.removeSubscription(mSubscriber);

        assertNoSubscribers();

        assertEquals(
                new VmsSubscriptionState(0, Collections.emptySet(), Collections.emptySet()),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testRemovePassiveSubscriber_NoSubscriptions() {
        mRouting.removeSubscription(mSubscriber);

        assertNoSubscribers();

        assertEquals(
                new VmsSubscriptionState(0, Collections.emptySet(), Collections.emptySet()),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testRemovePassiveSubscriber_MultipleTimes() {
        mRouting.addSubscription(mSubscriber);
        mRouting.removeSubscription(mSubscriber);
        mRouting.removeSubscription(mSubscriber);

        assertNoSubscribers();

        assertEquals(
                new VmsSubscriptionState(0, Collections.emptySet(), Collections.emptySet()),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testRemovePassiveSubscriber_Rewrapped() {
        mRouting.addSubscription(mSubscriber);
        mRouting.removeSubscription(mSubscriberRewrapped);

        assertNoSubscribers();

        assertEquals(
                new VmsSubscriptionState(0, Collections.emptySet(), Collections.emptySet()),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testRemovePassiveSubscriber_UnknownSubscriber() {
        mRouting.addSubscription(mSubscriber);
        mRouting.removeSubscription(mSubscriber2);

        assertSubscribers(mSubscriber);

        assertEquals(
                new VmsSubscriptionState(0, Collections.emptySet(), Collections.emptySet()),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testRemovePassiveSubscriber_MultipleSubscribers() {
        mRouting.addSubscription(mSubscriber);
        mRouting.addSubscription(mSubscriber2);
        mRouting.removeSubscription(mSubscriber2);

        assertSubscribers(mSubscriber);

        assertEquals(
                new VmsSubscriptionState(0, Collections.emptySet(), Collections.emptySet()),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testAddLayerSubscriber() {
        mRouting.addSubscription(mSubscriber, LAYER_1);

        assertSubscribers(LAYER_1, mSubscriber);
        assertNoSubscribers(LAYER_2);

        assertEquals(
                new VmsSubscriptionState(1, Collections.singleton(LAYER_1), Collections.emptySet()),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testAddLayerSubscriber_MultipleTimes() {
        mRouting.addSubscription(mSubscriber, LAYER_1);
        mRouting.addSubscription(mSubscriber, LAYER_1);

        assertSubscribers(LAYER_1, mSubscriber);
        assertNoSubscribers(LAYER_2);

        assertEquals(
                new VmsSubscriptionState(1, Collections.singleton(LAYER_1), Collections.emptySet()),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testAddLayerSubscriber_MultipleTimes_Rewrapped() {
        mRouting.addSubscription(mSubscriber, LAYER_1);
        mRouting.addSubscription(mSubscriberRewrapped, LAYER_1);

        assertSubscribers(LAYER_1, mSubscriber);
        assertNoSubscribers(LAYER_2);

        assertEquals(
                new VmsSubscriptionState(1, Collections.singleton(LAYER_1), Collections.emptySet()),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testAddLayerSubscriber_MultipleLayers() {
        mRouting.addSubscription(mSubscriber, LAYER_1);
        mRouting.addSubscription(mSubscriber, LAYER_2);

        assertSubscribers(LAYER_1, mSubscriber);
        assertSubscribers(LAYER_2, mSubscriber);

        assertEquals(
                new VmsSubscriptionState(2,
                        new HashSet<>(Arrays.asList(LAYER_1, LAYER_2)), Collections.emptySet()),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testAddLayerSubscriber_MultipleSubscribers() {
        mRouting.addSubscription(mSubscriber, LAYER_1);
        mRouting.addSubscription(mSubscriber2, LAYER_1);

        assertSubscribers(LAYER_1, mSubscriber, mSubscriber2);
        assertNoSubscribers(LAYER_2);

        assertEquals(
                new VmsSubscriptionState(2, Collections.singleton(LAYER_1), Collections.emptySet()),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testAddLayerSubscriber_MultipleSubscribers_MultipleLayers() {
        mRouting.addSubscription(mSubscriber, LAYER_1);
        mRouting.addSubscription(mSubscriber2, LAYER_2);

        assertSubscribers(LAYER_1, mSubscriber);
        assertSubscribers(LAYER_2, mSubscriber2);

        assertEquals(
                new VmsSubscriptionState(2,
                        new HashSet<>(Arrays.asList(LAYER_1, LAYER_2)), Collections.emptySet()),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testRemoveLayerSubscriber() {
        mRouting.addSubscription(mSubscriber, LAYER_1);
        mRouting.removeSubscription(mSubscriber, LAYER_1);

        assertNoSubscribers();

        assertEquals(
                new VmsSubscriptionState(2, Collections.emptySet(), Collections.emptySet()),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testRemoveLayerSubscriber_NoSubscriptions() {
        mRouting.removeSubscription(mSubscriber, LAYER_1);

        assertNoSubscribers();

        assertEquals(
                new VmsSubscriptionState(0, Collections.emptySet(), Collections.emptySet()),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testRemoveLayerSubscriber_MultipleTimes() {
        mRouting.addSubscription(mSubscriber, LAYER_1);
        mRouting.removeSubscription(mSubscriber, LAYER_1);
        mRouting.removeSubscription(mSubscriber, LAYER_1);

        assertNoSubscribers();

        assertEquals(
                new VmsSubscriptionState(2, Collections.emptySet(), Collections.emptySet()),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testRemoveLayerSubscriber_Rewrapped() {
        mRouting.addSubscription(mSubscriber, LAYER_1);
        mRouting.removeSubscription(mSubscriberRewrapped, LAYER_1);

        assertNoSubscribers();

        assertEquals(
                new VmsSubscriptionState(2, Collections.emptySet(), Collections.emptySet()),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testRemoveLayerSubscriber_UnknownSubscriber() {
        mRouting.addSubscription(mSubscriber, LAYER_1);
        mRouting.removeSubscription(mSubscriber2, LAYER_1);

        assertSubscribers(LAYER_1, mSubscriber);
        assertNoSubscribers(LAYER_2);

        assertEquals(
                new VmsSubscriptionState(1, Collections.singleton(LAYER_1), Collections.emptySet()),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testRemoveLayerSubscriber_MultipleLayers() {
        mRouting.addSubscription(mSubscriber, LAYER_1);
        mRouting.addSubscription(mSubscriber, LAYER_2);
        mRouting.removeSubscription(mSubscriber, LAYER_2);

        assertSubscribers(LAYER_1, mSubscriber);
        assertNoSubscribers(LAYER_2);

        assertEquals(
                new VmsSubscriptionState(3, Collections.singleton(LAYER_1), Collections.emptySet()),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testRemoveLayerSubscriber_MultipleSubscribers() {
        mRouting.addSubscription(mSubscriber, LAYER_1);
        mRouting.addSubscription(mSubscriber2, LAYER_1);
        mRouting.removeSubscription(mSubscriber2, LAYER_1);

        assertSubscribers(LAYER_1, mSubscriber);
        assertNoSubscribers(LAYER_2);

        assertEquals(
                new VmsSubscriptionState(3, Collections.singleton(LAYER_1), Collections.emptySet()),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testRemoveLayerSubscriber_MultipleSubscribers_MultipleLayers() {
        mRouting.addSubscription(mSubscriber, LAYER_1);
        mRouting.addSubscription(mSubscriber2, LAYER_2);
        mRouting.removeSubscription(mSubscriber2, LAYER_2);

        assertSubscribers(LAYER_1, mSubscriber);
        assertNoSubscribers(LAYER_2);

        assertEquals(
                new VmsSubscriptionState(3, Collections.singleton(LAYER_1), Collections.emptySet()),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testAddPublisherSubscriber() {
        mRouting.addSubscription(mSubscriber, LAYER_1, PUBLISHER_ID_1);

        assertSubscribers(LAYER_1, PUBLISHER_ID_1, mSubscriber);
        assertNoSubscribers(LAYER_1, PUBLISHER_ID_2);
        assertNoSubscribers(LAYER_2);

        assertEquals(
                new VmsSubscriptionState(1, Collections.emptySet(),
                        Collections.singleton(new VmsAssociatedLayer(
                                LAYER_1, Collections.singleton(PUBLISHER_ID_1)))),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testAddPublisherSubscriber_MultipleTimes() {
        mRouting.addSubscription(mSubscriber, LAYER_1, PUBLISHER_ID_1);
        mRouting.addSubscription(mSubscriber, LAYER_1, PUBLISHER_ID_1);

        assertSubscribers(LAYER_1, PUBLISHER_ID_1, mSubscriber);
        assertNoSubscribers(LAYER_1, PUBLISHER_ID_2);
        assertNoSubscribers(LAYER_2);

        assertEquals(
                new VmsSubscriptionState(1, Collections.emptySet(),
                        Collections.singleton(new VmsAssociatedLayer(
                                LAYER_1, Collections.singleton(PUBLISHER_ID_1)))),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testAddPublisherSubscriber_MultipleTimes_Rewrapped() {
        mRouting.addSubscription(mSubscriber, LAYER_1, PUBLISHER_ID_1);
        mRouting.addSubscription(mSubscriberRewrapped, LAYER_1, PUBLISHER_ID_1);

        assertSubscribers(LAYER_1, PUBLISHER_ID_1, mSubscriber);
        assertNoSubscribers(LAYER_1, PUBLISHER_ID_2);
        assertNoSubscribers(LAYER_2);

        assertEquals(
                new VmsSubscriptionState(1, Collections.emptySet(),
                        Collections.singleton(new VmsAssociatedLayer(
                                LAYER_1, Collections.singleton(PUBLISHER_ID_1)))),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testAddPublisherSubscriber_MultipleSubscribers() {
        mRouting.addSubscription(mSubscriber, LAYER_1, PUBLISHER_ID_1);
        mRouting.addSubscription(mSubscriber2, LAYER_1, PUBLISHER_ID_1);

        assertSubscribers(LAYER_1, PUBLISHER_ID_1, mSubscriber, mSubscriber2);
        assertNoSubscribers(LAYER_1, PUBLISHER_ID_2);
        assertNoSubscribers(LAYER_2);

        assertEquals(
                new VmsSubscriptionState(2, Collections.emptySet(),
                        Collections.singleton(new VmsAssociatedLayer(
                                LAYER_1, Collections.singleton(PUBLISHER_ID_1)))),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testAddPublisherSubscriber_MultipleLayers() {
        mRouting.addSubscription(mSubscriber, LAYER_1, PUBLISHER_ID_1);
        mRouting.addSubscription(mSubscriber, LAYER_2, PUBLISHER_ID_1);

        assertSubscribers(LAYER_1, PUBLISHER_ID_1, mSubscriber);
        assertSubscribers(LAYER_2, PUBLISHER_ID_1, mSubscriber);
        assertNoSubscribers(LAYER_1, PUBLISHER_ID_2);
        assertNoSubscribers(LAYER_2, PUBLISHER_ID_2);

        assertEquals(
                new VmsSubscriptionState(2, Collections.emptySet(),
                        new HashSet<>(Arrays.asList(
                                new VmsAssociatedLayer(
                                        LAYER_1, Collections.singleton(PUBLISHER_ID_1)),
                                new VmsAssociatedLayer(
                                        LAYER_2, Collections.singleton(PUBLISHER_ID_1))
                        ))),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testAddPublisherSubscriber_MultiplePublishers() {
        mRouting.addSubscription(mSubscriber, LAYER_1, PUBLISHER_ID_1);
        mRouting.addSubscription(mSubscriber, LAYER_1, PUBLISHER_ID_2);

        assertSubscribers(LAYER_1, PUBLISHER_ID_1, mSubscriber);
        assertSubscribers(LAYER_1, PUBLISHER_ID_2, mSubscriber);
        assertNoSubscribers(LAYER_2);

        assertEquals(
                new VmsSubscriptionState(2, Collections.emptySet(),
                        Collections.singleton(new VmsAssociatedLayer(
                                LAYER_1,
                                new HashSet<>(Arrays.asList(PUBLISHER_ID_1, PUBLISHER_ID_2))
                        ))),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testAddPublisherSubscriber_MultipleSubscribers_MultipleLayers() {
        mRouting.addSubscription(mSubscriber, LAYER_1, PUBLISHER_ID_1);
        mRouting.addSubscription(mSubscriber2, LAYER_2, PUBLISHER_ID_1);

        assertSubscribers(LAYER_1, PUBLISHER_ID_1, mSubscriber);
        assertSubscribers(LAYER_2, PUBLISHER_ID_1, mSubscriber2);
        assertNoSubscribers(LAYER_1, PUBLISHER_ID_2);
        assertNoSubscribers(LAYER_2, PUBLISHER_ID_2);

        assertEquals(
                new VmsSubscriptionState(2, Collections.emptySet(),
                        new HashSet<>(Arrays.asList(
                                new VmsAssociatedLayer(
                                        LAYER_1, Collections.singleton(PUBLISHER_ID_1)),
                                new VmsAssociatedLayer(
                                        LAYER_2, Collections.singleton(PUBLISHER_ID_1))
                        ))),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testAddPublisherSubscriber_MultipleSubscribers_MultiplePublishers() {
        mRouting.addSubscription(mSubscriber, LAYER_1, PUBLISHER_ID_1);
        mRouting.addSubscription(mSubscriber2, LAYER_1, PUBLISHER_ID_2);

        assertSubscribers(LAYER_1, PUBLISHER_ID_1, mSubscriber);
        assertSubscribers(LAYER_1, PUBLISHER_ID_2, mSubscriber2);
        assertNoSubscribers(LAYER_2);

        assertEquals(
                new VmsSubscriptionState(2, Collections.emptySet(),
                        Collections.singleton(new VmsAssociatedLayer(
                                LAYER_1,
                                new HashSet<>(Arrays.asList(PUBLISHER_ID_1, PUBLISHER_ID_2))
                        ))),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testRemovePublisherSubscriber() {
        mRouting.addSubscription(mSubscriber, LAYER_1, PUBLISHER_ID_1);
        mRouting.removeSubscription(mSubscriber, LAYER_1, PUBLISHER_ID_1);

        assertNoSubscribers();

        assertEquals(
                new VmsSubscriptionState(2, Collections.emptySet(), Collections.emptySet()),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testRemovePublisherSubscriber_NoSubscribers() {
        mRouting.removeSubscription(mSubscriber, LAYER_1, PUBLISHER_ID_1);

        assertNoSubscribers();

        assertEquals(
                new VmsSubscriptionState(0, Collections.emptySet(), Collections.emptySet()),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testRemovePublisherSubscriber_MultipleTimes() {
        mRouting.addSubscription(mSubscriber, LAYER_1, PUBLISHER_ID_1);
        mRouting.removeSubscription(mSubscriber, LAYER_1, PUBLISHER_ID_1);
        mRouting.removeSubscription(mSubscriber, LAYER_1, PUBLISHER_ID_1);

        assertNoSubscribers();

        assertEquals(
                new VmsSubscriptionState(2, Collections.emptySet(), Collections.emptySet()),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testRemovePublisherSubscriber_Rewrapped() {
        mRouting.addSubscription(mSubscriber, LAYER_1, PUBLISHER_ID_1);
        mRouting.removeSubscription(mSubscriberRewrapped, LAYER_1, PUBLISHER_ID_1);

        assertNoSubscribers();

        assertEquals(
                new VmsSubscriptionState(2, Collections.emptySet(), Collections.emptySet()),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testRemovePublisherSubscriber_UnknownSubscriber() {
        mRouting.addSubscription(mSubscriber, LAYER_1, PUBLISHER_ID_1);
        mRouting.removeSubscription(mSubscriber2, LAYER_1, PUBLISHER_ID_1);

        assertSubscribers(LAYER_1, PUBLISHER_ID_1, mSubscriber);
        assertNoSubscribers(LAYER_1, PUBLISHER_ID_2);
        assertNoSubscribers(LAYER_2);

        assertEquals(
                new VmsSubscriptionState(1, Collections.emptySet(),
                        Collections.singleton(new VmsAssociatedLayer(
                                LAYER_1, Collections.singleton(PUBLISHER_ID_1)))),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testRemovePublisherSubscriber_MultipleLayers() {
        mRouting.addSubscription(mSubscriber, LAYER_1, PUBLISHER_ID_1);
        mRouting.addSubscription(mSubscriber2, LAYER_2, PUBLISHER_ID_1);
        mRouting.removeSubscription(mSubscriber2, LAYER_2, PUBLISHER_ID_1);

        assertSubscribers(LAYER_1, PUBLISHER_ID_1, mSubscriber);
        assertNoSubscribers(LAYER_1, PUBLISHER_ID_2);
        assertNoSubscribers(LAYER_2);

        assertEquals(
                new VmsSubscriptionState(3, Collections.emptySet(),
                        Collections.singleton(new VmsAssociatedLayer(
                                LAYER_1, Collections.singleton(PUBLISHER_ID_1)))),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testRemovePublisherSubscriber_MultiplePublishers() {
        mRouting.addSubscription(mSubscriber, LAYER_1, PUBLISHER_ID_1);
        mRouting.addSubscription(mSubscriber2, LAYER_1, PUBLISHER_ID_2);
        mRouting.removeSubscription(mSubscriber2, LAYER_1, PUBLISHER_ID_2);

        assertSubscribers(LAYER_1, PUBLISHER_ID_1, mSubscriber);
        assertNoSubscribers(LAYER_1, PUBLISHER_ID_2);
        assertNoSubscribers(LAYER_2);

        assertEquals(
                new VmsSubscriptionState(3, Collections.emptySet(),
                        Collections.singleton(new VmsAssociatedLayer(
                                LAYER_1, Collections.singleton(PUBLISHER_ID_1)))),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testRemovePublisherSubscriber_MultipleSubscribers() {
        mRouting.addSubscription(mSubscriber, LAYER_1, PUBLISHER_ID_1);
        mRouting.addSubscription(mSubscriber2, LAYER_1, PUBLISHER_ID_1);
        mRouting.removeSubscription(mSubscriber2, LAYER_1, PUBLISHER_ID_1);

        assertSubscribers(LAYER_1, PUBLISHER_ID_1, mSubscriber);
        assertNoSubscribers(LAYER_1, PUBLISHER_ID_2);
        assertNoSubscribers(LAYER_2);

        assertEquals(
                new VmsSubscriptionState(3, Collections.emptySet(),
                        Collections.singleton(new VmsAssociatedLayer(
                                LAYER_1, Collections.singleton(PUBLISHER_ID_1)))),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testRemovePublisherSubscriber_MultipleSubscribers_MultipleLayers() {
        mRouting.addSubscription(mSubscriber, LAYER_1, PUBLISHER_ID_1);
        mRouting.addSubscription(mSubscriber2, LAYER_2, PUBLISHER_ID_1);
        mRouting.removeSubscription(mSubscriber2, LAYER_2, PUBLISHER_ID_1);

        assertSubscribers(LAYER_1, PUBLISHER_ID_1, mSubscriber);
        assertNoSubscribers(LAYER_2);

        assertEquals(
                new VmsSubscriptionState(3, Collections.emptySet(),
                        Collections.singleton(new VmsAssociatedLayer(
                                LAYER_1, Collections.singleton(PUBLISHER_ID_1)))),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testRemovePublisherSubscriber_MultipleSubscribers_MultiplePublishers() {
        mRouting.addSubscription(mSubscriber, LAYER_1, PUBLISHER_ID_1);
        mRouting.addSubscription(mSubscriber2, LAYER_1, PUBLISHER_ID_2);
        mRouting.removeSubscription(mSubscriber2, LAYER_1, PUBLISHER_ID_2);

        assertSubscribers(LAYER_1, PUBLISHER_ID_1, mSubscriber);
        assertNoSubscribers(LAYER_1, PUBLISHER_ID_2);
        assertNoSubscribers(LAYER_2);

        assertEquals(
                new VmsSubscriptionState(3, Collections.emptySet(),
                        Collections.singleton(new VmsAssociatedLayer(
                                LAYER_1, Collections.singleton(PUBLISHER_ID_1)))),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testAddingAllTypesOfSubscribers() {
        IVmsSubscriberClient passiveSubscriber = new MockVmsSubscriber();
        mRouting.addSubscription(passiveSubscriber);
        mRouting.addSubscription(mSubscriber, LAYER_1, PUBLISHER_ID_1);
        mRouting.addSubscription(mSubscriber2, LAYER_1);
        mRouting.addSubscription(mSubscriber, LAYER_2);
        mRouting.addSubscription(mSubscriber2, LAYER_2, PUBLISHER_ID_2);

        assertSubscribers(LAYER_1, PUBLISHER_ID_1, passiveSubscriber, mSubscriber, mSubscriber2);
        assertSubscribers(LAYER_1, PUBLISHER_ID_2, passiveSubscriber, mSubscriber2);
        assertSubscribers(LAYER_2, PUBLISHER_ID_1, passiveSubscriber, mSubscriber);
        assertSubscribers(LAYER_2, PUBLISHER_ID_2, passiveSubscriber, mSubscriber, mSubscriber2);

        assertEquals(
                new VmsSubscriptionState(4,
                        new HashSet<>(Arrays.asList(LAYER_1, LAYER_2)),
                        new HashSet<>(Arrays.asList(
                                new VmsAssociatedLayer(
                                        LAYER_1, Collections.singleton(PUBLISHER_ID_1)),
                                new VmsAssociatedLayer(
                                        LAYER_2, Collections.singleton(PUBLISHER_ID_2))
                        ))),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testAddingAndRemovingAllTypesOfSubscribers() {
        IVmsSubscriberClient passiveSubscriber = new MockVmsSubscriber();
        mRouting.addSubscription(passiveSubscriber);
        mRouting.addSubscription(mSubscriber, LAYER_1, PUBLISHER_ID_1);
        mRouting.addSubscription(mSubscriber2, LAYER_1);
        mRouting.addSubscription(mSubscriber, LAYER_2);
        mRouting.addSubscription(mSubscriber2, LAYER_2, PUBLISHER_ID_2);
        mRouting.removeSubscription(mSubscriber, LAYER_1, PUBLISHER_ID_1);
        mRouting.removeSubscription(mSubscriber, LAYER_2);

        assertSubscribers(LAYER_1, PUBLISHER_ID_1, passiveSubscriber, mSubscriber2);
        assertSubscribers(LAYER_1, PUBLISHER_ID_2, passiveSubscriber, mSubscriber2);
        assertSubscribers(LAYER_2, PUBLISHER_ID_1, passiveSubscriber);
        assertSubscribers(LAYER_2, PUBLISHER_ID_2, passiveSubscriber, mSubscriber2);

        assertEquals(
                new VmsSubscriptionState(6,
                        Collections.singleton(LAYER_1),
                        Collections.singleton(
                                new VmsAssociatedLayer(
                                        LAYER_2, Collections.singleton(PUBLISHER_ID_2))
                        )),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testRemoveDeadSubscriber() {
        IVmsSubscriberClient passiveSubscriber = new MockVmsSubscriber();
        mRouting.addSubscription(passiveSubscriber);
        mRouting.addSubscription(mSubscriber, LAYER_1, PUBLISHER_ID_1);
        mRouting.addSubscription(mSubscriber2, LAYER_1);
        mRouting.addSubscription(mSubscriber, LAYER_2);
        mRouting.addSubscription(mSubscriber2, LAYER_2, PUBLISHER_ID_2);
        assertTrue(mRouting.removeDeadSubscriber(mSubscriber));

        assertSubscribers(LAYER_1, PUBLISHER_ID_1, passiveSubscriber, mSubscriber2);
        assertSubscribers(LAYER_1, PUBLISHER_ID_2, passiveSubscriber, mSubscriber2);
        assertSubscribers(LAYER_2, PUBLISHER_ID_1, passiveSubscriber);
        assertSubscribers(LAYER_2, PUBLISHER_ID_2, passiveSubscriber, mSubscriber2);

        assertEquals(
                new VmsSubscriptionState(6,
                        Collections.singleton(LAYER_1),
                        Collections.singleton(
                                new VmsAssociatedLayer(
                                        LAYER_2, Collections.singleton(PUBLISHER_ID_2))
                        )),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testRemoveDeadSubscriber_NoSubscriptions() {
        IVmsSubscriberClient passiveSubscriber = new MockVmsSubscriber();
        mRouting.addSubscription(passiveSubscriber);
        mRouting.addSubscription(mSubscriber2, LAYER_1);
        mRouting.addSubscription(mSubscriber2, LAYER_2, PUBLISHER_ID_2);
        assertFalse(mRouting.removeDeadSubscriber(mSubscriber));

        assertSubscribers(LAYER_1, PUBLISHER_ID_1, passiveSubscriber, mSubscriber2);
        assertSubscribers(LAYER_1, PUBLISHER_ID_2, passiveSubscriber, mSubscriber2);
        assertSubscribers(LAYER_2, PUBLISHER_ID_1, passiveSubscriber);
        assertSubscribers(LAYER_2, PUBLISHER_ID_2, passiveSubscriber, mSubscriber2);

        assertEquals(
                new VmsSubscriptionState(2,
                        Collections.singleton(LAYER_1),
                        Collections.singleton(
                                new VmsAssociatedLayer(
                                        LAYER_2, Collections.singleton(PUBLISHER_ID_2))
                        )),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testRemoveDeadSubscriber_PassiveSubscriber() {
        IVmsSubscriberClient passiveSubscriber = new MockVmsSubscriber();
        mRouting.addSubscription(passiveSubscriber);
        mRouting.addSubscription(mSubscriber, LAYER_1, PUBLISHER_ID_1);
        mRouting.addSubscription(mSubscriber2, LAYER_1);
        mRouting.addSubscription(mSubscriber, LAYER_2);
        mRouting.addSubscription(mSubscriber2, LAYER_2, PUBLISHER_ID_2);
        assertFalse(mRouting.removeDeadSubscriber(passiveSubscriber));

        assertSubscribers(LAYER_1, PUBLISHER_ID_1, mSubscriber, mSubscriber2);
        assertSubscribers(LAYER_1, PUBLISHER_ID_2, mSubscriber2);
        assertSubscribers(LAYER_2, PUBLISHER_ID_1, mSubscriber);
        assertSubscribers(LAYER_2, PUBLISHER_ID_2, mSubscriber, mSubscriber2);

        assertEquals(
                new VmsSubscriptionState(4,
                        new HashSet<>(Arrays.asList(LAYER_1, LAYER_2)),
                        new HashSet<>(Arrays.asList(
                                new VmsAssociatedLayer(
                                        LAYER_1, Collections.singleton(PUBLISHER_ID_1)),
                                new VmsAssociatedLayer(
                                        LAYER_2, Collections.singleton(PUBLISHER_ID_2))
                        ))),
                mRouting.getSubscriptionState());
    }

    @Test
    public void testHasSubscriptions_Default() {
        assertFalse(mRouting.hasLayerSubscriptions(LAYER_1));
        assertFalse(mRouting.hasLayerSubscriptions(LAYER_2));
        assertFalse(mRouting.hasLayerFromPublisherSubscriptions(LAYER_1, PUBLISHER_ID_1));
        assertFalse(mRouting.hasLayerFromPublisherSubscriptions(LAYER_1, PUBLISHER_ID_2));
        assertFalse(mRouting.hasLayerFromPublisherSubscriptions(LAYER_2, PUBLISHER_ID_1));
        assertFalse(mRouting.hasLayerFromPublisherSubscriptions(LAYER_2, PUBLISHER_ID_2));
    }

    @Test
    public void testHasSubscriptions_PassiveSubscriber() {
        mRouting.addSubscription(mSubscriber);

        testHasSubscriptions_Default();
    }

    @Test
    public void testHasSubscriptions_DeadSubscriber() {
        mRouting.addSubscription(mSubscriber);
        mRouting.addSubscription(mSubscriber, LAYER_1, PUBLISHER_ID_1);
        mRouting.removeDeadSubscriber(mSubscriber);

        testHasSubscriptions_Default();
    }

    @Test
    public void testHasSubscriptions_Layer() {
        mRouting.addSubscription(mSubscriber, LAYER_1);

        assertTrue(mRouting.hasLayerSubscriptions(LAYER_1));
        assertFalse(mRouting.hasLayerSubscriptions(LAYER_2));
        assertFalse(mRouting.hasLayerFromPublisherSubscriptions(LAYER_1, PUBLISHER_ID_1));
        assertFalse(mRouting.hasLayerFromPublisherSubscriptions(LAYER_1, PUBLISHER_ID_2));
        assertFalse(mRouting.hasLayerFromPublisherSubscriptions(LAYER_2, PUBLISHER_ID_1));
        assertFalse(mRouting.hasLayerFromPublisherSubscriptions(LAYER_2, PUBLISHER_ID_2));

        mRouting.removeSubscription(mSubscriber, LAYER_1);

        assertFalse(mRouting.hasLayerSubscriptions(LAYER_1));
    }

    @Test
    public void testHasSubscriptions_LayerFromPublisher() {
        mRouting.addSubscription(mSubscriber, LAYER_1, PUBLISHER_ID_1);

        assertFalse(mRouting.hasLayerSubscriptions(LAYER_1));
        assertFalse(mRouting.hasLayerSubscriptions(LAYER_2));
        assertTrue(mRouting.hasLayerFromPublisherSubscriptions(LAYER_1, PUBLISHER_ID_1));
        assertFalse(mRouting.hasLayerFromPublisherSubscriptions(LAYER_1, PUBLISHER_ID_2));
        assertFalse(mRouting.hasLayerFromPublisherSubscriptions(LAYER_2, PUBLISHER_ID_1));
        assertFalse(mRouting.hasLayerFromPublisherSubscriptions(LAYER_2, PUBLISHER_ID_2));

        mRouting.removeSubscription(mSubscriber, LAYER_1, PUBLISHER_ID_1);

        assertFalse(mRouting.hasLayerFromPublisherSubscriptions(LAYER_1, PUBLISHER_ID_1));
    }

    class MockVmsSubscriber extends IVmsSubscriberClient.Stub {
        @Override
        public void onVmsMessageReceived(VmsLayer layer, byte[] payload) {
            throw new RuntimeException("Should not be accessed");
        }

        @Override
        public void onLayersAvailabilityChanged(VmsAvailableLayers availableLayers) {
            throw new RuntimeException("Should not be accessed");
        }
    }

    private void assertNoSubscribers() {
        assertSubscribers(); /* subscribers is empty */
    }

    private void assertNoSubscribers(VmsLayer layer) {
        assertSubscribers(layer); /* subscribers is empty */
    }

    private void assertNoSubscribers(VmsLayer layer, int publisherId) {
        assertSubscribers(layer, publisherId); /* subscribers is empty */
    }

    private void assertSubscribers(IVmsSubscriberClient... subscribers) {
        for (VmsLayer layer : LAYERS) {
            assertSubscribers(layer, subscribers);
        }
    }

    private void assertSubscribers(VmsLayer layer, IVmsSubscriberClient... subscribers) {
        for (int publisherId : PUBLISHER_IDS) {
            assertSubscribers(layer, publisherId, subscribers);
        }
    }

    private void assertSubscribers(VmsLayer layer, int publisherId,
            IVmsSubscriberClient... subscribers) {
        assertEquals(
                new HashSet<>(Arrays.asList(subscribers)),
                mRouting.getSubscribersForLayerFromPublisher(layer, publisherId));
    }
}