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

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

import kotlin.math.min

/**
 * A container that frames a timer circle of some sort. The circle is allowed to grow naturally
 * according to its layout constraints up to the [largest][R.dimen.max_timer_circle_size]
 * allowable size.
 */
class TimerCircleFrameLayout : FrameLayout {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr)

    /**
     * Note: this method assumes the parent container will specify [exact][MeasureSpec.EXACTLY]
     * width and height values.
     *
     * @param widthMeasureSpec horizontal space requirements as imposed by the parent
     * @param heightMeasureSpec vertical space requirements as imposed by the parent
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var variableWidthMeasureSpec = widthMeasureSpec
        var variableHeightMeasureSpec = heightMeasureSpec
        val paddingLeft = paddingLeft
        val paddingRight = paddingRight

        val paddingTop = paddingTop
        val paddingBottom = paddingBottom

        // Fetch the exact sizes imposed by the parent container.
        val width = MeasureSpec.getSize(variableWidthMeasureSpec) - paddingLeft - paddingRight
        val height = MeasureSpec.getSize(variableHeightMeasureSpec) - paddingTop - paddingBottom
        val smallestDimension = min(width, height)

        // Fetch the absolute maximum circle size allowed.
        val maxSize = resources.getDimensionPixelSize(R.dimen.max_timer_circle_size)
        val size = min(smallestDimension, maxSize)

        // Set the size of this container.
        variableWidthMeasureSpec = MeasureSpec.makeMeasureSpec(size + paddingLeft + paddingRight,
                MeasureSpec.EXACTLY)
        variableHeightMeasureSpec = MeasureSpec.makeMeasureSpec(size + paddingTop + paddingBottom,
                MeasureSpec.EXACTLY)

        super.onMeasure(variableWidthMeasureSpec, variableHeightMeasureSpec)
    }
}