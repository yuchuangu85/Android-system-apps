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

import android.text.format.DateUtils
import android.widget.TextView

import com.android.deskclock.uidata.UiDataModel

/**
 * A controller which will format a provided time in millis to display as a stopwatch.
 */
class StopwatchTextController(
    private val mMainTextView: TextView,
    private val mHundredthsTextView: TextView
) {
    private var mLastTime = Long.MIN_VALUE

    fun setTimeString(accumulatedTime: Long) {
        // Since time is only displayed to centiseconds, if there is a change at the milliseconds
        // level but not the centiseconds level, we can avoid unnecessary work.
        if (mLastTime / 10 == accumulatedTime / 10) {
            return
        }

        val hours = (accumulatedTime / DateUtils.HOUR_IN_MILLIS).toInt()
        var remainder = (accumulatedTime % DateUtils.HOUR_IN_MILLIS).toInt()

        val minutes = (remainder / DateUtils.MINUTE_IN_MILLIS).toInt()
        remainder = (remainder % DateUtils.MINUTE_IN_MILLIS).toInt()

        val seconds = (remainder / DateUtils.SECOND_IN_MILLIS).toInt()
        remainder = (remainder % DateUtils.SECOND_IN_MILLIS).toInt()

        mHundredthsTextView.text = UiDataModel.uiDataModel.getFormattedNumber(remainder / 10, 2)

        // Avoid unnecessary computations and garbage creation if seconds have not changed since
        // last layout pass.
        if (mLastTime / DateUtils.SECOND_IN_MILLIS !=
                accumulatedTime / DateUtils.SECOND_IN_MILLIS) {
            val context = mMainTextView.context
            val time = Utils.getTimeString(context, hours, minutes, seconds)
            mMainTextView.text = time
        }
        mLastTime = accumulatedTime
    }
}