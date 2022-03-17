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

import android.os.Bundle

import com.android.deskclock.ItemAdapter.ItemHolder
import com.android.deskclock.alarms.AlarmTimeClickHandler
import com.android.deskclock.provider.Alarm
import com.android.deskclock.provider.AlarmInstance

class AlarmItemHolder(
    alarm: Alarm,
    val alarmInstance: AlarmInstance?,
    val alarmTimeClickHandler: AlarmTimeClickHandler
) : ItemHolder<Alarm>(alarm, alarm.id) {
    var isExpanded = false
        private set

    override fun getItemViewType(): Int {
        return if (isExpanded) {
            ExpandedAlarmViewHolder.VIEW_TYPE
        } else {
            CollapsedAlarmViewHolder.VIEW_TYPE
        }
    }

    fun expand() {
        if (!isExpanded) {
            isExpanded = true
            notifyItemChanged()
        }
    }

    fun collapse() {
        if (isExpanded) {
            isExpanded = false
            notifyItemChanged()
        }
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        super.onSaveInstanceState(bundle)
        bundle.putBoolean(EXPANDED_KEY, isExpanded)
    }

    override fun onRestoreInstanceState(bundle: Bundle) {
        super.onRestoreInstanceState(bundle)
        isExpanded = bundle.getBoolean(EXPANDED_KEY)
    }

    companion object {
        private const val EXPANDED_KEY = "expanded"
    }
}