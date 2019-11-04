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

/**
 * Helper class for logging trusted device related events, e.g. unlock process.
 *
 * Events are logged with timestamp in fixed format for parsing and further analyzing.
 */
final class EventLog {
    private static final String UNLOCK_TAG = "CarTrustAgentUnlockEvent";
    private static final String ENROLL_TAG = "CarTrustAgentEnrollmentEvent";

    // Enrollment events.
    static final String STOP_ENROLLMENT_ADVERTISING = "STOP_ENROLLMENT_ADVERTISING";
    static final String START_ENROLLMENT_ADVERTISING = "START_ENROLLMENT_ADVERTISING";
    static final String ENROLLMENT_HANDSHAKE_ACCEPTED = "ENROLLMENT_HANDSHAKE_ACCEPTED";
    static final String ENROLLMENT_ENCRYPTION_STATE = "ENROLLMENT_ENCRYPTION_STATE";
    static final String SHOW_VERIFICATION_CODE = "SHOW_VERIFICATION_CODE";
    static final String ENCRYPTION_KEY_SAVED = "ENCRYPTION_KEY_SAVED";
    static final String ESCROW_TOKEN_ADDED = "ESCROW_TOKEN_ADDED";

    // Unlock events.
    static final String START_UNLOCK_ADVERTISING = "START_UNLOCK_ADVERTISING";
    static final String STOP_UNLOCK_ADVERTISING = "STOP_UNLOCK_ADVERTISING";
    static final String UNLOCK_SERVICE_INIT = "UNLOCK_SERVICE_INIT";
    static final String CLIENT_AUTHENTICATED = "CLIENT_AUTHENTICATED";
    static final String UNLOCK_CREDENTIALS_RECEIVED = "UNLOCK_CREDENTIALS_RECEIVED";
    static final String WAITING_FOR_CLIENT_AUTH = "WAITING_FOR_CLIENT_AUTH";
    static final String USER_UNLOCKED = "USER_UNLOCKED";
    static final String UNLOCK_ENCRYPTION_STATE = "UNLOCK_ENCRYPTION_STATE";
    static final String BLUETOOTH_STATE_CHANGED = "BLUETOOTH_STATE_CHANGED";

    // Shared events.
    static final String REMOTE_DEVICE_CONNECTED = "REMOTE_DEVICE_CONNECTED";
    static final String RECEIVED_DEVICE_ID = "RECEIVED_DEVICE_ID";

    private EventLog() {
        // Do not instantiate.
    }

    /**
     * Logs [timestamp and event] with unlock tag.
     * Format is "timestamp: <system time in milli-seconds> - <eventType>
     */
    static void logUnlockEvent(String eventType) {
        if (Log.isLoggable(UNLOCK_TAG, Log.INFO)) {
            Log.i(UNLOCK_TAG,
                    String.format("timestamp: %d - %s", System.currentTimeMillis(), eventType));
        }
    }

    /**
     * Logs [timestamp, event, and value] with unlock tag.
     * Format is "timestamp: <system time in milli-seconds> - <eventType>: <value>
     */
    static void logUnlockEvent(String eventType, int value) {
        if (Log.isLoggable(UNLOCK_TAG, Log.INFO)) {
            Log.i(UNLOCK_TAG, String.format("timestamp: %d - %s: %d",
                    System.currentTimeMillis(), eventType, value));
        }
    }

    /**
     * Logs [timestamp and event] with enrollment tag.
     * Format is "timestamp: <system time in milli-seconds> - <eventType>
     */
    static void logEnrollmentEvent(String eventType) {
        if (Log.isLoggable(ENROLL_TAG, Log.INFO)) {
            Log.i(ENROLL_TAG, String.format(
                    "timestamp: %d - %s", System.currentTimeMillis(), eventType));
        }
    }

    /**
     * Logs [timestamp, event, and value] with enrollment tag.
     * Format is "timestamp: <system time in milli-seconds> - <eventType>: <value>
     */
    static void logEnrollmentEvent(String eventType, int value) {
        if (Log.isLoggable(ENROLL_TAG, Log.INFO)) {
            Log.i(ENROLL_TAG, String.format("timestamp: %d - %s: %d",
                    System.currentTimeMillis(), eventType, value));
        }
    }
}
