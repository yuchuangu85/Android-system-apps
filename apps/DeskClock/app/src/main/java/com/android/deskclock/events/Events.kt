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

package com.android.deskclock.events

import androidx.annotation.StringRes

import com.android.deskclock.R
import com.android.deskclock.controller.Controller

/**
 * This thin layer over [Controller.sendEvent] eases the API usage.
 */
object Events {
    /** Extra describing the entity responsible for the action being performed.  */
    const val EXTRA_EVENT_LABEL = "com.android.deskclock.extra.EVENT_LABEL"

    /**
     * Tracks an alarm event.
     *
     * @param action resource id of event action
     * @param label resource id of event label
     */
    @JvmStatic
    fun sendAlarmEvent(@StringRes action: Int, @StringRes label: Int) {
        sendEvent(R.string.category_alarm, action, label)
    }

    /**
     * Tracks a clock event.
     *
     * @param action resource id of event action
     * @param label resource id of event label
     */
    @JvmStatic
    fun sendClockEvent(@StringRes action: Int, @StringRes label: Int) {
        sendEvent(R.string.category_clock, action, label)
    }

    /**
     * Tracks a timer event.
     *
     * @param action resource id of event action
     * @param label resource id of event label
     */
    @JvmStatic
    fun sendTimerEvent(@StringRes action: Int, @StringRes label: Int) {
        sendEvent(R.string.category_timer, action, label)
    }

    /**
     * Tracks a stopwatch event.
     *
     * @param action resource id of event action
     * @param label resource id of event label
     */
    @JvmStatic
    fun sendStopwatchEvent(@StringRes action: Int, @StringRes label: Int) {
        sendEvent(R.string.category_stopwatch, action, label)
    }

    /**
     * Tracks a screensaver event.
     *
     * @param action resource id of event action
     * @param label resource id of event label
     */
    @JvmStatic
    fun sendScreensaverEvent(@StringRes action: Int, @StringRes label: Int) {
        sendEvent(R.string.category_screensaver, action, label)
    }

    /**
     * Tracks an event. Events have a category, action, label and value. This
     * method can be used to track events such as button presses or other user
     * interactions with your application (value is not used in this app).
     *
     * @param category resource id of event category
     * @param action resource id of event action
     * @param label resource id of event label
     */
    @JvmStatic
    fun sendEvent(@StringRes category: Int, @StringRes action: Int, @StringRes label: Int) {
        Controller.getController().sendEvent(category, action, label)
    }
}