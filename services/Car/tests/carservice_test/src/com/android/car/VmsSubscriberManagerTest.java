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
import static org.junit.Assert.assertNull;

import android.car.vms.VmsAssociatedLayer;
import android.car.vms.VmsAvailableLayers;
import android.car.vms.VmsLayer;
import android.hardware.automotive.vehicle.V2_0.VmsAvailabilityStateIntegerValuesIndex;
import android.hardware.automotive.vehicle.V2_0.VmsMessageType;
import android.util.Pair;

import androidx.test.filters.MediumTest;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@MediumTest
public class VmsSubscriberManagerTest extends MockedVmsTestBase {
    private static final int PUBLISHER_ID = 17;
    private static final int WRONG_PUBLISHER_ID = 26;
    private static final Set<Integer> PUBLISHERS_LIST = Collections.singleton(PUBLISHER_ID);

    private static final int SUBSCRIPTION_LAYER_ID = 2;
    private static final int SUBSCRIPTION_LAYER_VERSION = 3;
    private static final int MOCK_PUBLISHER_LAYER_SUBTYPE = 444;
    private static final VmsLayer SUBSCRIPTION_LAYER = new VmsLayer(SUBSCRIPTION_LAYER_ID,
            MOCK_PUBLISHER_LAYER_SUBTYPE,
            SUBSCRIPTION_LAYER_VERSION);
    private static final VmsAssociatedLayer SUBSCRIPTION_ASSOCIATED_LAYER =
            new VmsAssociatedLayer(SUBSCRIPTION_LAYER, PUBLISHERS_LIST);

    private static final int SUBSCRIPTION_DEPENDANT_LAYER_ID_1 = 4;
    private static final int SUBSCRIPTION_DEPENDANT_LAYER_VERSION_1 = 5;
    private static final VmsLayer SUBSCRIPTION_DEPENDANT_LAYER_1 =
            new VmsLayer(SUBSCRIPTION_DEPENDANT_LAYER_ID_1,
                    MOCK_PUBLISHER_LAYER_SUBTYPE,
                    SUBSCRIPTION_DEPENDANT_LAYER_VERSION_1);

    private static final VmsAssociatedLayer SUBSCRIPTION_DEPENDANT_ASSOCIATED_LAYER_1 =
            new VmsAssociatedLayer(SUBSCRIPTION_DEPENDANT_LAYER_1, PUBLISHERS_LIST);

    private static final int SUBSCRIPTION_DEPENDANT_LAYER_ID_2 = 6;
    private static final int SUBSCRIPTION_DEPENDANT_LAYER_VERSION_2 = 7;
    private static final VmsLayer SUBSCRIPTION_DEPENDANT_LAYER_2 =
            new VmsLayer(SUBSCRIPTION_DEPENDANT_LAYER_ID_2,
                    MOCK_PUBLISHER_LAYER_SUBTYPE,
                    SUBSCRIPTION_DEPENDANT_LAYER_VERSION_2);

    private static final VmsAssociatedLayer SUBSCRIPTION_DEPENDANT_ASSOCIATED_LAYER_2 =
            new VmsAssociatedLayer(SUBSCRIPTION_DEPENDANT_LAYER_2, PUBLISHERS_LIST);

    private static final int SUBSCRIPTION_UNSUPPORTED_LAYER_ID = 100;
    private static final int SUBSCRIPTION_UNSUPPORTED_LAYER_VERSION = 200;

    private static final byte[] PAYLOAD = {0xa, 0xb};

    // Test injecting a value in the HAL and verifying it propagates to a subscriber.
    @Test
    public void testSubscribe() throws Exception {
        getSubscriberManager().subscribe(SUBSCRIPTION_LAYER);

        getMockHalClient().sendMessage(
                new int[]{
                        VmsMessageType.DATA,
                        SUBSCRIPTION_LAYER_ID,
                        MOCK_PUBLISHER_LAYER_SUBTYPE,
                        SUBSCRIPTION_LAYER_VERSION,
                        PUBLISHER_ID
                },
                PAYLOAD);

        Pair<VmsLayer, byte[]> message = getMockSubscriberClient().receiveMessage();
        assertEquals(SUBSCRIPTION_LAYER, message.first);
        assertArrayEquals(PAYLOAD, message.second);
    }

