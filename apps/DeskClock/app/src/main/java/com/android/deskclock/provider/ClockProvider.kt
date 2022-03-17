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

import android.annotation.TargetApi
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteQueryBuilder
import android.net.Uri
import android.os.Build
import android.provider.BaseColumns
import android.text.TextUtils
import android.util.ArrayMap

import com.android.deskclock.LogUtils
import com.android.deskclock.Utils
import com.android.deskclock.provider.ClockContract.AlarmSettingColumns
import com.android.deskclock.provider.ClockContract.AlarmsColumns
import com.android.deskclock.provider.ClockContract.InstancesColumns
import com.android.deskclock.provider.ClockDatabaseHelper.Companion.ALARMS_TABLE_NAME
import com.android.deskclock.provider.ClockDatabaseHelper.Companion.INSTANCES_TABLE_NAME

class ClockProvider : ContentProvider() {

    private lateinit var mOpenHelper: ClockDatabaseHelper

    companion object {
        private const val ALARMS = 1
        private const val ALARMS_ID = 2
        private const val INSTANCES = 3
        private const val INSTANCES_ID = 4
        private const val ALARMS_WITH_INSTANCES = 5

        private val ALARM_JOIN_INSTANCE_TABLE_STATEMENT =
                ALARMS_TABLE_NAME + " LEFT JOIN " +
                        INSTANCES_TABLE_NAME + " ON (" +
                        ALARMS_TABLE_NAME + "." +
                        BaseColumns._ID + " = " + InstancesColumns.ALARM_ID + ")"

        private val ALARM_JOIN_INSTANCE_WHERE_STATEMENT = INSTANCES_TABLE_NAME +
                "." + BaseColumns._ID + " IS NULL OR " +
                INSTANCES_TABLE_NAME + "." + BaseColumns._ID + " = (" +
                "SELECT " + BaseColumns._ID +
                " FROM " + INSTANCES_TABLE_NAME +
                " WHERE " + InstancesColumns.ALARM_ID +
                " = " + ALARMS_TABLE_NAME + "." + BaseColumns._ID +
                " ORDER BY " + InstancesColumns.ALARM_STATE + ", " +
                InstancesColumns.YEAR + ", " + InstancesColumns.MONTH + ", " +
                InstancesColumns.DAY + " LIMIT 1)"

        /**
         * Projection map used by query for snoozed alarms.
         */
        private val sAlarmsWithInstancesProjection: MutableMap<String, String> = ArrayMap()

        private val sURIMatcher: UriMatcher = UriMatcher(UriMatcher.NO_MATCH)

        init {
            sAlarmsWithInstancesProjection[ALARMS_TABLE_NAME + "." + BaseColumns._ID] =
                    ALARMS_TABLE_NAME + "." + BaseColumns._ID
            sAlarmsWithInstancesProjection[ALARMS_TABLE_NAME + "." + AlarmsColumns.HOUR] =
                    ALARMS_TABLE_NAME + "." + AlarmsColumns.HOUR
            sAlarmsWithInstancesProjection[ALARMS_TABLE_NAME + "." + AlarmsColumns.MINUTES] =
                    ALARMS_TABLE_NAME + "." + AlarmsColumns.MINUTES
            sAlarmsWithInstancesProjection[ALARMS_TABLE_NAME + "." + AlarmsColumns.DAYS_OF_WEEK] =
                    ALARMS_TABLE_NAME + "." + AlarmsColumns.DAYS_OF_WEEK
            sAlarmsWithInstancesProjection[ALARMS_TABLE_NAME + "." + AlarmsColumns.ENABLED] =
                    ALARMS_TABLE_NAME + "." + AlarmsColumns.ENABLED
            sAlarmsWithInstancesProjection[ALARMS_TABLE_NAME + "." + AlarmSettingColumns.VIBRATE] =
                    ALARMS_TABLE_NAME + "." + AlarmSettingColumns.VIBRATE
            sAlarmsWithInstancesProjection[ALARMS_TABLE_NAME + "." + AlarmSettingColumns.LABEL] =
                    ALARMS_TABLE_NAME + "." + AlarmSettingColumns.LABEL
            sAlarmsWithInstancesProjection[ALARMS_TABLE_NAME + "." + AlarmSettingColumns.RINGTONE] =
                    ALARMS_TABLE_NAME + "." + AlarmSettingColumns.RINGTONE
            sAlarmsWithInstancesProjection[ALARMS_TABLE_NAME + "." +
                    AlarmsColumns.DELETE_AFTER_USE] =
                    ALARMS_TABLE_NAME + "." + AlarmsColumns.DELETE_AFTER_USE
            sAlarmsWithInstancesProjection[INSTANCES_TABLE_NAME + "." +
                    InstancesColumns.ALARM_STATE] =
                    INSTANCES_TABLE_NAME + "." + InstancesColumns.ALARM_STATE
            sAlarmsWithInstancesProjection[INSTANCES_TABLE_NAME + "." + BaseColumns._ID] =
                    INSTANCES_TABLE_NAME + "." + BaseColumns._ID
            sAlarmsWithInstancesProjection[INSTANCES_TABLE_NAME + "." + InstancesColumns.YEAR] =
                    INSTANCES_TABLE_NAME + "." + InstancesColumns.YEAR
            sAlarmsWithInstancesProjection[INSTANCES_TABLE_NAME + "." + InstancesColumns.MONTH] =
                    INSTANCES_TABLE_NAME + "." + InstancesColumns.MONTH
            sAlarmsWithInstancesProjection[INSTANCES_TABLE_NAME + "." + InstancesColumns.DAY] =
                    INSTANCES_TABLE_NAME + "." + InstancesColumns.DAY
            sAlarmsWithInstancesProjection[INSTANCES_TABLE_NAME + "." + InstancesColumns.HOUR] =
                    INSTANCES_TABLE_NAME + "." + InstancesColumns.HOUR
            sAlarmsWithInstancesProjection[INSTANCES_TABLE_NAME + "." + InstancesColumns.MINUTES] =
                    INSTANCES_TABLE_NAME + "." + InstancesColumns.MINUTES
            sAlarmsWithInstancesProjection[INSTANCES_TABLE_NAME + "." + AlarmSettingColumns.LABEL] =
                    INSTANCES_TABLE_NAME + "." + AlarmSettingColumns.LABEL
            sAlarmsWithInstancesProjection[INSTANCES_TABLE_NAME + "." +
                    AlarmSettingColumns.VIBRATE] =
                    INSTANCES_TABLE_NAME + "." + AlarmSettingColumns.VIBRATE

            sURIMatcher.addURI(ClockContract.AUTHORITY, "alarms", ALARMS)
            sURIMatcher.addURI(ClockContract.AUTHORITY, "alarms/#", ALARMS_ID)
            sURIMatcher.addURI(ClockContract.AUTHORITY, "instances", INSTANCES)
            sURIMatcher.addURI(ClockContract.AUTHORITY, "instances/#", INSTANCES_ID)
            sURIMatcher.addURI(ClockContract.AUTHORITY,
                    "alarms_with_instances", ALARMS_WITH_INSTANCES)
        }
    }

