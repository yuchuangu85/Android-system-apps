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

package com.android.deskclock.alarms

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Vibrator
import androidx.fragment.app.Fragment

import com.android.deskclock.AlarmClockFragment
import com.android.deskclock.LabelDialogFragment
import com.android.deskclock.LogUtils
import com.android.deskclock.R
import com.android.deskclock.alarms.dataadapter.AlarmItemHolder
import com.android.deskclock.data.DataModel
import com.android.deskclock.data.Weekdays
import com.android.deskclock.events.Events
import com.android.deskclock.provider.Alarm
import com.android.deskclock.provider.AlarmInstance
import com.android.deskclock.provider.ClockContract.InstancesColumns
import com.android.deskclock.ringtone.RingtonePickerActivity

import java.util.Calendar

/**
 * Click handler for an alarm time item.
 */
class AlarmTimeClickHandler(
    private val mFragment: Fragment,
    savedState: Bundle?,
    private val mAlarmUpdateHandler: AlarmUpdateHandler,
    private val mScrollHandler: ScrollHandler
) {

    private val mContext: Context = mFragment.requireActivity().getApplicationContext()
    private var mSelectedAlarm: Alarm? = null
    private var mPreviousDaysOfWeekMap: Bundle? = null

    init {
        if (savedState != null) {
            mPreviousDaysOfWeekMap = savedState.getBundle(KEY_PREVIOUS_DAY_MAP)
        }
        if (mPreviousDaysOfWeekMap == null) {
            mPreviousDaysOfWeekMap = Bundle()
        }
    }

    fun setSelectedAlarm(selectedAlarm: Alarm?) {
        mSelectedAlarm = selectedAlarm
    }

    fun saveInstance(outState: Bundle) {
        outState.putBundle(KEY_PREVIOUS_DAY_MAP, mPreviousDaysOfWeekMap)
    }

    fun setAlarmEnabled(alarm: Alarm, newState: Boolean) {
        if (newState != alarm.enabled) {
            alarm.enabled = newState
            Events.sendAlarmEvent(if (newState) R.string.action_enable else R.string.action_disable,
                    R.string.label_deskclock)
            mAlarmUpdateHandler.asyncUpdateAlarm(alarm, alarm.enabled, minorUpdate = false)
            LOGGER.d("Updating alarm enabled state to $newState")
        }
    }

    fun setAlarmVibrationEnabled(alarm: Alarm, newState: Boolean) {
        if (newState != alarm.vibrate) {
            alarm.vibrate = newState
            Events.sendAlarmEvent(R.string.action_toggle_vibrate, R.string.label_deskclock)
            mAlarmUpdateHandler.asyncUpdateAlarm(alarm, popToast = false, minorUpdate = true)
            LOGGER.d("Updating vibrate state to $newState")

            if (newState) {
                // Buzz the vibrator to preview the alarm firing behavior.
                val v: Vibrator = mContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (v.hasVibrator()) {
                    v.vibrate(300)
                }
            }
        }
    }

    fun setAlarmRepeatEnabled(alarm: Alarm, isEnabled: Boolean) {
        val now = Calendar.getInstance()
        val oldNextAlarmTime = alarm.getNextAlarmTime(now)
        val alarmId = alarm.id.toString()
        if (isEnabled) {
            // Set all previously set days
            // or
            // Set all days if no previous.
            val bitSet: Int = mPreviousDaysOfWeekMap!!.getInt(alarmId)
            alarm.daysOfWeek = Weekdays.fromBits(bitSet)
            if (!alarm.daysOfWeek.isRepeating) {
                alarm.daysOfWeek = Weekdays.ALL
            }
        } else {
            // Remember the set days in case the user wants it back.
            val bitSet = alarm.daysOfWeek.bits
            mPreviousDaysOfWeekMap!!.putInt(alarmId, bitSet)

            // Remove all repeat days
            alarm.daysOfWeek = Weekdays.NONE
        }

        // if the change altered the next scheduled alarm time, tell the user
        val newNextAlarmTime = alarm.getNextAlarmTime(now)
        val popupToast = oldNextAlarmTime != newNextAlarmTime
        Events.sendAlarmEvent(R.string.action_toggle_repeat_days, R.string.label_deskclock)
        mAlarmUpdateHandler.asyncUpdateAlarm(alarm, popupToast, minorUpdate = false)
    }

    fun setDayOfWeekEnabled(alarm: Alarm, checked: Boolean, index: Int) {
        val now = Calendar.getInstance()
        val oldNextAlarmTime = alarm.getNextAlarmTime(now)

        val weekday = DataModel.dataModel.weekdayOrder.calendarDays[index]
        alarm.daysOfWeek = alarm.daysOfWeek.setBit(weekday, checked)

        // if the change altered the next scheduled alarm time, tell the user
        val newNextAlarmTime = alarm.getNextAlarmTime(now)
        val popupToast = oldNextAlarmTime != newNextAlarmTime
        mAlarmUpdateHandler.asyncUpdateAlarm(alarm, popupToast, minorUpdate = false)
    }

    fun onDeleteClicked(itemHolder: AlarmItemHolder) {
        if (mFragment is AlarmClockFragment) {
            (mFragment as AlarmClockFragment).removeItem(itemHolder)
        }
        val alarm = itemHolder.item
        Events.sendAlarmEvent(R.string.action_delete, R.string.label_deskclock)
        mAlarmUpdateHandler.asyncDeleteAlarm(alarm)
        LOGGER.d("Deleting alarm.")
    }

    fun onClockClicked(alarm: Alarm) {
        mSelectedAlarm = alarm
        Events.sendAlarmEvent(R.string.action_set_time, R.string.label_deskclock)
        TimePickerDialogFragment.show(mFragment, alarm.hour, alarm.minutes)
    }

    fun dismissAlarmInstance(alarmInstance: AlarmInstance) {
        val dismissIntent: Intent = AlarmStateManager.createStateChangeIntent(
                mContext, AlarmStateManager.ALARM_DISMISS_TAG, alarmInstance,
                InstancesColumns.PREDISMISSED_STATE)
        mContext.startService(dismissIntent)
        mAlarmUpdateHandler.showPredismissToast(alarmInstance)
    }

    fun onRingtoneClicked(context: Context, alarm: Alarm) {
        mSelectedAlarm = alarm
        Events.sendAlarmEvent(R.string.action_set_ringtone, R.string.label_deskclock)

        val intent: Intent = RingtonePickerActivity.createAlarmRingtonePickerIntent(context, alarm)
        context.startActivity(intent)
    }

    fun onEditLabelClicked(alarm: Alarm) {
        Events.sendAlarmEvent(R.string.action_set_label, R.string.label_deskclock)
        val fragment = LabelDialogFragment.newInstance(alarm, alarm.label, mFragment.getTag())
        LabelDialogFragment.show(mFragment.getFragmentManager(), fragment)
    }

    fun onTimeSet(hourOfDay: Int, minute: Int) {
        if (mSelectedAlarm == null) {
            // If mSelectedAlarm is null then we're creating a new alarm.
            val a = Alarm()
            a.hour = hourOfDay
            a.minutes = minute
            a.enabled = true
            mAlarmUpdateHandler.asyncAddAlarm(a)
        } else {
            mSelectedAlarm!!.hour = hourOfDay
            mSelectedAlarm!!.minutes = minute
            mSelectedAlarm!!.enabled = true
            mScrollHandler.setSmoothScrollStableId(mSelectedAlarm!!.id)
            mAlarmUpdateHandler
                    .asyncUpdateAlarm(mSelectedAlarm!!, popToast = true, minorUpdate = false)
            mSelectedAlarm = null
        }
    }

    companion object {
        private val LOGGER = LogUtils.Logger("AlarmTimeClickHandler")

        private const val KEY_PREVIOUS_DAY_MAP = "previousDayMap"
    }
}