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

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock

import com.android.deskclock.alarms.AlarmStateManager
import com.android.deskclock.controller.Controller
import com.android.deskclock.provider.Alarm
import com.android.deskclock.provider.AlarmInstance
import com.android.deskclock.provider.ClockContract.AlarmsColumns
import com.android.deskclock.provider.ClockContract.InstancesColumns

import java.text.DateFormatSymbols
import java.util.Calendar

/**
 * Returns a list of alarms that are specified by the intent
 * processed by HandleDeskClockApiCalls
 * if there are more than 1 matching alarms and the SEARCH_MODE is not ALL
 * we show a picker UI dialog
 */
internal class FetchMatchingAlarmsAction(
    private val mContext: Context,
    private val mAlarms: List<Alarm>,
    private val mIntent: Intent,
    private val mActivity: Activity
) : Runnable {
    private val mMatchingAlarms: MutableList<Alarm> = ArrayList()

    override fun run() {
        Utils.enforceNotMainLooper()

        val searchMode = mIntent.getStringExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE)
        // if search mode isn't specified show all alarms in the UI picker
        if (searchMode == null) {
            mMatchingAlarms.addAll(mAlarms)
            return
        }

        val cr = mContext.contentResolver
        when (searchMode) {
            AlarmClock.ALARM_SEARCH_MODE_TIME -> {
                // at least one of these has to be specified in this search mode.
                val hour = mIntent.getIntExtra(AlarmClock.EXTRA_HOUR, -1)
                // if minutes weren't specified default to 0
                val minutes = mIntent.getIntExtra(AlarmClock.EXTRA_MINUTES, 0)
                val isPm = mIntent.extras!![AlarmClock.EXTRA_IS_PM] as Boolean?
                var badInput = isPm != null && hour > 12 && isPm
                badInput = badInput or (hour < 0 || hour > 23)
                badInput = badInput or (minutes < 0 || minutes > 59)

                if (badInput) {
                    val ampm = DateFormatSymbols().amPmStrings
                    val amPm = if (isPm == null) "" else (if (isPm) ampm[1] else ampm[0])
                    val reason = mContext.getString(R.string.invalid_time, hour, minutes, amPm)
                    notifyFailureAndLog(reason, mActivity)
                    return
                }

                val hour24 = if (java.lang.Boolean.TRUE == isPm && hour < 12) hour + 12 else hour

                // there might me multiple alarms at the same time
                for (alarm in mAlarms) {
                    if (alarm.hour == hour24 && alarm.minutes == minutes) {
                        mMatchingAlarms.add(alarm)
                    }
                }
                if (mMatchingAlarms.isEmpty()) {
                    val reason = mContext.getString(R.string.no_alarm_at, hour24, minutes)
                    notifyFailureAndLog(reason, mActivity)
                    return
                }
            }
            AlarmClock.ALARM_SEARCH_MODE_NEXT -> {
                // Match currently firing alarms before scheduled alarms.
                for (alarm in mAlarms) {
                    val alarmInstance = AlarmInstance.getNextUpcomingInstanceByAlarmId(cr, alarm.id)
                    if (alarmInstance != null &&
                            alarmInstance.mAlarmState == InstancesColumns.FIRED_STATE) {
                        mMatchingAlarms.add(alarm)
                    }
                }
                if (mMatchingAlarms.isNotEmpty()) {
                    // return the matched firing alarms
                    return
                }
                val nextAlarm = AlarmStateManager.getNextFiringAlarm(mContext)
                if (nextAlarm == null) {
                    val reason = mContext.getString(R.string.no_scheduled_alarms)
                    notifyFailureAndLog(reason, mActivity)
                    return
                }

                // get time from nextAlarm and see if there are any other alarms matching this time
                val nextTime: Calendar = nextAlarm.alarmTime
                val alarmsFiringAtSameTime = getAlarmsByHourMinutes(
                        nextTime[Calendar.HOUR_OF_DAY], nextTime[Calendar.MINUTE], cr)
                // there might me multiple alarms firing next
                mMatchingAlarms.addAll(alarmsFiringAtSameTime)
            }
            AlarmClock.ALARM_SEARCH_MODE_ALL -> mMatchingAlarms.addAll(mAlarms)
            AlarmClock.ALARM_SEARCH_MODE_LABEL -> {
                // EXTRA_MESSAGE has to be set in this mode
                val label = mIntent.getStringExtra(AlarmClock.EXTRA_MESSAGE)
                if (label == null) {
                    val reason = mContext.getString(R.string.no_label_specified)
                    notifyFailureAndLog(reason, mActivity)
                    return
                }

                // there might me multiple alarms with this label
                for (alarm in mAlarms) {
                    if (alarm.label!!.contains(label)) {
                        mMatchingAlarms.add(alarm)
                    }
                }

                if (mMatchingAlarms.isEmpty()) {
                    val reason = mContext.getString(R.string.no_alarms_with_label)
                    notifyFailureAndLog(reason, mActivity)
                    return
                }
            }
        }
    }

    private fun getAlarmsByHourMinutes(
        hour24: Int,
        minutes: Int,
        cr: ContentResolver
    ): List<Alarm> {
        // if we want to dismiss we should only add enabled alarms
        val selection = String.format("%s=? AND %s=? AND %s=?",
                AlarmsColumns.HOUR, AlarmsColumns.MINUTES, AlarmsColumns.ENABLED)
        val args = arrayOf(hour24.toString(), minutes.toString(), "1")
        return Alarm.getAlarms(cr, selection, *args)
    }

    val matchingAlarms: List<Alarm>
        get() = mMatchingAlarms

    private fun notifyFailureAndLog(reason: String, activity: Activity) {
        LogUtils.e(reason)
        Controller.getController().notifyVoiceFailure(activity, reason)
    }
}