    // Test injecting a value in the HAL and verifying it propagates to a subscriber.
    @Test
    public void testSubscribeToPublisher() throws Exception {
        getSubscriberManager().subscribe(SUBSCRIPTION_LAYER, PUBLISHER_ID);

        getMockHalClient().sendMessage(
                new int[]{
                        VmsMessageType.DATA,
                        SUBSCRIPTION_LAYER_ID,
                        MOCK_PUBLISHER_LAYER_SUBTYPE,
                        SUBSCRIPTION_LAYER_VERSION,
                        WRONG_PUBLISHER_ID
                },
                PAYLOAD);

        assertNull(getMockSubscriberClient().receiveMessage());
    }

    // Test injecting a value in the HAL and verifying it propagates to a subscriber.
    @Test
    public void testSubscribeFromPublisher() throws Exception {
        getSubscriberManager().subscribe(SUBSCRIPTION_LAYER, PUBLISHER_ID);

        getMockHalClient().sendMessage(
                new int[]{
                        VmsMessageType.DATA,
                        SUBSCRIPTION_LAYER_ID,
                        MOCK_PUBLISHER_LAYER_SUBTYPE,
                        SUBSCRIPTION_LAYER_VERSION,
                        PUBLISHER_ID
                },
                PAYLOAD);

        Pair<VmsLayer, byte[]> message = getMockSubscriberClient().receiveMessage();
        assertEquals(SUBSCRIPTION_LAYER, message.first);
        assertArrayEquals(PAYLOAD, message.second);
    }

    // Test injecting a value in the HAL and verifying it does not propagate to a subscriber.
    @Test
    public void testUnsubscribe() throws Exception {
        getSubscriberManager().subscribe(SUBSCRIPTION_LAYER);
        getSubscriberManager().unsubscribe(SUBSCRIPTION_LAYER);

        getMockHalClient().sendMessage(
                new int[]{
                        VmsMessageType.DATA,
                        SUBSCRIPTION_LAYER_ID,
                        MOCK_PUBLISHER_LAYER_SUBTYPE,
                        SUBSCRIPTION_LAYER_VERSION,
                        PUBLISHER_ID
                },
                PAYLOAD);

        assertNull(getMockSubscriberClient().receiveMessage());
    }

    // Test injecting a value in the HAL and verifying it does not propagate to a subscriber.
    @Test
    public void testSubscribeFromWrongPublisher() throws Exception {
        getSubscriberManager().subscribe(SUBSCRIPTION_LAYER, PUBLISHER_ID);

        getMockHalClient().sendMessage(
                new int[]{
                        VmsMessageType.DATA,
                        SUBSCRIPTION_LAYER_ID,
                        MOCK_PUBLISHER_LAYER_SUBTYPE,
                        SUBSCRIPTION_LAYER_VERSION,
                        WRONG_PUBLISHER_ID
                },
                PAYLOAD);

        assertNull(getMockSubscriberClient().receiveMessage());
    }

    // Test injecting a value in the HAL and verifying it does not propagate to a subscriber.
    @Test
    public void testUnsubscribeFromPublisher() throws Exception {
        getSubscriberManager().subscribe(SUBSCRIPTION_LAYER, PUBLISHER_ID);
        getSubscriberManager().unsubscribe(SUBSCRIPTION_LAYER, PUBLISHER_ID);

        getMockHalClient().sendMessage(
                new int[]{
                        VmsMessageType.DATA,
                        SUBSCRIPTION_LAYER_ID,
                        MOCK_PUBLISHER_LAYER_SUBTYPE,
                        SUBSCRIPTION_LAYER_VERSION,
                        PUBLISHER_ID
                },
                PAYLOAD);

        assertNull(getMockSubscriberClient().receiveMessage());
    }

