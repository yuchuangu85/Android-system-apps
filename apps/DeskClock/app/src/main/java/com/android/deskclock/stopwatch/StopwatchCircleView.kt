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
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

import com.android.deskclock.R
import com.android.deskclock.ThemeUtils
import com.android.deskclock.Utils
import com.android.deskclock.data.DataModel
import com.android.deskclock.data.Lap
import com.android.deskclock.data.Stopwatch

import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Custom view that draws a reference lap as a circle when one exists.
 */
class StopwatchCircleView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    /** The size of the dot indicating the user's position within the reference lap.  */
    private val mDotRadius: Float

    /** An amount to subtract from the true radius to account for drawing thicknesses.  */
    private val mRadiusOffset: Float

    /** Used to scale the width of the marker to make it similarly visible on all screens.  */
    private val mScreenDensity: Float

    /** The color indicating the remaining portion of the current lap.  */
    private val mRemainderColor: Int

    /** The color indicating the completed portion of the lap.  */
    private val mCompletedColor: Int

    /** The size of the stroke that paints the lap circle.  */
    private val mStrokeSize: Float

    /** The size of the stroke that paints the marker for the end of the prior lap.  */
    private val mMarkerStrokeSize: Float

    private val mPaint: Paint = Paint()
    private val mFill: Paint = Paint()
    private val mArcRect: RectF = RectF()

    constructor(context: Context) : this(context, null) {
    }

    init {
        val resources: Resources = context.getResources()
        val dotDiameter: Float = resources.getDimension(R.dimen.circletimer_dot_size)

        mDotRadius = dotDiameter / 2f
        mScreenDensity = resources.getDisplayMetrics().density
        mStrokeSize = resources.getDimension(R.dimen.circletimer_circle_size)
        mMarkerStrokeSize = resources.getDimension(R.dimen.circletimer_marker_size)
        mRadiusOffset = Utils.calculateRadiusOffset(mStrokeSize, dotDiameter, mMarkerStrokeSize)

        mRemainderColor = Color.WHITE
        mCompletedColor = ThemeUtils.resolveColor(context, R.attr.colorAccent)

        mPaint.setAntiAlias(true)
        mPaint.setStyle(Paint.Style.STROKE)

        mFill.setAntiAlias(true)
        mFill.setColor(mCompletedColor)
        mFill.setStyle(Paint.Style.FILL)
    }

    /**
     * Start the animation if it is not currently running.
     */
    fun update() {
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        // Compute the size and location of the circle to be drawn.
        val xCenter: Int = getWidth() / 2
        val yCenter: Int = getHeight() / 2
        val radius = min(xCenter, yCenter) - mRadiusOffset

        // Reset old painting state.
        mPaint.setColor(mRemainderColor)
        mPaint.setStrokeWidth(mStrokeSize)
        val laps = laps

        // If a reference lap does not exist or should not be drawn, draw a simple white circle.
        if (laps.isEmpty() || !DataModel.dataModel.canAddMoreLaps()) {
            // Draw a complete white circle; no red arc required.
            canvas.drawCircle(xCenter.toFloat(), yCenter.toFloat(), radius, mPaint)

            // No need to continue animating the plain white circle.
            return
        }

        // The first lap is the reference lap to which all future laps are compared.
        val stopwatch = stopwatch
        val lapCount = laps.size
        val firstLap = laps[lapCount - 1]
        val priorLap = laps[0]
        val firstLapTime = firstLap.lapTime
        val currentLapTime = stopwatch.totalTime - priorLap.accumulatedTime

        // Draw a combination of red and white arcs to create a circle.
        mArcRect.top = yCenter - radius
        mArcRect.bottom = yCenter + radius
        mArcRect.left = xCenter - radius
        mArcRect.right = xCenter + radius
        val redPercent = currentLapTime.toFloat() / firstLapTime.toFloat()
        val whitePercent: Float = 1f - if (redPercent > 1) 1f else redPercent

        // Draw a white arc to indicate the amount of reference lap that remains.
        canvas.drawArc(mArcRect, 270 + (1 - whitePercent) * 360, whitePercent * 360, false, mPaint)

        // Draw a red arc to indicate the amount of reference lap completed.
        mPaint.setColor(mCompletedColor)
        canvas.drawArc(mArcRect, 270f, redPercent * 360, false, mPaint)

        // Starting on lap 2, a marker can be drawn indicating where the prior lap ended.
        if (lapCount > 1) {
            mPaint.setColor(mRemainderColor)
            mPaint.setStrokeWidth(mMarkerStrokeSize)
            val markerAngle = priorLap.lapTime.toFloat() / firstLapTime.toFloat() * 360
            val startAngle = 270 + markerAngle
            val sweepAngle = mScreenDensity * (360 / (radius * Math.PI)).toFloat()
            canvas.drawArc(mArcRect, startAngle, sweepAngle, false, mPaint)
        }

        // Draw a red dot to indicate current position relative to reference lap.
        val dotAngleDegrees = 270 + redPercent * 360
        val dotAngleRadians = Math.toRadians(dotAngleDegrees.toDouble())
        val dotX = xCenter + (radius * cos(dotAngleRadians)).toFloat()
        val dotY = yCenter + (radius * sin(dotAngleRadians)).toFloat()
        canvas.drawCircle(dotX, dotY, mDotRadius, mFill)

        // If the stopwatch is not running it does not require continuous updates.
        if (stopwatch.isRunning) {
            postInvalidateOnAnimation()
        }
    }

    private val stopwatch: Stopwatch
        get() = DataModel.dataModel.stopwatch

    private val laps: List<Lap>
        get() = DataModel.dataModel.laps
}