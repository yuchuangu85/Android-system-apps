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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

/**
 * This widget display an analog clock with two hands for hours and minutes.
 */
class AnalogClock @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val mIntentReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (mTimeZone == null && Intent.ACTION_TIMEZONE_CHANGED == intent.action) {
                val tz = intent.getStringExtra("time-zone")
                mTime = Calendar.getInstance(TimeZone.getTimeZone(tz))
            }
            onTimeChanged()
        }
    }

    private val mClockTick: Runnable = object : Runnable {
        override fun run() {
            onTimeChanged()

            if (mEnableSeconds) {
                val now = System.currentTimeMillis()
                val delay = DateUtils.SECOND_IN_MILLIS - now % DateUtils.SECOND_IN_MILLIS
                postDelayed(this, delay)
            }
        }
    }

    private val mHourHand: ImageView
    private val mMinuteHand: ImageView
    private val mSecondHand: ImageView

    private var mTime = Calendar.getInstance()
    private val mDescFormat =
            (DateFormat.getTimeFormat(context) as SimpleDateFormat).toLocalizedPattern()
    private var mTimeZone: TimeZone? = null
    private var mEnableSeconds = true

    init {
        // Must call mutate on these instances, otherwise the drawables will blur, because they're
        // sharing their size characteristics with the (smaller) world cities analog clocks.
        val dial: ImageView = AppCompatImageView(context)
        dial.setImageResource(R.drawable.clock_analog_dial)
        dial.drawable.mutate()
        addView(dial)

        mHourHand = AppCompatImageView(context)
        mHourHand.setImageResource(R.drawable.clock_analog_hour)
        mHourHand.drawable.mutate()
        addView(mHourHand)

        mMinuteHand = AppCompatImageView(context)
        mMinuteHand.setImageResource(R.drawable.clock_analog_minute)
        mMinuteHand.drawable.mutate()
        addView(mMinuteHand)

        mSecondHand = AppCompatImageView(context)
        mSecondHand.setImageResource(R.drawable.clock_analog_second)
        mSecondHand.drawable.mutate()
        addView(mSecondHand)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_TIME_TICK)
        filter.addAction(Intent.ACTION_TIME_CHANGED)
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED)
        context.registerReceiver(mIntentReceiver, filter)

        // Refresh the calendar instance since the time zone may have changed while the receiver
        // wasn't registered.
        mTime = Calendar.getInstance(mTimeZone ?: TimeZone.getDefault())
        onTimeChanged()

        // Tick every second.
        if (mEnableSeconds) {
            mClockTick.run()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        context.unregisterReceiver(mIntentReceiver)
        removeCallbacks(mClockTick)
    }

    private fun onTimeChanged() {
        mTime.timeInMillis = System.currentTimeMillis()
        val hourAngle = mTime[Calendar.HOUR] * 30f
        mHourHand.rotation = hourAngle
        val minuteAngle = mTime[Calendar.MINUTE] * 6f
        mMinuteHand.rotation = minuteAngle
        if (mEnableSeconds) {
            val secondAngle = mTime[Calendar.SECOND] * 6f
            mSecondHand.rotation = secondAngle
        }
        contentDescription = DateFormat.format(mDescFormat, mTime)
        invalidate()
    }

    fun setTimeZone(id: String) {
        mTimeZone = TimeZone.getTimeZone(id)
        mTime.timeZone = mTimeZone!!
        onTimeChanged()
    }

    fun enableSeconds(enable: Boolean) {
        mEnableSeconds = enable
        if (mEnableSeconds) {
            mSecondHand.visibility = View.VISIBLE
            mClockTick.run()
        } else {
            mSecondHand.visibility = View.GONE
        }
    }
}