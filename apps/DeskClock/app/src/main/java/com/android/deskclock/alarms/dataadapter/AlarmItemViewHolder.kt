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

import android.content.Context
import android.view.View
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.TextView

import com.android.deskclock.AlarmUtils
import com.android.deskclock.ItemAdapter.ItemViewHolder
import com.android.deskclock.ItemAnimator.OnAnimateChangeListener
import com.android.deskclock.R
import com.android.deskclock.provider.Alarm
import com.android.deskclock.provider.AlarmInstance
import com.android.deskclock.provider.ClockContract.InstancesColumns
import com.android.deskclock.widget.TextTime

/**
 * Abstract ViewHolder for alarm time items.
 */
abstract class AlarmItemViewHolder(itemView: View)
    : ItemViewHolder<AlarmItemHolder>(itemView), OnAnimateChangeListener {
    val clock: TextTime = itemView.findViewById(R.id.digital_clock)
    val onOff: CompoundButton = itemView.findViewById(R.id.onoff) as CompoundButton
    val arrow: ImageView = itemView.findViewById(R.id.arrow) as ImageView
    val preemptiveDismissButton: TextView =
            itemView.findViewById(R.id.preemptive_dismiss_button) as TextView

    init {
        preemptiveDismissButton.setOnClickListener { _ ->
            val alarmInstance = itemHolder!!.alarmInstance
            if (alarmInstance != null) {
                itemHolder!!.alarmTimeClickHandler.dismissAlarmInstance(alarmInstance)
            }
        }
        onOff.setOnCheckedChangeListener { _, checked ->
            itemHolder!!.alarmTimeClickHandler.setAlarmEnabled(itemHolder!!.item, checked)
        }
    }

    override fun onBindItemView(itemHolder: AlarmItemHolder) {
        val alarm = itemHolder.item
        bindOnOffSwitch(alarm)
        bindClock(alarm)
        val context: Context = itemView.getContext()
        itemView.setContentDescription(clock.text.toString() + " " +
                alarm.getLabelOrDefault(context))
    }

    protected fun bindOnOffSwitch(alarm: Alarm) {
        if (onOff.isChecked() != alarm.enabled) {
            onOff.isChecked = alarm.enabled
        }
    }

    protected fun bindClock(alarm: Alarm) {
        clock.setTime(alarm.hour, alarm.minutes)
        clock.alpha = if (alarm.enabled) CLOCK_ENABLED_ALPHA else CLOCK_DISABLED_ALPHA
    }

    protected fun bindPreemptiveDismissButton(
        context: Context,
        alarm: Alarm,
        alarmInstance: AlarmInstance?
    ): Boolean {
        val canBind = alarm.canPreemptivelyDismiss() && alarmInstance != null
        if (canBind) {
            preemptiveDismissButton.visibility = View.VISIBLE
            val dismissText: String = if (alarm.instanceState == InstancesColumns.SNOOZE_STATE) {
                context.getString(R.string.alarm_alert_snooze_until,
                        AlarmUtils.getAlarmText(context, alarmInstance!!, false))
            } else {
                context.getString(R.string.alarm_alert_dismiss_text)
            }
            preemptiveDismissButton.text = dismissText
            preemptiveDismissButton.isClickable = true
        } else {
            preemptiveDismissButton.visibility = View.GONE
            preemptiveDismissButton.isClickable = false
        }
        return canBind
    }

    companion object {
        private const val CLOCK_ENABLED_ALPHA = 1f
        private const val CLOCK_DISABLED_ALPHA = 0.69f

        const val ANIM_STANDARD_DELAY_MULTIPLIER = 1f / 6f
        const val ANIM_LONG_DURATION_MULTIPLIER = 2f / 3f
        const val ANIM_SHORT_DURATION_MULTIPLIER = 1f / 4f
        const val ANIM_SHORT_DELAY_INCREMENT_MULTIPLIER =
                1f - ANIM_LONG_DURATION_MULTIPLIER - ANIM_SHORT_DURATION_MULTIPLIER
        const val ANIM_LONG_DELAY_INCREMENT_MULTIPLIER =
                1f - ANIM_STANDARD_DELAY_MULTIPLIER - ANIM_SHORT_DURATION_MULTIPLIER
        const val ANIMATE_REPEAT_DAYS = "ANIMATE_REPEAT_DAYS"
    }
}