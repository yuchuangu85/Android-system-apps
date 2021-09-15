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
package com.android.deskclock.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Property
import android.view.Gravity
import android.view.View

import com.android.deskclock.R

import kotlin.math.min

/**
 * A [View] that draws primitive circles.
 */
class CircleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    /** The [Paint] used to draw the circle. */
    private val mCirclePaint = Paint()

    /** the current [Gravity] used to align/size the circle */
    var gravity: Int
        private set

    private var mCenterX: Float
    private var mCenterY: Float

    /** the radius of the circle */
    var radius: Float
        private set

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.CircleView, defStyleAttr, 0)

        gravity = a.getInt(R.styleable.CircleView_android_gravity, Gravity.NO_GRAVITY)
        mCenterX = a.getDimension(R.styleable.CircleView_centerX, 0.0f)
        mCenterY = a.getDimension(R.styleable.CircleView_centerY, 0.0f)
        radius = a.getDimension(R.styleable.CircleView_radius, 0.0f)

        mCirclePaint.color = a.getColor(R.styleable.CircleView_fillColor, Color.WHITE)

        a.recycle()
    }

    override fun onRtlPropertiesChanged(layoutDirection: Int) {
        super.onRtlPropertiesChanged(layoutDirection)

        if (gravity != Gravity.NO_GRAVITY) {
            applyGravity(gravity, layoutDirection)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        if (gravity != Gravity.NO_GRAVITY) {
            applyGravity(gravity, layoutDirection)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // draw the circle, duh
        canvas.drawCircle(mCenterX, mCenterY, radius, mCirclePaint)
    }

    override fun hasOverlappingRendering(): Boolean {
        // only if we have a background, which we shouldn't...
        return background != null
    }

    /**
     * Describes how to align/size the circle relative to the view's bounds. Defaults to
     * [Gravity.NO_GRAVITY].
     *
     * Note: using [.setCenterX], [.setCenterY], or
     * [.setRadius] will automatically clear any conflicting gravity bits.
     *
     * @param gravity the [Gravity] flags to use
     * @return this object, allowing calls to methods in this class to be chained
     * @see R.styleable.CircleView_android_gravity
     */
    fun setGravity(gravity: Int): CircleView {
        if (this.gravity != gravity) {
            this.gravity = gravity

            if (gravity != Gravity.NO_GRAVITY && isLayoutDirectionResolved) {
                applyGravity(gravity, layoutDirection)
            }
        }
        return this
    }

    /**
     * @return the ARGB color used to fill the circle
     */
    val fillColor: Int
        get() = mCirclePaint.color

    /**
     * Sets the ARGB color used to fill the circle and invalidates only the affected area.
     *
     * @param color the ARGB color to use
     * @return this object, allowing calls to methods in this class to be chained
     * @see R.styleable.CircleView_fillColor
     */
    fun setFillColor(color: Int): CircleView {
        if (mCirclePaint.color != color) {
            mCirclePaint.color = color

            // invalidate the current area
            invalidate(mCenterX, mCenterY, radius)
        }
        return this
    }

    /**
     * Sets the x-coordinate for the center of the circle and invalidates only the affected area.
     *
     * @param centerX the x-coordinate to use, relative to the view's bounds
     * @return this object, allowing calls to methods in this class to be chained
     * @see R.styleable.CircleView_centerX
     */
    fun setCenterX(centerX: Float): CircleView {
        val oldCenterX = mCenterX
        if (oldCenterX != centerX) {
            mCenterX = centerX

            // invalidate the old/new areas
            invalidate(oldCenterX, mCenterY, radius)
            invalidate(centerX, mCenterY, radius)
        }

        // clear the horizontal gravity flags
        gravity = gravity and Gravity.HORIZONTAL_GRAVITY_MASK.inv()

        return this
    }

    /**
     * Sets the y-coordinate for the center of the circle and invalidates only the affected area.
     *
     * @param centerY the y-coordinate to use, relative to the view's bounds
     * @return this object, allowing calls to methods in this class to be chained
     * @see R.styleable.CircleView_centerY
     */
    fun setCenterY(centerY: Float): CircleView {
        val oldCenterY = mCenterY
        if (oldCenterY != centerY) {
            mCenterY = centerY

            // invalidate the old/new areas
            invalidate(mCenterX, oldCenterY, radius)
            invalidate(mCenterX, centerY, radius)
        }

        // clear the vertical gravity flags
        gravity = gravity and Gravity.VERTICAL_GRAVITY_MASK.inv()

        return this
    }

    /**
     * Sets the radius of the circle and invalidates only the affected area.
     *
     * @param radius the radius to use
     * @return this object, allowing calls to methods in this class to be chained
     * @see R.styleable.CircleView_radius
     */
    fun setRadius(radius: Float): CircleView {
        val oldRadius = this.radius
        if (oldRadius != radius) {
            this.radius = radius

            // invalidate the old/new areas
            invalidate(mCenterX, mCenterY, oldRadius)
            if (radius > oldRadius) {
                invalidate(mCenterX, mCenterY, radius)
            }
        }

        // clear the fill gravity flags
        if (gravity and Gravity.FILL_HORIZONTAL == Gravity.FILL_HORIZONTAL) {
            gravity = gravity and Gravity.FILL_HORIZONTAL.inv()
        }
        if (gravity and Gravity.FILL_VERTICAL == Gravity.FILL_VERTICAL) {
            gravity = gravity and Gravity.FILL_VERTICAL.inv()
        }

        return this
    }

    /**
     * Invalidates the rectangular area that circumscribes the circle defined by `centerX`,
     * `centerY`, and `radius`.
     */
    private fun invalidate(centerX: Float, centerY: Float, radius: Float) {
        invalidate((centerX - radius - 0.5f).toInt(), (centerY - radius - 0.5f).toInt(),
                (centerX + radius + 0.5f).toInt(), (centerY + radius + 0.5f).toInt())
    }

    /**
     * Applies the specified `gravity` and `layoutDirection`, adjusting the alignment
     * and size of the circle depending on the resolved [Gravity] flags. Also invalidates the
     * affected area if necessary.
     *
     * @param gravity the [Gravity] the [Gravity] flags to use
     * @param layoutDirection the layout direction used to resolve the absolute gravity
     */
    @SuppressLint("RtlHardcoded")
    private fun applyGravity(gravity: Int, layoutDirection: Int) {
        val absoluteGravity = Gravity.getAbsoluteGravity(gravity, layoutDirection)

        val oldRadius = radius
        val oldCenterX = mCenterX
        val oldCenterY = mCenterY

        when (absoluteGravity and Gravity.HORIZONTAL_GRAVITY_MASK) {
            Gravity.LEFT -> mCenterX = 0.0f
            Gravity.CENTER_HORIZONTAL, Gravity.FILL_HORIZONTAL -> mCenterX = width / 2.0f
            Gravity.RIGHT -> mCenterX = width.toFloat()
        }

        when (absoluteGravity and Gravity.VERTICAL_GRAVITY_MASK) {
            Gravity.TOP -> mCenterY = 0.0f
            Gravity.CENTER_VERTICAL, Gravity.FILL_VERTICAL -> mCenterY = height / 2.0f
            Gravity.BOTTOM -> mCenterY = height.toFloat()
        }

        when (absoluteGravity and Gravity.FILL) {
            Gravity.FILL -> radius = min(width, height) / 2.0f
            Gravity.FILL_HORIZONTAL -> radius = width / 2.0f
            Gravity.FILL_VERTICAL -> radius = height / 2.0f
        }

        if (oldCenterX != mCenterX || oldCenterY != mCenterY || oldRadius != radius) {
            invalidate(oldCenterX, oldCenterY, oldRadius)
            invalidate(mCenterX, mCenterY, radius)
        }
    }

    companion object {
        /**
         * A Property wrapper around the fillColor functionality handled by the
         * [.setFillColor] and [.getFillColor] methods.
         */
        @JvmField
        val FILL_COLOR: Property<CircleView, Int> =
                object : Property<CircleView, Int>(Int::class.java, "fillColor") {
                    override fun get(view: CircleView): Int {
                        return view.fillColor
                    }

                    override fun set(view: CircleView, value: Int) {
                        view.setFillColor(value)
                    }
                }

        /**
         * A Property wrapper around the radius functionality handled by the
         * [.setRadius] and [.getRadius] methods.
         */
        @JvmField
        val RADIUS: Property<CircleView, Float> =
                object : Property<CircleView, Float>(Float::class.java, "radius") {
                    override fun get(view: CircleView): Float {
                        return view.radius
                    }

                    override fun set(view: CircleView, value: Float) {
                        view.setRadius(value)
                    }
                }
    }
}