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

package com.android.deskclock.timer

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

import com.android.deskclock.DeskClock
import com.android.deskclock.R
import com.android.deskclock.data.DataModel
import com.android.deskclock.data.Timer
import com.android.deskclock.events.Events
import com.android.deskclock.uidata.UiDataModel

/**
 *
 * This service exists solely to allow [android.app.AlarmManager] and timer notifications
 * to alter the state of timers without disturbing the notification shade. If an activity were used
 * instead (even one that is not displayed) the notification manager implicitly closes the
 * notification shade which clashes with the use case of starting/pausing/resetting timers without
 * disturbing the notification shade.
 *
 * The service has a second benefit. It is used to start heads-up notifications for expired
 * timers in the foreground. This keeps the entire application in the foreground and thus prevents
 * the operating system from killing it while expired timers are firing.
 */
class TimerService : Service() {
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        try {
            val action = intent.action
            val label = intent.getIntExtra(Events.EXTRA_EVENT_LABEL, R.string.label_intent)
            when (action) {
                ACTION_UPDATE_NOTIFICATION -> {
                    DataModel.dataModel.updateTimerNotification()
                    return START_NOT_STICKY
                }
                ACTION_RESET_EXPIRED_TIMERS -> {
                    DataModel.dataModel.resetOrDeleteExpiredTimers(label)
                    return START_NOT_STICKY
                }
                ACTION_RESET_UNEXPIRED_TIMERS -> {
                    DataModel.dataModel.resetUnexpiredTimers(label)
                    return START_NOT_STICKY
                }
                ACTION_RESET_MISSED_TIMERS -> {
                    DataModel.dataModel.resetMissedTimers(label)
                    return START_NOT_STICKY
                }
            }

            // Look up the timer in question.
            val timerId = intent.getIntExtra(EXTRA_TIMER_ID, -1)
            // If the timer cannot be located, ignore the action.
            val timer: Timer = DataModel.dataModel.getTimer(timerId) ?: return START_NOT_STICKY

            when (action) {
                ACTION_SHOW_TIMER -> {
                    Events.sendTimerEvent(R.string.action_show, label)

                    // Change to the timers tab.
                    UiDataModel.uiDataModel.selectedTab = UiDataModel.Tab.TIMERS

                    // Open DeskClock which is now positioned on the timers tab and show the timer
                    // in question.
                    val showTimers = Intent(this, DeskClock::class.java)
                            .putExtra(EXTRA_TIMER_ID, timerId)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(showTimers)
                }
                ACTION_START_TIMER -> {
                    Events.sendTimerEvent(R.string.action_start, label)
                    DataModel.dataModel.startTimer(this, timer)
                }
                ACTION_PAUSE_TIMER -> {
                    Events.sendTimerEvent(R.string.action_pause, label)
                    DataModel.dataModel.pauseTimer(timer)
                }
                ACTION_ADD_MINUTE_TIMER -> {
                    Events.sendTimerEvent(R.string.action_add_minute, label)
                    DataModel.dataModel.addTimerMinute(timer)
                }
                ACTION_RESET_TIMER -> {
                    DataModel.dataModel.resetOrDeleteTimer(timer, label)
                }
                ACTION_TIMER_EXPIRED -> {
                    Events.sendTimerEvent(R.string.action_fire, label)
                    DataModel.dataModel.expireTimer(this, timer)
                }
            }
        } finally {
            // This service is foreground when expired timers exist and stopped when none exist.
            if (DataModel.dataModel.expiredTimers.isEmpty()) {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    companion object {
        private const val ACTION_PREFIX = "com.android.deskclock.action."

        /** Shows the tab with timers; scrolls to a specific timer.  */
        const val ACTION_SHOW_TIMER = ACTION_PREFIX + "SHOW_TIMER"
        /** Pauses running timers; resets expired timers.  */
        const val ACTION_PAUSE_TIMER = ACTION_PREFIX + "PAUSE_TIMER"
        /** Starts the sole timer.  */
        const val ACTION_START_TIMER = ACTION_PREFIX + "START_TIMER"
        /** Resets the timer.  */
        const val ACTION_RESET_TIMER = ACTION_PREFIX + "RESET_TIMER"
        /** Adds an extra minute to the timer.  */
        const val ACTION_ADD_MINUTE_TIMER = ACTION_PREFIX + "ADD_MINUTE_TIMER"
        /** Extra for many actions specific to a given timer.  */
        const val EXTRA_TIMER_ID = "com.android.deskclock.extra.TIMER_ID"

        private const val ACTION_TIMER_EXPIRED = ACTION_PREFIX + "TIMER_EXPIRED"
        private const val ACTION_UPDATE_NOTIFICATION = ACTION_PREFIX + "UPDATE_NOTIFICATION"
        private const val ACTION_RESET_EXPIRED_TIMERS = ACTION_PREFIX + "RESET_EXPIRED_TIMERS"
        private const val ACTION_RESET_UNEXPIRED_TIMERS = ACTION_PREFIX + "RESET_UNEXPIRED_TIMERS"
        private const val ACTION_RESET_MISSED_TIMERS = ACTION_PREFIX + "RESET_MISSED_TIMERS"

        @JvmStatic
        fun createTimerExpiredIntent(context: Context, timer: Timer?): Intent {
            val timerId = timer?.id ?: -1
            return Intent(context, TimerService::class.java)
                    .setAction(ACTION_TIMER_EXPIRED)
                    .putExtra(EXTRA_TIMER_ID, timerId)
        }

        fun createResetExpiredTimersIntent(context: Context): Intent {
            return Intent(context, TimerService::class.java)
                    .setAction(ACTION_RESET_EXPIRED_TIMERS)
        }

        fun createResetUnexpiredTimersIntent(context: Context): Intent {
            return Intent(context, TimerService::class.java)
                    .setAction(ACTION_RESET_UNEXPIRED_TIMERS)
        }

        fun createResetMissedTimersIntent(context: Context): Intent {
            return Intent(context, TimerService::class.java)
                    .setAction(ACTION_RESET_MISSED_TIMERS)
        }

        fun createAddMinuteTimerIntent(context: Context, timerId: Int): Intent {
            return Intent(context, TimerService::class.java)
                    .setAction(ACTION_ADD_MINUTE_TIMER)
                    .putExtra(EXTRA_TIMER_ID, timerId)
        }

        fun createUpdateNotificationIntent(context: Context): Intent {
            return Intent(context, TimerService::class.java)
                    .setAction(ACTION_UPDATE_NOTIFICATION)
        }
    }
}