    @TargetApi(Build.VERSION_CODES.N)
    override fun onCreate(): Boolean {
        val context: Context = getContext()!!
        val storageContext: Context
        if (Utils.isNOrLater) {
            // All N devices have split storage areas, but we may need to
            // migrate existing database into the new device encrypted
            // storage area, which is where our data lives from now on.
            storageContext = context.createDeviceProtectedStorageContext()
            if (!storageContext.moveDatabaseFrom(context, ClockDatabaseHelper.DATABASE_NAME)) {
                LogUtils.wtf("Failed to migrate database: %s",
                        ClockDatabaseHelper.DATABASE_NAME)
            }
        } else {
            storageContext = context
        }

        mOpenHelper = ClockDatabaseHelper(storageContext)
        return true
    }

    override fun query(
        uri: Uri,
        projectionIn: Array<String?>?,
        selection: String?,
        selectionArgs: Array<String?>?,
        sort: String?
    ): Cursor? {
        val qb = SQLiteQueryBuilder()
        val db: SQLiteDatabase = mOpenHelper.getReadableDatabase()

        // Generate the body of the query
        when (sURIMatcher.match(uri)) {
            ALARMS -> qb.setTables(ALARMS_TABLE_NAME)
            ALARMS_ID -> {
                qb.setTables(ALARMS_TABLE_NAME)
                qb.appendWhere(BaseColumns._ID.toString() + "=")
                qb.appendWhere(uri.getLastPathSegment()!!)
            }
            INSTANCES -> qb.setTables(INSTANCES_TABLE_NAME)
            INSTANCES_ID -> {
                qb.setTables(INSTANCES_TABLE_NAME)
                qb.appendWhere(BaseColumns._ID.toString() + "=")
                qb.appendWhere(uri.getLastPathSegment()!!)
            }
            ALARMS_WITH_INSTANCES -> {
                qb.setTables(ALARM_JOIN_INSTANCE_TABLE_STATEMENT)
                qb.appendWhere(ALARM_JOIN_INSTANCE_WHERE_STATEMENT)
                qb.setProjectionMap(sAlarmsWithInstancesProjection)
            }
            else -> throw IllegalArgumentException("Unknown URI $uri")
        }

        val ret: Cursor? = qb.query(db, projectionIn, selection, selectionArgs, null, null, sort)
        if (ret == null) {
            LogUtils.e("Alarms.query: failed")
        } else {
            ret.setNotificationUri(getContext()!!.getContentResolver(), uri)
        }

        return ret
    }

