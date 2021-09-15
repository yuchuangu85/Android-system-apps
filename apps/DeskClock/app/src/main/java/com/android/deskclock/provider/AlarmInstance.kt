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
import android.provider.BaseColumns._ID

import com.android.deskclock.LogUtils
import com.android.deskclock.R
import com.android.deskclock.alarms.AlarmStateManager
import com.android.deskclock.data.DataModel
import com.android.deskclock.provider.ClockContract.AlarmSettingColumns
import com.android.deskclock.provider.ClockContract.InstancesColumns

import java.util.Calendar
import java.util.LinkedList

class AlarmInstance : InstancesColumns {
    // Public fields
    var mYear = 0
    var mMonth = 0
    var mDay = 0
    var mHour = 0
    var mMinute = 0

    @JvmField
    var mId: Long = 0

    @JvmField
    var mLabel: String? = null

    @JvmField
    var mVibrate = false

    @JvmField
    var mRingtone: Uri? = null

    @JvmField
    var mAlarmId: Long? = null

    @JvmField
    var mAlarmState: Int

    constructor(calendar: Calendar, alarmId: Long?) : this(calendar) {
        mAlarmId = alarmId
    }

    constructor(calendar: Calendar) {
        mId = INVALID_ID
        alarmTime = calendar
        mLabel = ""
        mVibrate = false
        mRingtone = null
        mAlarmState = InstancesColumns.SILENT_STATE
    }

    constructor(instance: AlarmInstance) {
        mId = instance.mId
        mYear = instance.mYear
        mMonth = instance.mMonth
        mDay = instance.mDay
        mHour = instance.mHour
        mMinute = instance.mMinute
        mLabel = instance.mLabel
        mVibrate = instance.mVibrate
        mRingtone = instance.mRingtone
        mAlarmId = instance.mAlarmId
        mAlarmState = instance.mAlarmState
    }

    constructor(c: Cursor, joinedTable: Boolean) {
        if (joinedTable) {
            mId = c.getLong(Alarm.INSTANCE_ID_INDEX)
            mYear = c.getInt(Alarm.INSTANCE_YEAR_INDEX)
            mMonth = c.getInt(Alarm.INSTANCE_MONTH_INDEX)
            mDay = c.getInt(Alarm.INSTANCE_DAY_INDEX)
            mHour = c.getInt(Alarm.INSTANCE_HOUR_INDEX)
            mMinute = c.getInt(Alarm.INSTANCE_MINUTE_INDEX)
            mLabel = c.getString(Alarm.INSTANCE_LABEL_INDEX)
            mVibrate = c.getInt(Alarm.INSTANCE_VIBRATE_INDEX) == 1
        } else {
            mId = c.getLong(ID_INDEX)
            mYear = c.getInt(YEAR_INDEX)
            mMonth = c.getInt(MONTH_INDEX)
            mDay = c.getInt(DAY_INDEX)
            mHour = c.getInt(HOUR_INDEX)
            mMinute = c.getInt(MINUTES_INDEX)
            mLabel = c.getString(LABEL_INDEX)
            mVibrate = c.getInt(VIBRATE_INDEX) == 1
        }
        mRingtone = if (c.isNull(RINGTONE_INDEX)) {
            // Should we be saving this with the current ringtone or leave it null
            // so it changes when user changes default ringtone?
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        } else {
            Uri.parse(c.getString(RINGTONE_INDEX))
        }

        if (!c.isNull(ALARM_ID_INDEX)) {
            mAlarmId = c.getLong(ALARM_ID_INDEX)
        }
        mAlarmState = c.getInt(ALARM_STATE_INDEX)
    }

    /**
     * @return the deeplink that identifies this alarm instance
     */
    val contentUri: Uri
        get() = getContentUri(mId)

    fun getLabelOrDefault(context: Context): String {
        return if (mLabel.isNullOrEmpty()) context.getString(R.string.default_label) else mLabel!!
    }

