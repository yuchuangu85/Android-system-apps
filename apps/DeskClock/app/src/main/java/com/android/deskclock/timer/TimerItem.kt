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

import android.R.attr
import android.content.Context
import android.content.res.ColorStateList
import android.os.SystemClock
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat

import com.android.deskclock.R
import com.android.deskclock.ThemeUtils
import com.android.deskclock.TimerTextController
import com.android.deskclock.Utils.ClickAccessibilityDelegate
import com.android.deskclock.data.Timer

/**
 * This view is a visual representation of a [Timer].
 */
class TimerItem @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    /** Displays the remaining time or time since expiration.  */
    private lateinit var mTimerText: TextView

    /** Formats and displays the text in the timer.  */
    private lateinit var mTimerTextController: TimerTextController

    /** Displays timer progress as a color circle that changes from white to red.  */
    private var mCircleView: TimerCircleView? = null

    /** A button that either resets the timer or adds time to it, depending on its state.  */
    private lateinit var mResetAddButton: Button

    /** Displays the label associated with the timer. Tapping it presents an edit dialog.  */
    private lateinit var mLabelView: TextView

    /** The last state of the timer that was rendered; used to avoid expensive operations.  */
    private var mLastState: Timer.State? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        mLabelView = findViewById<View>(R.id.timer_label) as TextView
        mResetAddButton = findViewById<View>(R.id.reset_add) as Button
        mCircleView = findViewById<View>(R.id.timer_time) as TimerCircleView
        mTimerText = findViewById<View>(R.id.timer_time_text) as TextView
        mTimerTextController = TimerTextController(mTimerText)

        val c = mTimerText.context
        val colorAccent = ThemeUtils.resolveColor(c, R.attr.colorAccent)
        val textColorPrimary = ThemeUtils.resolveColor(c, attr.textColorPrimary)
        mTimerText.setTextColor(ColorStateList(
                arrayOf(intArrayOf(-attr.state_activated, -attr.state_pressed),
                intArrayOf()),
                intArrayOf(textColorPrimary, colorAccent)))
    }

    /**
     * Updates this view to display the latest state of the `timer`.
     */
    fun update(timer: Timer) {
        // Update the time.
        mTimerTextController.setTimeString(timer.remainingTime)

        // Update the label if it changed.
        val label: String? = timer.label
        if (!TextUtils.equals(label, mLabelView.text)) {
            mLabelView.text = label
        }

        // Update visibility of things that may blink.
        val blinkOff = SystemClock.elapsedRealtime() % 1000 < 500
        if (mCircleView != null) {
            val hideCircle = (timer.isExpired || timer.isMissed) && blinkOff
            mCircleView!!.visibility = if (hideCircle) View.INVISIBLE else View.VISIBLE

            if (!hideCircle) {
                // Update the progress of the circle.
                mCircleView!!.update(timer)
            }
        }
        if (!timer.isPaused || !blinkOff || mTimerText.isPressed) {
            mTimerText.alpha = 1f
        } else {
            mTimerText.alpha = 0f
        }

        // Update some potentially expensive areas of the user interface only on state changes.
        if (timer.state != mLastState) {
            mLastState = timer.state
            val context = context
            when (mLastState) {
                Timer.State.RESET, Timer.State.PAUSED -> {
                    mResetAddButton.setText(R.string.timer_reset)
                    mResetAddButton.contentDescription = null
                    mTimerText.isClickable = true
                    mTimerText.isActivated = false
                    mTimerText.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                    ViewCompat.setAccessibilityDelegate(mTimerText, ClickAccessibilityDelegate(
                            context.getString(R.string.timer_start), true))
                }
                Timer.State.RUNNING -> {
                    val addTimeDesc = context.getString(R.string.timer_plus_one)
                    mResetAddButton.setText(R.string.timer_add_minute)
                    mResetAddButton.contentDescription = addTimeDesc
                    mTimerText.isClickable = true
                    mTimerText.isActivated = false
                    mTimerText.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                    ViewCompat.setAccessibilityDelegate(mTimerText, ClickAccessibilityDelegate(
                            context.getString(R.string.timer_pause)))
                }
                Timer.State.EXPIRED, Timer.State.MISSED -> {
                    val addTimeDesc = context.getString(R.string.timer_plus_one)
                    mResetAddButton.setText(R.string.timer_add_minute)
                    mResetAddButton.contentDescription = addTimeDesc
                    mTimerText.isClickable = false
                    mTimerText.isActivated = true
                    mTimerText.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
            }
        }
    }
}