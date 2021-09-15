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
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.text.BidiFormatter
import android.text.TextUtils
import android.text.format.DateUtils
import android.text.style.RelativeSizeSpan
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnLongClickListener
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.core.view.ViewCompat

import com.android.deskclock.FabContainer
import com.android.deskclock.FormattedTextUtils
import com.android.deskclock.R
import com.android.deskclock.ThemeUtils
import com.android.deskclock.uidata.UiDataModel

import java.io.Serializable

class TimerSetupView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs), View.OnClickListener, OnLongClickListener {
    private val mInput = intArrayOf(0, 0, 0, 0, 0, 0)

    private var mInputPointer = -1
    private val mTimeTemplate: CharSequence

    private lateinit var mTimeView: TextView
    private lateinit var mDeleteView: View
    private lateinit var mDividerView: View
    private lateinit var mDigitViews: Array<TextView>

    /** Updates to the fab are requested via this container.  */
    private lateinit var mFabContainer: FabContainer

    init {
        val bf = BidiFormatter.getInstance(false /* rtlContext */)
        val hoursLabel = bf.unicodeWrap(context.getString(R.string.hours_label))
        val minutesLabel = bf.unicodeWrap(context.getString(R.string.minutes_label))
        val secondsLabel = bf.unicodeWrap(context.getString(R.string.seconds_label))

        // Create a formatted template for "00h 00m 00s".
        mTimeTemplate = TextUtils.expandTemplate("^1^4 ^2^5 ^3^6",
                bf.unicodeWrap("^1"),
                bf.unicodeWrap("^2"),
                bf.unicodeWrap("^3"),
                FormattedTextUtils.formatText(hoursLabel, RelativeSizeSpan(0.5f)),
                FormattedTextUtils.formatText(minutesLabel, RelativeSizeSpan(0.5f)),
                FormattedTextUtils.formatText(secondsLabel, RelativeSizeSpan(0.5f)))

        LayoutInflater.from(context).inflate(R.layout.timer_setup_container, this)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        mTimeView = findViewById<View>(R.id.timer_setup_time) as TextView
        mDeleteView = findViewById(R.id.timer_setup_delete)
        mDividerView = findViewById(R.id.timer_setup_divider)
        mDigitViews = arrayOf(
                findViewById<View>(R.id.timer_setup_digit_0) as TextView,
                findViewById<View>(R.id.timer_setup_digit_1) as TextView,
                findViewById<View>(R.id.timer_setup_digit_2) as TextView,
                findViewById<View>(R.id.timer_setup_digit_3) as TextView,
                findViewById<View>(R.id.timer_setup_digit_4) as TextView,
                findViewById<View>(R.id.timer_setup_digit_5) as TextView,
                findViewById<View>(R.id.timer_setup_digit_6) as TextView,
                findViewById<View>(R.id.timer_setup_digit_7) as TextView,
                findViewById<View>(R.id.timer_setup_digit_8) as TextView,
                findViewById<View>(R.id.timer_setup_digit_9) as TextView)

        // Tint the divider to match the disabled control color by default and used the activated
        // control color when there is valid input.
        val dividerContext = mDividerView.context
        val colorControlActivated = ThemeUtils.resolveColor(dividerContext,
                R.attr.colorControlActivated)
        val colorControlDisabled = ThemeUtils.resolveColor(dividerContext,
                R.attr.colorControlNormal, intArrayOf(android.R.attr.state_enabled.inv()))
        ViewCompat.setBackgroundTintList(mDividerView,
                ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_activated), intArrayOf()),
                        intArrayOf(colorControlActivated, colorControlDisabled)))
        ViewCompat.setBackgroundTintMode(mDividerView, PorterDuff.Mode.SRC)

        // Initialize the digit buttons.
        val uidm = UiDataModel.uiDataModel
        for (digitView in mDigitViews) {
            val digit = getDigitForId(digitView.id)
            digitView.text = uidm.getFormattedNumber(digit, 1)
            digitView.setOnClickListener(this)
        }

        mDeleteView.setOnClickListener(this)
        mDeleteView.setOnLongClickListener(this)

        updateTime()
        updateDeleteAndDivider()
    }

    fun setFabContainer(fabContainer: FabContainer) {
        mFabContainer = fabContainer
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        var view: View? = null
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            view = mDeleteView
        } else if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            view = mDigitViews[keyCode - KeyEvent.KEYCODE_0]
        }

        if (view != null) {
            val result = view.performClick()
            if (result && hasValidInput()) {
                mFabContainer.updateFab(FabContainer.FAB_REQUEST_FOCUS)
            }
            return result
        }

        return false
    }

    override fun onClick(view: View) {
        if (view === mDeleteView) {
            delete()
        } else {
            append(getDigitForId(view.id))
        }
    }

    override fun onLongClick(view: View): Boolean {
        if (view === mDeleteView) {
            reset()
            updateFab()
            return true
        }
        return false
    }

    private fun getDigitForId(@IdRes id: Int): Int = when (id) {
        R.id.timer_setup_digit_0 -> 0
        R.id.timer_setup_digit_1 -> 1
        R.id.timer_setup_digit_2 -> 2
        R.id.timer_setup_digit_3 -> 3
        R.id.timer_setup_digit_4 -> 4
        R.id.timer_setup_digit_5 -> 5
        R.id.timer_setup_digit_6 -> 6
        R.id.timer_setup_digit_7 -> 7
        R.id.timer_setup_digit_8 -> 8
        R.id.timer_setup_digit_9 -> 9
        else -> throw IllegalArgumentException("Invalid id: $id")
    }

    private fun updateTime() {
        val seconds = mInput[1] * 10 + mInput[0]
        val minutes = mInput[3] * 10 + mInput[2]
        val hours = mInput[5] * 10 + mInput[4]

        val uidm = UiDataModel.uiDataModel
        mTimeView.text = TextUtils.expandTemplate(mTimeTemplate,
                uidm.getFormattedNumber(hours, 2),
                uidm.getFormattedNumber(minutes, 2),
                uidm.getFormattedNumber(seconds, 2))

        val r = resources
        mTimeView.contentDescription = r.getString(R.string.timer_setup_description,
                r.getQuantityString(R.plurals.hours, hours, hours),
                r.getQuantityString(R.plurals.minutes, minutes, minutes),
                r.getQuantityString(R.plurals.seconds, seconds, seconds))
    }

    private fun updateDeleteAndDivider() {
        val enabled = hasValidInput()
        mDeleteView.isEnabled = enabled
        mDividerView.isActivated = enabled
    }

    private fun updateFab() {
        mFabContainer.updateFab(FabContainer.FAB_SHRINK_AND_EXPAND)
    }

    private fun append(digit: Int) {
        require(!(digit < 0 || digit > 9)) { "Invalid digit: $digit" }

        // Pressing "0" as the first digit does nothing.
        if (mInputPointer == -1 && digit == 0) {
            return
        }

        // No space for more digits, so ignore input.
        if (mInputPointer == mInput.size - 1) {
            return
        }

        // Append the new digit.
        System.arraycopy(mInput, 0, mInput, 1, mInputPointer + 1)
        mInput[0] = digit
        mInputPointer++
        updateTime()

        // Update TalkBack to read the number being deleted.
        mDeleteView.contentDescription = context.getString(
                R.string.timer_descriptive_delete,
                UiDataModel.uiDataModel.getFormattedNumber(digit))

        // Update the fab, delete, and divider when we have valid input.
        if (mInputPointer == 0) {
            updateFab()
            updateDeleteAndDivider()
        }
    }

    private fun delete() {
        // Nothing exists to delete so return.
        if (mInputPointer < 0) {
            return
        }

        System.arraycopy(mInput, 1, mInput, 0, mInputPointer)
        mInput[mInputPointer] = 0
        mInputPointer--
        updateTime()

        // Update TalkBack to read the number being deleted or its original description.
        if (mInputPointer >= 0) {
            mDeleteView.contentDescription = context.getString(
                    R.string.timer_descriptive_delete,
                    UiDataModel.uiDataModel.getFormattedNumber(mInput[0]))
        } else {
            mDeleteView.contentDescription = context.getString(R.string.timer_delete)
        }

        // Update the fab, delete, and divider when we no longer have valid input.
        if (mInputPointer == -1) {
            updateFab()
            updateDeleteAndDivider()
        }
    }

    fun reset() {
        if (mInputPointer != -1) {
            mInput.fill(0)
            mInputPointer = -1
            updateTime()
            updateDeleteAndDivider()
        }
    }

    fun hasValidInput(): Boolean {
        return mInputPointer != -1
    }

    val timeInMillis: Long
        get() {
            val seconds = mInput[1] * 10 + mInput[0]
            val minutes = mInput[3] * 10 + mInput[2]
            val hours = mInput[5] * 10 + mInput[4]
            return seconds * DateUtils.SECOND_IN_MILLIS +
                    minutes * DateUtils.MINUTE_IN_MILLIS +
                    hours * DateUtils.HOUR_IN_MILLIS
        }

    var state: Serializable?
        /**
         * @return an opaque representation of the state of timer setup
         */
        get() = mInput.copyOf(mInput.size)
        /**
         * @param state an opaque state of this view previously produced by [.getState]
         */
        set(state) {
            val input = state as IntArray?
            if (input != null && mInput.size == input.size) {
                for (i in mInput.indices) {
                    mInput[i] = input[i]
                    if (mInput[i] != 0) {
                        mInputPointer = i
                    }
                }
                updateTime()
                updateDeleteAndDivider()
            }
        }
}