    /**
     * Return the time when a alarm should fire.
     *
     * @return the time
     */
    var alarmTime: Calendar
        get() {
            val calendar = Calendar.getInstance()
            calendar[Calendar.YEAR] = mYear
            calendar[Calendar.MONTH] = mMonth
            calendar[Calendar.DAY_OF_MONTH] = mDay
            calendar[Calendar.HOUR_OF_DAY] = mHour
            calendar[Calendar.MINUTE] = mMinute
            calendar[Calendar.SECOND] = 0
            calendar[Calendar.MILLISECOND] = 0
            return calendar
        }
        set(calendar) {
            mYear = calendar[Calendar.YEAR]
            mMonth = calendar[Calendar.MONTH]
            mDay = calendar[Calendar.DAY_OF_MONTH]
            mHour = calendar[Calendar.HOUR_OF_DAY]
            mMinute = calendar[Calendar.MINUTE]
        }

    /**
     * Return the time when a low priority notification should be shown.
     *
     * @return the time
     */
    val lowNotificationTime: Calendar
        get() {
            val calendar = alarmTime
            calendar.add(Calendar.HOUR_OF_DAY, LOW_NOTIFICATION_HOUR_OFFSET)
            return calendar
        }

    /**
     * Return the time when a high priority notification should be shown.
     *
     * @return the time
     */
    val highNotificationTime: Calendar
        get() {
            val calendar = alarmTime
            calendar.add(Calendar.MINUTE, HIGH_NOTIFICATION_MINUTE_OFFSET)
            return calendar
        }

    /**
     * Return the time when a missed notification should be removed.
     *
     * @return the time
     */
    val missedTimeToLive: Calendar
        get() {
            val calendar = alarmTime
            calendar.add(Calendar.HOUR, MISSED_TIME_TO_LIVE_HOUR_OFFSET)
            return calendar
        }

    /**
     * Return the time when the alarm should stop firing and be marked as missed.
     *
     * @return the time when alarm should be silence, or null if never
     */
    val timeout: Calendar?
        get() {
            val timeoutMinutes = DataModel.dataModel.alarmTimeout

            // Alarm silence has been set to "None"
            if (timeoutMinutes < 0) {
                return null
            }

            val calendar = alarmTime
            calendar.add(Calendar.MINUTE, timeoutMinutes)
            return calendar
        }

    override fun equals(other: Any?): Boolean {
        if (other !is AlarmInstance) return false
        return mId == other.mId
    }

    override fun hashCode(): Int {
        return java.lang.Long.valueOf(mId).hashCode()
    }

    override fun toString(): String {
        return "AlarmInstance{" +
                "mId=" + mId +
                ", mYear=" + mYear +
                ", mMonth=" + mMonth +
                ", mDay=" + mDay +
                ", mHour=" + mHour +
                ", mMinute=" + mMinute +
                ", mLabel=" + mLabel +
                ", mVibrate=" + mVibrate +
                ", mRingtone=" + mRingtone +
                ", mAlarmId=" + mAlarmId +
                ", mAlarmState=" + mAlarmState +
                '}'
    }

