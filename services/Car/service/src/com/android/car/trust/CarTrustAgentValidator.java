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

/**
 * A utility class that provides validations.
 */
final class CarTrustAgentValidator {

    /**
     * Returns whether device id received during trust agent enrollment is valid.
     * <p>
     * Default implementation has no expectation (always returns true).
     *
     * @param value Data received in the initial state of enrollment.
     * @return {@code True} if input is valid; {@code false} otherwise.
     */
    static boolean isValidEnrollmentDeviceId(byte[] value) {
        return true;
    }

    /**
     * Returns whether device id received during trust agent unlock is valid.
     * <p>
     * Default implementation has no expectation (always returns true).
     *
     * @param value Data received in the initial state of unlocking.
     * @return {@code True} if input is valid; {@code false} otherwise.
     */
    static boolean isValidUnlockDeviceId(byte[] value) {
        return true;
    }

    private CarTrustAgentValidator() {
        // Do not instantiate.
    }
}
