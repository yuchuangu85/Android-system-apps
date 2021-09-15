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

package com.android.deskclock.widget

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import android.util.AttributeSet
import android.widget.TextView
import androidx.annotation.VisibleForTesting

import com.android.deskclock.Utils
import com.android.deskclock.data.DataModel

import java.util.Calendar
import java.util.TimeZone

/**
 * Based on [android.widget.TextClock], This widget displays a constant time of day using
 * format specifiers. [android.widget.TextClock] doesn't support a non-ticking clock.
 */
class TextTime @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : TextView(context, attrs, defStyle) {
    private var mFormat12: CharSequence? = Utils.get12ModeFormat(0.3f, false)
    private var mFormat24: CharSequence? = Utils.get24ModeFormat(false)
    private var mFormat: CharSequence? = null

    private var mAttached = false

    private var mHour = 0
    private var mMinute = 0

    private val mFormatChangeObserver: ContentObserver =
            object : ContentObserver(Handler(Looper.myLooper()!!)) {
        override fun onChange(selfChange: Boolean) {
            chooseFormat()
            updateTime()
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            chooseFormat()
            updateTime()
        }
    }

    var format12Hour: CharSequence?
        get() = mFormat12
        set(format) {
            mFormat12 = format
            chooseFormat()
            updateTime()
        }

    var format24Hour: CharSequence?
        get() = mFormat24
        set(format) {
            mFormat24 = format
            chooseFormat()
            updateTime()
        }

    init {
        chooseFormat()
    }

    private fun chooseFormat() {
        val format24Requested: Boolean = DataModel.dataModel.is24HourFormat()
        mFormat = if (format24Requested) {
            mFormat24 ?: DEFAULT_FORMAT_24_HOUR
        } else {
            mFormat12 ?: DEFAULT_FORMAT_12_HOUR
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!mAttached) {
            mAttached = true
            registerObserver()
            updateTime()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (mAttached) {
            unregisterObserver()
            mAttached = false
        }
    }

    private fun registerObserver() {
        val resolver = context.contentResolver
        resolver.registerContentObserver(Settings.System.CONTENT_URI, true, mFormatChangeObserver)
    }

    private fun unregisterObserver() {
        val resolver = context.contentResolver
        resolver.unregisterContentObserver(mFormatChangeObserver)
    }

    fun setTime(hour: Int, minute: Int) {
        mHour = hour
        mMinute = minute
        updateTime()
    }

    private fun updateTime() {
        // Format the time relative to UTC to ensure hour and minute are not adjusted for DST.
        val calendar: Calendar = DataModel.dataModel.calendar
        calendar.timeZone = UTC
        calendar[Calendar.HOUR_OF_DAY] = mHour
        calendar[Calendar.MINUTE] = mMinute
        val text = DateFormat.format(mFormat, calendar)
        setText(text)
        // Strip away the spans from text so talkback is not confused
        contentDescription = text.toString()
    }

    companion object {
        /** UTC does not have DST rules and will not alter the [.mHour] and [.mMinute].  */
        private val UTC = TimeZone.getTimeZone("UTC")

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        val DEFAULT_FORMAT_12_HOUR: CharSequence = "h:mm a"

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        val DEFAULT_FORMAT_24_HOUR: CharSequence = "H:mm"
    }
}