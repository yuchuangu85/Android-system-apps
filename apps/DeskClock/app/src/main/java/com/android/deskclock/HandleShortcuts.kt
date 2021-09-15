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

import android.app.Activity
import android.content.Intent
import android.os.Bundle

import com.android.deskclock.events.Events
import com.android.deskclock.stopwatch.StopwatchService
import com.android.deskclock.uidata.UiDataModel

class HandleShortcuts : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent

        try {
            when (val action = intent.action) {
                StopwatchService.ACTION_PAUSE_STOPWATCH -> {
                    Events.sendStopwatchEvent(R.string.action_pause, R.string.label_shortcut)

                    // Open DeskClock positioned on the stopwatch tab.
                    UiDataModel.uiDataModel.selectedTab = UiDataModel.Tab.STOPWATCH
                    startActivity(Intent(this, DeskClock::class.java)
                            .setAction(StopwatchService.ACTION_PAUSE_STOPWATCH))
                    setResult(RESULT_OK)
                }
                StopwatchService.ACTION_START_STOPWATCH -> {
                    Events.sendStopwatchEvent(R.string.action_start, R.string.label_shortcut)

                    // Open DeskClock positioned on the stopwatch tab.
                    UiDataModel.uiDataModel.selectedTab = UiDataModel.Tab.STOPWATCH
                    startActivity(Intent(this, DeskClock::class.java)
                            .setAction(StopwatchService.ACTION_START_STOPWATCH))
                    setResult(RESULT_OK)
                }
                else -> throw IllegalArgumentException("Unsupported action: $action")
            }
        } catch (e: Exception) {
            LOGGER.e("Error handling intent: $intent", e)
            setResult(RESULT_CANCELED)
        } finally {
            finish()
        }
    }

    companion object {
        private val LOGGER = LogUtils.Logger("HandleShortcuts")
    }
}