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

import android.text.TextUtils
import android.text.format.DateUtils.HOUR_IN_MILLIS
import android.text.format.DateUtils.MINUTE_IN_MILLIS
import android.text.format.DateUtils.SECOND_IN_MILLIS

import com.android.deskclock.Utils

import kotlin.math.max
import kotlin.math.min

/**
 * A read-only domain object representing a countdown timer.
 */
class Timer internal constructor(
    /** A unique identifier for the timer.  */
    val id: Int,
    /** The current state of the timer.  */
    val state: State,
    /** The original length of the timer in milliseconds when it was created.  */
    val length: Long,
    /** The length of the timer in milliseconds including additional time added by the user.  */
    val totalLength: Long,
    /** The time at which the timer was last started; [.UNUSED] when not running.  */
    val lastStartTime: Long,
    /** The time since epoch at which the timer was last started.  */
    val lastWallClockTime: Long,
    /** The time at which the timer is scheduled to expire; negative if it is already expired.  */
    val lastRemainingTime: Long,
    /** A message describing the meaning of the timer.  */
    val label: String?,
    /** A flag indicating the timer should be deleted when it is reset.  */
    val deleteAfterUse: Boolean
) {

    enum class State(
        /** The value assigned to this State in prior releases.  */
        val value: Int
    ) {
        RUNNING(1), PAUSED(2), EXPIRED(3), RESET(4), MISSED(5);

        companion object {
            /**
             * @return the state corresponding to the given `value`
             */
            fun fromValue(value: Int): State? {
                for (state in values()) {
                    if (state.value == value) {
                        return state
                    }
                }
                return null
            }
        }
    }

    val isReset: Boolean
        get() = state == State.RESET

    val isRunning: Boolean
        get() = state == State.RUNNING

    val isPaused: Boolean
        get() = state == State.PAUSED

    val isExpired: Boolean
        get() = state == State.EXPIRED

    val isMissed: Boolean
        get() = state == State.MISSED

    /**
     * @return the total amount of time remaining up to this moment; expired and missed timers will
     * return a negative amount
     */
    val remainingTime: Long
        get() {
            if (state == State.PAUSED || state == State.RESET) {
                return lastRemainingTime
            }

            // In practice, "now" can be any value due to device reboots. When the real-time clock
            // is reset, there is no more guarantee that "now" falls after the last start time. To
            // ensure the timer is monotonically decreasing, normalize negative time segments to 0,
            val timeSinceStart = Utils.now() - lastStartTime
            return lastRemainingTime - max(0, timeSinceStart)
        }

    /**
     * @return the elapsed realtime at which this timer will or did expire
     */
    val expirationTime: Long
        get() {
            check(!(state != State.RUNNING && state != State.EXPIRED && state != State.MISSED)) {
                "cannot compute expiration time in state $state"
            }
            return lastStartTime + lastRemainingTime
        }

    /**
     * @return the wall clock time at which this timer will or did expire
     */
    val wallClockExpirationTime: Long
        get() {
            check(!(state != State.RUNNING && state != State.EXPIRED && state != State.MISSED)) {
                "cannot compute expiration time in state $state"
            }
            return lastWallClockTime + lastRemainingTime
        }

    /**
     *
     * @return the total amount of time elapsed up to this moment; expired timers will report more
     * than the [total length][.getTotalLength]
     */
    val elapsedTime: Long
        get() = totalLength - remainingTime

    /**
     * @return a copy of this timer that is running, expired or missed
     */
    fun start(): Timer {
        return if (state == State.RUNNING || state == State.EXPIRED || state == State.MISSED) {
            this
        } else {
            Timer(id, State.RUNNING, length, totalLength,
                    Utils.now(), Utils.wallClock(), lastRemainingTime, label, deleteAfterUse)
        }
    }

    /**
     * @return a copy of this timer that is paused or reset
     */
    fun pause(): Timer {
        if (state == State.PAUSED || state == State.RESET) {
            return this
        } else if (state == State.EXPIRED || state == State.MISSED) {
            return reset()
        }

        val remainingTime = this.remainingTime
        return Timer(id, State.PAUSED, length, totalLength, UNUSED, UNUSED, remainingTime, label,
                deleteAfterUse)
    }

    /**
     * @return a copy of this timer that is expired, missed or reset
     */
    fun expire(): Timer {
        if (state == State.EXPIRED || state == State.RESET || state == State.MISSED) {
            return this
        }

        val remainingTime = min(0L, lastRemainingTime)
        return Timer(id, State.EXPIRED, length, 0L, Utils.now(),
                Utils.wallClock(), remainingTime, label, deleteAfterUse)
    }

    /**
     * @return a copy of this timer that is missed or reset
     */
    fun miss(): Timer {
        if (state == State.RESET || state == State.MISSED) {
            return this
        }

        val remainingTime = min(0L, lastRemainingTime)
        return Timer(id, State.MISSED, length, 0L, Utils.now(),
                Utils.wallClock(), remainingTime, label, deleteAfterUse)
    }

    /**
     * @return a copy of this timer that is reset
     */
    fun reset(): Timer {
        return if (state == State.RESET) {
            this
        } else {
            Timer(id, State.RESET, length, length, UNUSED, UNUSED, length, label,
                    deleteAfterUse)
        }
    }

    /**
     * @return a copy of this timer that has its times adjusted after a reboot
     */
    fun updateAfterReboot(): Timer {
        if (state == State.RESET || state == State.PAUSED) {
            return this
        }
        val timeSinceBoot = Utils.now()
        val wallClockTime = Utils.wallClock()
        // Avoid negative time deltas. They can happen in practice, but they can't be used. Simply
        // update the recorded times and proceed with no change in accumulated time.
        val delta = max(0, wallClockTime - lastWallClockTime)
        val remainingTime = lastRemainingTime - delta
        return Timer(id, state, length, totalLength, timeSinceBoot, wallClockTime,
                remainingTime, label, deleteAfterUse)
    }

    /**
     * @return a copy of this timer that has its times adjusted after time has been set
     */
    fun updateAfterTimeSet(): Timer {
        if (state == State.RESET || state == State.PAUSED) {
            return this
        }
        val timeSinceBoot = Utils.now()
        val wallClockTime = Utils.wallClock()
        val delta = timeSinceBoot - lastStartTime
        val remainingTime = lastRemainingTime - delta
        return if (delta < 0) {
            // Avoid negative time deltas. They typically happen following reboots when TIME_SET is
            // broadcast before BOOT_COMPLETED. Simply ignore the time update and hope
            // updateAfterReboot() can successfully correct the data at a later time.
            this
        } else {
            Timer(id, state, length, totalLength, timeSinceBoot, wallClockTime,
                    remainingTime, label, deleteAfterUse)
        }
    }

    /**
     * @return a copy of this timer with the given `label`
     */
    fun setLabel(label: String?): Timer {
        return if (TextUtils.equals(this.label, label)) {
            this
        } else {
            Timer(id, state, length, totalLength, lastStartTime,
                    lastWallClockTime, lastRemainingTime, label, deleteAfterUse)
        }
    }

    /**
     * @return a copy of this timer with the given `length` or this timer if the length could
     * not be legally adjusted
     */
    fun setLength(length: Long): Timer {
        if (this.length == length || length <= MIN_LENGTH) {
            return this
        }

        val totalLength: Long
        val remainingTime: Long
        if (state == State.RESET) {
            totalLength = length
            remainingTime = length
        } else {
            totalLength = this.totalLength
            remainingTime = lastRemainingTime
        }

        return Timer(id, state, length, totalLength, lastStartTime,
                lastWallClockTime, remainingTime, label, deleteAfterUse)
    }

    /**
     * @return a copy of this timer with the given `remainingTime` or this timer if the
     * remaining time could not be legally adjusted
     */
    fun setRemainingTime(remainingTime: Long): Timer {
        // Do not change the remaining time of a reset timer.
        if (lastRemainingTime == remainingTime || state == State.RESET) {
            return this
        }

        val delta = remainingTime - lastRemainingTime
        val totalLength = totalLength + delta

        val lastStartTime: Long
        val lastWallClockTime: Long
        val state: State?
        if (remainingTime > 0 && (this.state == State.EXPIRED || this.state == State.MISSED)) {
            state = State.RUNNING
            lastStartTime = Utils.now()
            lastWallClockTime = Utils.wallClock()
        } else {
            state = this.state
            lastStartTime = this.lastStartTime
            lastWallClockTime = this.lastWallClockTime
        }

        return Timer(id, state, length, totalLength, lastStartTime,
                lastWallClockTime, remainingTime, label, deleteAfterUse)
    }

    /**
     * @return a copy of this timer with an additional minute added to the remaining time and total
     * length, or this Timer if the minute could not be added
     */
    fun addMinute(): Timer {
        return if (state == State.EXPIRED || state == State.MISSED) {
            // Expired and missed timers restart with 60 seconds of remaining time.
            setRemainingTime(MINUTE_IN_MILLIS)
        } else {
            // Otherwise try to add a minute to the remaining time.
            setRemainingTime(lastRemainingTime + MINUTE_IN_MILLIS)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val timer = other as Timer

        return id == timer.id
    }

    override fun hashCode(): Int {
        return id
    }

    companion object {
        /** The minimum duration of a timer.  */
        @JvmField
        val MIN_LENGTH: Long = SECOND_IN_MILLIS

        /** The maximum duration of a new timer created via the user interface.  */
        val MAX_LENGTH: Long = 99 * HOUR_IN_MILLIS + 99 * MINUTE_IN_MILLIS + 99 * SECOND_IN_MILLIS

        const val UNUSED = Long.MIN_VALUE

        /**
         * Orders timers by their IDs. Oldest timers are at the bottom. Newest timers are at the top
         */
        @JvmField
        var ID_COMPARATOR = Comparator<Timer> { timer1, timer2 -> timer2.id.compareTo(timer1.id) }

        /**
         * Orders timers by their expected/actual expiration time. The general order is:
         *
         *  1. [MISSED][State.MISSED] timers; ties broken by [.getRemainingTime]
         *  2. [EXPIRED][State.EXPIRED] timers; ties broken by [.getRemainingTime]
         *  3. [RUNNING][State.RUNNING] timers; ties broken by [.getRemainingTime]
         *  4. [PAUSED][State.PAUSED] timers; ties broken by [.getRemainingTime]
         *  5. [RESET][State.RESET] timers; ties broken by [.getLength]
         *
         */
        @JvmField
        var EXPIRY_COMPARATOR: Comparator<Timer> = object : Comparator<Timer> {
            private val stateExpiryOrder =
                    listOf(State.MISSED, State.EXPIRED, State.RUNNING, State.PAUSED, State.RESET)

            override fun compare(timer1: Timer, timer2: Timer): Int {
                val stateIndex1 = stateExpiryOrder.indexOf(timer1.state)
                val stateIndex2 = stateExpiryOrder.indexOf(timer2.state)

                var order = stateIndex1.compareTo(stateIndex2)
                if (order == 0) {
                    val state = timer1.state
                    order = if (state == State.RESET) {
                        timer1.length.compareTo(timer2.length)
                    } else {
                        timer1.lastRemainingTime.compareTo(timer2.lastRemainingTime)
                    }
                }

                return order
            }
        }
    }
}