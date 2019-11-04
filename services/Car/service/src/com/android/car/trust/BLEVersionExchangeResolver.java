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

import com.android.car.BLEStreamProtos.VersionExchangeProto.BLEVersionExchange;

/**
 * Resolver of version exchanges between this device and a client device.
 */
class BLEVersionExchangeResolver {
    private static final String TAG = "BLEVersionExchangeResolver";

    // Currently, only version 1 of the messaging and security supported.
    private static final int MESSAGING_VERSION = 1;
    private static final int SECURITY_VERSION = 1;

    /**
     * Return whether or not the given version exchange proto has the a version that is currently
     * supported by this device.
     *
     * @param versionExchange The version exchange proto to resolve
     * @return {@code true} if there is a supported version.
     */
    static boolean hasSupportedVersion(BLEVersionExchange versionExchange) {
        int minMessagingVersion = versionExchange.getMinSupportedMessagingVersion();
        int minSecurityVersion = versionExchange.getMinSupportedSecurityVersion();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Checking for supported version on (minMessagingVersion: "
                    + minMessagingVersion + ", minSecurityVersion: " + minSecurityVersion + ")");
        }

        // Only one supported version, so ensure the minimum version matches.
        return minMessagingVersion == MESSAGING_VERSION && minSecurityVersion == SECURITY_VERSION;
    }

    /**
     * Returns a version exchange proto with the maximum and minimum protocol and security versions
     * this device currently supports.
     */
    static BLEVersionExchange makeVersionExchange() {
        return BLEVersionExchange.newBuilder()
                .setMinSupportedMessagingVersion(MESSAGING_VERSION)
                .setMaxSupportedMessagingVersion(MESSAGING_VERSION)
                .setMinSupportedSecurityVersion(SECURITY_VERSION)
                .setMinSupportedSecurityVersion(SECURITY_VERSION)
                .build();
    }

    private BLEVersionExchangeResolver() {}
}
