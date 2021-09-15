/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.deskclock.timer

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.SystemClock
import android.text.TextUtils
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.widget.FrameLayout
import android.widget.TextView
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager

import com.android.deskclock.BaseActivity
import com.android.deskclock.LogUtils
import com.android.deskclock.R
import com.android.deskclock.data.DataModel
import com.android.deskclock.data.Timer
import com.android.deskclock.data.TimerListener

/**
 * This activity is designed to be shown over the lock screen. As such, it displays the expired
 * timers and a single button to reset them all. Each expired timer can also be reset to one minute
 * with a button in the user interface. All other timer operations are disabled in this activity.
 */
class ExpiredTimersActivity : BaseActivity() {
    /** Scheduled to update the timers while at least one is expired.  */
    private val mTimeUpdateRunnable: Runnable = TimeUpdateRunnable()

    /** Updates the timers displayed in this activity as the backing data changes.  */
    private val mTimerChangeWatcher: TimerListener = TimerChangeWatcher()

    /** The scene root for transitions when expired timers are added/removed from this container. */
    private lateinit var mExpiredTimersScrollView: ViewGroup

    /** Displays the expired timers.  */
    private lateinit var mExpiredTimersView: ViewGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val expiredTimers = expiredTimers

        // If no expired timers, finish
        if (expiredTimers.size == 0) {
            LogUtils.i("No expired timers, skipping display.")
            finish()
            return
        }

        setContentView(R.layout.expired_timers_activity)

        mExpiredTimersView = findViewById(R.id.expired_timers_list) as ViewGroup
        mExpiredTimersScrollView = findViewById(R.id.expired_timers_scroll) as ViewGroup

        (findViewById(R.id.fab) as View).setOnClickListener(FabClickListener())

