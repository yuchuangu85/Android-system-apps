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

import android.content.Context
import androidx.annotation.VisibleForTesting

import com.android.deskclock.R

import java.text.DateFormatSymbols
import java.util.Calendar

/**
 * This class is responsible for encoding a weekly repeat cycle in a [bitset][.getBits]. It
 * also converts between those bits and the [Calendar.DAY_OF_WEEK] values for easier mutation
 * and querying.
 */
class Weekdays private constructor(bits: Int) {
    /**
     * The preferred starting day of the week can differ by locale. This enumerated value is used to
     * describe the preferred ordering.
     */
    enum class Order(vararg calendarDays: Int) {
        SAT_TO_FRI(Calendar.SATURDAY, Calendar.SUNDAY, Calendar.MONDAY,
                Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY),
        SUN_TO_SAT(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY),
        MON_TO_SUN(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
                Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY);

        val calendarDays: List<Int> = calendarDays.asList()
    }

    companion object {
        /** All valid bits set.  */
        private const val ALL_DAYS = 0x7F

        /** An instance with all weekdays in the weekly repeat cycle.  */
        @JvmField
        val ALL = fromBits(ALL_DAYS)

        /** An instance with no weekdays in the weekly repeat cycle.  */
        @JvmField
        val NONE = fromBits(0)

        /** Maps calendar weekdays to the bit masks that represent them in this class.  */
        private val sCalendarDayToBit: Map<Int, Int>

        init {
            val map: MutableMap<Int, Int> = mutableMapOf()
            map[Calendar.MONDAY] = 0x01
            map[Calendar.TUESDAY] = 0x02
            map[Calendar.WEDNESDAY] = 0x04
            map[Calendar.THURSDAY] = 0x08
            map[Calendar.FRIDAY] = 0x10
            map[Calendar.SATURDAY] = 0x20
            map[Calendar.SUNDAY] = 0x40
            sCalendarDayToBit = map
        }

        /**
         * @param bits [bits][.getBits] representing the encoded weekly repeat schedule
         * @return a Weekdays instance representing the same repeat schedule as the `bits`
         */
        @JvmStatic
        fun fromBits(bits: Int): Weekdays {
            return Weekdays(bits)
        }

        /**
         * @param calendarDays an array containing any or all of the following values
         *
         *  * [Calendar.SUNDAY]
         *  * [Calendar.MONDAY]
         *  * [Calendar.TUESDAY]
         *  * [Calendar.WEDNESDAY]
         *  * [Calendar.THURSDAY]
         *  * [Calendar.FRIDAY]
         *  * [Calendar.SATURDAY]
         *
         * @return a Weekdays instance representing the given `calendarDays`
         */
        @JvmStatic
        fun fromCalendarDays(vararg calendarDays: Int): Weekdays {
            var bits = 0
            for (calendarDay in calendarDays) {
                val bit = sCalendarDayToBit[calendarDay]
                if (bit != null) {
                    bits = bits or bit
                }
            }
            return Weekdays(bits)
        }
    }

    /** An encoded form of a weekly repeat schedule.  */
    val bits: Int = ALL_DAYS and bits

    /**
     * @param calendarDay any of the following values
     *
     *  * [Calendar.SUNDAY]
     *  * [Calendar.MONDAY]
     *  * [Calendar.TUESDAY]
     *  * [Calendar.WEDNESDAY]
     *  * [Calendar.THURSDAY]
     *  * [Calendar.FRIDAY]
     *  * [Calendar.SATURDAY]
     *
     * @param on `true` if the `calendarDay` is on; `false` otherwise
     * @return a WeekDays instance with the `calendarDay` mutated
     */
    fun setBit(calendarDay: Int, on: Boolean): Weekdays {
        val bit = sCalendarDayToBit[calendarDay] ?: return this
        return Weekdays(if (on) bits or bit else bits and bit.inv())
    }

    /**
     * @param calendarDay any of the following values
     *
     *  * [Calendar.SUNDAY]
     *  * [Calendar.MONDAY]
     *  * [Calendar.TUESDAY]
     *  * [Calendar.WEDNESDAY]
     *  * [Calendar.THURSDAY]
     *  * [Calendar.FRIDAY]
     *  * [Calendar.SATURDAY]
     *
     * @return `true` if the given `calendarDay`
     */
    fun isBitOn(calendarDay: Int): Boolean {
        val bit = sCalendarDayToBit[calendarDay]
                ?: throw IllegalArgumentException("$calendarDay is not a valid weekday")
        return bits and bit > 0
    }

