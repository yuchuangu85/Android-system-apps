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

import android.content.Context
import android.os.SystemClock
import android.text.format.DateFormat

import java.util.Calendar

/**
 * All time data is accessed via this model. This model exists so that time can be mocked for
 * testing purposes.
 */
internal class TimeModel(private val mContext: Context) {
    /**
     * @return the current time in milliseconds
     */
    fun currentTimeMillis(): Long = System.currentTimeMillis()

    /**
     * @return milliseconds since boot, including time spent in sleep
     */
    fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()

    /**
     * @return `true` if 24 hour time format is selected; `false` otherwise
     */
    fun is24HourFormat(): Boolean = DateFormat.is24HourFormat(mContext)

    /**
     * @return a new Calendar with the [.currentTimeMillis]
     */
    val calendar: Calendar
        get() {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = currentTimeMillis()
            return calendar
        }
}