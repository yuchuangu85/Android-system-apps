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

package com.android.deskclock.alarms.dataadapter

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.content.Context.VIBRATOR_SERVICE
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.View.TRANSLATION_Y
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView.ViewHolder

import com.android.deskclock.AnimatorUtils
import com.android.deskclock.ItemAdapter.ItemViewHolder
import com.android.deskclock.R
import com.android.deskclock.ThemeUtils
import com.android.deskclock.Utils
import com.android.deskclock.alarms.AlarmTimeClickHandler
import com.android.deskclock.data.DataModel
import com.android.deskclock.events.Events
import com.android.deskclock.provider.Alarm
import com.android.deskclock.uidata.UiDataModel

/**
 * A ViewHolder containing views for an alarm item in expanded state.
 */
class ExpandedAlarmViewHolder private constructor(itemView: View, private val mHasVibrator: Boolean)
    : AlarmItemViewHolder(itemView) {
    val repeat: CheckBox = itemView.findViewById(R.id.repeat_onoff) as CheckBox
    private val editLabel: TextView = itemView.findViewById(R.id.edit_label) as TextView
    val repeatDays: LinearLayout = itemView.findViewById(R.id.repeat_days) as LinearLayout
    private val dayButtons: Array<CompoundButton?> = arrayOfNulls<CompoundButton>(7)
    val vibrate: CheckBox = itemView.findViewById(R.id.vibrate_onoff) as CheckBox
    val ringtone: TextView = itemView.findViewById(R.id.choose_ringtone) as TextView
    val delete: TextView = itemView.findViewById(R.id.delete) as TextView
    private val hairLine: View = itemView.findViewById(R.id.hairline)

    init {
        val context: Context = itemView.getContext()
        itemView.setBackground(LayerDrawable(arrayOf(
                ContextCompat.getDrawable(context, R.drawable.alarm_background_expanded),
                ThemeUtils.resolveDrawable(context, R.attr.selectableItemBackground)
        )))

        // Build button for each day.
        val inflater: LayoutInflater = LayoutInflater.from(context)
        val weekdays = DataModel.dataModel.weekdayOrder.calendarDays
        for (i in 0..6) {
            val dayButtonFrame: View = inflater.inflate(R.layout.day_button, repeatDays,
                    false /* attachToRoot */)
            val dayButton: CompoundButton =
                    dayButtonFrame.findViewById(R.id.day_button_box) as CompoundButton
            val weekday = weekdays[i]
            dayButton.text = UiDataModel.uiDataModel.getShortWeekday(weekday)
            dayButton.setContentDescription(UiDataModel.uiDataModel.getLongWeekday(weekday))
            repeatDays.addView(dayButtonFrame)
            dayButtons[i] = dayButton
        }

        // Cannot set in xml since we need compat functionality for API < 21
        val labelIcon: Drawable? = Utils.getVectorDrawable(context, R.drawable.ic_label)
        editLabel.setCompoundDrawablesRelativeWithIntrinsicBounds(labelIcon, null, null, null)
        val deleteIcon: Drawable? = Utils.getVectorDrawable(context, R.drawable.ic_delete_small)
        delete.setCompoundDrawablesRelativeWithIntrinsicBounds(deleteIcon, null, null, null)

        // Collapse handler
        itemView.setOnClickListener { _ ->
            Events.sendAlarmEvent(R.string.action_collapse_implied, R.string.label_deskclock)
            itemHolder?.collapse()
        }
        arrow.setOnClickListener { _ ->
            Events.sendAlarmEvent(R.string.action_collapse, R.string.label_deskclock)
            itemHolder?.collapse()
        }
        // Edit time handler
        clock.setOnClickListener { _ ->
            alarmTimeClickHandler.onClockClicked(itemHolder!!.item)
        }
        // Edit label handler
        editLabel.setOnClickListener { _ ->
            alarmTimeClickHandler.onEditLabelClicked(itemHolder!!.item)
        }
        // Vibrator checkbox handler
        vibrate.setOnClickListener { view ->
            alarmTimeClickHandler.setAlarmVibrationEnabled(itemHolder!!.item,
                    (view as CheckBox).isChecked)
        }
        // Ringtone editor handler
        ringtone.setOnClickListener { _ ->
            alarmTimeClickHandler.onRingtoneClicked(context, itemHolder!!.item)
        }
        // Delete alarm handler
        delete.setOnClickListener { view ->
            alarmTimeClickHandler.onDeleteClicked(itemHolder!!)
            view.announceForAccessibility(context.getString(R.string.alarm_deleted))
        }
        // Repeat checkbox handler
        repeat.setOnClickListener { view ->
            val checked: Boolean = (view as CheckBox).isChecked
            alarmTimeClickHandler.setAlarmRepeatEnabled(itemHolder!!.item, checked)
            itemHolder?.notifyItemChanged(ANIMATE_REPEAT_DAYS)
        }
        // Day buttons handler
        for (i in dayButtons.indices) {
            dayButtons[i]?.setOnClickListener { view ->
                val isChecked: Boolean = (view as CompoundButton).isChecked
                alarmTimeClickHandler.setDayOfWeekEnabled(itemHolder!!.item, isChecked, i)
            }
        }
        itemView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
    }

    override fun onBindItemView(itemHolder: AlarmItemHolder) {
        super.onBindItemView(itemHolder)

        val alarm = itemHolder.item
        val alarmInstance = itemHolder.alarmInstance
        val context: Context = itemView.getContext()
        bindEditLabel(context, alarm)
        bindDaysOfWeekButtons(alarm, context)
        bindVibrator(alarm)
        bindRingtone(context, alarm)
        bindPreemptiveDismissButton(context, alarm, alarmInstance)
    }

    private fun bindRingtone(context: Context, alarm: Alarm) {
        val title = DataModel.dataModel.getRingtoneTitle(alarm.alert!!)
        ringtone.text = title

        val description: String = context.getString(R.string.ringtone_description)
        ringtone.setContentDescription("$description $title")

        val silent: Boolean = Utils.RINGTONE_SILENT == alarm.alert
        val icon: Drawable? = Utils.getVectorDrawable(context,
                if (silent) R.drawable.ic_ringtone_silent else R.drawable.ic_ringtone)
        ringtone.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
    }

    private fun bindDaysOfWeekButtons(alarm: Alarm, context: Context) {
        val weekdays = DataModel.dataModel.weekdayOrder.calendarDays
        for (i in weekdays.indices) {
            val dayButton: CompoundButton? = dayButtons[i]
            dayButton?.let {
                if (alarm.daysOfWeek.isBitOn(weekdays[i])) {
                    dayButton.isChecked = true
                    dayButton.setTextColor(ThemeUtils.resolveColor(context,
                            android.R.attr.windowBackground))
                } else {
                    dayButton.isChecked = false
                    dayButton.setTextColor(Color.WHITE)
                }
            }
        }
        if (alarm.daysOfWeek.isRepeating) {
            repeat.isChecked = true
            repeatDays.visibility = View.VISIBLE
        } else {
            repeat.isChecked = false
            repeatDays.visibility = View.GONE
        }
    }

    private fun bindEditLabel(context: Context, alarm: Alarm) {
        editLabel.text = alarm.label
        editLabel.contentDescription = if (!alarm.label.isNullOrEmpty()) {
            context.getString(R.string.label_description).toString() + " " + alarm.label
        } else {
            context.getString(R.string.no_label_specified)
        }
    }

    private fun bindVibrator(alarm: Alarm) {
        if (!mHasVibrator) {
            vibrate.visibility = View.INVISIBLE
        } else {
            vibrate.visibility = View.VISIBLE
            vibrate.isChecked = alarm.vibrate
        }
    }

    private val alarmTimeClickHandler: AlarmTimeClickHandler
        get() = itemHolder!!.alarmTimeClickHandler

    override fun onAnimateChange(
        payloads: List<Any>?,
        fromLeft: Int,
        fromTop: Int,
        fromRight: Int,
        fromBottom: Int,
        duration: Long
    ): Animator? {
        if (payloads == null || payloads.isEmpty() || !payloads.contains(ANIMATE_REPEAT_DAYS)) {
            return null
        }

        val isExpansion = repeatDays.getVisibility() == View.VISIBLE
        val height: Int = repeatDays.getHeight()
        setTranslationY(if (isExpansion) {
            -height.toFloat()
        } else {
            0f
        }, if (isExpansion) {
            -height.toFloat()
        } else {
            height.toFloat()
        })
        repeatDays.visibility = View.VISIBLE
        repeatDays.alpha = if (isExpansion) 0f else 1f

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(AnimatorUtils.getBoundsAnimator(itemView,
                fromLeft, fromTop, fromRight, fromBottom,
                itemView.getLeft(), itemView.getTop(), itemView.getRight(), itemView.getBottom()),
                ObjectAnimator.ofFloat(repeatDays, View.ALPHA, if (isExpansion) 1f else 0f),
                ObjectAnimator.ofFloat(repeatDays, TRANSLATION_Y, if (isExpansion) {
                    0f
                } else {
                    -height.toFloat()
                }),
                ObjectAnimator.ofFloat(ringtone, TRANSLATION_Y, 0f),
                ObjectAnimator.ofFloat(vibrate, TRANSLATION_Y, 0f),
                ObjectAnimator.ofFloat(editLabel, TRANSLATION_Y, 0f),
                ObjectAnimator.ofFloat(preemptiveDismissButton, TRANSLATION_Y, 0f),
                ObjectAnimator.ofFloat(hairLine, TRANSLATION_Y, 0f),
                ObjectAnimator.ofFloat(delete, TRANSLATION_Y, 0f),
                ObjectAnimator.ofFloat(arrow, TRANSLATION_Y, 0f))
        animatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animator: Animator?) {
                setTranslationY(0f, 0f)
                repeatDays.alpha = 1f
                repeatDays.visibility = if (isExpansion) View.VISIBLE else View.GONE
                itemView.requestLayout()
            }
        })
        animatorSet.duration = duration
        animatorSet.interpolator = AnimatorUtils.INTERPOLATOR_FAST_OUT_SLOW_IN

        return animatorSet
    }

    private fun setTranslationY(repeatDaysTranslationY: Float, translationY: Float) {
        repeatDays.setTranslationY(repeatDaysTranslationY)
        ringtone.setTranslationY(translationY)
        vibrate.setTranslationY(translationY)
        editLabel.setTranslationY(translationY)
        preemptiveDismissButton.setTranslationY(translationY)
        hairLine.setTranslationY(translationY)
        delete.setTranslationY(translationY)
        arrow.setTranslationY(translationY)
    }

    override fun onAnimateChange(
        oldHolder: ViewHolder,
        newHolder: ViewHolder,
        duration: Long
    ): Animator? {
        if (oldHolder !is AlarmItemViewHolder ||
                newHolder !is AlarmItemViewHolder) {
            return null
        }

        val isExpanding = this == newHolder
        AnimatorUtils.setBackgroundAlpha(itemView, if (isExpanding) 0 else 255)
        setChangingViewsAlpha(if (isExpanding) 0f else 1f)

        val changeAnimatorSet: Animator = if (isExpanding) {
            createExpandingAnimator(oldHolder, duration)
        } else {
            createCollapsingAnimator(newHolder, duration)
        }
        changeAnimatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animator: Animator?) {
                AnimatorUtils.setBackgroundAlpha(itemView, 255)
                clock.visibility = View.VISIBLE
                onOff.visibility = View.VISIBLE
                arrow.visibility = View.VISIBLE
                arrow.setTranslationY(0f)
                setChangingViewsAlpha(1f)
                arrow.jumpDrawablesToCurrentState()
            }
        })
        return changeAnimatorSet
    }

    private fun createCollapsingAnimator(newHolder: AlarmItemViewHolder, duration: Long): Animator {
        arrow.visibility = View.INVISIBLE
        clock.visibility = View.INVISIBLE
        onOff.visibility = View.INVISIBLE

        val daysVisible = repeatDays.getVisibility() == View.VISIBLE
        val numberOfItems = countNumberOfItems()

        val oldView: View = itemView
        val newView: View = newHolder.itemView

        val backgroundAnimator: Animator = ObjectAnimator.ofPropertyValuesHolder(oldView,
                PropertyValuesHolder.ofInt(AnimatorUtils.BACKGROUND_ALPHA, 255, 0))
        backgroundAnimator.duration = duration

        val boundsAnimator: Animator = AnimatorUtils.getBoundsAnimator(oldView, oldView, newView)
        boundsAnimator.duration = duration
        boundsAnimator.interpolator = AnimatorUtils.INTERPOLATOR_FAST_OUT_SLOW_IN

        val shortDuration = (duration * ANIM_SHORT_DURATION_MULTIPLIER).toLong()
        val repeatAnimation: Animator = ObjectAnimator.ofFloat(repeat, View.ALPHA, 0f)
                .setDuration(shortDuration)
        val editLabelAnimation: Animator = ObjectAnimator.ofFloat(editLabel, View.ALPHA, 0f)
                .setDuration(shortDuration)
        val repeatDaysAnimation: Animator = ObjectAnimator.ofFloat(repeatDays, View.ALPHA, 0f)
                .setDuration(shortDuration)
        val vibrateAnimation: Animator = ObjectAnimator.ofFloat(vibrate, View.ALPHA, 0f)
                .setDuration(shortDuration)
        val ringtoneAnimation: Animator = ObjectAnimator.ofFloat(ringtone, View.ALPHA, 0f)
                .setDuration(shortDuration)
        val dismissAnimation: Animator = ObjectAnimator.ofFloat(preemptiveDismissButton,
                View.ALPHA, 0f).setDuration(shortDuration)
        val deleteAnimation: Animator = ObjectAnimator.ofFloat(delete, View.ALPHA, 0f)
                .setDuration(shortDuration)
        val hairLineAnimation: Animator = ObjectAnimator.ofFloat(hairLine, View.ALPHA, 0f)
                .setDuration(shortDuration)

        // Set the staggered delays; use the first portion (duration * (1 - 1/4 - 1/6)) of the time,
        // so that the final animation, with a duration of 1/4 the total duration, finishes exactly
        // before the collapsed holder begins expanding.
        var startDelay = 0L
        val delayIncrement = (duration * ANIM_LONG_DELAY_INCREMENT_MULTIPLIER).toLong() /
                (numberOfItems - 1)
        deleteAnimation.setStartDelay(startDelay)
        if (preemptiveDismissButton.getVisibility() == View.VISIBLE) {
            startDelay += delayIncrement
            dismissAnimation.setStartDelay(startDelay)
        }
        hairLineAnimation.setStartDelay(startDelay)
        startDelay += delayIncrement
        editLabelAnimation.setStartDelay(startDelay)
        startDelay += delayIncrement
        vibrateAnimation.setStartDelay(startDelay)
        ringtoneAnimation.setStartDelay(startDelay)
        startDelay += delayIncrement
        if (daysVisible) {
            repeatDaysAnimation.setStartDelay(startDelay)
            startDelay += delayIncrement
        }
        repeatAnimation.setStartDelay(startDelay)

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(backgroundAnimator, boundsAnimator, repeatAnimation,
                repeatDaysAnimation, vibrateAnimation, ringtoneAnimation, editLabelAnimation,
                deleteAnimation, hairLineAnimation, dismissAnimation)
        return animatorSet
    }

    private fun createExpandingAnimator(oldHolder: AlarmItemViewHolder, duration: Long): Animator {
        val oldView: View = oldHolder.itemView
        val newView: View = itemView
        val boundsAnimator: Animator = AnimatorUtils.getBoundsAnimator(newView, oldView, newView)
        boundsAnimator.duration = duration
        boundsAnimator.interpolator = AnimatorUtils.INTERPOLATOR_FAST_OUT_SLOW_IN

        val backgroundAnimator: Animator = ObjectAnimator.ofPropertyValuesHolder(newView,
                PropertyValuesHolder.ofInt(AnimatorUtils.BACKGROUND_ALPHA, 0, 255))
        backgroundAnimator.duration = duration

        val oldArrow: View = oldHolder.arrow
        val oldArrowRect = Rect(0, 0, oldArrow.getWidth(), oldArrow.getHeight())
        val newArrowRect = Rect(0, 0, arrow.getWidth(), arrow.getHeight())
        (newView as ViewGroup).offsetDescendantRectToMyCoords(arrow, newArrowRect)
        (oldView as ViewGroup).offsetDescendantRectToMyCoords(oldArrow, oldArrowRect)
        val arrowTranslationY: Float = (oldArrowRect.bottom - newArrowRect.bottom).toFloat()

        arrow.setTranslationY(arrowTranslationY)
        arrow.visibility = View.VISIBLE
        clock.visibility = View.VISIBLE
        onOff.visibility = View.VISIBLE

        val longDuration = (duration * ANIM_LONG_DURATION_MULTIPLIER).toLong()
        val repeatAnimation: Animator = ObjectAnimator.ofFloat(repeat, View.ALPHA, 1f)
                .setDuration(longDuration)
        val repeatDaysAnimation: Animator = ObjectAnimator.ofFloat(repeatDays, View.ALPHA, 1f)
                .setDuration(longDuration)
        val ringtoneAnimation: Animator = ObjectAnimator.ofFloat(ringtone, View.ALPHA, 1f)
                .setDuration(longDuration)
        val dismissAnimation: Animator = ObjectAnimator.ofFloat(preemptiveDismissButton,
                View.ALPHA, 1f).setDuration(longDuration)
        val vibrateAnimation: Animator = ObjectAnimator.ofFloat(vibrate, View.ALPHA, 1f)
                .setDuration(longDuration)
        val editLabelAnimation: Animator = ObjectAnimator.ofFloat(editLabel, View.ALPHA, 1f)
                .setDuration(longDuration)
        val hairLineAnimation: Animator = ObjectAnimator.ofFloat(hairLine, View.ALPHA, 1f)
                .setDuration(longDuration)
        val deleteAnimation: Animator = ObjectAnimator.ofFloat(delete, View.ALPHA, 1f)
                .setDuration(longDuration)
        val arrowAnimation: Animator = ObjectAnimator.ofFloat(arrow, View.TRANSLATION_Y, 0f)
                .setDuration(duration)
        arrowAnimation.interpolator = AnimatorUtils.INTERPOLATOR_FAST_OUT_SLOW_IN

        // Set the stagger delays; delay the first by the amount of time it takes for the collapse
        // to complete, then stagger the expansion with the remaining time.
        var startDelay = (duration * ANIM_STANDARD_DELAY_MULTIPLIER).toLong()
        val numberOfItems = countNumberOfItems()
        val delayIncrement = (duration * ANIM_SHORT_DELAY_INCREMENT_MULTIPLIER).toLong() /
                (numberOfItems - 1)
        repeatAnimation.setStartDelay(startDelay)
        startDelay += delayIncrement
        val daysVisible = repeatDays.getVisibility() == View.VISIBLE
        if (daysVisible) {
            repeatDaysAnimation.setStartDelay(startDelay)
            startDelay += delayIncrement
        }
        ringtoneAnimation.setStartDelay(startDelay)
        vibrateAnimation.setStartDelay(startDelay)
        startDelay += delayIncrement
        editLabelAnimation.setStartDelay(startDelay)
        startDelay += delayIncrement
        hairLineAnimation.setStartDelay(startDelay)
        if (preemptiveDismissButton.getVisibility() == View.VISIBLE) {
            dismissAnimation.setStartDelay(startDelay)
            startDelay += delayIncrement
        }
        deleteAnimation.setStartDelay(startDelay)

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(backgroundAnimator, repeatAnimation, boundsAnimator,
                repeatDaysAnimation, vibrateAnimation, ringtoneAnimation, editLabelAnimation,
                deleteAnimation, hairLineAnimation, dismissAnimation, arrowAnimation)
        animatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animator: Animator?) {
                AnimatorUtils.startDrawableAnimation(arrow)
            }
        })
        return animatorSet
    }

    private fun countNumberOfItems(): Int {
        // Always between 4 and 6 items.
        var numberOfItems = 4
        if (preemptiveDismissButton.getVisibility() == View.VISIBLE) {
            numberOfItems++
        }
        if (repeatDays.getVisibility() == View.VISIBLE) {
            numberOfItems++
        }
        return numberOfItems
    }

    private fun setChangingViewsAlpha(alpha: Float) {
        repeat.alpha = alpha
        editLabel.alpha = alpha
        repeatDays.alpha = alpha
        vibrate.alpha = alpha
        ringtone.alpha = alpha
        hairLine.alpha = alpha
        delete.alpha = alpha
        preemptiveDismissButton.alpha = alpha
    }

    class Factory(context: Context) : ItemViewHolder.Factory {
        private val mLayoutInflater: LayoutInflater = LayoutInflater.from(context)
        private val mHasVibrator: Boolean =
                (context.getSystemService(VIBRATOR_SERVICE) as Vibrator).hasVibrator()

        override fun createViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder<*> {
            val itemView: View = mLayoutInflater.inflate(viewType, parent, false)
            return ExpandedAlarmViewHolder(itemView, mHasVibrator)
        }
    }

    companion object {
        @JvmField
        val VIEW_TYPE: Int = R.layout.alarm_time_expanded
    }
}