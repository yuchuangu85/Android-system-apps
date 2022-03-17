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

package com.android.deskclock.uidata

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.ArrayMap
import android.util.SparseArray

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale

/**
 * All formatted strings that are cached for performance are accessed via this model.
 */
internal class FormattedStringModel(context: Context) {
    /** Clears data structures containing data that is locale-sensitive.  */
    private val mLocaleChangedReceiver: BroadcastReceiver = LocaleChangedReceiver()

    /**
     * Caches formatted numbers in the current locale padded with zeroes to requested lengths.
     * The first level of the cache maps length to the second level of the cache.
     * The second level of the cache maps an integer to a formatted String in the current locale.
     */
    private val mNumberFormatCache = SparseArray<SparseArray<String>>(3)

    /** Single-character version of weekday names; e.g.: 'S', 'M', 'T', 'W', 'T', 'F', 'S'  */
    private var mShortWeekdayNames: MutableMap<Int, String>? = null

    /** Full weekday names; e.g.: 'Sunday', 'Monday', 'Tuesday', etc.  */
    private var mLongWeekdayNames: MutableMap<Int, String>? = null

    init {
        // Clear caches affected by locale when locale changes.
        val localeBroadcastFilter = IntentFilter(Intent.ACTION_LOCALE_CHANGED)
        context.registerReceiver(mLocaleChangedReceiver, localeBroadcastFilter)
    }

    /**
     * This method is intended to be used when formatting numbers occurs in a hotspot such as the
     * update loop of a timer or stopwatch. It returns cached results when possible in order to
     * provide speed and limit garbage to be collected by the virtual machine.
     *
     * @param value a positive integer to format as a String
     * @return the `value` formatted as a String in the current locale
     * @throws IllegalArgumentException if `value` is negative
     */
    fun getFormattedNumber(value: Int): String {
        val length = if (value == 0) 1 else Math.log10(value.toDouble()).toInt() + 1
        return getFormattedNumber(false, value, length)
    }

    /**
     * This method is intended to be used when formatting numbers occurs in a hotspot such as the
     * update loop of a timer or stopwatch. It returns cached results when possible in order to
     * provide speed and limit garbage to be collected by the virtual machine.
     *
     * @param value a positive integer to format as a String
     * @param length the length of the String; zeroes are padded to match this length
     * @return the `value` formatted as a String in the current locale and padded to the
     * requested `length`
     * @throws IllegalArgumentException if `value` is negative
     */
    fun getFormattedNumber(value: Int, length: Int): String {
        return getFormattedNumber(false, value, length)
    }

    /**
     * This method is intended to be used when formatting numbers occurs in a hotspot such as the
     * update loop of a timer or stopwatch. It returns cached results when possible in order to
     * provide speed and limit garbage to be collected by the virtual machine.
     *
     * @param negative force a minus sign (-) onto the display, even if `value` is `0`
     * @param value a positive integer to format as a String
     * @param length the length of the String; zeroes are padded to match this length. If
     * `negative` is `true` the return value will contain a minus sign and a total
     * length of `length + 1`.
     * @return the `value` formatted as a String in the current locale and padded to the
     * requested `length`
     * @throws IllegalArgumentException if `value` is negative
     */
    fun getFormattedNumber(negative: Boolean, value: Int, length: Int): String {
        require(value >= 0) { "value may not be negative: $value" }

        // Look up the value cache using the length; -ve and +ve values are cached separately.
        val lengthCacheKey = if (negative) -length else length
        var valueCache = mNumberFormatCache[lengthCacheKey]
        if (valueCache == null) {
            valueCache = SparseArray(Math.pow(10.0, length.toDouble()).toInt())
            mNumberFormatCache.put(lengthCacheKey, valueCache)
        }

        // Look up the cached formatted value using the value.
        var formatted = valueCache[value]
        if (formatted == null) {
            val sign = if (negative) "−" else ""
            formatted = String.format(Locale.getDefault(), sign + "%0" + length + "d", value)
            valueCache.put(value, formatted)
        }

        return formatted
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
     * @return single-character weekday name; e.g.: 'S', 'M', 'T', 'W', 'T', 'F', 'S'
     */
    fun getShortWeekday(calendarDay: Int): String? {
        if (mShortWeekdayNames == null) {
            mShortWeekdayNames = ArrayMap(7)

            val format = SimpleDateFormat("ccccc", Locale.getDefault())
            for (i in Calendar.SUNDAY..Calendar.SATURDAY) {
                val calendar: Calendar = GregorianCalendar(2014, Calendar.JULY, 20 + i - 1)
                val weekday = format.format(calendar.time)
                mShortWeekdayNames!![i] = weekday
            }
        }

        return mShortWeekdayNames!![calendarDay]
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
     * @return full weekday name; e.g.: 'Sunday', 'Monday', 'Tuesday', etc.
     */
    fun getLongWeekday(calendarDay: Int): String? {
        if (mLongWeekdayNames == null) {
            mLongWeekdayNames = ArrayMap(7)

            val calendar: Calendar = GregorianCalendar(2014, Calendar.JULY, 20)
            val format = SimpleDateFormat("EEEE", Locale.getDefault())
            for (i in Calendar.SUNDAY..Calendar.SATURDAY) {
                val weekday = format.format(calendar.time)
                mLongWeekdayNames!![i] = weekday
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        return mLongWeekdayNames!![calendarDay]
    }

    /**
     * Cached information that is locale-sensitive must be cleared in response to locale changes.
     */
    private inner class LocaleChangedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            mNumberFormatCache.clear()
            mShortWeekdayNames = null
            mLongWeekdayNames = null
        }
    }
}