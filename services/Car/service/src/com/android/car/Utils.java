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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.util.SparseArray;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Some potentially useful static methods.
 */
public class Utils {
    static final Boolean DBG = false;
    // https://developer.android.com/reference/java/util/UUID
    private static final int UUID_LENGTH = 16;


    /*
     * Maps of types and status to human readable strings
     */

    private static final SparseArray<String> sAdapterStates = new SparseArray<String>();
    private static final SparseArray<String> sBondStates = new SparseArray<String>();
    private static final SparseArray<String> sConnectionStates = new SparseArray<String>();
    private static final SparseArray<String> sProfileNames = new SparseArray<String>();
    static {

        // Bluetooth Adapter states
        sAdapterStates.put(BluetoothAdapter.STATE_ON, "On");
        sAdapterStates.put(BluetoothAdapter.STATE_OFF, "Off");
        sAdapterStates.put(BluetoothAdapter.STATE_TURNING_ON, "Turning On");
        sAdapterStates.put(BluetoothAdapter.STATE_TURNING_OFF, "Turning Off");

        // Device Bonding states
        sBondStates.put(BluetoothDevice.BOND_BONDED, "Bonded");
        sBondStates.put(BluetoothDevice.BOND_BONDING, "Bonding");
        sBondStates.put(BluetoothDevice.BOND_NONE, "Unbonded");

        // Device and Profile Connection states
        sConnectionStates.put(BluetoothAdapter.STATE_CONNECTED, "Connected");
        sConnectionStates.put(BluetoothAdapter.STATE_DISCONNECTED, "Disconnected");
        sConnectionStates.put(BluetoothAdapter.STATE_CONNECTING, "Connecting");
        sConnectionStates.put(BluetoothAdapter.STATE_DISCONNECTING, "Disconnecting");

        // Profile Names
        sProfileNames.put(BluetoothProfile.HEADSET, "HFP Server");
        sProfileNames.put(BluetoothProfile.A2DP, "A2DP Source");
        sProfileNames.put(BluetoothProfile.HEALTH, "HDP");
        sProfileNames.put(BluetoothProfile.HID_HOST, "HID Host");
        sProfileNames.put(BluetoothProfile.PAN, "PAN");
        sProfileNames.put(BluetoothProfile.PBAP, "PBAP Server");
        sProfileNames.put(BluetoothProfile.GATT, "GATT Client");
        sProfileNames.put(BluetoothProfile.GATT_SERVER, "GATT Server");
        sProfileNames.put(BluetoothProfile.MAP, "MAP Server");
        sProfileNames.put(BluetoothProfile.SAP, "SAP");
        sProfileNames.put(BluetoothProfile.A2DP_SINK, "A2DP Sink");
        sProfileNames.put(BluetoothProfile.AVRCP_CONTROLLER, "AVRCP Controller");
        sProfileNames.put(BluetoothProfile.AVRCP, "AVRCP Target");
        sProfileNames.put(BluetoothProfile.HEADSET_CLIENT, "HFP Client");
        sProfileNames.put(BluetoothProfile.PBAP_CLIENT, "PBAP Client");
        sProfileNames.put(BluetoothProfile.MAP_CLIENT, "MAP Client");
        sProfileNames.put(BluetoothProfile.HID_DEVICE, "HID Device");
        sProfileNames.put(BluetoothProfile.OPP, "OPP");
        sProfileNames.put(BluetoothProfile.HEARING_AID, "Hearing Aid");
    }

    static String getDeviceDebugInfo(BluetoothDevice device) {
        if (device == null) {
            return "(null)";
        }
        return "(name = " + device.getName() + ", addr = " + device.getAddress() + ")";
    }

    static String getProfileName(int profile) {
        String name = sProfileNames.get(profile, "Unknown");
        return "(" + profile + ") " + name;
    }

    static String getConnectionStateName(int state) {
        String name = sConnectionStates.get(state, "Unknown");
        return "(" + state + ") " + name;
    }

    static String getBondStateName(int state) {
        String name = sBondStates.get(state, "Unknown");
        return "(" + state + ") " + name;
    }

    static String getAdapterStateName(int state) {
        String name = sAdapterStates.get(state, "Unknown");
        return "(" + state + ") " + name;
    }

