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

package com.android.car.trust;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.runner.AndroidJUnit4;

import com.android.car.BLEStreamProtos.BLEMessageProto.BLEMessage;
import com.android.car.BLEStreamProtos.BLEOperationProto.OperationType;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.List;

/**
 * Unit test for {@link BLEMessagePayloadStream}.
 *
 * <p>Run:
 * {@code atest BLEMessagePayloadStreamTest}
 */
@RunWith(AndroidJUnit4.class)
public class BLEMessagePayloadStreamTest {
    private static final boolean IS_MESSAGE_ENCRYPTED = false;
    private static final OperationType OPERATION_TYPE = OperationType.CLIENT_MESSAGE;
    private static final byte[] TEST_MESSAGE_PAYLOAD = "testMessage".getBytes();
    private static final int TEST_SINGLE_MESSAGE_SIZE =
            TEST_MESSAGE_PAYLOAD.length + BLEMessageV1Factory.getProtoHeaderSize(
                    OPERATION_TYPE, IS_MESSAGE_ENCRYPTED);

    private BLEMessagePayloadStream mBLEMessagePayloadStream;
    private List<BLEMessage> mBleMessages;

    @Before
    public void setUp() {
        mBLEMessagePayloadStream = new BLEMessagePayloadStream();
    }

    @Test
    public void testReset_getEmptyValue() throws IOException {
        mBleMessages = BLEMessageV1Factory.makeBLEMessages(TEST_MESSAGE_PAYLOAD, OPERATION_TYPE,
                TEST_SINGLE_MESSAGE_SIZE, IS_MESSAGE_ENCRYPTED);
        mBLEMessagePayloadStream.write(mBleMessages.get(0));
        assertThat(mBLEMessagePayloadStream.toByteArray()).isNotEmpty();

        mBLEMessagePayloadStream.reset();

        assertThat(mBLEMessagePayloadStream.toByteArray()).isEmpty();
        assertThat(mBLEMessagePayloadStream.isComplete()).isFalse();
    }

    @Test
    public void testMessageContent_withOneMessage_shouldBeConsistent() throws IOException {
        mBleMessages = BLEMessageV1Factory.makeBLEMessages(TEST_MESSAGE_PAYLOAD, OPERATION_TYPE,
                TEST_SINGLE_MESSAGE_SIZE, IS_MESSAGE_ENCRYPTED);
        assertThat(mBleMessages).hasSize(1);
        for (BLEMessage message : mBleMessages) {
            mBLEMessagePayloadStream.write(message);
        }

        assertThat(mBLEMessagePayloadStream.toByteArray()).isEqualTo(TEST_MESSAGE_PAYLOAD);
    }

    @Test
    public void testMessageContent_withMultipleMessages_shouldBeConsistent() throws IOException {
        // Ensure the message to be split into 2 packets.
        int maxSize = TEST_SINGLE_MESSAGE_SIZE - 1;
        mBleMessages = BLEMessageV1Factory.makeBLEMessages(TEST_MESSAGE_PAYLOAD,
                OPERATION_TYPE, maxSize, IS_MESSAGE_ENCRYPTED);
        assertThat(mBleMessages).hasSize(2);
        for (BLEMessage message : mBleMessages) {
            mBLEMessagePayloadStream.write(message);
        }

        assertThat(mBLEMessagePayloadStream.toByteArray()).isEqualTo(
                TEST_MESSAGE_PAYLOAD);
    }

    @Test
    public void testMessageStreamComeToEnd_withOneMessage_streamCompleted() throws IOException {
        mBleMessages = BLEMessageV1Factory.makeBLEMessages(TEST_MESSAGE_PAYLOAD, OPERATION_TYPE,
                TEST_SINGLE_MESSAGE_SIZE, IS_MESSAGE_ENCRYPTED);

        mBLEMessagePayloadStream.write(mBleMessages.get(mBleMessages.size() - 1));

        assertThat(mBLEMessagePayloadStream.isComplete()).isTrue();
    }

    @Test
    public void testMessageStreamComeToEnd_withMultipleMessages_streamCompleted()
            throws IOException {
        // Ensure the message to be split into 2 packets.
        int maxSize = TEST_SINGLE_MESSAGE_SIZE - 1;
        mBleMessages = BLEMessageV1Factory.makeBLEMessages(TEST_MESSAGE_PAYLOAD,
                OPERATION_TYPE, maxSize, IS_MESSAGE_ENCRYPTED);

        mBLEMessagePayloadStream.write(mBleMessages.get(mBleMessages.size() - 1));

        assertThat(mBLEMessagePayloadStream.isComplete()).isTrue();
    }
}
