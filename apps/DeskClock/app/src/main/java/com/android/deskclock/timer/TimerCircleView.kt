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

package com.android.deskclock.timer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

import com.android.deskclock.R
import com.android.deskclock.ThemeUtils
import com.android.deskclock.Utils
import com.android.deskclock.data.Timer

import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Custom view that draws timer progress as a circle.
 */
class TimerCircleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    /** The size of the dot indicating the progress through the timer.  */
    private val mDotRadius: Float

    /** An amount to subtract from the true radius to account for drawing thicknesses.  */
    private val mRadiusOffset: Float

    /** The color indicating the remaining portion of the timer.  */
    private val mRemainderColor: Int

    /** The color indicating the completed portion of the timer.  */
    private val mCompletedColor: Int

    /** The size of the stroke that paints the timer circle.  */
    private val mStrokeSize: Float

    private val mPaint = Paint()
    private val mFill = Paint()
    private val mArcRect = RectF()

    private var mTimer: Timer? = null

    init {
        val resources = context.resources
        val dotDiameter = resources.getDimension(R.dimen.circletimer_dot_size)

        mDotRadius = dotDiameter / 2f
        mStrokeSize = resources.getDimension(R.dimen.circletimer_circle_size)
        mRadiusOffset = Utils.calculateRadiusOffset(mStrokeSize, dotDiameter, 0f)

        mRemainderColor = Color.WHITE
        mCompletedColor = ThemeUtils.resolveColor(context, R.attr.colorAccent)

        mPaint.isAntiAlias = true
        mPaint.style = Paint.Style.STROKE

        mFill.isAntiAlias = true
        mFill.color = mCompletedColor
        mFill.style = Paint.Style.FILL
    }

    fun update(timer: Timer) {
        if (mTimer !== timer) {
            mTimer = timer
            postInvalidateOnAnimation()
        }
    }

    public override fun onDraw(canvas: Canvas) {
        if (mTimer == null) {
            return
        }

        // Compute the size and location of the circle to be drawn.
        val xCenter = width / 2
        val yCenter = height / 2
        val radius = min(xCenter, yCenter) - mRadiusOffset

        // Reset old painting state.
        mPaint.color = mRemainderColor
        mPaint.strokeWidth = mStrokeSize

        // If the timer is reset, draw a simple white circle.
        val redPercent: Float
        when {
            mTimer!!.isReset -> {
                // Draw a complete white circle; no red arc required.
                canvas.drawCircle(xCenter.toFloat(), yCenter.toFloat(), radius, mPaint)

                // Red percent is 0 since no timer progress has been made.
                redPercent = 0f
            }
            mTimer!!.isExpired -> {
                mPaint.color = mCompletedColor

                // Draw a complete white circle; no red arc required.
                canvas.drawCircle(xCenter.toFloat(), yCenter.toFloat(), radius, mPaint)

                // Red percent is 1 since the timer has expired.
                redPercent = 1f
            }
            else -> {
                // Draw a combination of red and white arcs to create a circle.
                mArcRect.top = yCenter - radius
                mArcRect.bottom = yCenter + radius
                mArcRect.left = xCenter - radius
                mArcRect.right = xCenter + radius
                redPercent = min(1f,
                        mTimer!!.elapsedTime.toFloat() / mTimer!!.totalLength.toFloat())
                val whitePercent = 1 - redPercent

                // Draw a white arc to indicate the amount of timer that remains.
                canvas.drawArc(mArcRect, 270f, whitePercent * 360, false, mPaint)

                // Draw a red arc to indicate the amount of timer completed.
                mPaint.color = mCompletedColor
                canvas.drawArc(mArcRect, 270f, -redPercent * 360, false, mPaint)
            }
        }

        // Draw a red dot to indicate current progress through the timer.
        val dotAngleDegrees = 270 - redPercent * 360
        val dotAngleRadians = Math.toRadians(dotAngleDegrees.toDouble())
        val dotX = xCenter + (radius * cos(dotAngleRadians)).toFloat()
        val dotY = yCenter + (radius * sin(dotAngleRadians)).toFloat()
        canvas.drawCircle(dotX, dotY, mDotRadius, mFill)

        if (mTimer!!.isRunning) {
            postInvalidateOnAnimation()
        }
    }
}