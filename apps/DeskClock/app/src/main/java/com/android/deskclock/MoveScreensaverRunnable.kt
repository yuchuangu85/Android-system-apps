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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator

import com.android.deskclock.uidata.UiDataModel

import kotlin.math.min

/**
 * This runnable chooses a random initial position for [.mSaverView] within
 * [.mContentView] if [.mSaverView] is transparent. It also schedules itself to run
 * each minute, at which time [.mSaverView] is faded out, set to a new random location, and
 * faded in.
 */
class MoveScreensaverRunnable(
    /** The container that houses [.mSaverView].  */
    private val mContentView: View,
    /** The display within the [.mContentView] that is randomly positioned.  */
    private val mSaverView: View
) : Runnable {
    /** Accelerate the hide animation.  */
    private val mAcceleration: Interpolator = AccelerateInterpolator()

    /** Decelerate the show animation.  */
    private val mDeceleration: Interpolator = DecelerateInterpolator()

    /** Tracks the currently executing animation if any; used to gracefully stop the animation.  */
    private var mActiveAnimator: Animator? = null

    /** Start or restart the random movement of the saver view within the content view. */
    fun start() {
        // Stop any existing animations or callbacks.
        stop()

        // Reset the alpha to 0 so saver view will be randomly positioned within the new bounds.
        mSaverView.alpha = 0f

        // Execute the position updater runnable to choose the first random position of saver view.
        run()

        // Schedule callbacks every minute to adjust the position of mSaverView.
        UiDataModel.uiDataModel.addMinuteCallback(this, -FADE_TIME)
    }

    /** Stop the random movement of the saver view within the content view. */
    fun stop() {
        UiDataModel.uiDataModel.removePeriodicCallback(this)

        // End any animation currently running.
        if (mActiveAnimator != null) {
            mActiveAnimator?.end()
            mActiveAnimator = null
        }
    }

    override fun run() {
        Utils.enforceMainLooper()

        val selectInitialPosition = mSaverView.alpha == 0f
        if (selectInitialPosition) {
            // When selecting an initial position for the saver view the width and height of
            // mContentView are untrustworthy if this was caused by a configuration change. To
            // combat this, we position the mSaverView randomly within the smallest box that is
            // guaranteed to work.
            val smallestDim = min(mContentView.width, mContentView.height)
            val newX = getRandomPoint(smallestDim - mSaverView.width.toFloat())
            val newY = getRandomPoint(smallestDim - mSaverView.height.toFloat())

            mSaverView.x = newX
            mSaverView.y = newY
            mActiveAnimator = AnimatorUtils.getAlphaAnimator(mSaverView, 0f, 1f)
            mActiveAnimator?.duration = FADE_TIME
            mActiveAnimator?.interpolator = mDeceleration
            mActiveAnimator?.start()
        } else {
            // Select a new random position anywhere in mContentView that will fit mSaverView.
            val newX = getRandomPoint(mContentView.width - mSaverView.width.toFloat())
            val newY = getRandomPoint(mContentView.height - mSaverView.height.toFloat())

            // Fade out and shrink the saver view.
            val hide = AnimatorSet()
            hide.duration = FADE_TIME
            hide.interpolator = mAcceleration
            hide.play(AnimatorUtils.getAlphaAnimator(mSaverView, 1f, 0f))
                    .with(AnimatorUtils.getScaleAnimator(mSaverView, 1f, 0.85f))

            // Fade in and grow the saver view after altering its position.
            val show = AnimatorSet()
            show.duration = FADE_TIME
            show.interpolator = mDeceleration
            show.play(AnimatorUtils.getAlphaAnimator(mSaverView, 0f, 1f))
                    .with(AnimatorUtils.getScaleAnimator(mSaverView, 0.85f, 1f))
            show.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    mSaverView.x = newX
                    mSaverView.y = newY
                }
            })

            // Execute hide followed by show.
            val all = AnimatorSet()
            all.play(show).after(hide)
            mActiveAnimator = all
            mActiveAnimator?.start()
        }
    }

    companion object {
        /** The duration over which the fade in/out animations occur.  */
        private const val FADE_TIME = 3000L

        /**
         * @return a random integer between 0 and the `maximum` exclusive.
         */
        private fun getRandomPoint(maximum: Float): Float {
            return (Math.random() * maximum).toFloat()
        }
    }
}