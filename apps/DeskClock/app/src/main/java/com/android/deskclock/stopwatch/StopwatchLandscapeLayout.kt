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
import android.util.AttributeSet
import android.view.View
import android.view.View.MeasureSpec.AT_MOST
import android.view.View.MeasureSpec.EXACTLY
import android.view.View.MeasureSpec.UNSPECIFIED
import android.view.ViewGroup

import com.android.deskclock.R

import kotlin.math.max

/**
 * Dynamically apportions size the stopwatch circle depending on the preferred width of the laps
 * list and the container size. Layouts fall into two different buckets:
 *
 * When the width of the laps list is less than half the container width, the laps list and
 * stopwatch display are each centered within half the container.
 * <pre>
 * ---------------------------------------------------------------------------
 * |                                    |               Lap 5                |
 * |                                    |               Lap 4                |
 * |             21:45.67               |               Lap 3                |
 * |                                    |               Lap 2                |
 * |                                    |               Lap 1                |
 * ---------------------------------------------------------------------------
</pre> *
 *
 * When the width of the laps list is greater than half the container width, the laps list is
 * granted all of the space it requires and the stopwatch display is centered within the remaining
 * container width.
 * <pre>
 * ---------------------------------------------------------------------------
 * |               |                          Lap 5                          |
 * |               |                          Lap 4                          |
 * |   21:45.67    |                          Lap 3                          |
 * |               |                          Lap 2                          |
 * |               |                          Lap 1                          |
 * ---------------------------------------------------------------------------
</pre> *
 */
class StopwatchLandscapeLayout : ViewGroup {
    private var mLapsListView: View? = null
    private lateinit var mStopwatchView: View

    constructor(context: Context?) : super(context) {
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int)
            : super(context, attrs, defStyleAttr) {
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        mLapsListView = findViewById(R.id.laps_list)
        mStopwatchView = findViewById(R.id.stopwatch_time_wrapper)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height: Int = MeasureSpec.getSize(heightMeasureSpec)
        val width: Int = MeasureSpec.getSize(widthMeasureSpec)
        val halfWidth = width / 2

        val minWidthSpec: Int = MeasureSpec.makeMeasureSpec(width, UNSPECIFIED)
        val maxHeightSpec: Int = MeasureSpec.makeMeasureSpec(height, AT_MOST)

        // First determine the width of the laps list.
        val lapsListWidth: Int
        val lapsListView = mLapsListView
        if (lapsListView != null && lapsListView.getVisibility() != GONE) {
            // Measure the intrinsic size of the laps list.
            lapsListView.measure(minWidthSpec, maxHeightSpec)

            // Actual laps list width is the larger of half the container and its intrinsic width.
            lapsListWidth = max(lapsListView.getMeasuredWidth(), halfWidth)
            val lapsListWidthSpec: Int = MeasureSpec.makeMeasureSpec(lapsListWidth, EXACTLY)
            lapsListView.measure(lapsListWidthSpec, maxHeightSpec)
        } else {
            lapsListWidth = 0
        }

        // Stopwatch timer consumes the remaining width of container not granted to laps list.
        val stopwatchWidth = width - lapsListWidth
        val stopwatchWidthSpec: Int = MeasureSpec.makeMeasureSpec(stopwatchWidth, EXACTLY)
        mStopwatchView.measure(stopwatchWidthSpec, maxHeightSpec)

        // Record the measured size of this container.
        setMeasuredDimension(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        // Compute the space available for layout.
        val left: Int = getPaddingLeft()
        val top: Int = getPaddingTop()
        val right: Int = getWidth() - getPaddingRight()
        val bottom: Int = getHeight() - getPaddingBottom()
        val width = right - left
        val height = bottom - top
        val halfHeight = height / 2
        val isLTR = getLayoutDirection() == LAYOUT_DIRECTION_LTR

        val lapsListWidth: Int
        val lapsListView = mLapsListView
        if (lapsListView != null && lapsListView.getVisibility() != GONE) {
            // Layout the laps list, centering it vertically.
            lapsListWidth = lapsListView.getMeasuredWidth()
            val lapsListHeight: Int = lapsListView.getMeasuredHeight()
            val lapsListTop = top + halfHeight - lapsListHeight / 2
            val lapsListBottom = lapsListTop + lapsListHeight
            val lapsListLeft: Int
            val lapsListRight: Int
            if (isLTR) {
                lapsListLeft = right - lapsListWidth
                lapsListRight = right
            } else {
                lapsListLeft = left
                lapsListRight = left + lapsListWidth
            }
            lapsListView.layout(lapsListLeft, lapsListTop, lapsListRight, lapsListBottom)
        } else {
            lapsListWidth = 0
        }

        // Layout the stopwatch, centering it horizontally and vertically.
        val stopwatchWidth: Int = mStopwatchView.getMeasuredWidth()
        val stopwatchHeight: Int = mStopwatchView.getMeasuredHeight()
        val stopwatchTop = top + halfHeight - stopwatchHeight / 2
        val stopwatchBottom = stopwatchTop + stopwatchHeight
        val stopwatchLeft: Int
        val stopwatchRight: Int
        if (isLTR) {
            stopwatchLeft = left + (width - lapsListWidth - stopwatchWidth) / 2
            stopwatchRight = stopwatchLeft + stopwatchWidth
        } else {
            stopwatchRight = right - (width - lapsListWidth - stopwatchWidth) / 2
            stopwatchLeft = stopwatchRight - stopwatchWidth
        }

        mStopwatchView.layout(stopwatchLeft, stopwatchTop, stopwatchRight, stopwatchBottom)
    }
}