        val view: View = findViewById(R.id.expired_timers_activity)
        view.systemUiVisibility = View.SYSTEM_UI_FLAG_LOW_PROFILE

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                or WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON)

        setTurnScreenOn(true)
        setShowWhenLocked(true)

        // Close dialogs and window shade, so this is fully visible
        sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))

        // Honor rotation on tablets; fix the orientation on phones.
        if (!getResources().getBoolean(R.bool.rotateAlarmAlert)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR)
        }

        // Create views for each of the expired timers.
        for (timer in expiredTimers) {
            addTimer(timer)
        }

        // Update views in response to timer data changes.
        DataModel.dataModel.addTimerListener(mTimerChangeWatcher)
    }

    override fun onResume() {
        super.onResume()
        startUpdatingTime()
    }

    override fun onPause() {
        super.onPause()
        stopUpdatingTime()
    }

    override fun onDestroy() {
        super.onDestroy()
        DataModel.dataModel.removeTimerListener(mTimerChangeWatcher)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_VOLUME_MUTE,
                KeyEvent.KEYCODE_CAMERA,
                KeyEvent.KEYCODE_FOCUS -> {
                    DataModel.dataModel.resetOrDeleteExpiredTimers(R.string.label_hardware_button)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * Post the first runnable to update times within the UI. It will reschedule itself as needed.
     */
    private fun startUpdatingTime() {
        // Ensure only one copy of the runnable is ever scheduled by first stopping updates.
        stopUpdatingTime()
        mExpiredTimersView.post(mTimeUpdateRunnable)
    }

    /**
     * Remove the runnable that updates times within the UI.
     */
    private fun stopUpdatingTime() {
        mExpiredTimersView.removeCallbacks(mTimeUpdateRunnable)
    }

    /**
     * Create and add a new view that corresponds with the given `timer`.
     */
    private fun addTimer(timer: Timer) {
        TransitionManager.beginDelayedTransition(mExpiredTimersScrollView, AutoTransition())

        val timerId: Int = timer.id
        val timerItem = getLayoutInflater()
                .inflate(R.layout.timer_item, mExpiredTimersView, false) as TimerItem
        // Store the timer id as a tag on the view so it can be located on delete.
        timerItem.id = timerId
        mExpiredTimersView.addView(timerItem)

        // Hide the label hint for expired timers.
        val labelView = timerItem.findViewById<View>(R.id.timer_label) as TextView
        labelView.hint = null
        labelView.visibility = if (TextUtils.isEmpty(timer.label)) View.GONE else View.VISIBLE

        // Add logic to the "Add 1 Minute" button.
        val addMinuteButton = timerItem.findViewById<View>(R.id.reset_add)
        addMinuteButton.setOnClickListener {
            val timer: Timer = DataModel.dataModel.getTimer(timerId)!!
            DataModel.dataModel.addTimerMinute(timer)
        }

        // If the first timer was just added, center it.
        val expiredTimers = expiredTimers
        if (expiredTimers.size == 1) {
            centerFirstTimer()
        } else if (expiredTimers.size == 2) {
            uncenterFirstTimer()
        }
    }

    /**
     * Remove an existing view that corresponds with the given `timer`.
     */
    private fun removeTimer(timer: Timer) {
        TransitionManager.beginDelayedTransition(mExpiredTimersScrollView, AutoTransition())

        val timerId: Int = timer.id
        val count = mExpiredTimersView.childCount
        for (i in 0 until count) {
            val timerView = mExpiredTimersView.getChildAt(i)
            if (timerView.id == timerId) {
                mExpiredTimersView.removeView(timerView)
                break
            }
        }

        // If the second last timer was just removed, center the last timer.
        val expiredTimers = expiredTimers
        if (expiredTimers.isEmpty()) {
            finish()
        } else if (expiredTimers.size == 1) {
            centerFirstTimer()
        }
    }

    /**
     * Center the single timer.
     */
    private fun centerFirstTimer() {
        val lp = mExpiredTimersView.layoutParams as FrameLayout.LayoutParams
        lp.gravity = Gravity.CENTER
        mExpiredTimersView.requestLayout()
    }

    /**
     * Display the multiple timers as a scrollable list.
     */
    private fun uncenterFirstTimer() {
        val lp = mExpiredTimersView.layoutParams as FrameLayout.LayoutParams
        lp.gravity = Gravity.NO_GRAVITY
        mExpiredTimersView.requestLayout()
    }

    private val expiredTimers: List<Timer>
        get() = DataModel.dataModel.expiredTimers

    /**
     * Periodically refreshes the state of each timer.
     */
    private inner class TimeUpdateRunnable : Runnable {
        override fun run() {
            val startTime = SystemClock.elapsedRealtime()

            val count = mExpiredTimersView.childCount
            for (i in 0 until count) {
                val timerItem = mExpiredTimersView.getChildAt(i) as TimerItem
                val timer: Timer? = DataModel.dataModel.getTimer(timerItem.id)
                if (timer != null) {
                    timerItem.update(timer)
                }
            }

            val endTime = SystemClock.elapsedRealtime()

            // Try to maintain a consistent period of time between redraws.
            val delay = Math.max(0L, startTime + 20L - endTime)
            mExpiredTimersView.postDelayed(this, delay)
        }
    }

    /**
     * Clicking the fab resets all expired timers.
     */
    private inner class FabClickListener : View.OnClickListener {
        override fun onClick(v: View) {
            stopUpdatingTime()
            DataModel.dataModel.removeTimerListener(mTimerChangeWatcher)
            DataModel.dataModel.resetOrDeleteExpiredTimers(R.string.label_deskclock)
            finish()
        }
    }

    /**
     * Adds and removes expired timers from this activity based on their state changes.
     */
    private inner class TimerChangeWatcher : TimerListener {
        override fun timerAdded(timer: Timer) {
            if (timer.isExpired) {
                addTimer(timer)
            }
        }

        override fun timerUpdated(before: Timer, after: Timer) {
            if (!before.isExpired && after.isExpired) {
                addTimer(after)
            } else if (before.isExpired && !after.isExpired) {
                removeTimer(before)
            }
        }

        override fun timerRemoved(timer: Timer) {
            if (timer.isExpired) {
                removeTimer(timer)
            }
        }
    }
}