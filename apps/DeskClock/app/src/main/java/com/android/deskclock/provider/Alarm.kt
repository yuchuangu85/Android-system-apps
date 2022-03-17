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

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.RingtoneManager
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import android.provider.BaseColumns
import androidx.loader.content.CursorLoader

import com.android.deskclock.R
import com.android.deskclock.data.DataModel
import com.android.deskclock.data.Weekdays
import com.android.deskclock.provider.ClockContract.AlarmSettingColumns
import com.android.deskclock.provider.ClockContract.AlarmsColumns
import com.android.deskclock.provider.ClockContract.InstancesColumns

import java.util.Calendar
import java.util.LinkedList

class Alarm : Parcelable, AlarmsColumns {
    // Public fields
    // TODO: Refactor instance names
    @JvmField
    var id: Long

    @JvmField
    var enabled = false

    @JvmField
    var hour: Int

    @JvmField
    var minutes: Int

    @JvmField
    var daysOfWeek: Weekdays

    @JvmField
    var vibrate: Boolean

    @JvmField
    var label: String?

    @JvmField
    var alert: Uri? = null

    @JvmField
    var deleteAfterUse: Boolean

    @JvmField
    var instanceState = 0

    var instanceId = 0

    // Creates a default alarm at the current time.
    @JvmOverloads
    constructor(hour: Int = 0, minutes: Int = 0) {
        id = INVALID_ID
        this.hour = hour
        this.minutes = minutes
        vibrate = true
        daysOfWeek = Weekdays.NONE
        label = ""
        alert = DataModel.dataModel.defaultAlarmRingtoneUri
        deleteAfterUse = false
    }

    constructor(c: Cursor) {
        id = c.getLong(ID_INDEX)
        enabled = c.getInt(ENABLED_INDEX) == 1
        hour = c.getInt(HOUR_INDEX)
        minutes = c.getInt(MINUTES_INDEX)
        daysOfWeek = Weekdays.fromBits(c.getInt(DAYS_OF_WEEK_INDEX))
        vibrate = c.getInt(VIBRATE_INDEX) == 1
        label = c.getString(LABEL_INDEX)
        deleteAfterUse = c.getInt(DELETE_AFTER_USE_INDEX) == 1

        if (c.getColumnCount() == ALARM_JOIN_INSTANCE_COLUMN_COUNT) {
            instanceState = c.getInt(INSTANCE_STATE_INDEX)
            instanceId = c.getInt(INSTANCE_ID_INDEX)
        }

        alert = if (c.isNull(RINGTONE_INDEX)) {
            // Should we be saving this with the current ringtone or leave it null
            // so it changes when user changes default ringtone?
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        } else {
            Uri.parse(c.getString(RINGTONE_INDEX))
        }
    }

    internal constructor(p: Parcel) {
        id = p.readLong()
        enabled = p.readInt() == 1
        hour = p.readInt()
        minutes = p.readInt()
        daysOfWeek = Weekdays.fromBits(p.readInt())
        vibrate = p.readInt() == 1
        label = p.readString()
        alert = p.readParcelable(null)
        deleteAfterUse = p.readInt() == 1
    }

    /**
     * @return the deeplink that identifies this alarm
     */
    val contentUri: Uri
        get() = getContentUri(id)

    fun getLabelOrDefault(context: Context): String {
        return if (label.isNullOrEmpty()) context.getString(R.string.default_label) else label!!
    }

    /**
     * Whether the alarm is in a state to show preemptive dismiss. Valid states are SNOOZE_STATE
     * HIGH_NOTIFICATION, LOW_NOTIFICATION, and HIDE_NOTIFICATION.
     */
    fun canPreemptivelyDismiss(): Boolean {
        return instanceState == InstancesColumns.SNOOZE_STATE ||
                instanceState == InstancesColumns.HIGH_NOTIFICATION_STATE ||
                instanceState == InstancesColumns.LOW_NOTIFICATION_STATE ||
                instanceState == InstancesColumns.HIDE_NOTIFICATION_STATE
    }

    override fun writeToParcel(p: Parcel, flags: Int) {
        p.writeLong(id)
        p.writeInt(if (enabled) 1 else 0)
        p.writeInt(hour)
        p.writeInt(minutes)
        p.writeInt(daysOfWeek.bits)
        p.writeInt(if (vibrate) 1 else 0)
        p.writeString(label)
        p.writeParcelable(alert, flags)
        p.writeInt(if (deleteAfterUse) 1 else 0)
    }

    override fun describeContents(): Int = 0

