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

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

import com.android.deskclock.AlarmAlertWakeLock.createPartialWakeLock
import com.android.deskclock.alarms.AlarmStateManager
import com.android.deskclock.controller.Controller
import com.android.deskclock.data.DataModel

class AlarmInitReceiver : BroadcastReceiver() {
    /**
     * This receiver handles a variety of actions:
     *
     * <ul>
     *     <li>Clean up backup data that was recently restored to this device on
     *     ACTION_COMPLETE_RESTORE.</li>
     *     <li>Reset timers and stopwatch on ACTION_BOOT_COMPLETED</li>
     *     <li>Fix alarm states on ACTION_BOOT_COMPLETED, TIME_SET, TIMEZONE_CHANGED,
     *     and LOCALE_CHANGED</li>
     *     <li>Rebuild notifications on MY_PACKAGE_REPLACED</li>
     * </ul>
     */
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        LogUtils.i("AlarmInitReceiver $action")

        val result = goAsync()
        val wl = createPartialWakeLock(context)
        wl.acquire()

        // We need to increment the global id out of the async task to prevent race conditions
        DataModel.dataModel.updateGlobalIntentId()

        // Updates stopwatch and timer data after a device reboot so they are as accurate as
        // possible.
        if (ACTION_BOOT_COMPLETED == action) {
            DataModel.dataModel.updateAfterReboot()
            // Stopwatch and timer data need to be updated on time change so the reboot
            // functionality works as expected.
        } else if (Intent.ACTION_TIME_CHANGED == action) {
            DataModel.dataModel.updateAfterTimeSet()
        }

        // Update shortcuts so they exist for the user.
        if (Intent.ACTION_BOOT_COMPLETED == action || Intent.ACTION_LOCALE_CHANGED == action) {
            Controller.getController().updateShortcuts()
            NotificationUtils.updateNotificationChannels(context)
        }

        // Notifications are canceled by the system on application upgrade. This broadcast signals
        // that the new app is free to rebuild the notifications using the existing data.
        // Additionally on new app installs, make sure to enable shortcuts immediately as opposed
        // to waiting for system reboot.
        if (Intent.ACTION_MY_PACKAGE_REPLACED == action) {
            DataModel.dataModel.updateAllNotifications()
            Controller.getController().updateShortcuts()
        }

        AsyncHandler.post {
            try {
                // Process restored data if any exists
                if (!DeskClockBackupAgent.processRestoredData(context)) {
                    // Update all the alarm instances on time change event
                    AlarmStateManager.fixAlarmInstances(context)
                }
            } finally {
                result.finish()
                wl.release()
                LogUtils.v("AlarmInitReceiver finished")
            }
        }
    }

    companion object {
        /**
         * When running on N devices, we're interested in the boot completed event that is sent
         * while the user is still locked, so that we can schedule alarms.
         */
        @SuppressLint("InlinedApi")
        private val ACTION_BOOT_COMPLETED = if (Utils.isNOrLater) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED
        } else {
            Intent.ACTION_BOOT_COMPLETED
        }
    }
}