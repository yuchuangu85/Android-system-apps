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

import android.util.Log;

import com.android.car.BLEStreamProtos.BLEMessageProto.BLEMessage;
import com.android.car.BLEStreamProtos.BLEOperationProto.OperationType;
import com.android.car.protobuf.ByteString;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Methods for creating {@link BLEStream} protos
 */
class BLEMessageV1Factory {

    private static final String TAG = "BLEMessageFactory";

    /**
     * The size in bytes of a {@code fixed32} field in the proto.
     * See this <a href="https://developers.google.com/protocol-buffers/docs/encoding">site</a> for
     * more details.
     */
    private static final int FIXED_32_SIZE = 4;

    /**
     * Additional bytes that are needed during the encoding of the {@code payload} field.
     *
     * <p>The {@code payload} field is defined as {@code bytes}, and thus, needs 2 extra bytes to
     * encode: one to encode the field number and another for length delimiting.
     */
    private static final int ADDITIONAL_PAYLOAD_ENCODING_SIZE = 2;

    // The size needed to encode a boolean proto field
    private static final int BOOLEAN_FIELD_ENCODING_SIZE = 1;

    /**
     * The bytes needed to encode the field number in the proto.
     *
     * <p>Since the v1 proto only has 6 fields, it will only take 1 additional byte to encode.
     */
    private static final int FIELD_NUMBER_ENCODING_SIZE = 1;
    /**
     * Current version of the proto.
     */
    private static final int PROTOCOL_VERSION = 1;
    /**
     * The size of the version field in the proto.
     *
     * <p>The version field is a {@code variant} and thus, its size can vary between 1-5 bytes.
     * Since the version is {@code 1}, it will only take 1 byte to encode + 1 byte for the field
     * number.
     */
    private static final int VERSION_SIZE = getEncodedSize(PROTOCOL_VERSION)
            + FIELD_NUMBER_ENCODING_SIZE;
    /**
     * The size of fields in the header that do not change depending on their value.
     *
     * <p>The fixed fields are:
     *
     * <ol>
     * <li>Version (1 byte + 1 byte for field)
     * <li>Packet number (4 bytes + 1 byte for field)
     * <li>Total packets (4 bytes + 1 byte for field)
     * </ol>
     *
     * <p>Note, the version code is an {@code Int32Value} and thus, can vary, but it is always a set
     * value at compile time. Thus, it can be calculated statically.
     */
    private static final int CONSTANT_HEADER_FIELD_SIZE = VERSION_SIZE
            + (FIXED_32_SIZE + FIELD_NUMBER_ENCODING_SIZE)
            + (FIXED_32_SIZE + FIELD_NUMBER_ENCODING_SIZE);

    private BLEMessageV1Factory() {}

    /**
     * Creates an acknowledgement {@link BLEMessage}.
     *
     * <p>This type of proto should be used to let a client know that this device has received
     * a partially completed {@code BLEMessage}.
     *
     * <p>Note, this type of message has an empty {@code payload} field.
     *
     * @return A {@code BLEMessage} with an {@code OperationType} of {@link OperationType.ACK}.
     */
    static BLEMessage makeAcknowledgementMessage() {
        return BLEMessage.newBuilder()
                .setVersion(PROTOCOL_VERSION)
                .setOperation(OperationType.ACK)
                .setPacketNumber(1)
                .setTotalPackets(1)
                .setIsPayloadEncrypted(false)
                .build();
    }

    /**
     * Method used to generate a single message, the packet number and total packets will set to 1
     * by default
     *
     * @param payload   The data object to use as the
     *                  {@link com.android.car.trust.BLEStream.BLEMessage}
     *                  payload
     * @param operation The operation this message represents
     * @return The generated {@link com.android.car.trust.BLEStream.BLEMessage}
     */
    private static BLEMessage makeBLEMessage(byte[] payload, OperationType operation,
            boolean isPayloadEncrypted) {
        return BLEMessage.newBuilder()
                .setVersion(PROTOCOL_VERSION)
                .setOperation(operation)
                .setPacketNumber(1)
                .setTotalPackets(1)
                .setIsPayloadEncrypted(isPayloadEncrypted)
                .setPayload(ByteString.copyFrom(payload))
                .build();
    }

    /**
     * Split given data if necessary to fit within the given {@code maxSize}
     *
     * @param payload   The payload to potentially split across multiple {@link
     *                  com.android.car.trust.BLEStream.BLEMessage}s
     * @param operation The operation this message represents
     * @param maxSize   The maximum size of each chunk
     * @return An array of {@link com.android.car.trust.BLEStream.BLEMessage}s
     */
    public static List<BLEMessage> makeBLEMessages(byte[] payload, OperationType operation,
            int maxSize, boolean isPayloadEncrypted) {
        List<BLEMessage> bleMessages = new ArrayList();
        int maxPayloadSize = maxSize - getProtoHeaderSize(operation, isPayloadEncrypted);
        int payloadLength = payload.length;
        if (payloadLength <= maxPayloadSize) {
            bleMessages.add(makeBLEMessage(payload, operation, isPayloadEncrypted));
            return bleMessages;
        }
        int totalPackets = (int) Math.ceil((double) payloadLength / maxPayloadSize);
        int start = 0;
        int end = maxPayloadSize;
        for (int i = 0; i < totalPackets; i++) {
            bleMessages.add(BLEMessage.newBuilder()
                    .setVersion(PROTOCOL_VERSION)
                    .setOperation(operation)
                    .setPacketNumber(i + 1)
                    .setTotalPackets(totalPackets)
                    .setIsPayloadEncrypted(isPayloadEncrypted)
                    .setPayload(ByteString.copyFrom(Arrays.copyOfRange(payload, start, end)))
                    .build());
            start = end;
            end = Math.min(start + maxPayloadSize, payloadLength);
        }
        return bleMessages;
    }

    /**
     * Returns the header size for the proto in bytes. This method assumes that the proto
     * contain a payload.
     */
    @VisibleForTesting
    static int getProtoHeaderSize(OperationType operation, boolean isPayloadEncrypted) {
        int isPayloadEncryptedFieldSize =
                isPayloadEncrypted ? (BOOLEAN_FIELD_ENCODING_SIZE + FIELD_NUMBER_ENCODING_SIZE) : 0;
        int operationSize = getEncodedSize(operation.getNumber()) + FIELD_NUMBER_ENCODING_SIZE;
        return CONSTANT_HEADER_FIELD_SIZE + operationSize + isPayloadEncryptedFieldSize
                + ADDITIONAL_PAYLOAD_ENCODING_SIZE;
    }

    /**
     * The methods in this section are taken from
     * google3/third_party/swift/swift_protobuf/Sources/SwiftProtobuf/Variant.swift.
     * It should be kept in sync as long as the proto version remains the same.
     *
     * <p>Computes the number of bytes that would be needed to store a 32-bit variant. Negative
     * value is not considered because all proto values should be positive.
     *
     * @param value the data that need to be encoded
     * @return the size of the encoded data
     */
    private static int getEncodedSize(int value) {
        if (value < 0) {
            Log.e(TAG, "Get a negative value from proto");
            return 10;
        }
        if ((value & (~0 << 7)) == 0) {
            return 1;
        }
        if ((value & (~0 << 14)) == 0) {
            return 2;
        }
        if ((value & (~0 << 21)) == 0) {
            return 3;
        }
        if ((value & (~0 << 28)) == 0) {
            return 4;
        }
        return 5;
    }
}
