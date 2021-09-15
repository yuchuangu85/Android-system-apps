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

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.View.OnSystemUiVisibilityChangeListener
import android.view.ViewTreeObserver.OnPreDrawListener
import android.view.Window
import android.view.WindowManager
import android.widget.TextClock

import com.android.deskclock.events.Events
import com.android.deskclock.uidata.UiDataModel

class ScreensaverActivity : BaseActivity() {
    private val mStartPositionUpdater: OnPreDrawListener = StartPositionUpdater()

    private val mIntentReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            LOGGER.v("ScreensaverActivity onReceive, action: " + intent.action)

            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> updateWakeLock(true)
                Intent.ACTION_POWER_DISCONNECTED -> updateWakeLock(false)
                Intent.ACTION_USER_PRESENT -> finish()
                AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED -> {
                    Utils.refreshAlarm(this@ScreensaverActivity, mContentView)
                }
            }
        }
    }

    /* Register ContentObserver to see alarm changes for pre-L */
    private val mSettingsContentObserver: ContentObserver? = if (Utils.isPreL) {
        object : ContentObserver(Handler(Looper.myLooper()!!)) {
            override fun onChange(selfChange: Boolean) {
                Utils.refreshAlarm(this@ScreensaverActivity, mContentView)
            }
        }
    } else {
        null
    }

    // Runs every midnight or when the time changes and refreshes the date.
    private val mMidnightUpdater = Runnable {
        Utils.updateDate(mDateFormat, mDateFormatForAccessibility, mContentView)
    }

    private lateinit var mDateFormat: String
    private lateinit var mDateFormatForAccessibility: String

    private lateinit var mContentView: View
    private lateinit var mMainClockView: View

    private lateinit var mPositionUpdater: MoveScreensaverRunnable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mDateFormat = getString(R.string.abbrev_wday_month_day_no_year)
        mDateFormatForAccessibility = getString(R.string.full_wday_month_day_no_year)

        setContentView(R.layout.desk_clock_saver)
        mContentView = findViewById(R.id.saver_container)
        mMainClockView = mContentView.findViewById(R.id.main_clock)

        val digitalClock = mMainClockView.findViewById<View>(R.id.digital_clock)
        val analogClock = mMainClockView.findViewById<View>(R.id.analog_clock) as AnalogClock

        Utils.setClockIconTypeface(mMainClockView)
        Utils.setTimeFormat(digitalClock as TextClock, false)
        Utils.setClockStyle(digitalClock, analogClock)
        Utils.dimClockView(true, mMainClockView)
        analogClock.enableSeconds(false)

        mContentView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LOW_PROFILE
                or View.SYSTEM_UI_FLAG_IMMERSIVE
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        mContentView.setOnSystemUiVisibilityChangeListener(InteractionListener())

        mPositionUpdater = MoveScreensaverRunnable(mContentView, mMainClockView)

        getIntent()?.let {
            val eventLabel = it.getIntExtra(Events.EXTRA_EVENT_LABEL, 0)
            Events.sendScreensaverEvent(R.string.action_show, eventLabel)
        }
    }

    override fun onStart() {
        super.onStart()

        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_POWER_CONNECTED)
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED)
        filter.addAction(Intent.ACTION_USER_PRESENT)
        if (Utils.isLOrLater) {
            filter.addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
        }
        registerReceiver(mIntentReceiver, filter)

        mSettingsContentObserver?.let {
            val uri = Settings.System.getUriFor(Settings.System.NEXT_ALARM_FORMATTED)
            getContentResolver().registerContentObserver(uri, false, it)
        }
    }

    override fun onResume() {
        super.onResume()

        Utils.updateDate(mDateFormat, mDateFormatForAccessibility, mContentView)
        Utils.refreshAlarm(this, mContentView)

        startPositionUpdater()
        UiDataModel.uiDataModel.addMidnightCallback(mMidnightUpdater)

        val intent: Intent? = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val pluggedIn = intent != null && intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        updateWakeLock(pluggedIn)
    }

    override fun onPause() {
        super.onPause()
        UiDataModel.uiDataModel.removePeriodicCallback(mMidnightUpdater)
        stopPositionUpdater()
    }

    override fun onStop() {
        mSettingsContentObserver?.let {
            getContentResolver().unregisterContentObserver(it)
        }
        unregisterReceiver(mIntentReceiver)
        super.onStop()
    }

    override fun onUserInteraction() {
        // We want the screen saver to exit upon user interaction.
        finish()
    }

    /**
     * @param pluggedIn `true` iff the device is currently plugged in to a charger
     */
    private fun updateWakeLock(pluggedIn: Boolean) {
        val win: Window = getWindow()
        val winParams = win.attributes
        winParams.flags = winParams.flags or WindowManager.LayoutParams.FLAG_FULLSCREEN
        if (pluggedIn) {
            winParams.flags = winParams.flags or WINDOW_FLAGS
        } else {
            winParams.flags = winParams.flags and WINDOW_FLAGS.inv()
        }
        win.attributes = winParams
    }

    /**
     * The [.mContentView] will be drawn shortly. When that draw occurs, the position updater
     * callback will also be executed to choose a random position for the time display as well as
     * schedule future callbacks to move the time display each minute.
     */
    private fun startPositionUpdater() {
        mContentView.viewTreeObserver.addOnPreDrawListener(mStartPositionUpdater)
    }

    /**
     * This activity is no longer in the foreground; position callbacks should be removed.
     */
    private fun stopPositionUpdater() {
        mContentView.viewTreeObserver.removeOnPreDrawListener(mStartPositionUpdater)
        mPositionUpdater.stop()
    }

    private inner class StartPositionUpdater : OnPreDrawListener {
        /**
         * This callback occurs after initial layout has completed. It is an appropriate place to
         * select a random position for [.mMainClockView] and schedule future callbacks to update
         * its position.
         *
         * @return `true` to continue with the drawing pass
         */
        override fun onPreDraw(): Boolean {
            if (mContentView.viewTreeObserver.isAlive) {
                // Start the periodic position updater.
                mPositionUpdater.start()

                // This listener must now be removed to avoid starting the position updater again.
                mContentView.viewTreeObserver.removeOnPreDrawListener(mStartPositionUpdater)
            }
            return true
        }
    }

    private inner class InteractionListener : OnSystemUiVisibilityChangeListener {
        override fun onSystemUiVisibilityChange(visibility: Int) {
            // When the user interacts with the screen, the navigation bar reappears
            if (visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == 0) {
                // We want the screen saver to exit upon user interaction.
                finish()
            }
        }
    }

    companion object {
        private val LOGGER = LogUtils.Logger("ScreensaverActivity")

        /** These flags keep the screen on if the device is plugged in.  */
        private const val WINDOW_FLAGS = (WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                or WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
                or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}