    // Test injecting a value in the HAL and verifying it propagates to a subscriber.
    @Test
    public void testSubscribeAll() throws Exception {
        getSubscriberManager().startMonitoring();

        getMockHalClient().sendMessage(
                new int[]{
                        VmsMessageType.DATA,
                        SUBSCRIPTION_LAYER_ID,
                        MOCK_PUBLISHER_LAYER_SUBTYPE,
                        SUBSCRIPTION_LAYER_VERSION,
                        PUBLISHER_ID
                },
                PAYLOAD);

        Pair<VmsLayer, byte[]> message = getMockSubscriberClient().receiveMessage();
        assertEquals(SUBSCRIPTION_LAYER, message.first);
        assertArrayEquals(PAYLOAD, message.second);
    }

    // Test injecting a value in the HAL and verifying it propagates to a subscriber.
    @Test
    public void testSimpleAvailableLayers() throws Exception {
        //
        // Offering:
        // Layer             | Dependency
        // ===============================
        // (2, 3, 444), [17] | {}

        // Expected availability:
        // {(2, 3, 444 [17])}
        //
        getMockHalClient().sendMessage(
                VmsMessageType.OFFERING,
                PUBLISHER_ID,
                1, // Number of offered layers

                SUBSCRIPTION_LAYER_ID,
                MOCK_PUBLISHER_LAYER_SUBTYPE,
                SUBSCRIPTION_LAYER_VERSION,
                0 // number of dependencies for layer
        );

        assertEquals(
                Collections.singleton(SUBSCRIPTION_ASSOCIATED_LAYER),
                getMockSubscriberClient().receiveLayerAvailability().getAssociatedLayers());
    }

    // Test injecting a value in the HAL and verifying it propagates to a subscriber after it has
    // subscribed to a layer.
    @Test
    public void testSimpleAvailableLayersAfterSubscription() throws Exception {
        getSubscriberManager().subscribe(SUBSCRIPTION_LAYER);

        //
        // Offering:
        // Layer             | Dependency
        // ===============================
        // (2, 3, 444), [17] | {}

        // Expected availability:
        // {(2, 3, 444 [17])}
        //
        getMockHalClient().sendMessage(
                VmsMessageType.OFFERING, // MessageType
                PUBLISHER_ID,
                1, // Number of offered layers

                SUBSCRIPTION_LAYER_ID,
                MOCK_PUBLISHER_LAYER_SUBTYPE,
                SUBSCRIPTION_LAYER_VERSION,
                0 // number of dependencies for layer
        );

        assertEquals(
                Collections.singleton(SUBSCRIPTION_ASSOCIATED_LAYER),
                getMockSubscriberClient().receiveLayerAvailability().getAssociatedLayers());
    }

    // Test injecting a value in the HAL and verifying it does not propagates to a subscriber after
    // it has cleared its callback.
    @Test
    public void testSimpleAvailableLayersAfterClear() throws Exception {
        getSubscriberManager().clearVmsSubscriberClientCallback();
        //
        // Offering:
        // Layer             | Dependency
        // ===============================
        // (2, 3, 444), [17] | {}

        // Expected availability:
        // {(2, 3, 444 [17])}
        //
        getMockHalClient().sendMessage(
                VmsMessageType.OFFERING, // MessageType
                PUBLISHER_ID,
                1, // Number of offered layers

                SUBSCRIPTION_LAYER_ID,
                SUBSCRIPTION_LAYER_VERSION,
                MOCK_PUBLISHER_LAYER_SUBTYPE,
                0 // number of dependencies for layer
        );

        assertNull(getMockSubscriberClient().receiveLayerAvailability());
    }

