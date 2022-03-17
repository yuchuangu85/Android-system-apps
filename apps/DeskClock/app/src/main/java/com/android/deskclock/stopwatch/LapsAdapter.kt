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

import android.content.Context
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.recyclerview.widget.RecyclerView

import com.android.deskclock.R
import com.android.deskclock.data.DataModel
import com.android.deskclock.data.Lap
import com.android.deskclock.data.Stopwatch
import com.android.deskclock.stopwatch.LapsAdapter.LapItemHolder
import com.android.deskclock.uidata.UiDataModel

import java.text.DecimalFormatSymbols

import kotlin.math.max

/**
 * Displays a list of lap times in reverse order. That is, the newest lap is at the top, the oldest
 * lap is at the bottom.
 */
internal class LapsAdapter(context: Context) : RecyclerView.Adapter<LapItemHolder?>() {
    private val mInflater: LayoutInflater
    private val mContext: Context

    /** Used to determine when the time format for the lap time column has changed length.  */
    private var mLastFormattedLapTimeLength = 0

    /** Used to determine when the time format for the total time column has changed length.  */
    private var mLastFormattedAccumulatedTimeLength = 0

    init {
        mContext = context
        mInflater = LayoutInflater.from(context)
        setHasStableIds(true)
    }

    /**
     * After recording the first lap, there is always a "current lap" in progress.
     *
     * @return 0 if no laps are yet recorded; lap count + 1 if any laps exist
     */
    override fun getItemCount(): Int {
        val lapCount = laps.size
        val currentLapCount = if (lapCount == 0) 0 else 1
        return currentLapCount + lapCount
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LapItemHolder {
        val v: View = mInflater.inflate(R.layout.lap_view, parent, false /* attachToRoot */)
        return LapItemHolder(v)
    }

    override fun onBindViewHolder(viewHolder: LapItemHolder, position: Int) {
        val lapTime: Long
        val lapNumber: Int
        val totalTime: Long

        // Lap will be null for the current lap.
        val lap = if (position == 0) null else laps[position - 1]
        if (lap != null) {
            // For a recorded lap, merely extract the values to format.
            lapTime = lap.lapTime
            lapNumber = lap.lapNumber
            totalTime = lap.accumulatedTime
        } else {
            // For the current lap, compute times relative to the stopwatch.
            totalTime = stopwatch.totalTime
            lapTime = DataModel.dataModel.getCurrentLapTime(totalTime)
            lapNumber = laps.size + 1
        }

        // Bind data into the child views.
        viewHolder.lapTime.setText(formatLapTime(lapTime, true))
        viewHolder.accumulatedTime.setText(formatAccumulatedTime(totalTime, true))
        viewHolder.lapNumber.setText(formatLapNumber(laps.size + 1, lapNumber))
    }

    override fun getItemId(position: Int): Long {
        val laps = laps
        return if (position == 0) {
            (laps.size + 1).toLong()
        } else {
            laps[position - 1].lapNumber.toLong()
        }
    }

    /**
     * @param rv the RecyclerView that contains the `childView`
     * @param totalTime time accumulated for the current lap and all prior laps
     */
    fun updateCurrentLap(rv: RecyclerView, totalTime: Long) {
        // If no laps exist there is nothing to do.
        if (itemCount == 0) {
            return
        }

        val currentLapView: View? = rv.getChildAt(0)
        if (currentLapView != null) {
            // Compute the lap time using the total time.
            val lapTime = DataModel.dataModel.getCurrentLapTime(totalTime)
            val holder = rv.getChildViewHolder(currentLapView) as LapItemHolder
            holder.lapTime.setText(formatLapTime(lapTime, false))
            holder.accumulatedTime.setText(formatAccumulatedTime(totalTime, false))
        }
    }

    /**
     * Record a new lap and update this adapter to include it.
     *
     * @return a newly cleared lap
     */
    fun addLap(): Lap? {
        val lap = DataModel.dataModel.addLap()

        if (itemCount == 10) {
            // 10 total laps indicates all items switch from 1 to 2 digit lap numbers.
            notifyDataSetChanged()
        } else {
            // New current lap now exists.
            notifyItemInserted(0)

            // Prior current lap must be refreshed once with the true values in place.
            notifyItemChanged(1)
        }

        return lap
    }

    /**
     * Remove all recorded laps and update this adapter.
     */
    fun clearLaps() {
        // Clear the computed time lengths related to the old recorded laps.
        mLastFormattedLapTimeLength = 0
        mLastFormattedAccumulatedTimeLength = 0

        notifyDataSetChanged()
    }

    /**
     * @return a formatted textual description of lap times and total time
     */
    val shareText: String
        get() {
            val stopwatch = stopwatch
            val totalTime = stopwatch.totalTime
            val stopwatchTime = formatTime(totalTime, totalTime, ":")

            // Choose a size for the builder that is unlikely to be resized.
            val builder = StringBuilder(1000)

            // Add the total elapsed time of the stopwatch.
            builder.append(mContext.getString(R.string.sw_share_main, stopwatchTime))
            builder.append("\n")

            val laps = laps
            if (laps.isNotEmpty()) {
                // Add a header for lap times.
                builder.append(mContext.getString(R.string.sw_share_laps))
                builder.append("\n")

                // Loop through the laps in the order they were recorded; reverse of display order.
                val separator = DecimalFormatSymbols.getInstance().decimalSeparator.toString() + " "
                for (i in laps.indices.reversed()) {
                    val lap = laps[i]
                    builder.append(lap.lapNumber)
                    builder.append(separator)
                    val lapTime = lap.lapTime
                    builder.append(formatTime(lapTime, lapTime, " "))
                    builder.append("\n")
                }

                // Append the final lap
                builder.append(laps.size + 1)
                builder.append(separator)
                val lapTime = DataModel.dataModel.getCurrentLapTime(totalTime)
                builder.append(formatTime(lapTime, lapTime, " "))
                builder.append("\n")
            }
            return builder.toString()
        }

    /**
     * @param lapCount the total number of recorded laps
     * @param lapNumber the number of the lap being formatted
     * @return e.g. "# 7" if `lapCount` less than 10; "# 07" if `lapCount` is 10 or more
     */
    @VisibleForTesting
    fun formatLapNumber(lapCount: Int, lapNumber: Int): String {
        return if (lapCount < 10) {
            mContext.getString(R.string.lap_number_single_digit, lapNumber)
        } else {
            mContext.getString(R.string.lap_number_double_digit, lapNumber)
        }
    }

    /**
     * @param lapTime the lap time to be formatted
     * @param isBinding if the lap time is requested so it can be bound avoid notifying of data
     * set changes; they are not allowed to occur during bind
     * @return a formatted version of the lap time
     */
    private fun formatLapTime(lapTime: Long, isBinding: Boolean): String {
        // The longest lap dictates the way the given lapTime must be formatted.
        val longestLapTime = max(DataModel.dataModel.longestLapTime, lapTime)
        val formattedTime = formatTime(longestLapTime, lapTime, LRM_SPACE)

        // If the newly formatted lap time has altered the format, refresh all laps.
        val newLength = formattedTime.length
        if (!isBinding && mLastFormattedLapTimeLength != newLength) {
            mLastFormattedLapTimeLength = newLength
            notifyDataSetChanged()
        }

        return formattedTime
    }

    /**
     * @param accumulatedTime the accumulated time to be formatted
     * @param isBinding if the lap time is requested so it can be bound avoid notifying of data
     * set changes; they are not allowed to occur during bind
     * @return a formatted version of the accumulated time
     */
    private fun formatAccumulatedTime(accumulatedTime: Long, isBinding: Boolean): String {
        val totalTime = stopwatch.totalTime
        val longestAccumulatedTime = max(totalTime, accumulatedTime)
        val formattedTime = formatTime(longestAccumulatedTime, accumulatedTime, LRM_SPACE)

        // If the newly formatted accumulated time has altered the format, refresh all laps.
        val newLength = formattedTime.length
        if (!isBinding && mLastFormattedAccumulatedTimeLength != newLength) {
            mLastFormattedAccumulatedTimeLength = newLength
            notifyDataSetChanged()
        }

        return formattedTime
    }

    private val stopwatch: Stopwatch
        get() = DataModel.dataModel.stopwatch

    private val laps: List<Lap>
        get() = DataModel.dataModel.laps

    /**
     * Cache the child views of each lap item view.
     */
    internal class LapItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val lapNumber: TextView
        val lapTime: TextView
        val accumulatedTime: TextView

        init {
            lapTime = itemView.findViewById(R.id.lap_time) as TextView
            lapNumber = itemView.findViewById(R.id.lap_number) as TextView
            accumulatedTime = itemView.findViewById(R.id.lap_total) as TextView
        }
    }

