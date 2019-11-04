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
import static org.junit.Assert.assertNotNull;

import android.car.vms.VmsLayer;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VmsBaseMessageIntegerValuesIndex;
import android.hardware.automotive.vehicle.V2_0.VmsMessageType;
import android.hardware.automotive.vehicle.V2_0.VmsMessageWithLayerIntegerValuesIndex;

import androidx.test.filters.MediumTest;

import org.junit.Test;

@MediumTest
public class VmsPublisherClientServiceTest extends MockedVmsTestBase {
    private static final int MOCK_PUBLISHER_LAYER_ID = 12;
    private static final int MOCK_PUBLISHER_LAYER_VERSION = 34;
    private static final int MOCK_PUBLISHER_LAYER_SUBTYPE = 56;
    public static final int MOCK_PUBLISHER_ID = 1234;
    public static final VmsLayer MOCK_PUBLISHER_LAYER =
            new VmsLayer(MOCK_PUBLISHER_LAYER_ID,
                    MOCK_PUBLISHER_LAYER_SUBTYPE,
                    MOCK_PUBLISHER_LAYER_VERSION);
    public static final byte[] PAYLOAD = new byte[]{1, 1, 2, 3, 5, 8, 13};

    @Test
    public void testPublish() throws Exception {
        MockHalClient client = getMockHalClient();
        client.sendMessage(
                VmsMessageType.SUBSCRIBE,
                MOCK_PUBLISHER_LAYER_ID,
                MOCK_PUBLISHER_LAYER_SUBTYPE,
                MOCK_PUBLISHER_LAYER_VERSION);

        getMockPublisherClient().publish(MOCK_PUBLISHER_LAYER, MOCK_PUBLISHER_ID, PAYLOAD);

        VehiclePropValue message;
        do {
            message = client.receiveMessage();
        } while (message != null && message.value.int32Values.get(
                VmsBaseMessageIntegerValuesIndex.MESSAGE_TYPE) != VmsMessageType.DATA);
        assertNotNull("No data message received", message);

        VehiclePropValue.RawValue rawValue = message.value;
        int messageType = rawValue.int32Values.get(
                VmsMessageWithLayerIntegerValuesIndex.MESSAGE_TYPE);
        int layerId = rawValue.int32Values.get(
                VmsMessageWithLayerIntegerValuesIndex.LAYER_TYPE);
        int layerVersion = rawValue.int32Values.get(
                VmsMessageWithLayerIntegerValuesIndex.LAYER_VERSION);
        byte[] payload = new byte[rawValue.bytes.size()];
        for (int i = 0; i < rawValue.bytes.size(); ++i) {
            payload[i] = rawValue.bytes.get(i);
        }
        assertEquals(VmsMessageType.DATA, messageType);
        assertEquals(MOCK_PUBLISHER_LAYER_ID, layerId);
        assertEquals(MOCK_PUBLISHER_LAYER_VERSION, layerVersion);
        assertArrayEquals(PAYLOAD, payload);
    }
}
