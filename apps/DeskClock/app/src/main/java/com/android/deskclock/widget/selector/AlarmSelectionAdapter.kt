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
package com.android.deskclock.widget.selector

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

import com.android.deskclock.R
import com.android.deskclock.data.DataModel
import com.android.deskclock.data.Weekdays
import com.android.deskclock.provider.Alarm
import com.android.deskclock.widget.TextTime

import java.util.Calendar

class AlarmSelectionAdapter(
    context: Context,
    id: Int,
    alarms: List<AlarmSelection>
) : ArrayAdapter<AlarmSelection?>(context, id, alarms) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val context = context
        var row = convertView
        if (row == null) {
            val inflater = LayoutInflater.from(context)
            row = inflater.inflate(R.layout.alarm_row, parent, false)
        }

        val selection = getItem(position)
        val alarm = selection?.alarm

        val alarmTime = row!!.findViewById<View>(R.id.digital_clock) as TextTime
        alarmTime.setTime(alarm!!.hour, alarm.minutes)

        val alarmLabel = row.findViewById<View>(R.id.label) as TextView
        alarmLabel.text = alarm.label

        // find days when alarm is firing
        val daysOfWeek: String
        daysOfWeek = if (!alarm.daysOfWeek.isRepeating) {
            if (Alarm.isTomorrow(alarm, Calendar.getInstance())) {
                context.resources.getString(R.string.alarm_tomorrow)
            } else {
                context.resources.getString(R.string.alarm_today)
            }
        } else {
            val weekdayOrder: Weekdays.Order = DataModel.dataModel.weekdayOrder
            alarm.daysOfWeek.toString(context, weekdayOrder)
        }

        val daysOfWeekView = row.findViewById<View>(R.id.daysOfWeek) as TextView
        daysOfWeekView.text = daysOfWeek

        return row
    }
}