    companion object {
        private val TEN_MINUTES: Long = 10 * DateUtils.MINUTE_IN_MILLIS
        private val HOUR: Long = DateUtils.HOUR_IN_MILLIS
        private val TEN_HOURS = 10 * HOUR
        private val HUNDRED_HOURS = 100 * HOUR

        /** A single space preceded by a zero-width LRM; This groups adjacent chars left-to-right.  */
        private const val LRM_SPACE = "\u200E "

        /** Reusable StringBuilder that assembles a formatted time; alleviates memory churn.  */
        private val sTimeBuilder = StringBuilder(12)

        /**
         * @param maxTime the maximum amount of time; used to choose a time format
         * @param time the time to format guaranteed not to exceed `maxTime`
         * @param separator displayed between hours and minutes as well as minutes and seconds
         * @return a formatted version of the time
         */
        @VisibleForTesting
        fun formatTime(maxTime: Long, time: Long, separator: String?): String {
            val hours: Int
            val minutes: Int
            val seconds: Int
            val hundredths: Int
            if (time <= 0) {
                // A negative time should be impossible, but is tolerated to avoid crashing the app.
                hundredths = 0
                seconds = hundredths
                minutes = seconds
                hours = minutes
            } else {
                hours = (time / DateUtils.HOUR_IN_MILLIS).toInt()
                var remainder = (time % DateUtils.HOUR_IN_MILLIS).toInt()
                minutes = (remainder / DateUtils.MINUTE_IN_MILLIS).toInt()
                remainder = (remainder % DateUtils.MINUTE_IN_MILLIS).toInt()
                seconds = (remainder / DateUtils.SECOND_IN_MILLIS).toInt()
                remainder = (remainder % DateUtils.SECOND_IN_MILLIS).toInt()
                hundredths = remainder / 10
            }

            val decimalSeparator = DecimalFormatSymbols.getInstance().decimalSeparator

            sTimeBuilder.setLength(0)

            // The display of hours and minutes varies based on maxTime.
            when {
                maxTime < TEN_MINUTES -> {
                    sTimeBuilder.append(UiDataModel.uiDataModel.getFormattedNumber(minutes, 1))
                }
                maxTime < HOUR -> {
                    sTimeBuilder.append(UiDataModel.uiDataModel.getFormattedNumber(minutes, 2))
                }
                maxTime < TEN_HOURS -> {
                    sTimeBuilder.append(UiDataModel.uiDataModel.getFormattedNumber(hours, 1))
                    sTimeBuilder.append(separator)
                    sTimeBuilder.append(UiDataModel.uiDataModel.getFormattedNumber(minutes, 2))
                }
                maxTime < HUNDRED_HOURS -> {
                    sTimeBuilder.append(UiDataModel.uiDataModel.getFormattedNumber(hours, 2))
                    sTimeBuilder.append(separator)
                    sTimeBuilder.append(UiDataModel.uiDataModel.getFormattedNumber(minutes, 2))
                }
                else -> {
                    sTimeBuilder.append(UiDataModel.uiDataModel.getFormattedNumber(hours, 3))
                    sTimeBuilder.append(separator)
                    sTimeBuilder.append(UiDataModel.uiDataModel.getFormattedNumber(minutes, 2))
                }
            }

            // The display of seconds and hundredths-of-a-second is constant.
            sTimeBuilder.append(separator)
            sTimeBuilder.append(UiDataModel.uiDataModel.getFormattedNumber(seconds, 2))
            sTimeBuilder.append(decimalSeparator)
            sTimeBuilder.append(UiDataModel.uiDataModel.getFormattedNumber(hundredths, 2))

            return sTimeBuilder.toString()
        }
    }
}