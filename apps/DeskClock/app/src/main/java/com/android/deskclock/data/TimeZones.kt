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

package com.android.deskclock.data

import android.text.TextUtils

/**
 * A read-only domain object representing the timezones from which to choose a "home" timezone.
 */
class TimeZones internal constructor(
    val timeZoneIds: Array<CharSequence>,
    val timeZoneNames: Array<CharSequence>
) {

    /**
     * @param timeZoneId identifies the timezone to locate
     * @return the timezone name with the `timeZoneId`; `null` if it does not exist
     */
    fun getTimeZoneName(timeZoneId: CharSequence?): CharSequence? {
        for (i in timeZoneIds.indices) {
            if (TextUtils.equals(timeZoneId, timeZoneIds[i])) {
                return timeZoneNames[i]
            }
        }

        return null
    }

    /**
     * @param timeZoneId identifies the timezone to locate
     * @return `true` iff the timezone with the given id is present
     */
    operator fun contains(timeZoneId: String?): Boolean {
        return getTimeZoneName(timeZoneId) != null
    }
}