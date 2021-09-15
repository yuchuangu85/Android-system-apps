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

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * When this layout is in the Horizontal orientation and one and only one child is a TextView with a
 * non-null android:ellipsize, this layout will reduce android:maxWidth of that TextView to ensure
 * the siblings are not truncated. This class is useful when that ellipsize-text-view "starts"
 * before other children of this view group. This layout has no effect if:
 * <ul>
 *     <li>the orientation is not horizontal</li>
 *     <li>any child has weights.</li>
 *     <li>more than one child has a non-null android:ellipsize.</li>
 * </ul>
 *
 * The purpose of this horizontal-linear-layout is to ensure that when the sum of widths of the
 * children are greater than this parent, the maximum width of the ellipsize-text-view, is reduced
 * so that no siblings are truncated.
 *
 *
 * For example: Given Text1 has android:ellipsize="end" and Text2 has android:ellipsize="none",
 * as Text1 and/or Text2 grow in width, both will consume more width until Text2 hits the end
 * margin, then Text1 will cease to grow and instead shrink to accommodate any further growth in
 * Text2.
 * <ul>
 * <li>|[text1]|[text2]              |</li>
 * <li>|[text1 text1]|[text2 text2]  |</li>
 * <li>|[text... ]|[text2 text2 text2]|</li>
 * </ul>
 */
class EllipsizeLayout @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    /**
     * This override only acts when the LinearLayout is in the Horizontal orientation and is in it's
     * final measurement pass(MeasureSpec.EXACTLY). In this case only, this class
     *
     *  * Identifies the one TextView child with the non-null android:ellipsize.
     *  * Re-measures the needed width of all children (by calling measureChildWithMargins with
     * the width measure specification to MeasureSpec.UNSPECIFIED.)
     *  * Sums the children's widths.
     *  * Whenever the sum of the children's widths is greater than this parent was allocated,
     * the maximum width of the one TextView child with the non-null android:ellipsize is
     * reduced.
     *
     *
     * @param widthMeasureSpec horizontal space requirements as imposed by the parent
     * @param heightMeasureSpec vertical space requirements as imposed by the parent
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (orientation == HORIZONTAL &&
                MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
            var totalLength = 0
            // If any of the constraints of this class are exceeded, outOfSpec becomes true
            // and the no alterations are made to the ellipsize-text-view.
            var outOfSpec = false
            var ellipsizeView: TextView? = null
            val count = childCount
            val parentWidth = MeasureSpec.getSize(widthMeasureSpec)
            val queryWidthMeasureSpec =
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec),
                            MeasureSpec.UNSPECIFIED)

            var ii = 0
            while (ii < count && !outOfSpec) {
                val child = getChildAt(ii)
                if (child != null && child.visibility != View.GONE) {
                    // Identify the ellipsize view
                    if (child is TextView) {
                        val tv = child
                        if (tv.ellipsize != null) {
                            if (ellipsizeView == null) {
                                ellipsizeView = tv
                                // Clear the maximum width on ellipsizeView before measurement
                                ellipsizeView.maxWidth = Int.MAX_VALUE
                            } else {
                                // TODO: support multiple android:ellipsize
                                outOfSpec = true
                            }
                        }
                    }
                    // Ask the child to measure itself
                    measureChildWithMargins(child, queryWidthMeasureSpec, 0, heightMeasureSpec, 0)

                    // Get the layout parameters to check for a weighted width and to add the
                    // child's margins to the total length.
                    val layoutParams = child.layoutParams as LayoutParams?
                    if (layoutParams != null) {
                        outOfSpec = outOfSpec or (layoutParams.weight > 0f)
                        totalLength += (child.measuredWidth +
                                layoutParams.leftMargin + layoutParams.rightMargin)
                    } else {
                        outOfSpec = true
                    }
                }
                ++ii
            }
            // Last constraint test
            outOfSpec = outOfSpec or (ellipsizeView == null || totalLength == 0)

            if (!outOfSpec && totalLength > parentWidth) {
                var maxWidth = ellipsizeView!!.measuredWidth - (totalLength - parentWidth)
                // TODO: Respect android:minWidth (easy with @TargetApi(16))
                val minWidth = 0
                if (maxWidth < minWidth) {
                    maxWidth = minWidth
                }
                ellipsizeView.maxWidth = maxWidth
            }
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}