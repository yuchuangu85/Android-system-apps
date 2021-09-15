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

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.provider.BaseColumns
import android.text.TextUtils

import com.android.deskclock.LogUtils
import com.android.deskclock.data.Weekdays
import com.android.deskclock.provider.ClockContract.AlarmSettingColumns
import com.android.deskclock.provider.ClockContract.AlarmsColumns
import com.android.deskclock.provider.ClockContract.InstancesColumns

import java.util.Calendar

/**
 * Helper class for opening the database from multiple providers.  Also provides
 * some common functionality.
 */
class ClockDatabaseHelper(context: Context)
    : SQLiteOpenHelper(context, DATABASE_NAME, null, VERSION_8) {

    override fun onCreate(db: SQLiteDatabase) {
        createAlarmsTable(db)
        createInstanceTable(db)

        // insert default alarms
        LogUtils.i("Inserting default alarms")
        val cs: String = ", " // comma and space
        val insertMe: String = "INSERT INTO " + ALARMS_TABLE_NAME + " (" +
                AlarmsColumns.HOUR + cs +
                AlarmsColumns.MINUTES + cs +
                AlarmsColumns.DAYS_OF_WEEK + cs +
                AlarmsColumns.ENABLED + cs +
                AlarmSettingColumns.VIBRATE + cs +
                AlarmSettingColumns.LABEL + cs +
                AlarmSettingColumns.RINGTONE + cs +
                AlarmsColumns.DELETE_AFTER_USE + ") VALUES "
        db.execSQL(insertMe + DEFAULT_ALARM_1)
        db.execSQL(insertMe + DEFAULT_ALARM_2)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, currentVersion: Int) {
        LogUtils.v("Upgrading alarms database from version %d to %d",
                oldVersion, currentVersion)

        if (oldVersion <= VERSION_7) {
            // This was not used in VERSION_7 or prior, so we can just drop it.
            db.execSQL("DROP TABLE IF EXISTS " + SELECTED_CITIES_TABLE_NAME + ";")
        }

        if (oldVersion <= VERSION_6) {
            // This was not used in VERSION_6 or prior, so we can just drop it.
            db.execSQL("DROP TABLE IF EXISTS " + INSTANCES_TABLE_NAME + ";")

            // Create new alarms table and copy over the data
            createAlarmsTable(db)
            createInstanceTable(db)

            LogUtils.i("Copying old alarms to new table")
            val OLD_TABLE_COLUMNS: Array<String> = arrayOf(
                    "_id",
                    "hour",
                    "minutes",
                    "daysofweek",
                    "enabled",
                    "vibrate",
                    "message",
                    "alert"
            )
            val cursor: Cursor? =
                    db.query(OLD_ALARMS_TABLE_NAME, OLD_TABLE_COLUMNS, null, null, null, null, null)
            val currentTime: Calendar = Calendar.getInstance()
            while (cursor != null && cursor.moveToNext()) {
                val alarm = Alarm()
                alarm.id = cursor.getLong(0)
                alarm.hour = cursor.getInt(1)
                alarm.minutes = cursor.getInt(2)
                alarm.daysOfWeek = Weekdays.fromBits(cursor.getInt(3))
                alarm.enabled = cursor.getInt(4) == 1
                alarm.vibrate = cursor.getInt(5) == 1
                alarm.label = cursor.getString(6)

                val alertString: String = cursor.getString(7)
                if ("silent" == alertString) {
                    alarm.alert = AlarmSettingColumns.NO_RINGTONE_URI
                } else {
                    alarm.alert = if (TextUtils.isEmpty(alertString)) {
                        null
                    } else {
                        Uri.parse(alertString)
                    }
                }

                // Save new version of alarm and create alarm instance for it
                db.insert(ALARMS_TABLE_NAME, null, Alarm.createContentValues(alarm))
                if (alarm.enabled) {
                    val newInstance: AlarmInstance = alarm.createInstanceAfter(currentTime)
                    db.insert(INSTANCES_TABLE_NAME, null,
                            AlarmInstance.createContentValues(newInstance))
                }
            }

            LogUtils.i("Dropping old alarm table")
            db.execSQL("DROP TABLE IF EXISTS " + OLD_ALARMS_TABLE_NAME + ";")
        }
    }

    fun fixAlarmInsert(values: ContentValues): Long {
        // Why are we doing this? Is this not a programming bug if we try to
        // insert an already used id?
        val db: SQLiteDatabase = getWritableDatabase()
        db.beginTransaction()
        val rowId: Long
        try {
            // Check if we are trying to re-use an existing id.
            val value = values.get(BaseColumns._ID)
            if (value != null) {
                val id: Long = value as Long
                if (id > -1) {
                    val columns: Array<String> = arrayOf(BaseColumns._ID)
                    val selection: String = BaseColumns._ID + " = ?"
                    val selectionArgs: Array<String> = arrayOf(id.toString())
                    val cursor: Cursor =
                            db.query(ALARMS_TABLE_NAME, columns,
                                    selection, selectionArgs, null, null, null)
                    if (cursor.moveToFirst()) {
                        // Record exists. Remove the id so sqlite can generate a new one.
                        values.putNull(BaseColumns._ID)
                    }
                }
            }

            rowId = db.insert(ALARMS_TABLE_NAME, AlarmSettingColumns.RINGTONE, values)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        if (rowId < 0) {
            throw SQLException("Failed to insert row")
        }
        LogUtils.v("Added alarm rowId = " + rowId)

        return rowId
    }

    companion object {
        /**
         * Original Clock Database.
         **/
        private const val VERSION_5: Int = 5

        /**
         * Added alarm_instances table
         * Added selected_cities table
         * Added DELETE_AFTER_USE column to alarms table
         */
        private const val VERSION_6: Int = 6

        /**
         * Added alarm settings to instance table.
         */
        private const val VERSION_7: Int = 7

        /**
         * Removed selected_cities table.
         */
        private const val VERSION_8: Int = 8

        // This creates a default alarm at 8:30 for every Mon,Tue,Wed,Thu,Fri
        private const val DEFAULT_ALARM_1: String = "(8, 30, 31, 0, 1, '', NULL, 0);"

        // This creates a default alarm at 9:30 for every Sat,Sun
        private const val DEFAULT_ALARM_2: String = "(9, 00, 96, 0, 1, '', NULL, 0);"

        // Database and table names
        const val DATABASE_NAME: String = "alarms.db"
        const val OLD_ALARMS_TABLE_NAME: String = "alarms"
        const val ALARMS_TABLE_NAME: String = "alarm_templates"
        const val INSTANCES_TABLE_NAME: String = "alarm_instances"
        private const val SELECTED_CITIES_TABLE_NAME: String = "selected_cities"

        private fun createAlarmsTable(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE " + ALARMS_TABLE_NAME + " (" +
                    BaseColumns._ID + " INTEGER PRIMARY KEY," +
                    AlarmsColumns.HOUR + " INTEGER NOT NULL, " +
                    AlarmsColumns.MINUTES + " INTEGER NOT NULL, " +
                    AlarmsColumns.DAYS_OF_WEEK + " INTEGER NOT NULL, " +
                    AlarmsColumns.ENABLED + " INTEGER NOT NULL, " +
                    AlarmSettingColumns.VIBRATE + " INTEGER NOT NULL, " +
                    AlarmSettingColumns.LABEL + " TEXT NOT NULL, " +
                    AlarmSettingColumns.RINGTONE + " TEXT, " +
                    AlarmsColumns.DELETE_AFTER_USE + " INTEGER NOT NULL DEFAULT 0);")
            LogUtils.i("Alarms Table created")
        }

        private fun createInstanceTable(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE " + INSTANCES_TABLE_NAME + " (" +
                    BaseColumns._ID + " INTEGER PRIMARY KEY," +
                    InstancesColumns.YEAR + " INTEGER NOT NULL, " +
                    InstancesColumns.MONTH + " INTEGER NOT NULL, " +
                    InstancesColumns.DAY + " INTEGER NOT NULL, " +
                    InstancesColumns.HOUR + " INTEGER NOT NULL, " +
                    InstancesColumns.MINUTES + " INTEGER NOT NULL, " +
                    AlarmSettingColumns.VIBRATE + " INTEGER NOT NULL, " +
                    AlarmSettingColumns.LABEL + " TEXT NOT NULL, " +
                    AlarmSettingColumns.RINGTONE + " TEXT, " +
                    InstancesColumns.ALARM_STATE + " INTEGER NOT NULL, " +
                    InstancesColumns.ALARM_ID + " INTEGER REFERENCES " +
                    ALARMS_TABLE_NAME + "(" + BaseColumns._ID + ") " +
                    "ON UPDATE CASCADE ON DELETE CASCADE" +
                    ");")
            LogUtils.i("Instance table created")
        }
    }
}