    fun createInstanceAfter(time: Calendar): AlarmInstance {
        val nextInstanceTime = getNextAlarmTime(time)
        val result = AlarmInstance(nextInstanceTime, id)
        result.mVibrate = vibrate
        result.mLabel = label
        result.mRingtone = alert
        return result
    }

    /**
     *
     * @param currentTime the current time
     * @return previous firing time, or null if this is a one-time alarm.
     */
    fun getPreviousAlarmTime(currentTime: Calendar): Calendar? {
        val previousInstanceTime = Calendar.getInstance(currentTime.timeZone)
        previousInstanceTime[Calendar.YEAR] = currentTime[Calendar.YEAR]
        previousInstanceTime[Calendar.MONTH] = currentTime[Calendar.MONTH]
        previousInstanceTime[Calendar.DAY_OF_MONTH] = currentTime[Calendar.DAY_OF_MONTH]
        previousInstanceTime[Calendar.HOUR_OF_DAY] = hour
        previousInstanceTime[Calendar.MINUTE] = minutes
        previousInstanceTime[Calendar.SECOND] = 0
        previousInstanceTime[Calendar.MILLISECOND] = 0

        val subtractDays = daysOfWeek.getDistanceToPreviousDay(previousInstanceTime)
        return if (subtractDays > 0) {
            previousInstanceTime.add(Calendar.DAY_OF_WEEK, -subtractDays)
            previousInstanceTime
        } else {
            null
        }
    }

