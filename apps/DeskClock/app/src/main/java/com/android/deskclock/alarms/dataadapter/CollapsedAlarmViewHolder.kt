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
import android.content.Context
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView.ViewHolder

import com.android.deskclock.AnimatorUtils
import com.android.deskclock.ItemAdapter.ItemViewHolder
import com.android.deskclock.R
import com.android.deskclock.data.DataModel
import com.android.deskclock.events.Events
import com.android.deskclock.provider.Alarm

import java.util.Calendar

/**
 * A ViewHolder containing views for an alarm item in collapsed stated.
 */
class CollapsedAlarmViewHolder private constructor(itemView: View) : AlarmItemViewHolder(itemView) {
    private val alarmLabel: TextView = itemView.findViewById(R.id.label) as TextView
    val daysOfWeek: TextView = itemView.findViewById(R.id.days_of_week) as TextView
    private val upcomingInstanceLabel: TextView =
            itemView.findViewById(R.id.upcoming_instance_label) as TextView
    private val hairLine: View = itemView.findViewById(R.id.hairline)

    init {
        // Expand handler
        itemView.setOnClickListener { _ ->
            Events.sendAlarmEvent(R.string.action_expand_implied, R.string.label_deskclock)
            itemHolder?.expand()
        }
        alarmLabel.setOnClickListener { _ ->
            Events.sendAlarmEvent(R.string.action_expand_implied, R.string.label_deskclock)
            itemHolder?.expand()
        }
        arrow.setOnClickListener { _ ->
            Events.sendAlarmEvent(R.string.action_expand, R.string.label_deskclock)
            itemHolder?.expand()
        }
        // Edit time handler
        clock.setOnClickListener { _ ->
            itemHolder!!.alarmTimeClickHandler.onClockClicked(itemHolder!!.item)
            Events.sendAlarmEvent(R.string.action_expand_implied, R.string.label_deskclock)
            itemHolder?.expand()
        }

        itemView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
    }

    override fun onBindItemView(itemHolder: AlarmItemHolder) {
        super.onBindItemView(itemHolder)
        val alarm = itemHolder.item
        val alarmInstance = itemHolder.alarmInstance
        val context: Context = itemView.getContext()
        bindRepeatText(context, alarm)
        bindReadOnlyLabel(context, alarm)
        bindUpcomingInstance(context, alarm)
        bindPreemptiveDismissButton(context, alarm, alarmInstance)
    }

    private fun bindReadOnlyLabel(context: Context, alarm: Alarm) {
        if (!alarm.label.isNullOrEmpty()) {
            alarmLabel.text = alarm.label
            alarmLabel.visibility = View.VISIBLE
            alarmLabel.setContentDescription(context.getString(R.string.label_description)
                    .toString() + " " + alarm.label)
        } else {
            alarmLabel.visibility = View.GONE
        }
    }

    private fun bindRepeatText(context: Context, alarm: Alarm) {
        if (alarm.daysOfWeek.isRepeating) {
            val weekdayOrder = DataModel.dataModel.weekdayOrder
            val daysOfWeekText = alarm.daysOfWeek.toString(context, weekdayOrder)
            daysOfWeek.text = daysOfWeekText

            val string = alarm.daysOfWeek.toAccessibilityString(context, weekdayOrder)
            daysOfWeek.setContentDescription(string)

            daysOfWeek.visibility = View.VISIBLE
        } else {
            daysOfWeek.visibility = View.GONE
        }
    }

    private fun bindUpcomingInstance(context: Context, alarm: Alarm) {
        if (alarm.daysOfWeek.isRepeating) {
            upcomingInstanceLabel.visibility = View.GONE
        } else {
            upcomingInstanceLabel.visibility = View.VISIBLE
            val labelText: String = if (Alarm.isTomorrow(alarm, Calendar.getInstance())) {
                context.getString(R.string.alarm_tomorrow)
            } else {
                context.getString(R.string.alarm_today)
            }
            upcomingInstanceLabel.text = labelText
        }
    }

