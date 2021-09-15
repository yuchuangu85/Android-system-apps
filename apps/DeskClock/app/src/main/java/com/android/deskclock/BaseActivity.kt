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

import android.R
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity

/**
 * Base activity class that changes the app window's color based on the current hour.
 */
abstract class BaseActivity : AppCompatActivity() {
    /** Sets the app window color on each frame of the [.mAppColorAnimator].  */
    private val mAppColorAnimationListener = AppColorAnimationListener()

    /** The current animator that is changing the app window color or `null`.  */
    private var mAppColorAnimator: ValueAnimator? = null

    /** Draws the app window's color.  */
    private var mBackground: ColorDrawable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Allow the content to layout behind the status and navigation bars.
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)

        @ColorInt val color = ThemeUtils.resolveColor(this, R.attr.windowBackground)
        adjustAppColor(color, animate = false)
    }

    override fun onStart() {
        super.onStart()

        // Ensure the app window color is up-to-date.
        @ColorInt val color = ThemeUtils.resolveColor(this, R.attr.windowBackground)
        adjustAppColor(color, animate = false)
    }

    /**
     * Adjusts the current app window color of this activity; animates the change if desired.
     *
     * @param color the ARGB value to set as the current app window color
     * @param animate `true` if the change should be animated
     */
    protected fun adjustAppColor(@ColorInt color: Int, animate: Boolean) {
        // Create and install the drawable that defines the window color.
        if (mBackground == null) {
            mBackground = ColorDrawable(color)
            getWindow().setBackgroundDrawable(mBackground)
        }

        // Cancel the current window color animation if one exists.
        mAppColorAnimator?.cancel()

        @ColorInt val currentColor = mBackground!!.color
        if (currentColor != color) {
            if (animate) {
                mAppColorAnimator = ValueAnimator.ofObject(AnimatorUtils.ARGB_EVALUATOR,
                        currentColor, color)
                        .setDuration(3000L)
                mAppColorAnimator!!.addUpdateListener(mAppColorAnimationListener)
                mAppColorAnimator!!.addListener(mAppColorAnimationListener)
                mAppColorAnimator!!.start()
            } else {
                setAppColor(color)
            }
        }
    }

    private fun setAppColor(@ColorInt color: Int) {
        mBackground!!.color = color
    }

    /**
     * Sets the app window color to the current color produced by the animator.
     */
    private inner class AppColorAnimationListener
        : AnimatorListenerAdapter(), AnimatorUpdateListener {
        override fun onAnimationUpdate(valueAnimator: ValueAnimator) {
            @ColorInt val color = valueAnimator.animatedValue as Int
            setAppColor(color)
        }

        override fun onAnimationEnd(animation: Animator) {
            if (mAppColorAnimator === animation) {
                mAppColorAnimator = null
            }
        }
    }
}