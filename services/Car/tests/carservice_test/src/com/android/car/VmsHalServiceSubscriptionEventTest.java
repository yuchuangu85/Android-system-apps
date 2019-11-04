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

import android.car.vms.VmsLayer;
import android.hardware.automotive.vehicle.V2_0.VmsBaseMessageIntegerValuesIndex;
import android.hardware.automotive.vehicle.V2_0.VmsMessageType;
import android.hardware.automotive.vehicle.V2_0.VmsSubscriptionsStateIntegerValuesIndex;

import androidx.test.filters.MediumTest;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

@MediumTest
public class VmsHalServiceSubscriptionEventTest extends MockedVmsTestBase {
    @Test
    public void testEmptySubscriptions() throws Exception {
        List<VmsLayer> layers = new ArrayList<>();
        subscriptionTestLogic(layers);
    }

    @Test
    public void testOneSubscription() throws Exception {
        List<VmsLayer> layers =
                Collections.singletonList(new VmsLayer(8, 0, 3));
        subscriptionTestLogic(layers);
    }

    @Test
    public void testManySubscriptions() throws Exception {
        List<VmsLayer> layers = Arrays.asList(
                new VmsLayer(8, 1, 3),
                new VmsLayer(5, 2, 1),
                new VmsLayer(3, 3, 9),
                new VmsLayer(2, 4, 7),
                new VmsLayer(9, 5, 3));
        subscriptionTestLogic(layers);
    }

    /**
     * First, it subscribes to the given layers. Then it validates that a subscription request
     * responds with the same layers.
     */
    private void subscriptionTestLogic(List<VmsLayer> layers) throws Exception {
        int sequenceNumber = 0;
        for (VmsLayer layer : layers) {
            sequenceNumber++;
            subscribeViaHal(sequenceNumber, layer);
        }
        // Send subscription request.
        getMockHalClient().sendMessage(VmsMessageType.SUBSCRIPTIONS_REQUEST);

        // Validate response.
        List<Integer> v = getMockHalClient().receiveMessage().value.int32Values;
        assertEquals(VmsMessageType.SUBSCRIPTIONS_RESPONSE,
                (int) v.get(VmsBaseMessageIntegerValuesIndex.MESSAGE_TYPE));
        assertEquals(sequenceNumber,
                (int) v.get(VmsSubscriptionsStateIntegerValuesIndex.SEQUENCE_NUMBER));
        assertEquals(layers.size(),
                (int) v.get(VmsSubscriptionsStateIntegerValuesIndex.NUMBER_OF_LAYERS));
        List<VmsLayer> receivedLayers = new ArrayList<>();
        int start = VmsSubscriptionsStateIntegerValuesIndex.SUBSCRIPTIONS_START;
        int end = VmsSubscriptionsStateIntegerValuesIndex.SUBSCRIPTIONS_START + 3 * layers.size();
        while (start < end) {
            int type = v.get(start++);
            int subtype = v.get(start++);
            int version = v.get(start++);
            receivedLayers.add(new VmsLayer(type, subtype, version));
        }
        assertEquals(new HashSet<>(layers), new HashSet<>(receivedLayers));
    }

    /**
     * Subscribes to a layer, waits for the state change to propagate back to the HAL layer and
     * validates the propagated message.
     */
    private void subscribeViaHal(int sequenceNumber, VmsLayer layer) throws Exception {
        // Send subscribe request.
        getMockHalClient().sendMessage(
                VmsMessageType.SUBSCRIBE,
                layer.getType(),
                layer.getSubtype(),
                layer.getVersion());

        // Validate response.
        List<Integer> v = getMockHalClient().receiveMessage().value.int32Values;
        assertEquals(VmsMessageType.SUBSCRIPTIONS_CHANGE,
                (int) v.get(VmsBaseMessageIntegerValuesIndex.MESSAGE_TYPE));
        assertEquals(sequenceNumber,
                (int) v.get(VmsSubscriptionsStateIntegerValuesIndex.SEQUENCE_NUMBER));
        assertEquals(sequenceNumber,
                (int) v.get(VmsSubscriptionsStateIntegerValuesIndex.NUMBER_OF_LAYERS));
    }
}