    /**
     * @return `true` iff at least one weekday is enabled in the repeat schedule
     */
    val isRepeating: Boolean
        get() = bits != 0

    /**
     * Note: only the day-of-week is read from the `time`. The time fields
     * are not considered in this computation.
     *
     * @param time a timestamp relative to which the answer is given
     * @return the number of days between the given `time` and the previous enabled weekday
     * which is always between 1 and 7 inclusive; `-1` if no weekdays are enabled
     */
    fun getDistanceToPreviousDay(time: Calendar): Int {
        var calendarDay = time[Calendar.DAY_OF_WEEK]
        for (count in 1..7) {
            calendarDay--
            if (calendarDay < Calendar.SUNDAY) {
                calendarDay = Calendar.SATURDAY
            }
            if (isBitOn(calendarDay)) {
                return count
            }
        }

        return -1
    }

    /**
     * Note: only the day-of-week is read from the `time`. The time fields
     * are not considered in this computation.
     *
     * @param time a timestamp relative to which the answer is given
     * @return the number of days between the given `time` and the next enabled weekday which
     * is always between 0 and 6 inclusive; `-1` if no weekdays are enabled
     */
    fun getDistanceToNextDay(time: Calendar): Int {
        var calendarDay = time[Calendar.DAY_OF_WEEK]
        for (count in 0..6) {
            if (isBitOn(calendarDay)) {
                return count
            }

            calendarDay++
            if (calendarDay > Calendar.SATURDAY) {
                calendarDay = Calendar.SUNDAY
            }
        }

        return -1
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val weekdays = other as Weekdays
        return bits == weekdays.bits
    }

    override fun hashCode(): Int {
        return bits
    }

    override fun toString(): String {
        val builder = StringBuilder(19)
        builder.append("[")
        if (isBitOn(Calendar.MONDAY)) {
            builder.append(if (builder.length > 1) " M" else "M")
        }
        if (isBitOn(Calendar.TUESDAY)) {
            builder.append(if (builder.length > 1) " T" else "T")
        }
        if (isBitOn(Calendar.WEDNESDAY)) {
            builder.append(if (builder.length > 1) " W" else "W")
        }
        if (isBitOn(Calendar.THURSDAY)) {
            builder.append(if (builder.length > 1) " Th" else "Th")
        }
        if (isBitOn(Calendar.FRIDAY)) {
            builder.append(if (builder.length > 1) " F" else "F")
        }
        if (isBitOn(Calendar.SATURDAY)) {
            builder.append(if (builder.length > 1) " Sa" else "Sa")
        }
        if (isBitOn(Calendar.SUNDAY)) {
            builder.append(if (builder.length > 1) " Su" else "Su")
        }
        builder.append("]")
        return builder.toString()
    }

    /**
     * @param context for accessing resources
     * @param order the order in which to present the weekdays
     * @return the enabled weekdays in the given `order`
     */
    fun toString(context: Context, order: Order): String {
        return toString(context, order, false /* forceLongNames */)
    }

    /**
     * @param context for accessing resources
     * @param order the order in which to present the weekdays
     * @return the enabled weekdays in the given `order` in a manner that
     * is most appropriate for talk-back
     */
    fun toAccessibilityString(context: Context, order: Order): String {
        return toString(context, order, true /* forceLongNames */)
    }

    @get:VisibleForTesting
    val count: Int
        get() {
            var count = 0
            for (calendarDay in Calendar.SUNDAY..Calendar.SATURDAY) {
                if (isBitOn(calendarDay)) {
                    count++
                }
            }
            return count
        }

    /**
     * @param context for accessing resources
     * @param order the order in which to present the weekdays
     * @param forceLongNames if `true` the un-abbreviated weekdays are used
     * @return the enabled weekdays in the given `order`
     */
    private fun toString(context: Context, order: Order, forceLongNames: Boolean): String {
        if (!isRepeating) {
            return ""
        }

        if (bits == ALL_DAYS) {
            return context.getString(R.string.every_day)
        }

        val longNames = forceLongNames || count <= 1
        val dfs = DateFormatSymbols()
        val weekdays = if (longNames) dfs.weekdays else dfs.shortWeekdays

        val separator: String = context.getString(R.string.day_concat)

        val builder = StringBuilder(40)
        for (calendarDay in order.calendarDays) {
            if (isBitOn(calendarDay)) {
                if (builder.isNotEmpty()) {
                    builder.append(separator)
                }
                builder.append(weekdays[calendarDay])
            }
        }
        return builder.toString()
    }
}