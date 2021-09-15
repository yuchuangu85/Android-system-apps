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
import android.text.format.DateUtils.HOUR_IN_MILLIS
import android.text.format.DateUtils.MINUTE_IN_MILLIS
import android.text.format.DateUtils.SECOND_IN_MILLIS
import androidx.annotation.StringRes

import com.android.deskclock.R
import com.android.deskclock.Utils

object TimerStringFormatter {
    /**
     * Format "7 hours 52 minutes 14 seconds remaining"
     */
    @JvmStatic
    fun formatTimeRemaining(
        context: Context,
        remainingTime: Long,
        shouldShowSeconds: Boolean
    ): String? {
        var roundedHours = (remainingTime / HOUR_IN_MILLIS).toInt()
        var roundedMinutes = (remainingTime / MINUTE_IN_MILLIS % 60).toInt()
        var roundedSeconds = (remainingTime / SECOND_IN_MILLIS % 60).toInt()

        val seconds: Int
        val minutes: Int
        val hours: Int
        if (remainingTime % SECOND_IN_MILLIS != 0L && shouldShowSeconds) {
            // Add 1 because there's a partial second.
            roundedSeconds += 1
            if (roundedSeconds == 60) {
                // Wind back and fix the hours and minutes as needed.
                seconds = 0
                roundedMinutes += 1
                if (roundedMinutes == 60) {
                    minutes = 0
                    roundedHours += 1
                    hours = roundedHours
                } else {
                    minutes = roundedMinutes
                    hours = roundedHours
                }
            } else {
                seconds = roundedSeconds
                minutes = roundedMinutes
                hours = roundedHours
            }
        } else {
            // Already perfect precision, or we don't want to consider seconds at all.
            seconds = roundedSeconds
            minutes = roundedMinutes
            hours = roundedHours
        }

        val minSeq = Utils.getNumberFormattedQuantityString(context, R.plurals.minutes, minutes)
        val hourSeq = Utils.getNumberFormattedQuantityString(context, R.plurals.hours, hours)
        val secSeq = Utils.getNumberFormattedQuantityString(context, R.plurals.seconds, seconds)

        // The verb "remaining" may have to change tense for singular subjects in some languages.
        val remainingSuffix: String =
                context.getString(if (minutes > 1 || hours > 1 || seconds > 1) {
                    R.string.timer_remaining_multiple
                } else {
                    R.string.timer_remaining_single
                })

        val showHours = hours > 0
        val showMinutes = minutes > 0
        val showSeconds = seconds > 0 && shouldShowSeconds

        var formatStringId = -1
        if (showHours) {
            formatStringId = if (showMinutes) {
                if (showSeconds) {
                    R.string.timer_notifications_hours_minutes_seconds
                } else {
                    R.string.timer_notifications_hours_minutes
                }
            } else if (showSeconds) {
                R.string.timer_notifications_hours_seconds
            } else {
                R.string.timer_notifications_hours
            }
        } else if (showMinutes) {
            formatStringId = if (showSeconds) {
                R.string.timer_notifications_minutes_seconds
            } else {
                R.string.timer_notifications_minutes
            }
        } else if (showSeconds) {
            formatStringId = R.string.timer_notifications_seconds
        } else if (!shouldShowSeconds) {
            formatStringId = R.string.timer_notifications_less_min
        }

        return if (formatStringId == -1) {
            null
        } else {
            String.format(context.getString(formatStringId), hourSeq, minSeq,
                    remainingSuffix, secSeq)
        }
    }

    @JvmStatic
    fun formatString(
        context: Context,
        @StringRes stringResId: Int,
        currentTime: Long,
        shouldShowSeconds: Boolean
    ): String {
        return String.format(context.getString(stringResId),
                formatTimeRemaining(context, currentTime, shouldShowSeconds))
    }
}