    static String getProfilePriorityName(int priority) {
        String name = "";
        if (priority >= BluetoothProfile.PRIORITY_AUTO_CONNECT) {
            name = "PRIORITY_AUTO_CONNECT";
        } else if (priority >= BluetoothProfile.PRIORITY_ON) {
            name = "PRIORITY_ON";
        } else if (priority >= BluetoothProfile.PRIORITY_OFF) {
            name = "PRIORITY_OFF";
        } else {
            name = "PRIORITY_UNDEFINED";
        }
        return "(" + priority + ") " + name;
    }

    /**
     * An utility class to dump transition events across different car service components.
     * The output will be of the form
     * <p>
     * "Time <svc name>: [optional context information] changed from <from state> to <to state>"
     * This can be used in conjunction with the dump() method to dump this information through
     * adb shell dumpsys activity service com.android.car
     * <p>
     * A specific service in CarService can choose to use a circular buffer of N records to keep
     * track of the last N transitions.
     */
    public static class TransitionLog {
        private String mServiceName; // name of the service or tag
        private int mFromState; // old state
        private int mToState; // new state
        private long mTimestampMs; // System.currentTimeMillis()
        private String mExtra; // Additional information as a String

        public TransitionLog(String name, int fromState, int toState, long timestamp,
                String extra) {
            this(name, fromState, toState, timestamp);
            mExtra = extra;
        }

        public TransitionLog(String name, int fromState, int toState, long timeStamp) {
            mServiceName = name;
            mFromState = fromState;
            mToState = toState;
            mTimestampMs = timeStamp;
        }

        private CharSequence timeToLog(long timestamp) {
            return android.text.format.DateFormat.format("MM-dd HH:mm:ss", timestamp);
        }

        @Override
        public String toString() {
            return timeToLog(mTimestampMs) + " " + mServiceName + ": " + (mExtra != null ? mExtra
                    : "") + " changed from " + mFromState + " to " + mToState;
        }
    }

    /**
     * Returns a byte buffer corresponding to the passed long argument.
     *
     * @param primitive data to convert format.
     */
    public static byte[] longToBytes(long primitive) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(primitive);
        return buffer.array();
    }

    /**
     * Returns a byte buffer corresponding to the passed long argument.
     *
     * @param array data to convert format.
     */
    public static long bytesToLong(byte[] array) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.SIZE / Byte.SIZE);
        buffer.put(array);
        buffer.flip();
        long value = buffer.getLong();
        return value;
    }

    /**
     * Returns a String in Hex format that is formed from the bytes in the byte array
     * Useful for debugging
     *
     * @param array the byte array
     * @return the Hex string version of the input byte array
     */
    public static String byteArrayToHexString(byte[] array) {
        StringBuilder sb = new StringBuilder(array.length * 2);
        for (byte b : array) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Convert UUID to Big Endian byte array
     *
     * @param uuid UUID to convert
     * @return the byte array representing the UUID
     */
    @NonNull
    public static byte[] uuidToBytes(@NonNull UUID uuid) {

        return ByteBuffer.allocate(UUID_LENGTH)
                .order(ByteOrder.BIG_ENDIAN)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }

    /**
     * Convert Big Endian byte array to UUID
     *
     * @param bytes byte array to convert
     * @return the UUID representing the byte array, or null if not a valid UUID
     */
    @Nullable
    public static UUID bytesToUUID(@NonNull byte[] bytes) {
        if (bytes.length != UUID_LENGTH) {
            return null;
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    /**
     * Generate a random zero-filled string of given length
     *
     * @param length of string
     * @return generated string
     */
    @SuppressLint("DefaultLocale")  // Should always have the same format regardless of locale
    public static String generateRandomNumberString(int length) {
        return String.format("%0" + length + "d",
                ThreadLocalRandom.current().nextInt((int) Math.pow(10, length)));
    }


    /**
     * Concatentate the given 2 byte arrays
     *
     * @param a input array 1
     * @param b input array 2
     * @return concatenated array of arrays 1 and 2
     */
    @Nullable
    public static byte[] concatByteArrays(@Nullable byte[] a, @Nullable byte[] b) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            if (a != null) {
                outputStream.write(a);
            }
            if (b != null) {
                outputStream.write(b);
            }
        } catch (IOException e) {
            return null;
        }
        return outputStream.toByteArray();
    }

}
