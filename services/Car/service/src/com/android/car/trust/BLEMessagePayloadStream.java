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

import android.annotation.NonNull;

import com.android.car.BLEStreamProtos.BLEMessageProto.BLEMessage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Manage a stream in which the {@code payload} field of
 * {@link com.android.car.trust.BLEStream.BLEMessage} is written to.
 */
class BLEMessagePayloadStream {
    private ByteArrayOutputStream mPendingData = new ByteArrayOutputStream();
    private boolean mIsComplete;

    /**
     * Clears this data stream.
     */
    public void reset() {
        mPendingData.reset();
        mIsComplete = false;
    }

    /**
     * Extracts the payload from the given {@code BLEMessage} and writes it to the stream.
     *
     * @param message The {@link com.android.car.trust.BLEStream.BLEMessage} to parse.
     */
    public void write(BLEMessage message) throws IOException {
        mPendingData.write(message.getPayload().toByteArray());
        mIsComplete = message.getPacketNumber() == message.getTotalPackets();
    }

    /**
     * Returns {@code true} if a complete payload has been formed.
     */
    public boolean isComplete() {
        return mIsComplete;
    }

    /**
     * Returns the current contents of a stream as a byte array.
     */
    @NonNull
    public byte[] toByteArray() {
        return mPendingData.toByteArray();
    }
}