    fun getNextAlarmTime(currentTime: Calendar): Calendar {
        val nextInstanceTime = Calendar.getInstance(currentTime.timeZone)
        nextInstanceTime[Calendar.YEAR] = currentTime[Calendar.YEAR]
        nextInstanceTime[Calendar.MONTH] = currentTime[Calendar.MONTH]
        nextInstanceTime[Calendar.DAY_OF_MONTH] = currentTime[Calendar.DAY_OF_MONTH]
        nextInstanceTime[Calendar.HOUR_OF_DAY] = hour
        nextInstanceTime[Calendar.MINUTE] = minutes
        nextInstanceTime[Calendar.SECOND] = 0
        nextInstanceTime[Calendar.MILLISECOND] = 0

        // If we are still behind the passed in currentTime, then add a day
        if (nextInstanceTime.timeInMillis <= currentTime.timeInMillis) {
            nextInstanceTime.add(Calendar.DAY_OF_YEAR, 1)
        }

        // The day of the week might be invalid, so find next valid one
        val addDays = daysOfWeek.getDistanceToNextDay(nextInstanceTime)
        if (addDays > 0) {
            nextInstanceTime.add(Calendar.DAY_OF_WEEK, addDays)
        }

        // Daylight Savings Time can alter the hours and minutes when adjusting the day above.
        // Reset the desired hour and minute now that the correct day has been chosen.
        nextInstanceTime[Calendar.HOUR_OF_DAY] = hour
        nextInstanceTime[Calendar.MINUTE] = minutes

        return nextInstanceTime
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Alarm) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return java.lang.Long.valueOf(id).hashCode()
    }

    override fun toString(): String {
        return "Alarm{" +
                "alert=" + alert +
                ", id=" + id +
                ", enabled=" + enabled +
                ", hour=" + hour +
                ", minutes=" + minutes +
                ", daysOfWeek=" + daysOfWeek +
                ", vibrate=" + vibrate +
                ", label='" + label + '\'' +
                ", deleteAfterUse=" + deleteAfterUse +
                '}'
    }

    companion object {
        /**
         * Alarms start with an invalid id when it hasn't been saved to the database.
         */
        const val INVALID_ID: Long = -1

        /**
         * The default sort order for this table
         */
        private val DEFAULT_SORT_ORDER = ClockDatabaseHelper.ALARMS_TABLE_NAME + "." +
                AlarmsColumns.HOUR + ", " + ClockDatabaseHelper.ALARMS_TABLE_NAME + "." +
                AlarmsColumns.MINUTES + " ASC" + ", " +
                ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + BaseColumns._ID + " DESC"

        private val QUERY_COLUMNS = arrayOf(
                BaseColumns._ID,
                AlarmsColumns.HOUR,
                AlarmsColumns.MINUTES,
                AlarmsColumns.DAYS_OF_WEEK,
                AlarmsColumns.ENABLED,
                AlarmSettingColumns.VIBRATE,
                AlarmSettingColumns.LABEL,
                AlarmSettingColumns.RINGTONE,
                AlarmsColumns.DELETE_AFTER_USE
        )

        private val QUERY_ALARMS_WITH_INSTANCES_COLUMNS = arrayOf(
                ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + BaseColumns._ID,
                ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + AlarmsColumns.HOUR,
                ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + AlarmsColumns.MINUTES,
                ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + AlarmsColumns.DAYS_OF_WEEK,
                ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + AlarmsColumns.ENABLED,
                ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + AlarmSettingColumns.VIBRATE,
                ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + AlarmSettingColumns.LABEL,
                ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + AlarmSettingColumns.RINGTONE,
                ClockDatabaseHelper.ALARMS_TABLE_NAME + "." + AlarmsColumns.DELETE_AFTER_USE,
                ClockDatabaseHelper.INSTANCES_TABLE_NAME + "." + InstancesColumns.ALARM_STATE,
                ClockDatabaseHelper.INSTANCES_TABLE_NAME + "." + BaseColumns._ID,
                ClockDatabaseHelper.INSTANCES_TABLE_NAME + "." + InstancesColumns.YEAR,
                ClockDatabaseHelper.INSTANCES_TABLE_NAME + "." + InstancesColumns.MONTH,
                ClockDatabaseHelper.INSTANCES_TABLE_NAME + "." + InstancesColumns.DAY,
                ClockDatabaseHelper.INSTANCES_TABLE_NAME + "." + InstancesColumns.HOUR,
                ClockDatabaseHelper.INSTANCES_TABLE_NAME + "." + InstancesColumns.MINUTES,
                ClockDatabaseHelper.INSTANCES_TABLE_NAME + "." + AlarmSettingColumns.LABEL,
                ClockDatabaseHelper.INSTANCES_TABLE_NAME + "." + AlarmSettingColumns.VIBRATE
        )

        /**
         * These save calls to cursor.getColumnIndexOrThrow()
         * THEY MUST BE KEPT IN SYNC WITH ABOVE QUERY COLUMNS
         */
        private const val ID_INDEX = 0
        private const val HOUR_INDEX = 1
        private const val MINUTES_INDEX = 2
        private const val DAYS_OF_WEEK_INDEX = 3
        private const val ENABLED_INDEX = 4
        private const val VIBRATE_INDEX = 5
        private const val LABEL_INDEX = 6
        private const val RINGTONE_INDEX = 7
        private const val DELETE_AFTER_USE_INDEX = 8
        private const val INSTANCE_STATE_INDEX = 9
        const val INSTANCE_ID_INDEX = 10
        const val INSTANCE_YEAR_INDEX = 11
        const val INSTANCE_MONTH_INDEX = 12
        const val INSTANCE_DAY_INDEX = 13
        const val INSTANCE_HOUR_INDEX = 14
        const val INSTANCE_MINUTE_INDEX = 15
        const val INSTANCE_LABEL_INDEX = 16
        const val INSTANCE_VIBRATE_INDEX = 17

        private const val COLUMN_COUNT = DELETE_AFTER_USE_INDEX + 1
        private const val ALARM_JOIN_INSTANCE_COLUMN_COUNT = INSTANCE_VIBRATE_INDEX + 1

        @JvmStatic
        fun createContentValues(alarm: Alarm): ContentValues {
            val values = ContentValues(COLUMN_COUNT)
            if (alarm.id != INVALID_ID) {
                values.put(BaseColumns._ID, alarm.id)
            }

            values.put(AlarmsColumns.ENABLED, if (alarm.enabled) 1 else 0)
            values.put(AlarmsColumns.HOUR, alarm.hour)
            values.put(AlarmsColumns.MINUTES, alarm.minutes)
            values.put(AlarmsColumns.DAYS_OF_WEEK, alarm.daysOfWeek.bits)
            values.put(AlarmSettingColumns.VIBRATE, if (alarm.vibrate) 1 else 0)
            values.put(AlarmSettingColumns.LABEL, alarm.label)
            values.put(AlarmsColumns.DELETE_AFTER_USE, alarm.deleteAfterUse)
            if (alarm.alert == null) {
                // We want to put null, so default alarm changes
                values.putNull(AlarmSettingColumns.RINGTONE)
            } else {
                values.put(AlarmSettingColumns.RINGTONE, alarm.alert.toString())
            }
            return values
        }

        @JvmStatic
        fun createIntent(context: Context?, cls: Class<*>?, alarmId: Long): Intent {
            return Intent(context, cls).setData(getContentUri(alarmId))
        }

        fun getContentUri(alarmId: Long): Uri {
            return ContentUris.withAppendedId(AlarmsColumns.CONTENT_URI, alarmId)
        }

        fun getId(contentUri: Uri): Long {
            return ContentUris.parseId(contentUri)
        }

        /**
         * Get alarm cursor loader for all alarms.
         *
         * @param context to query the database.
         * @return cursor loader with all the alarms.
         */
        @JvmStatic
        fun getAlarmsCursorLoader(context: Context): CursorLoader {
            return object : CursorLoader(context, AlarmsColumns.ALARMS_WITH_INSTANCES_URI,
                    QUERY_ALARMS_WITH_INSTANCES_COLUMNS, null, null, DEFAULT_SORT_ORDER) {

                override fun onContentChanged() {
                    // There is a bug in Loader which can result in stale data if a loader is stopped
                    // immediately after a call to onContentChanged. As a workaround we stop the
                    // loader before delivering onContentChanged to ensure mContentChanged is set to
                    // true before forceLoad is called.
                    if (isStarted() && !isAbandoned()) {
                        stopLoading()
                        super.onContentChanged()
                        startLoading()
                    } else {
                        super.onContentChanged()
                    }
                }

                override fun loadInBackground(): Cursor? {
                    // Prime the ringtone title cache for later access. Most alarms will refer to
                    // system ringtones.
                    DataModel.dataModel.loadRingtoneTitles()
                    return super.loadInBackground()
                }
            }
        }

        /**
         * Get alarm by id.
         *
         * @param cr provides access to the content model
         * @param alarmId for the desired alarm.
         * @return alarm if found, null otherwise
         */
        @JvmStatic
        fun getAlarm(cr: ContentResolver, alarmId: Long): Alarm? {
            val cursor: Cursor? = cr.query(getContentUri(alarmId), QUERY_COLUMNS, null, null, null)
            cursor?.let {
                if (cursor.moveToFirst()) {
                    return Alarm(cursor)
                }
            }

            return null
        }

        /**
         * Get all alarms given conditions.
         *
         * @param cr provides access to the content model
         * @param selection A filter declaring which rows to return, formatted as an
         * SQL WHERE clause (excluding the WHERE itself). Passing null will
         * return all rows for the given URI.
         * @param selectionArgs You may include ?s in selection, which will be
         * replaced by the values from selectionArgs, in the order that they
         * appear in the selection. The values will be bound as Strings.
         * @return list of alarms matching where clause or empty list if none found.
         */
        @JvmStatic
        fun getAlarms(
            cr: ContentResolver,
            selection: String?,
            vararg selectionArgs: String?
        ): List<Alarm> {
            val result: MutableList<Alarm> = LinkedList()
            val cursor: Cursor? =
                    cr.query(AlarmsColumns.CONTENT_URI, QUERY_COLUMNS,
                            selection, selectionArgs, null)
            cursor?.let {
                if (cursor.moveToFirst()) {
                    do {
                        result.add(Alarm(cursor))
                    } while (cursor.moveToNext())
                }
            }

            return result
        }

        @JvmStatic
        fun isTomorrow(alarm: Alarm, now: Calendar): Boolean {
            if (alarm.instanceState == InstancesColumns.SNOOZE_STATE) {
                return false
            }

            val totalAlarmMinutes = alarm.hour * 60 + alarm.minutes
            val totalNowMinutes = now[Calendar.HOUR_OF_DAY] * 60 + now[Calendar.MINUTE]
            return totalAlarmMinutes <= totalNowMinutes
        }

        @JvmStatic
        fun addAlarm(contentResolver: ContentResolver, alarm: Alarm): Alarm {
            val values: ContentValues = createContentValues(alarm)
            val uri: Uri = contentResolver.insert(AlarmsColumns.CONTENT_URI, values)!!
            alarm.id = getId(uri)
            return alarm
        }

        @JvmStatic
        fun updateAlarm(contentResolver: ContentResolver, alarm: Alarm): Boolean {
            if (alarm.id == INVALID_ID) return false
            val values: ContentValues = createContentValues(alarm)
            val rowsUpdated: Long =
                    contentResolver.update(getContentUri(alarm.id), values, null, null).toLong()
            return rowsUpdated == 1L
        }

        @JvmStatic
        fun deleteAlarm(contentResolver: ContentResolver, alarmId: Long): Boolean {
            if (alarmId == INVALID_ID) return false
            val deletedRows: Int = contentResolver.delete(getContentUri(alarmId), "", null)
            return deletedRows == 1
        }

        val CREATOR: Parcelable.Creator<Alarm> = object : Parcelable.Creator<Alarm> {
            override fun createFromParcel(p: Parcel): Alarm {
                return Alarm(p)
            }

            override fun newArray(size: Int): Array<Alarm?> {
                return arrayOfNulls(size)
            }
        }
    }
}