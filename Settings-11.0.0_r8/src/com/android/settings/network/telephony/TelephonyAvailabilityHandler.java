/*
 * Copyright (C) 2020 The Android Open Source Project
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

/**
 * Interface letting {@link TelephonyTogglePreferenceController and
 * @link TelephonyBasePreferenceController} can handle availability status.
 */
package com.android.settings.network.telephony;

import android.content.Context;

public interface TelephonyAvailabilityHandler {

    /**
     * Set availability status of preference controller to a fixed value.
     * @param status is the given status. Which will be reported from
     * {@link BasePreferenceController#getAvailabilityStatus()}
     */
    void setAvailabilityStatus(int status);

    /**
     * Do not set availability, use
     * {@link MobileNetworkUtils#getAvailability(Context, int, TelephonyAvailabilityCallback)}
     * to get the availability.
     */
    void unsetAvailabilityStatus();
}