    override fun onAnimateChange(
        payloads: List<Any>?,
        fromLeft: Int,
        fromTop: Int,
        fromRight: Int,
        fromBottom: Int,
        duration: Long
    ): Animator? {
        /* There are no possible partial animations for collapsed view holders. */
        return null
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

        val isCollapsing = this == newHolder
        setChangingViewsAlpha(if (isCollapsing) 0f else 1f)

        val changeAnimatorSet: Animator = if (isCollapsing) {
            createCollapsingAnimator(oldHolder, duration)
        } else {
            createExpandingAnimator(newHolder, duration)
        }
        changeAnimatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animator: Animator?) {
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

    private fun createExpandingAnimator(newHolder: AlarmItemViewHolder, duration: Long): Animator {
        clock.visibility = View.INVISIBLE
        onOff.visibility = View.INVISIBLE
        arrow.visibility = View.INVISIBLE

        val alphaAnimatorSet = AnimatorSet()
        alphaAnimatorSet.playTogether(
                ObjectAnimator.ofFloat(alarmLabel, View.ALPHA, 0f),
                ObjectAnimator.ofFloat(daysOfWeek, View.ALPHA, 0f),
                ObjectAnimator.ofFloat(upcomingInstanceLabel, View.ALPHA, 0f),
                ObjectAnimator.ofFloat(preemptiveDismissButton, View.ALPHA, 0f),
                ObjectAnimator.ofFloat(hairLine, View.ALPHA, 0f))
        alphaAnimatorSet.setDuration((duration * ANIM_SHORT_DURATION_MULTIPLIER).toLong())

        val oldView: View = itemView
        val newView: View = newHolder.itemView
        val boundsAnimator: Animator = AnimatorUtils.getBoundsAnimator(oldView, oldView, newView)
                .setDuration(duration)
        boundsAnimator.interpolator = AnimatorUtils.INTERPOLATOR_FAST_OUT_SLOW_IN

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(alphaAnimatorSet, boundsAnimator)
        return animatorSet
    }

    private fun createCollapsingAnimator(oldHolder: AlarmItemViewHolder, duration: Long): Animator {
        val alphaAnimatorSet = AnimatorSet()
        alphaAnimatorSet.playTogether(
                ObjectAnimator.ofFloat(alarmLabel, View.ALPHA, 1f),
                ObjectAnimator.ofFloat(daysOfWeek, View.ALPHA, 1f),
                ObjectAnimator.ofFloat(upcomingInstanceLabel, View.ALPHA, 1f),
                ObjectAnimator.ofFloat(preemptiveDismissButton, View.ALPHA, 1f),
                ObjectAnimator.ofFloat(hairLine, View.ALPHA, 1f))
        val standardDelay = (duration * ANIM_STANDARD_DELAY_MULTIPLIER).toLong()
        alphaAnimatorSet.setDuration(standardDelay)
        alphaAnimatorSet.setStartDelay(duration - standardDelay)

        val oldView: View = oldHolder.itemView
        val newView: View = itemView
        val boundsAnimator: Animator = AnimatorUtils.getBoundsAnimator(newView, oldView, newView)
                .setDuration(duration)
        boundsAnimator.interpolator = AnimatorUtils.INTERPOLATOR_FAST_OUT_SLOW_IN

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

        val arrowAnimation: Animator = ObjectAnimator.ofFloat(arrow, View.TRANSLATION_Y, 0f)
                .setDuration(duration)
        arrowAnimation.interpolator = AnimatorUtils.INTERPOLATOR_FAST_OUT_SLOW_IN

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(alphaAnimatorSet, boundsAnimator, arrowAnimation)
        animatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animator: Animator?) {
                AnimatorUtils.startDrawableAnimation(arrow)
            }
        })
        return animatorSet
    }

    private fun setChangingViewsAlpha(alpha: Float) {
        alarmLabel.alpha = alpha
        daysOfWeek.alpha = alpha
        upcomingInstanceLabel.alpha = alpha
        hairLine.alpha = alpha
        preemptiveDismissButton.alpha = alpha
    }

    class Factory(private val layoutInflater: LayoutInflater) : ItemViewHolder.Factory {
        override fun createViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder<*> {
            return CollapsedAlarmViewHolder(layoutInflater.inflate(
                    viewType, parent, false /* attachToRoot */))
        }
    }

    companion object {
        @JvmField
        val VIEW_TYPE: Int = R.layout.alarm_time_collapsed
    }
}