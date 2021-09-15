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
import android.app.ListActivity
import android.os.AsyncTask
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ListView

import com.android.deskclock.provider.Alarm
import com.android.deskclock.widget.selector.AlarmSelection
import com.android.deskclock.widget.selector.AlarmSelectionAdapter

import java.util.Locale

class AlarmSelectionActivity : ListActivity() {
    private val mSelections: MutableList<AlarmSelection> = ArrayList()
    private var mAction = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        // this activity is shown if:
        // a) no search mode was specified in which case we show all
        // enabled alarms
        // b) if search mode was next and there was multiple alarms firing next
        // (at the same time) then we only show those alarms firing at the same time
        // c) if search mode was time and there are multiple alarms with that time
        // then we only show those alarms with that time
        super.onCreate(savedInstanceState)
        setContentView(R.layout.selection_layout)

        val cancelButton = findViewById<View>(R.id.cancel_button) as Button
        cancelButton.setOnClickListener { finish() }

        val intent = intent
        val alarmsFromIntent = intent.getParcelableArrayExtra(EXTRA_ALARMS)
        mAction = intent.getIntExtra(EXTRA_ACTION, ACTION_INVALID)

        // reading alarms from intent
        // PickSelection is started only if there are more than 1 relevant alarm
        // so no need to check if alarmsFromIntent is empty
        for (parcelable in alarmsFromIntent!!) {
            val alarm = parcelable as Alarm

            // filling mSelections that go into the UI picker list
            val label = String.format(Locale.US, "%d %02d", alarm.hour, alarm.minutes)
            mSelections.add(AlarmSelection(label, alarm))
        }

        listAdapter = AlarmSelectionAdapter(this, R.layout.alarm_row, mSelections)
    }

    public override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        super.onListItemClick(l, v, position, id)
        // id corresponds to mSelections id because the view adapter used mSelections
        val selection = mSelections[id.toInt()]
        val alarm: Alarm? = selection.alarm
        alarm?.let {
            ProcessAlarmActionAsync(it, this, mAction).execute()
        }
        finish()
    }

    // TODO(b/165664115) Replace deprecated AsyncTask calls
    private class ProcessAlarmActionAsync(
        private val mAlarm: Alarm,
        private val mActivity: Activity,
        private val mAction: Int
    ) : AsyncTask<Void?, Void?, Void?>() {
        override fun doInBackground(vararg parameters: Void?): Void? {
            when (mAction) {
                ACTION_DISMISS -> HandleApiCalls.dismissAlarm(mAlarm, mActivity)
                ACTION_INVALID -> LogUtils.i("Invalid action")
            }
            return null
        }
    }

    companion object {
        /** Used by default when an invalid action provided.  */
        private const val ACTION_INVALID = -1

        /** Action used to signify alarm should be dismissed on selection.  */
        const val ACTION_DISMISS = 0

        const val EXTRA_ACTION = "com.android.deskclock.EXTRA_ACTION"
        const val EXTRA_ALARMS = "com.android.deskclock.EXTRA_ALARMS"
    }
}