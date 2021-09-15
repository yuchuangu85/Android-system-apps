/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.deskclock.alarms

import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.TimePicker
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

import com.android.deskclock.Utils

import java.util.Calendar

/**
 * DialogFragment used to show TimePicker.
 */
class TimePickerDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val listener = getParentFragment() as OnTimeSetListener

        val now = Calendar.getInstance()
        val args: Bundle = arguments ?: Bundle.EMPTY
        val hour: Int = args.getInt(ARG_HOUR, now[Calendar.HOUR_OF_DAY])
        val minute: Int = args.getInt(ARG_MINUTE, now[Calendar.MINUTE])
        return if (Utils.isLOrLater) {
            val context: Context = requireActivity()
            TimePickerDialog(context, { _, hourOfDay, minuteOfHour ->
                listener.onTimeSet(this@TimePickerDialogFragment, hourOfDay, minuteOfHour)
            }, hour, minute, DateFormat.is24HourFormat(context))
        } else {
            val builder: AlertDialog.Builder = AlertDialog.Builder(requireActivity())
            val context: Context = builder.getContext()

            val timePicker = TimePicker(context)
            timePicker.setCurrentHour(hour)
            timePicker.setCurrentMinute(minute)
            timePicker.setIs24HourView(DateFormat.is24HourFormat(context))

            builder.setView(timePicker)
                    .setPositiveButton(android.R.string.ok, { _, _ ->
                        listener.onTimeSet(this@TimePickerDialogFragment,
                                timePicker.getCurrentHour(), timePicker.getCurrentMinute())
                    }).setNegativeButton(android.R.string.cancel, null /* listener */)
                    .create()
        }
    }

    /**
     * The callback interface used to indicate the user is done filling in the time (e.g. they
     * clicked on the 'OK' button).
     */
    interface OnTimeSetListener {
        /**
         * Called when the user is done setting a new time and the dialog has closed.
         *
         * @param fragment the fragment associated with this listener
         * @param hourOfDay the hour that was set
         * @param minute the minute that was set
         */
        fun onTimeSet(fragment: TimePickerDialogFragment?, hourOfDay: Int, minute: Int)
    }

    companion object {
        /**
         * Tag for timer picker fragment in FragmentManager.
         */
        private const val TAG = "TimePickerDialogFragment"

        private const val ARG_HOUR = TAG + "_hour"
        private const val ARG_MINUTE = TAG + "_minute"

        @JvmStatic
        fun show(fragment: Fragment) {
            show(fragment, -1 /* hour */, -1 /* minute */)
        }

        fun show(parentFragment: Fragment, hourOfDay: Int, minute: Int) {
            require(parentFragment is OnTimeSetListener) {
                "Fragment must implement OnTimeSetListener"
            }

            val manager: FragmentManager = parentFragment.getChildFragmentManager()
            if (manager == null || manager.isDestroyed()) {
                return
            }

            // Make sure the dialog isn't already added.
            removeTimeEditDialog(manager)

            val fragment = TimePickerDialogFragment()

            val args = Bundle()
            if (hourOfDay in 0..23) {
                args.putInt(ARG_HOUR, hourOfDay)
            }
            if (minute in 0..59) {
                args.putInt(ARG_MINUTE, minute)
            }

            fragment.setArguments(args)
            fragment.show(manager, TAG)
        }

        @JvmStatic
        fun removeTimeEditDialog(manager: FragmentManager?) {
            manager?.let { manager ->
                val prev: Fragment? = manager.findFragmentByTag(TAG)
                prev?.let {
                    manager.beginTransaction().remove(it).commit()
                }
            }
        }
    }
}