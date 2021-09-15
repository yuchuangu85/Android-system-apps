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

package com.android.deskclock.stopwatch

import android.app.Service
import android.content.Intent
import android.os.IBinder

import com.android.deskclock.DeskClock
import com.android.deskclock.R
import com.android.deskclock.data.DataModel
import com.android.deskclock.events.Events
import com.android.deskclock.uidata.UiDataModel

/**
 * This service exists solely to allow the stopwatch notification to alter the state of the
 * stopwatch without disturbing the notification shade. If an activity were used instead (even one
 * that is not displayed) the notification manager implicitly closes the notification shade which
 * clashes with the use case of starting/pausing/lapping/resetting the stopwatch without
 * disturbing the notification shade.
 */
class StopwatchService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val action: String? = intent.getAction()
        val label: Int = intent.getIntExtra(Events.EXTRA_EVENT_LABEL, R.string.label_intent)
        when (action) {
            ACTION_SHOW_STOPWATCH -> {
                Events.sendStopwatchEvent(R.string.action_show, label)

                // Open DeskClock positioned on the stopwatch tab.
                UiDataModel.uiDataModel.selectedTab = UiDataModel.Tab.STOPWATCH
                val showStopwatch: Intent = Intent(this, DeskClock::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(showStopwatch)
            }
            ACTION_START_STOPWATCH -> {
                Events.sendStopwatchEvent(R.string.action_start, label)
                DataModel.dataModel.startStopwatch()
            }
            ACTION_PAUSE_STOPWATCH -> {
                Events.sendStopwatchEvent(R.string.action_pause, label)
                DataModel.dataModel.pauseStopwatch()
            }
            ACTION_RESET_STOPWATCH -> {
                Events.sendStopwatchEvent(R.string.action_reset, label)
                DataModel.dataModel.resetStopwatch()
            }
            ACTION_LAP_STOPWATCH -> {
                Events.sendStopwatchEvent(R.string.action_lap, label)
                DataModel.dataModel.addLap()
            }
        }

        return START_NOT_STICKY
    }

    companion object {
        private const val ACTION_PREFIX = "com.android.deskclock.action."

        // shows the tab with the stopwatch
        const val ACTION_SHOW_STOPWATCH = ACTION_PREFIX + "SHOW_STOPWATCH"

        // starts the current stopwatch
        const val ACTION_START_STOPWATCH = ACTION_PREFIX + "START_STOPWATCH"

        // pauses the current stopwatch that's currently running
        const val ACTION_PAUSE_STOPWATCH = ACTION_PREFIX + "PAUSE_STOPWATCH"

        // laps the stopwatch that's currently running
        const val ACTION_LAP_STOPWATCH = ACTION_PREFIX + "LAP_STOPWATCH"

        // resets the stopwatch if it's stopped
        const val ACTION_RESET_STOPWATCH = ACTION_PREFIX + "RESET_STOPWATCH"
    }
}