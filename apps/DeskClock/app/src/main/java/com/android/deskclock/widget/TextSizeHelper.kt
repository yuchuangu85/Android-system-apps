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

package com.android.deskclock.widget

import android.text.Layout
import android.text.TextPaint
import android.util.TypedValue
import android.view.View.MeasureSpec
import android.widget.TextView

/**
 * A TextView which automatically re-sizes its text to fit within its boundaries.
 */
class TextSizeHelper(private val mTextView: TextView) {

    // Text paint used for measuring.
    private val mMeasurePaint = TextPaint()

    // The maximum size the text is allowed to be (in pixels).
    private val mMaxTextSize: Float = mTextView.textSize

    // The maximum width the text is allowed to be (in pixels).
    private var mWidthConstraint = Int.MAX_VALUE

    // The maximum height the text is allowed to be (in pixels).
    private var mHeightConstraint = Int.MAX_VALUE

    // When {@code true} calls to {@link #requestLayout()} should be ignored.
    private var mIgnoreRequestLayout = false

    fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var widthConstraint = Int.MAX_VALUE
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            widthConstraint = (MeasureSpec.getSize(widthMeasureSpec) -
                    mTextView.compoundPaddingLeft - mTextView.compoundPaddingRight)
        }

        var heightConstraint = Int.MAX_VALUE
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            heightConstraint = (MeasureSpec.getSize(heightMeasureSpec) -
                    mTextView.compoundPaddingTop - mTextView.compoundPaddingBottom)
        }

        if (mTextView.isLayoutRequested ||
                mWidthConstraint != widthConstraint ||
                mHeightConstraint != heightConstraint) {
            mWidthConstraint = widthConstraint
            mHeightConstraint = heightConstraint
            adjustTextSize()
        }
    }

    fun onTextChanged(lengthBefore: Int, lengthAfter: Int) {
        // The length of the text has changed, request layout to recalculate the current text
        // size. This is necessary to workaround an optimization in TextView#checkForRelayout()
        // which will avoid re-layout when the view has a fixed layout width.
        if (lengthBefore != lengthAfter) {
            mTextView.requestLayout()
        }
    }

    fun shouldIgnoreRequestLayout(): Boolean {
        return mIgnoreRequestLayout
    }

    private fun adjustTextSize() {
        val text = mTextView.text
        var textSize = mMaxTextSize
        if (text.isNotEmpty() &&
                (mWidthConstraint < Int.MAX_VALUE || mHeightConstraint < Int.MAX_VALUE)) {
            mMeasurePaint.set(mTextView.paint)

            var minTextSize = 1f
            var maxTextSize = mMaxTextSize
            while (maxTextSize >= minTextSize) {
                val midTextSize = Math.round((maxTextSize + minTextSize) / 2f).toFloat()
                mMeasurePaint.textSize = midTextSize

                val width = Layout.getDesiredWidth(text, mMeasurePaint)
                val height = mMeasurePaint.getFontMetricsInt(null).toFloat()
                if (width > mWidthConstraint || height > mHeightConstraint) {
                    maxTextSize = midTextSize - 1f
                } else {
                    textSize = midTextSize
                    minTextSize = midTextSize + 1f
                }
            }
        }

        if (mTextView.textSize != textSize) {
            mIgnoreRequestLayout = true
            mTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
            mIgnoreRequestLayout = false
        }
    }
}