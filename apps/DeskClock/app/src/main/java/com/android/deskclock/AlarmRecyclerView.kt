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
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView

/**
 * Thin wrapper around RecyclerView to prevent simultaneous layout passes, particularly during
 * animations.
 */
class AlarmRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : RecyclerView(context, attrs, defStyle) {
    private var mIgnoreRequestLayout = false

    init {
        addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                // Disable scrolling/user action to prevent choppy animations.
                return rv.getItemAnimator()!!.isRunning()
            }
        })
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        mIgnoreRequestLayout = true
        super.onLayout(changed, left, top, right, bottom)
        mIgnoreRequestLayout = false
    }

    override fun requestLayout() {
        if (!mIgnoreRequestLayout &&
                (getItemAnimator() == null || !getItemAnimator()!!.isRunning())) {
            super.requestLayout()
        }
    }
}