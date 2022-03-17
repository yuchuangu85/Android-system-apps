/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.deskclock

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView

import kotlin.math.min
import kotlin.math.sqrt

/**
 * This class adjusts the locations of child buttons and text of this view group by adjusting the
 * margins of each item. The left and right buttons are aligned with the bottom of the circle. The
 * stop button and label text are located within the circle with the stop button near the bottom and
 * the label text near the top. The maximum text size for the label text view is also calculated.
 */
class CircleButtonsLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private val mDiamOffset: Float
    private var mCircleView: View? = null
    private var mResetAddButton: Button? = null
    private var mLabel: TextView? = null

    init {
        val res = getContext().resources
        val strokeSize = res.getDimension(R.dimen.circletimer_circle_size)
        val dotStrokeSize = res.getDimension(R.dimen.circletimer_dot_size)
        val markerStrokeSize = res.getDimension(R.dimen.circletimer_marker_size)
        mDiamOffset = Utils.calculateRadiusOffset(strokeSize, dotStrokeSize, markerStrokeSize) * 2
    }

    public override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // We must call onMeasure both before and after re-measuring our views because the circle
        // may not always be drawn here yet. The first onMeasure will force the circle to be drawn,
        // and the second will force our re-measurements to take effect.
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        remeasureViews()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun remeasureViews() {
        if (mLabel == null) {
            mCircleView = findViewById(R.id.timer_time)
            mLabel = findViewById<View>(R.id.timer_label) as TextView
            mResetAddButton = findViewById<View>(R.id.reset_add) as Button
        }

        val frameWidth = mCircleView!!.measuredWidth
        val frameHeight = mCircleView!!.measuredHeight
        val minBound = min(frameWidth, frameHeight)
        val circleDiam = (minBound - mDiamOffset).toInt()

        mResetAddButton?.let {
            val resetAddParams = it.layoutParams as MarginLayoutParams
            resetAddParams.bottomMargin = circleDiam / 6
            if (minBound == frameWidth) {
                resetAddParams.bottomMargin += (frameHeight - frameWidth) / 2
            }
        }

        mLabel?.let {
            val labelParams = it.layoutParams as MarginLayoutParams
            labelParams.topMargin = circleDiam / 6
            if (minBound == frameWidth) {
                labelParams.topMargin += (frameHeight - frameWidth) / 2
            }
            /* The following formula has been simplified based on the following:
             * Our goal is to calculate the maximum width for the label frame.
             * We may do this with the following diagram to represent the top half of the circle:
             *                 ___
             *            .     |     .
             *        ._________|         .
             *     .       ^    |            .
             *   /         x    |              \
             *  |_______________|_______________|
             *
             *  where x represents the value we would like to calculate, and the final width of the
             *  label will be w = 2 * x.
             *
             *  We may find x by drawing a right triangle from the center of the circle:
             *                 ___
             *            .     |     .
             *        ._________|         .
             *     .    .       |            .
             *   /          .   | }y           \
             *  |_____________.t|_______________|
             *
             *  where t represents the angle of that triangle, and y is the height of that triangle.
             *
             *  If r = radius of the circle, we know the following trigonometric identities:
             *        cos(t) = y / r
             *  and   sin(t) = x / r
             *     => r * sin(t) = x
             *  and   sin^2(t) = 1 - cos^2(t)
             *     => sin(t) = +/- sqrt(1 - cos^2(t))
             *  (note: because we need the positive value, we may drop the +/-).
             *
             *  To calculate the final width, we may combine our formulas:
             *        w = 2 * x
             *     => w = 2 * r * sin(t)
             *     => w = 2 * r * sqrt(1 - cos^2(t))
             *     => w = 2 * r * sqrt(1 - (y / r)^2)
             *
             *  Simplifying even further, to mitigate the complexity of the final formula:
             *        sqrt(1 - (y / r)^2)
             *     => sqrt(1 - (y^2 / r^2))
             *     => sqrt((r^2 / r^2) - (y^2 / r^2))
             *     => sqrt((r^2 - y^2) / (r^2))
             *     => sqrt(r^2 - y^2) / sqrt(r^2)
             *     => sqrt(r^2 - y^2) / r
             *     => sqrt((r + y)*(r - y)) / r
             *
             * Placing this back in our formula, we end up with, as our final, reduced equation:
             *        w = 2 * r * sqrt(1 - (y / r)^2)
             *     => w = 2 * r * sqrt((r + y)*(r - y)) / r
             *     => w = 2 * sqrt((r + y)*(r - y))
             */
            // Radius of the circle.
            val r = circleDiam / 2
            // Y value of the top of the label, calculated from the center of the circle.
            val y = frameHeight / 2 - labelParams.topMargin
            // New maximum width of the label.
            val w = 2 * sqrt((r + y) * (r - y).toDouble())

            it.maxWidth = w.toInt()
        }
    }
}