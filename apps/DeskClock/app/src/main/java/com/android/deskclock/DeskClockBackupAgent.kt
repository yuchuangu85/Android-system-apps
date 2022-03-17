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

package com.android.deskclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock

import com.android.deskclock.alarms.AlarmStateManager
import com.android.deskclock.data.DataModel
import com.android.deskclock.provider.Alarm
import com.android.deskclock.provider.AlarmInstance

import java.io.File
import java.io.IOException
import java.util.Calendar

class DeskClockBackupAgent : BackupAgent() {
    @Throws(IOException::class)
    override fun onBackup(
        oldState: ParcelFileDescriptor,
        data: BackupDataOutput,
        newState: ParcelFileDescriptor
    ) {
    }

    @Throws(IOException::class)
    override fun onRestore(
        data: BackupDataInput,
        appVersionCode: Int,
        newState: ParcelFileDescriptor
    ) {
    }

    @Throws(IOException::class)
    override fun onRestoreFile(
        data: ParcelFileDescriptor,
        size: Long,
        destination: File,
        type: Int,
        mode: Long,
        mtime: Long
    ) {
        // The preference file on the backup device may not be the same on the restore device.
        // Massage the file name here before writing it.
        var variableDestination = destination
        if (variableDestination.name.endsWith("_preferences.xml")) {
            val prefFileName = packageName + "_preferences.xml"
            variableDestination = File(variableDestination.parentFile, prefFileName)
        }

        super.onRestoreFile(data, size, variableDestination, type, mode, mtime)
    }

    /**
     * When this method is called during backup/restore, the application is executing in a
     * "minimalist" state. Because of this, the application's ContentResolver cannot be used.
     * Consequently, the work of scheduling alarms on the restore device cannot be done here.
     * Instead, a future callback to DeskClock is used as a signal to reschedule the alarms. The
     * future callback may take the form of ACTION_BOOT_COMPLETED if the device is not yet fully
     * booted (i.e. the restore occurred as part of the setup wizard). If the device is booted, an
     * ACTION_COMPLETE_RESTORE broadcast is scheduled 10 seconds in the future to give
     * backup/restore enough time to kill the Clock process. Both of these future callbacks result
     * in the execution of [.processRestoredData].
     */
    override fun onRestoreFinished() {
        if (Utils.isNOrLater) {
            // TODO: migrate restored database and preferences over into
            // the device-encrypted storage area
        }

        // Indicate a data restore has been completed.
        DataModel.dataModel.isRestoreBackupFinished = true

        // Create an Intent to send into DeskClock indicating restore is complete.
        val restoreIntent = PendingIntent.getBroadcast(this, 0,
                Intent(ACTION_COMPLETE_RESTORE).setClass(this, AlarmInitReceiver::class.java),
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_CANCEL_CURRENT)

        // Deliver the Intent 10 seconds from now.
        val triggerAtMillis = SystemClock.elapsedRealtime() + 10000

        // Schedule the Intent delivery in AlarmManager.
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, restoreIntent)

        LOGGER.i("Waiting for %s to complete the data restore", ACTION_COMPLETE_RESTORE)
    }

    companion object {
        private val LOGGER = LogUtils.Logger("DeskClockBackupAgent")

        const val ACTION_COMPLETE_RESTORE = "com.android.deskclock.action.COMPLETE_RESTORE"

        /**
         * @param context a context to access resources and services
         * @return `true` if restore data was processed; `false` otherwise.
         */
        @JvmStatic
        fun processRestoredData(context: Context): Boolean {
            // If data was not recently restored, there is nothing to do.
            if (!DataModel.dataModel.isRestoreBackupFinished) {
                return false
            }

            LOGGER.i("processRestoredData() started")

            // Now that alarms have been restored, schedule new instances in AlarmManager.
            val contentResolver = context.contentResolver
            val alarms = Alarm.getAlarms(contentResolver, null)

            val now = Calendar.getInstance()
            for (alarm in alarms) {
                // Remove any instances that may currently exist for the alarm;
                // these aren't relevant on the restore device and we'll recreate them below.
                AlarmStateManager.deleteAllInstances(context, alarm.id)

                if (alarm.enabled) {
                    // Create the next alarm instance to schedule.
                    var alarmInstance = alarm.createInstanceAfter(now)

                    // Add the next alarm instance to the database.
                    alarmInstance = AlarmInstance.addInstance(contentResolver, alarmInstance)

                    // Schedule the next alarm instance in AlarmManager.
                    AlarmStateManager.registerInstance(context, alarmInstance, true)
                    LOGGER.i("DeskClockBackupAgent scheduled alarm instance: %s", alarmInstance)
                }
            }

            // Remove the preference to avoid executing this logic multiple times.
            DataModel.dataModel.isRestoreBackupFinished = false

            LOGGER.i("processRestoredData() completed")
            return true
        }
    }
}