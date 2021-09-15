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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

import com.android.deskclock.LabelDialogFragment
import com.android.deskclock.R
import com.android.deskclock.data.DataModel
import com.android.deskclock.data.Timer
import com.android.deskclock.data.TimerStringFormatter
import com.android.deskclock.events.Events

/** The public no-arg constructor required by all fragments.  */
class TimerItemFragment : Fragment() {
    var timerId = 0
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        timerId = requireArguments().getInt(KEY_TIMER_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val timer = timer ?: return null

        val view = inflater.inflate(R.layout.timer_item, container, false) as TimerItem
        view.findViewById<View>(R.id.reset_add).setOnClickListener(ResetAddListener())
        view.findViewById<View>(R.id.timer_label).setOnClickListener(EditLabelListener())
        view.findViewById<View>(R.id.timer_time_text).setOnClickListener(TimeTextListener())
        view.update(timer)

        return view
    }

    /**
     * @return `true` iff the timer is in a state that requires continuous updates
     */
    fun updateTime(): Boolean {
        val view = view as TimerItem?
        if (view != null) {
            val timer = timer!!
            view.update(timer)
            return !timer.isReset
        }

        return false
    }

    val timer: Timer?
        get() = DataModel.dataModel.getTimer(timerId)

    private inner class ResetAddListener : View.OnClickListener {
        override fun onClick(v: View) {
            val timer = timer!!
            if (timer.isPaused) {
                DataModel.dataModel.resetOrDeleteTimer(timer, R.string.label_deskclock)
            } else if (timer.isRunning || timer.isExpired || timer.isMissed) {
                DataModel.dataModel.addTimerMinute(timer)
                Events.sendTimerEvent(R.string.action_add_minute, R.string.label_deskclock)

                val context = v.context
                // Must re-retrieve timer because old timer is no longer accurate.
                val currentTime: Long = this@TimerItemFragment.timer!!.remainingTime
                if (currentTime > 0) {
                    v.announceForAccessibility(TimerStringFormatter.formatString(
                            context, R.string.timer_accessibility_one_minute_added, currentTime,
                            true))
                }
            }
        }
    }

    private inner class EditLabelListener : View.OnClickListener {
        override fun onClick(v: View) {
            val fragment = LabelDialogFragment.newInstance(timer!!)
            LabelDialogFragment.show(parentFragmentManager, fragment)
        }
    }

    private inner class TimeTextListener : View.OnClickListener {
        override fun onClick(view: View) {
            val clickedTimer = timer!!
            if (clickedTimer.isPaused || clickedTimer.isReset) {
                DataModel.dataModel.startTimer(clickedTimer)
            } else if (clickedTimer.isRunning) {
                DataModel.dataModel.pauseTimer(clickedTimer)
            }
        }
    }

    companion object {
        private const val KEY_TIMER_ID = "KEY_TIMER_ID"

        fun newInstance(timer: Timer): TimerItemFragment {
            val fragment = TimerItemFragment()
            val args = Bundle()
            args.putInt(KEY_TIMER_ID, timer.id)
            fragment.arguments = args
            return fragment
        }
    }
}