    // Test injecting a value in the HAL and verifying it propagates to a subscriber.
    @Test
    public void testComplexAvailableLayers() throws Exception {
        //
        // Offering:
        // Layer             | Dependency
        // =====================================
        // (2, 3, 444), [17] | {}
        // (4, 5, 444), [17] | {(2, 3)}
        // (6, 7, 444), [17] | {(2, 3), (4, 5)}
        // (6, 7, 444), [17] | {(100, 200)}

        // Expected availability:
        // {(2, 3, 444 [17]), (4, 5, 444 [17]), (6, 7, 444 [17])}
        //

        getMockHalClient().sendMessage(
                VmsMessageType.OFFERING, // MessageType
                PUBLISHER_ID,
                4, // Number of offered layers

                SUBSCRIPTION_LAYER_ID,
                MOCK_PUBLISHER_LAYER_SUBTYPE,
                SUBSCRIPTION_LAYER_VERSION,
                0, // number of dependencies for layer

                SUBSCRIPTION_DEPENDANT_LAYER_ID_1,
                MOCK_PUBLISHER_LAYER_SUBTYPE,
                SUBSCRIPTION_DEPENDANT_LAYER_VERSION_1,
                1, // number of dependencies for layer
                SUBSCRIPTION_LAYER_ID,
                MOCK_PUBLISHER_LAYER_SUBTYPE,
                SUBSCRIPTION_LAYER_VERSION,

                SUBSCRIPTION_DEPENDANT_LAYER_ID_2,
                MOCK_PUBLISHER_LAYER_SUBTYPE,
                SUBSCRIPTION_DEPENDANT_LAYER_VERSION_2,
                2, // number of dependencies for layer
                SUBSCRIPTION_LAYER_ID,
                MOCK_PUBLISHER_LAYER_SUBTYPE,
                SUBSCRIPTION_LAYER_VERSION,
                SUBSCRIPTION_DEPENDANT_LAYER_ID_1,
                MOCK_PUBLISHER_LAYER_SUBTYPE,
                SUBSCRIPTION_DEPENDANT_LAYER_VERSION_1,

                SUBSCRIPTION_DEPENDANT_LAYER_ID_2,
                MOCK_PUBLISHER_LAYER_SUBTYPE,
                SUBSCRIPTION_DEPENDANT_LAYER_VERSION_2,
                1, // number of dependencies for layer
                SUBSCRIPTION_UNSUPPORTED_LAYER_ID,
                MOCK_PUBLISHER_LAYER_SUBTYPE,
                SUBSCRIPTION_UNSUPPORTED_LAYER_VERSION
        );

        Set<VmsAssociatedLayer> associatedLayers =
                new HashSet<>(Arrays.asList(
                        SUBSCRIPTION_ASSOCIATED_LAYER,
                        SUBSCRIPTION_DEPENDANT_ASSOCIATED_LAYER_1,
                        SUBSCRIPTION_DEPENDANT_ASSOCIATED_LAYER_2
                ));

        // Verify applications API.
        VmsAvailableLayers availableLayers = getMockSubscriberClient().receiveLayerAvailability();
        assertEquals(associatedLayers, availableLayers.getAssociatedLayers());
        assertEquals(1, availableLayers.getSequence());

        // Verify HAL API.
        ArrayList<Integer> values = getMockHalClient().receiveMessage().value.int32Values;
        int messageType = values.get(VmsAvailabilityStateIntegerValuesIndex.MESSAGE_TYPE);
        int sequenceNumber = values.get(VmsAvailabilityStateIntegerValuesIndex.SEQUENCE_NUMBER);
        int numberLayers =
                values.get(VmsAvailabilityStateIntegerValuesIndex.NUMBER_OF_ASSOCIATED_LAYERS);

        assertEquals(messageType, VmsMessageType.AVAILABILITY_CHANGE);
        assertEquals(1, sequenceNumber);
        assertEquals(3, numberLayers);
    }