    companion object {
        /**
         * Offset from alarm time to show low priority notification
         */
        const val LOW_NOTIFICATION_HOUR_OFFSET = -2

        /**
         * Offset from alarm time to show high priority notification
         */
        const val HIGH_NOTIFICATION_MINUTE_OFFSET = -30

        /**
         * Offset from alarm time to stop showing missed notification.
         */
        private const val MISSED_TIME_TO_LIVE_HOUR_OFFSET = 12

        /**
         * AlarmInstances start with an invalid id when it hasn't been saved to the database.
         */
        const val INVALID_ID: Long = -1

        private val QUERY_COLUMNS = arrayOf(
                _ID,
                InstancesColumns.YEAR,
                InstancesColumns.MONTH,
                InstancesColumns.DAY,
                InstancesColumns.HOUR,
                InstancesColumns.MINUTES,
                AlarmSettingColumns.LABEL,
                AlarmSettingColumns.VIBRATE,
                AlarmSettingColumns.RINGTONE,
                InstancesColumns.ALARM_ID,
                InstancesColumns.ALARM_STATE
        )

        /**
         * These save calls to cursor.getColumnIndexOrThrow()
         * THEY MUST BE KEPT IN SYNC WITH ABOVE QUERY COLUMNS
         */
        private const val ID_INDEX = 0
        private const val YEAR_INDEX = 1
        private const val MONTH_INDEX = 2
        private const val DAY_INDEX = 3
        private const val HOUR_INDEX = 4
        private const val MINUTES_INDEX = 5
        private const val LABEL_INDEX = 6
        private const val VIBRATE_INDEX = 7
        private const val RINGTONE_INDEX = 8
        private const val ALARM_ID_INDEX = 9
        private const val ALARM_STATE_INDEX = 10

        private const val COLUMN_COUNT = ALARM_STATE_INDEX + 1

        @JvmStatic
        fun createContentValues(instance: AlarmInstance): ContentValues {
            val values = ContentValues(COLUMN_COUNT)
            if (instance.mId != INVALID_ID) {
                values.put(_ID, instance.mId)
            }

            values.put(InstancesColumns.YEAR, instance.mYear)
            values.put(InstancesColumns.MONTH, instance.mMonth)
            values.put(InstancesColumns.DAY, instance.mDay)
            values.put(InstancesColumns.HOUR, instance.mHour)
            values.put(InstancesColumns.MINUTES, instance.mMinute)
            values.put(AlarmSettingColumns.LABEL, instance.mLabel)
            values.put(AlarmSettingColumns.VIBRATE, if (instance.mVibrate) 1 else 0)
            if (instance.mRingtone == null) {
                // We want to put null in the database, so we'll be able
                // to pick up on changes to the default alarm
                values.putNull(AlarmSettingColumns.RINGTONE)
            } else {
                values.put(AlarmSettingColumns.RINGTONE, instance.mRingtone.toString())
            }
            values.put(InstancesColumns.ALARM_ID, instance.mAlarmId)
            values.put(InstancesColumns.ALARM_STATE, instance.mAlarmState)
            return values
        }

        fun createIntent(action: String?, instanceId: Long): Intent {
            return Intent(action).setData(getContentUri(instanceId))
        }

        @JvmStatic
        fun createIntent(context: Context?, cls: Class<*>?, instanceId: Long): Intent {
            return Intent(context, cls).setData(getContentUri(instanceId))
        }

        @JvmStatic
        fun getId(contentUri: Uri): Long {
            return ContentUris.parseId(contentUri)
        }

        /**
         * @return the [Uri] identifying the alarm instance
         */
        fun getContentUri(instanceId: Long): Uri {
            return ContentUris.withAppendedId(InstancesColumns.CONTENT_URI, instanceId)
        }

        /**
         * Get alarm instance from instanceId.
         *
         * @param cr provides access to the content model
         * @param instanceId for the desired instance.
         * @return instance if found, null otherwise
         */
        @JvmStatic
        fun getInstance(cr: ContentResolver, instanceId: Long): AlarmInstance? {
            val cursor: Cursor? =
                    cr.query(getContentUri(instanceId), QUERY_COLUMNS, null, null, null)
            cursor?.let {
                if (cursor.moveToFirst()) {
                    return AlarmInstance(cursor, false /* joinedTable */)
                }
            }
            return null
        }

        /**
         * Get alarm instance for the `contentUri`.
         *
         * @param cr provides access to the content model
         * @param contentUri the [deeplink][.getContentUri] for the desired instance
         * @return instance if found, null otherwise
         */
        fun getInstance(cr: ContentResolver, contentUri: Uri): AlarmInstance? {
            val instanceId: Long = ContentUris.parseId(contentUri)
            return getInstance(cr, instanceId)
        }

        /**
         * Get an alarm instances by alarmId.
         *
         * @param contentResolver provides access to the content model
         * @param alarmId of instances desired.
         * @return list of alarms instances that are owned by alarmId.
         */
        @JvmStatic
        fun getInstancesByAlarmId(
            contentResolver: ContentResolver,
            alarmId: Long
        ): List<AlarmInstance> {
            return getInstances(contentResolver, InstancesColumns.ALARM_ID + "=" + alarmId)
        }

        /**
         * Get the next instance of an alarm given its alarmId
         * @param contentResolver provides access to the content model
         * @param alarmId of instance desired
         * @return the next instance of an alarm by alarmId.
         */
        @JvmStatic
        fun getNextUpcomingInstanceByAlarmId(
            contentResolver: ContentResolver,
            alarmId: Long
        ): AlarmInstance? {
            val alarmInstances = getInstancesByAlarmId(contentResolver, alarmId)
            if (alarmInstances.isEmpty()) {
                return null
            }
            var nextAlarmInstance = alarmInstances[0]
            for (instance in alarmInstances) {
                if (instance.alarmTime.before(nextAlarmInstance.alarmTime)) {
                    nextAlarmInstance = instance
                }
            }
            return nextAlarmInstance
        }

        /**
         * Get alarm instance by id and state.
         */
        fun getInstancesByInstanceIdAndState(
            contentResolver: ContentResolver,
            alarmInstanceId: Long,
            state: Int
        ): List<AlarmInstance> {
            return getInstances(contentResolver,
                    _ID.toString() + "=" + alarmInstanceId + " AND " +
                            InstancesColumns.ALARM_STATE + "=" + state)
        }

        /**
         * Get alarm instances in the specified state.
         */
        @JvmStatic
        fun getInstancesByState(
            contentResolver: ContentResolver,
            state: Int
        ): List<AlarmInstance> {
            return getInstances(contentResolver,
                    InstancesColumns.ALARM_STATE + "=" + state)
        }

        /**
         * Get a list of instances given selection.
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
        fun getInstances(
            cr: ContentResolver,
            selection: String?,
            vararg selectionArgs: String?
        ): MutableList<AlarmInstance> {
            val result: MutableList<AlarmInstance> = LinkedList()
            val cursor: Cursor? =
                    cr.query(InstancesColumns.CONTENT_URI, QUERY_COLUMNS,
                            selection, selectionArgs, null)
            cursor?.let {
                if (cursor.moveToFirst()) {
                    do {
                        result.add(AlarmInstance(cursor, false /* joinedTable */))
                    } while (cursor.moveToNext())
                }
            }

            return result
        }

        @JvmStatic
        fun addInstance(
            contentResolver: ContentResolver,
            instance: AlarmInstance
        ): AlarmInstance {
            // Make sure we are not adding a duplicate instances. This is not a
            // fix and should never happen. This is only a safe guard against bad code, and you
            // should fix the root issue if you see the error message.
            val dupSelector = InstancesColumns.ALARM_ID + " = " + instance.mAlarmId
            for (otherInstances in getInstances(contentResolver, dupSelector)) {
                if (otherInstances.alarmTime == instance.alarmTime) {
                    LogUtils.i("Detected duplicate instance in DB. Updating " +
                            otherInstances + " to " + instance)
                    // Copy over the new instance values and update the db
                    instance.mId = otherInstances.mId
                    updateInstance(contentResolver, instance)
                    return instance
                }
            }

            val values: ContentValues = createContentValues(instance)
            val uri: Uri = contentResolver.insert(InstancesColumns.CONTENT_URI, values)!!
            instance.mId = getId(uri)
            return instance
        }

        @JvmStatic
        fun updateInstance(contentResolver: ContentResolver, instance: AlarmInstance): Boolean {
            if (instance.mId == INVALID_ID) return false
            val values: ContentValues = createContentValues(instance)
            val rowsUpdated: Long =
                    contentResolver.update(getContentUri(instance.mId), values, null, null).toLong()
            return rowsUpdated == 1L
        }

        @JvmStatic
        fun deleteInstance(contentResolver: ContentResolver, instanceId: Long): Boolean {
            if (instanceId == INVALID_ID) return false
            val deletedRows: Int = contentResolver.delete(getContentUri(instanceId), "", null)
            return deletedRows == 1
        }

        @JvmStatic
        fun deleteOtherInstances(
            context: Context,
            contentResolver: ContentResolver,
            alarmId: Long,
            instanceId: Long
        ) {
            val instances = getInstancesByAlarmId(contentResolver, alarmId)
            for (instance in instances) {
                if (instance.mId != instanceId) {
                    AlarmStateManager.unregisterInstance(context, instance)
                    deleteInstance(contentResolver, instance.mId)
                }
            }
        }
    }
}