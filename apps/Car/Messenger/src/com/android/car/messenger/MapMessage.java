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

package com.android.car.messenger;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothMapClient;
import android.content.Intent;

import androidx.annotation.Nullable;

/**
 * Represents a message obtained via MAP service from a connected Bluetooth device.
 */
class MapMessage {
    private String mDeviceAddress;
    private String mHandle;
    private String mSenderName;
    @Nullable
    private String mSenderContactUri;
    private String mMessageText;
    private long mReceiveTime;
    private boolean mIsReadOnPhone;
    private boolean mIsReadOnCar;

    /**
     * Constructs a {@link MapMessage} from {@code intent} that was received from MAP service via
     * {@link BluetoothMapClient#ACTION_MESSAGE_RECEIVED} broadcast.
     *
     * @param intent intent received from MAP service
     * @return message constructed from extras in {@code intent}
     * @throws NullPointerException if {@code intent} is missing the device extra
     * @throws IllegalArgumentException if {@code intent} is missing any other required extras
     */
    public static MapMessage parseFrom(Intent intent) {
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        String handle = intent.getStringExtra(BluetoothMapClient.EXTRA_MESSAGE_HANDLE);
        String senderUri = intent.getStringExtra(BluetoothMapClient.EXTRA_SENDER_CONTACT_URI);
        String senderName = intent.getStringExtra(BluetoothMapClient.EXTRA_SENDER_CONTACT_NAME);
        String messageText = intent.getStringExtra(android.content.Intent.EXTRA_TEXT);
        long receiveTime = intent.getLongExtra(BluetoothMapClient.EXTRA_MESSAGE_TIMESTAMP,
                System.currentTimeMillis());
        boolean isRead = intent.getBooleanExtra(BluetoothMapClient.EXTRA_MESSAGE_READ_STATUS,
                false);

        return new MapMessage(
                device.getAddress(),
                handle,
                senderName,
                senderUri,
                messageText,
                receiveTime,
                isRead
        );
    }

    private MapMessage(String deviceAddress,
            String handle,
            String senderName,
            @Nullable String senderContactUri,
            String messageText,
            long receiveTime,
            boolean isRead) {
        boolean missingDevice = (deviceAddress == null);
        boolean missingHandle = (handle == null);
        boolean missingSenderName = (senderName == null);
        boolean missingText = (messageText == null);
        if (missingDevice || missingHandle || missingSenderName || missingText) {
            StringBuilder builder = new StringBuilder("Missing required fields:");
            if (missingDevice) {
                builder.append(" device");
            }
            if (missingHandle) {
                builder.append(" handle");
            }
            if (missingSenderName) {
                builder.append(" senderName");
            }
            if (missingText) {
                builder.append(" messageText");
            }
            throw new IllegalArgumentException(builder.toString());
        }
        mDeviceAddress = deviceAddress;
        mHandle = handle;
        mMessageText = messageText;
        mSenderContactUri = senderContactUri;
        mSenderName = senderName;
        mReceiveTime = receiveTime;
        mIsReadOnPhone = isRead;
    }

    /**
     * Returns the bluetooth address of the device from which this message was received.
     */
    public String getDeviceAddress() {
        return mDeviceAddress;
    }

    /**
     * Returns a unique handle for this message.
     * Note: The handle is only required to be unique for the lifetime of a single MAP session.
     */
    public String getHandle() {
        return mHandle;
    }

    /**
     * Returns the milliseconds since epoch at which this message notification was received on the
     * head-unit.
     */
    public long getReceiveTime() {
        return mReceiveTime;
    }

    /**
     * Returns the contact name as obtained from the device.
     * If contact is in the device's address-book, this is typically the contact name.
     * Otherwise it will be the phone number.
     */
    public String getSenderName() {
        return mSenderName;
    }

    /**
     * Returns the sender's phone number available as a URI string.
     * Note: iPhone's don't provide these.
     */
    @Nullable
    public String getSenderContactUri() {
        return mSenderContactUri;
    }

    /**
     * Returns the actual content of the message.
     */
    public String getMessageText() {
        return mMessageText;
    }

    public void markMessageAsRead() {
        mIsReadOnCar = true;
    }

    /**
     * Returns {@code true} if message was read on the phone before it was received on the car.
     */
    public boolean isReadOnPhone() {
        return mIsReadOnPhone;
    }

    /**
     * Returns {@code true} if message was read on the car.
     */
    public boolean isReadOnCar() {
        return mIsReadOnCar;
    }

    @Override
    public String toString() {
        return "MapMessage{" +
                "mDeviceAddress=" + mDeviceAddress +
                ", mHandle='" + mHandle + '\'' +
                ", mMessageText='" + mMessageText + '\'' +
                ", mSenderContactUri='" + mSenderContactUri + '\'' +
                ", mSenderName='" + mSenderName + '\'' +
                ", mReceiveTime=" + mReceiveTime + '\'' +
                ", mIsReadOnPhone= " + mIsReadOnPhone + '\'' +
                ", mIsReadOnCar= " + mIsReadOnCar +
                "}";
    }
}
