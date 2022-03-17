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

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/**
 * A TextView which automatically re-sizes its text to fit within its boundaries.
 */
class AutoSizingTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {
    private val mTextSizeHelper: TextSizeHelper? = TextSizeHelper(this)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        mTextSizeHelper!!.onMeasure(widthMeasureSpec, heightMeasureSpec)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onTextChanged(
        text: CharSequence?,
        start: Int,
        lengthBefore: Int,
        lengthAfter: Int
    ) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        if (mTextSizeHelper != null) {
            mTextSizeHelper.onTextChanged(lengthBefore, lengthAfter)
        } else {
            requestLayout()
        }
    }

    override fun requestLayout() {
        if (mTextSizeHelper == null || !mTextSizeHelper.shouldIgnoreRequestLayout()) {
            super.requestLayout()
        }
    }
}