    override fun getType(uri: Uri): String {
        return when (sURIMatcher.match(uri)) {
            ALARMS -> "vnd.android.cursor.dir/alarms"
            ALARMS_ID -> "vnd.android.cursor.item/alarms"
            INSTANCES -> "vnd.android.cursor.dir/instances"
            INSTANCES_ID -> "vnd.android.cursor.item/instances"
            else -> throw IllegalArgumentException("Unknown URI")
        }
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        where: String?,
        whereArgs: Array<String?>?
    ): Int {
        val count: Int
        val alarmId: String?
        val db: SQLiteDatabase = mOpenHelper.getWritableDatabase()
        when (sURIMatcher.match(uri)) {
            ALARMS_ID -> {
                alarmId = uri.getLastPathSegment()
                count = db.update(ALARMS_TABLE_NAME, values,
                        BaseColumns._ID.toString() + "=" + alarmId,
                        null)
            }
            INSTANCES_ID -> {
                alarmId = uri.getLastPathSegment()
                count = db.update(INSTANCES_TABLE_NAME, values,
                        BaseColumns._ID.toString() + "=" + alarmId,
                        null)
            }
            else -> {
                throw UnsupportedOperationException("Cannot update URI: $uri")
            }
        }
        LogUtils.v("*** notifyChange() id: $alarmId url $uri")
        notifyChange(getContext()!!.getContentResolver(), uri)
        return count
    }

    override fun insert(uri: Uri, initialValues: ContentValues?): Uri? {
        val db: SQLiteDatabase = mOpenHelper.getWritableDatabase()
        val rowId: Long = when (sURIMatcher.match(uri)) {
            ALARMS -> mOpenHelper.fixAlarmInsert(initialValues!!)
            INSTANCES -> db.insert(INSTANCES_TABLE_NAME, null, initialValues)
            else -> throw IllegalArgumentException("Cannot insert from URI: $uri")
        }

        val uriResult: Uri = ContentUris.withAppendedId(uri, rowId)
        notifyChange(getContext()!!.getContentResolver(), uriResult)
        return uriResult
    }

    override fun delete(uri: Uri, where: String?, whereArgs: Array<String>?): Int {
        var whereString = where
        val count: Int
        val primaryKey: String?
        val db: SQLiteDatabase = mOpenHelper.getWritableDatabase()
        when (sURIMatcher.match(uri)) {
            ALARMS -> count =
                    db.delete(ALARMS_TABLE_NAME, whereString, whereArgs)
            ALARMS_ID -> {
                primaryKey = uri.getLastPathSegment()
                whereString = if (TextUtils.isEmpty(whereString)) {
                    BaseColumns._ID.toString() + "=" + primaryKey
                } else {
                    BaseColumns._ID.toString() + "=" + primaryKey + " AND (" + whereString + ")"
                }
                count = db.delete(ALARMS_TABLE_NAME, whereString, whereArgs)
            }
            INSTANCES -> count =
                    db.delete(INSTANCES_TABLE_NAME, whereString, whereArgs)
            INSTANCES_ID -> {
                primaryKey = uri.getLastPathSegment()
                whereString = if (TextUtils.isEmpty(whereString)) {
                    BaseColumns._ID.toString() + "=" + primaryKey
                } else {
                    BaseColumns._ID.toString() + "=" + primaryKey + " AND (" + whereString + ")"
                }
                count = db.delete(INSTANCES_TABLE_NAME, whereString, whereArgs)
            }
            else -> throw IllegalArgumentException("Cannot delete from URI: $uri")
        }

        notifyChange(getContext()!!.getContentResolver(), uri)
        return count
    }

    /**
     * Notify affected URIs of changes.
     */
    private fun notifyChange(resolver: ContentResolver, uri: Uri) {
        resolver.notifyChange(uri, null)

        val match: Int = sURIMatcher.match(uri)
        // Also notify the joined table of changes to instances or alarms.
        if (match == ALARMS || match == INSTANCES || match == ALARMS_ID || match == INSTANCES_ID) {
            resolver.notifyChange(AlarmsColumns.ALARMS_WITH_INSTANCES_URI, null)
        }
    }
}