    // Test injecting a value in the HAL twice the sequence for availability is incremented.
    @Test
    public void testDoubleOfferingAvailableLayers() throws Exception {
        //
        // Offering:
        // Layer             | Dependency
        // ===============================
        // (2, 3, 444), [17] | {}

        // Expected availability:
        // {(2, 3, 444 [17])}
        //
        int[] offeringMessage = {
                VmsMessageType.OFFERING, // MessageType
                PUBLISHER_ID,
                1, // Number of offered layers

                SUBSCRIPTION_LAYER_ID,
                MOCK_PUBLISHER_LAYER_SUBTYPE,
                SUBSCRIPTION_LAYER_VERSION,
                0 // number of dependencies for layer
        };

        // Inject first offer.
        getMockHalClient().sendMessage(offeringMessage);

        // Verify applications API.
        VmsAvailableLayers availableLayers = getMockSubscriberClient().receiveLayerAvailability();
        assertEquals(
                Collections.singleton(SUBSCRIPTION_ASSOCIATED_LAYER),
                availableLayers.getAssociatedLayers());
        assertEquals(1, availableLayers.getSequence());

        // Verify HAL API.
        ArrayList<Integer> values = getMockHalClient().receiveMessage().value.int32Values;
        int messageType = values.get(VmsAvailabilityStateIntegerValuesIndex.MESSAGE_TYPE);
        int sequenceNumber = values.get(VmsAvailabilityStateIntegerValuesIndex.SEQUENCE_NUMBER);
        int numberLayers =
                values.get(VmsAvailabilityStateIntegerValuesIndex.NUMBER_OF_ASSOCIATED_LAYERS);

        assertEquals(messageType, VmsMessageType.AVAILABILITY_CHANGE);
        assertEquals(1, sequenceNumber);
        assertEquals(1, numberLayers);

        // Inject second offer.
        getMockHalClient().sendMessage(offeringMessage);

        // Verify applications API.
        availableLayers = getMockSubscriberClient().receiveLayerAvailability();
        assertEquals(
                Collections.singleton(SUBSCRIPTION_ASSOCIATED_LAYER),
                availableLayers.getAssociatedLayers());
        assertEquals(2, availableLayers.getSequence());

        // Verify HAL API.
        values = getMockHalClient().receiveMessage().value.int32Values;
        messageType = values.get(VmsAvailabilityStateIntegerValuesIndex.MESSAGE_TYPE);
        sequenceNumber = values.get(VmsAvailabilityStateIntegerValuesIndex.SEQUENCE_NUMBER);
        numberLayers =
                values.get(VmsAvailabilityStateIntegerValuesIndex.NUMBER_OF_ASSOCIATED_LAYERS);

        assertEquals(messageType, VmsMessageType.AVAILABILITY_CHANGE);
        assertEquals(2, sequenceNumber);
        assertEquals(1, numberLayers);

    }

    // Test GetAvailableLayers().
    @Test
    public void testGetAvailableLayers() throws Exception {
        //
        // Offering:
        // Layer             | Dependency
        // ===============================
        // (2, 3, 444), [17] | {}

        // Expected availability:
        // {(2, 3, 444 [17])}
        //
        getMockHalClient().sendMessage(
                VmsMessageType.OFFERING, // MessageType
                PUBLISHER_ID,
                1, // Number of offered layers

                SUBSCRIPTION_LAYER_ID,
                MOCK_PUBLISHER_LAYER_SUBTYPE,
                SUBSCRIPTION_LAYER_VERSION,
                0 // number of dependencies for layer
        );

        // Wait for an availability update to the subscriber to guarantee the state is settled.
        getMockSubscriberClient().receiveLayerAvailability();

        VmsAvailableLayers availableLayers = getSubscriberManager().getAvailableLayers();
        assertEquals(
                Collections.singleton(SUBSCRIPTION_ASSOCIATED_LAYER),
                availableLayers.getAssociatedLayers());
        assertEquals(1, availableLayers.getSequence());
    }
}
