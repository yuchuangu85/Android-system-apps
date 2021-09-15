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

package com.android.deskclock.provider

import android.net.Uri
import android.provider.BaseColumns

import com.android.deskclock.BuildConfig
import com.android.deskclock.provider.ClockContract.AlarmsColumns
import com.android.deskclock.provider.ClockContract.InstancesColumns

/**
 * The contract between the clock provider and desk clock. Contains
 * definitions for the supported URIs and data columns.
 *
 * <h3>Overview</h3>
 *
 * ClockContract defines the data model of clock related information.
 * This data is stored in a number of tables:
 *
 *  * The [AlarmsColumns] table holds the user created alarms
 *  * The [InstancesColumns] table holds the current state of each
 * alarm in the AlarmsColumn table.
 */
object ClockContract {
    /**
     * This authority is used for writing to or querying from the clock
     * provider.
     */
    @JvmField
    val AUTHORITY: String = BuildConfig.APPLICATION_ID

    /**
     * Constants for tables with AlarmSettings.
     */
    interface AlarmSettingColumns : BaseColumns {
        companion object {
            /**
             * This string is used to indicate no ringtone.
             */
            @JvmField
            val NO_RINGTONE_URI: Uri = Uri.EMPTY

            /**
             * This string is used to indicate no ringtone.
             */
            @JvmField
            val NO_RINGTONE: String = NO_RINGTONE_URI.toString()

            /**
             * True if alarm should vibrate
             *
             * Type: BOOLEAN
             */
            @JvmField
            val VIBRATE = "vibrate"

            /**
             * Alarm label.
             *
             * Type: STRING
             */
            @JvmField
            val LABEL = "label"

            /**
             * Audio alert to play when alarm triggers. Null entry
             * means use system default and entry that equal
             * Uri.EMPTY.toString() means no ringtone.
             *
             * Type: STRING
             */
            @JvmField
            val RINGTONE = "ringtone"
        }
    }

    /**
     * Constants for the Alarms table, which contains the user created alarms.
     */
    interface AlarmsColumns : AlarmSettingColumns, BaseColumns {
        companion object {
            /**
             * The content:// style URL for this table.
             */
            val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/alarms")

            /**
             * The content:// style URL for the alarms with instance tables, which is used to get the
             * next firing instance and the current state of an alarm.
             */
            val ALARMS_WITH_INSTANCES_URI: Uri = Uri.parse("content://" + AUTHORITY +
                    "/alarms_with_instances")

            /**
             * Hour in 24-hour localtime 0 - 23.
             *
             * Type: INTEGER
             */
            const val HOUR = "hour"

            /**
             * Minutes in localtime 0 - 59.
             *
             * Type: INTEGER
             */
            const val MINUTES = "minutes"

            /**
             * Days of the week encoded as a bit set.
             *
             * Type: INTEGER
             *
             * [com.android.deskclock.data.Weekdays]
             */
            const val DAYS_OF_WEEK = "daysofweek"

            /**
             * True if alarm is active.
             *
             * Type: BOOLEAN
             */
            const val ENABLED = "enabled"

            /**
             * Determine if alarm is deleted after it has been used.
             *
             * Type: INTEGER
             */
            const val DELETE_AFTER_USE = "delete_after_use"
        }
    }

    /**
     * Constants for the Instance table, which contains the state of each alarm.
     */
    interface InstancesColumns : AlarmSettingColumns, BaseColumns {
        companion object {
            /**
             * The content:// style URL for this table.
             */
            val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/instances")

            /**
             * Alarm state when to show no notification.
             *
             * Can transitions to:
             * LOW_NOTIFICATION_STATE
             */
            const val SILENT_STATE = 0

            /**
             * Alarm state to show low priority alarm notification.
             *
             * Can transitions to:
             * HIDE_NOTIFICATION_STATE
             * HIGH_NOTIFICATION_STATE
             * DISMISSED_STATE
             */
            const val LOW_NOTIFICATION_STATE = 1

            /**
             * Alarm state to hide low priority alarm notification.
             *
             * Can transitions to:
             * HIGH_NOTIFICATION_STATE
             */
            const val HIDE_NOTIFICATION_STATE = 2

            /**
             * Alarm state to show high priority alarm notification.
             *
             * Can transitions to:
             * DISMISSED_STATE
             * FIRED_STATE
             */
            const val HIGH_NOTIFICATION_STATE = 3

            /**
             * Alarm state when alarm is in snooze.
             *
             * Can transitions to:
             * DISMISSED_STATE
             * FIRED_STATE
             */
            const val SNOOZE_STATE = 4

            /**
             * Alarm state when alarm is being fired.
             *
             * Can transitions to:
             * DISMISSED_STATE
             * SNOOZED_STATE
             * MISSED_STATE
             */
            const val FIRED_STATE = 5

            /**
             * Alarm state when alarm has been missed.
             *
             * Can transitions to:
             * DISMISSED_STATE
             */
            const val MISSED_STATE = 6

            /**
             * Alarm state when alarm is done.
             */
            const val DISMISSED_STATE = 7

            /**
             * Alarm state when alarm has been dismissed before its intended firing time.
             */
            const val PREDISMISSED_STATE = 8

            /**
             * Alarm year.
             *
             * Type: INTEGER
             */
            const val YEAR = "year"

            /**
             * Alarm month in year.
             *
             * Type: INTEGER
             */
            const val MONTH = "month"

            /**
             * Alarm day in month.
             *
             * Type: INTEGER
             */
            const val DAY = "day"

            /**
             * Alarm hour in 24-hour localtime 0 - 23.
             *
             * Type: INTEGER
             */
            const val HOUR = "hour"

            /**
             * Alarm minutes in localtime 0 - 59
             *
             * Type: INTEGER
             */
            const val MINUTES = "minutes"

            /**
             * Foreign key to Alarms table
             *
             * Type: INTEGER (long)
             */
            const val ALARM_ID = "alarm_id"

            /**
             * Alarm state
             *
             * Type: INTEGER
             */
            const val ALARM_STATE = "alarm_state"
        }
    }
}