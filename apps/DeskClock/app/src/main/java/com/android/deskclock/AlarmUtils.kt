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
 * limitations under the License
 */
package com.android.deskclock

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.View
import android.widget.Toast
import androidx.annotation.VisibleForTesting

import com.android.deskclock.provider.AlarmInstance
import com.android.deskclock.widget.toast.SnackbarManager
import com.android.deskclock.widget.toast.ToastManager

import com.google.android.material.snackbar.Snackbar

import java.util.Calendar
import java.util.Locale

/**
 * Static utility methods for Alarms.
 */
object AlarmUtils {
    @JvmStatic
    fun getFormattedTime(context: Context, time: Calendar): String {
        val skeleton = if (DateFormat.is24HourFormat(context)) "EHm" else "Ehma"
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton)
        return DateFormat.format(pattern, time) as String
    }

    @JvmStatic
    fun getFormattedTime(context: Context, timeInMillis: Long): String {
        val c = Calendar.getInstance()
        c.timeInMillis = timeInMillis
        return getFormattedTime(context, c)
    }

    @JvmStatic
    fun getAlarmText(context: Context, instance: AlarmInstance, includeLabel: Boolean): String {
        val alarmTimeStr: String = getFormattedTime(context, instance.alarmTime)
        return if (instance.mLabel!!.isEmpty() || !includeLabel) {
            alarmTimeStr
        } else {
            alarmTimeStr + " - " + instance.mLabel
        }
    }

    /**
     * format "Alarm set for 2 days, 7 hours, and 53 minutes from now."
     */
    @VisibleForTesting
    fun formatElapsedTimeUntilAlarm(context: Context, delta: Long): String {
        // If the alarm will ring within 60 seconds, just report "less than a minute."
        var variableDelta = delta
        val formats = context.resources.getStringArray(R.array.alarm_set)
        if (variableDelta < DateUtils.MINUTE_IN_MILLIS) {
            return formats[0]
        }

        // Otherwise, format the remaining time until the alarm rings.

        // Round delta upwards to the nearest whole minute. (e.g. 7m 58s -> 8m)
        val remainder = variableDelta % DateUtils.MINUTE_IN_MILLIS
        variableDelta += if (remainder == 0L) 0 else DateUtils.MINUTE_IN_MILLIS - remainder
        var hours = variableDelta.toInt() / (1000 * 60 * 60)
        val minutes = variableDelta.toInt() / (1000 * 60) % 60
        val days = hours / 24
        hours %= 24

        val daySeq = Utils.getNumberFormattedQuantityString(context, R.plurals.days, days)
        val minSeq = Utils.getNumberFormattedQuantityString(context, R.plurals.minutes, minutes)
        val hourSeq = Utils.getNumberFormattedQuantityString(context, R.plurals.hours, hours)

        val showDays = days > 0
        val showHours = hours > 0
        val showMinutes = minutes > 0

        // Compute the index of the most appropriate time format based on the time delta.
        val index = ((if (showDays) 1 else 0)
                or (if (showHours) 2 else 0)
                or (if (showMinutes) 4 else 0))

        return String.format(formats[index], daySeq, hourSeq, minSeq)
    }

    @JvmStatic
    fun popAlarmSetToast(context: Context, alarmTime: Long) {
        val alarmTimeDelta = alarmTime - System.currentTimeMillis()
        val text = formatElapsedTimeUntilAlarm(context, alarmTimeDelta)
        val toast = Toast.makeText(context, text, Toast.LENGTH_LONG)
        ToastManager.setToast(toast)
        toast.show()
    }

    @JvmStatic
    fun popAlarmSetSnackbar(snackbarAnchor: View, alarmTime: Long) {
        val alarmTimeDelta = alarmTime - System.currentTimeMillis()
        val text = formatElapsedTimeUntilAlarm(
                snackbarAnchor.context, alarmTimeDelta)
        SnackbarManager.show(Snackbar.make(snackbarAnchor, text, Snackbar.LENGTH_SHORT))
        snackbarAnchor.announceForAccessibility(text)
    }
}