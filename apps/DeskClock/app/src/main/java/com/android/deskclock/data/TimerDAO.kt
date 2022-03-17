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

package com.android.deskclock.data

import android.content.SharedPreferences

/**
 * This class encapsulates the transfer of data between [Timer] domain objects and their
 * permanent storage in [SharedPreferences].
 */
internal object TimerDAO {
    /** Key to a preference that stores the set of timer ids.  */
    private const val TIMER_IDS = "timers_list"

    /** Key to a preference that stores the id to assign to the next timer.  */
    private const val NEXT_TIMER_ID = "next_timer_id"

    /** Prefix for a key to a preference that stores the state of the timer.  */
    private const val STATE = "timer_state_"

    /** Prefix for a key to a preference that stores the original timer length at creation.  */
    private const val LENGTH = "timer_setup_timet_"

    /** Prefix for a key to a preference that stores the total timer length with additions.  */
    private const val TOTAL_LENGTH = "timer_original_timet_"

    /** Prefix for a key to a preference that stores the last start time of the timer.  */
    private const val LAST_START_TIME = "timer_start_time_"

    /** Prefix for a key to a preference that stores the epoch time when the timer last started.  */
    private const val LAST_WALL_CLOCK_TIME = "timer_wall_clock_time_"

    /** Prefix for a key to a preference that stores the remaining time before expiry.  */
    private const val REMAINING_TIME = "timer_time_left_"

    /** Prefix for a key to a preference that stores the label of the timer.  */
    private const val LABEL = "timer_label_"

    /** Prefix for a key to a preference that signals the timer should be deleted on first reset. */
    private const val DELETE_AFTER_USE = "delete_after_use_"

    /**
     * @return the timers from permanent storage
     */
    @JvmStatic
    fun getTimers(prefs: SharedPreferences): MutableList<Timer> {
        // Read the set of timer ids.
        val timerIds: Set<String> = prefs.getStringSet(TIMER_IDS, emptySet<String>())!!
        val timers: MutableList<Timer> = ArrayList(timerIds.size)

        // Build a timer using the data associated with each timer id.
        for (timerId in timerIds) {
            val id = timerId.toInt()
            val stateValue: Int = prefs.getInt(STATE + id, Timer.State.RESET.value)
            val state: Timer.State? = Timer.State.fromValue(stateValue)

            // Timer state may be null when migrating timers from prior releases which defined a
            // "deleted" state. Such a state is no longer required.
            state?.let {
                val length: Long = prefs.getLong(LENGTH + id, Long.MIN_VALUE)
                val totalLength: Long = prefs.getLong(TOTAL_LENGTH + id, Long.MIN_VALUE)
                val lastStartTime: Long = prefs.getLong(LAST_START_TIME + id, Timer.UNUSED)
                val lastWallClockTime: Long = prefs.getLong(LAST_WALL_CLOCK_TIME + id, Timer.UNUSED)
                val remainingTime: Long = prefs.getLong(REMAINING_TIME + id, totalLength)
                val label: String? = prefs.getString(LABEL + id, null)
                val deleteAfterUse: Boolean = prefs.getBoolean(DELETE_AFTER_USE + id, false)
                timers.add(Timer(id, it, length, totalLength, lastStartTime,
                        lastWallClockTime, remainingTime, label, deleteAfterUse))
            }
        }

        return timers
    }

    /**
     * @param timer the timer to be added
     */
    @JvmStatic
    fun addTimer(prefs: SharedPreferences, timer: Timer): Timer {
        val editor: SharedPreferences.Editor = prefs.edit()

        // Fetch the next timer id.
        val id: Int = prefs.getInt(NEXT_TIMER_ID, 0)
        editor.putInt(NEXT_TIMER_ID, id + 1)

        // Add the new timer id to the set of all timer ids.
        val timerIds: MutableSet<String> = HashSet(getTimerIds(prefs))
        timerIds.add(id.toString())
        editor.putStringSet(TIMER_IDS, timerIds)

        // Record the fields of the timer.
        editor.putInt(STATE + id, timer.state.value)
        editor.putLong(LENGTH + id, timer.length)
        editor.putLong(TOTAL_LENGTH + id, timer.totalLength)
        editor.putLong(LAST_START_TIME + id, timer.lastStartTime)
        editor.putLong(LAST_WALL_CLOCK_TIME + id, timer.lastWallClockTime)
        editor.putLong(REMAINING_TIME + id, timer.remainingTime)
        editor.putString(LABEL + id, timer.label)
        editor.putBoolean(DELETE_AFTER_USE + id, timer.deleteAfterUse)

        editor.apply()

        // Return a new timer with the generated timer id present.
        return Timer(id, timer.state, timer.length, timer.totalLength,
                timer.lastStartTime, timer.lastWallClockTime, timer.remainingTime,
                timer.label, timer.deleteAfterUse)
    }

    /**
     * @param timer the timer to be updated
     */
    @JvmStatic
    fun updateTimer(prefs: SharedPreferences, timer: Timer) {
        val editor: SharedPreferences.Editor = prefs.edit()

        // Record the fields of the timer.
        val id = timer.id
        editor.putInt(STATE + id, timer.state.value)
        editor.putLong(LENGTH + id, timer.length)
        editor.putLong(TOTAL_LENGTH + id, timer.totalLength)
        editor.putLong(LAST_START_TIME + id, timer.lastStartTime)
        editor.putLong(LAST_WALL_CLOCK_TIME + id, timer.lastWallClockTime)
        editor.putLong(REMAINING_TIME + id, timer.remainingTime)
        editor.putString(LABEL + id, timer.label)
        editor.putBoolean(DELETE_AFTER_USE + id, timer.deleteAfterUse)

        editor.apply()
    }

    /**
     * @param timer the timer to be removed
     */
    @JvmStatic
    fun removeTimer(prefs: SharedPreferences, timer: Timer) {
        val editor: SharedPreferences.Editor = prefs.edit()
        val id = timer.id

        // Remove the timer id from the set of all timer ids.
        val timerIds: MutableSet<String> = HashSet(getTimerIds(prefs))
        timerIds.remove(id.toString())
        if (timerIds.isEmpty()) {
            editor.remove(TIMER_IDS)
            editor.remove(NEXT_TIMER_ID)
        } else {
            editor.putStringSet(TIMER_IDS, timerIds)
        }

        // Record the fields of the timer.
        editor.remove(STATE + id)
        editor.remove(LENGTH + id)
        editor.remove(TOTAL_LENGTH + id)
        editor.remove(LAST_START_TIME + id)
        editor.remove(LAST_WALL_CLOCK_TIME + id)
        editor.remove(REMAINING_TIME + id)
        editor.remove(LABEL + id)
        editor.remove(DELETE_AFTER_USE + id)

        editor.apply()
    }

    private fun getTimerIds(prefs: SharedPreferences): Set<String> {
        return prefs.getStringSet(TIMER_IDS, emptySet<String>())!!
    }
}