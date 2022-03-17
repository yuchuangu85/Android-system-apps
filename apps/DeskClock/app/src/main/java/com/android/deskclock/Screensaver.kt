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

package com.android.deskclock

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.dreams.DreamService
import android.view.View
import android.view.ViewTreeObserver.OnPreDrawListener
import android.widget.TextClock

import com.android.deskclock.data.DataModel
import com.android.deskclock.uidata.UiDataModel

class Screensaver : DreamService() {
    private val mStartPositionUpdater: OnPreDrawListener = StartPositionUpdater()
    private var mPositionUpdater: MoveScreensaverRunnable? = null

    private var mDateFormat: String? = null
    private var mDateFormatForAccessibility: String? = null

    private var mContentView: View? = null
    private var mMainClockView: View? = null
    private var mDigitalClock: TextClock? = null
    private var mAnalogClock: AnalogClock? = null

    /* Register ContentObserver to see alarm changes for pre-L */
    private val mSettingsContentObserver: ContentObserver? = if (Utils.isLOrLater) {
        null
    } else {
        object : ContentObserver(Handler(Looper.myLooper()!!)) {
            override fun onChange(selfChange: Boolean) {
                Utils.refreshAlarm(this@Screensaver, mContentView)
            }
        }
    }

    // Runs every midnight or when the time changes and refreshes the date.
    private val mMidnightUpdater = Runnable {
        Utils.updateDate(mDateFormat, mDateFormatForAccessibility, mContentView)
    }

    /**
     * Receiver to alarm clock changes.
     */
    private val mAlarmChangedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Utils.refreshAlarm(this@Screensaver, mContentView)
        }
    }

    override fun onCreate() {
        LOGGER.v("Screensaver created")

        setTheme(R.style.Theme_DeskClock)
        super.onCreate()

        mDateFormat = getString(R.string.abbrev_wday_month_day_no_year)
        mDateFormatForAccessibility = getString(R.string.full_wday_month_day_no_year)
    }

    override fun onAttachedToWindow() {
        LOGGER.v("Screensaver attached to window")
        super.onAttachedToWindow()

        setContentView(R.layout.desk_clock_saver)

        mContentView = findViewById(R.id.saver_container)
        mMainClockView = mContentView?.findViewById(R.id.main_clock)
        mDigitalClock = mMainClockView?.findViewById<View>(R.id.digital_clock) as TextClock
        mAnalogClock = mMainClockView?.findViewById<View>(R.id.analog_clock) as AnalogClock

        setClockStyle()
        Utils.setClockIconTypeface(mContentView)
        Utils.setTimeFormat(mDigitalClock, false)
        mAnalogClock?.enableSeconds(false)

        mContentView?.systemUiVisibility = (View.SYSTEM_UI_FLAG_LOW_PROFILE
                or View.SYSTEM_UI_FLAG_IMMERSIVE
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)

        mPositionUpdater = MoveScreensaverRunnable(mContentView!!, mMainClockView!!)

        // We want the screen saver to exit upon user interaction.
        isInteractive = false
        isFullscreen = true

        // Setup handlers for time reference changes and date updates.
        if (Utils.isLOrLater) {
            registerReceiver(mAlarmChangedReceiver,
                    IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED))
        }

        mSettingsContentObserver?.let {
            val uri = Settings.System.getUriFor(Settings.System.NEXT_ALARM_FORMATTED)
            contentResolver.registerContentObserver(uri, false, it)
        }

        Utils.updateDate(mDateFormat, mDateFormatForAccessibility, mContentView)
        Utils.refreshAlarm(this, mContentView)

        startPositionUpdater()
        UiDataModel.uiDataModel.addMidnightCallback(mMidnightUpdater)
    }

    override fun onDetachedFromWindow() {
        LOGGER.v("Screensaver detached from window")
        super.onDetachedFromWindow()

        mSettingsContentObserver?.let {
            contentResolver.unregisterContentObserver(it)
        }

        UiDataModel.uiDataModel.removePeriodicCallback(mMidnightUpdater)
        stopPositionUpdater()

        // Tear down handlers for time reference changes and date updates.
        if (Utils.isLOrLater) {
            unregisterReceiver(mAlarmChangedReceiver)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        LOGGER.v("Screensaver configuration changed")
        super.onConfigurationChanged(newConfig)

        startPositionUpdater()
    }

    private fun setClockStyle() {
        Utils.setScreensaverClockStyle(mDigitalClock!!, mAnalogClock!!)
        val dimNightMode: Boolean = DataModel.dataModel.screensaverNightModeOn
        Utils.dimClockView(dimNightMode, mMainClockView!!)
        isScreenBright = !dimNightMode
    }

    /**
     * The [.mContentView] will be drawn shortly. When that draw occurs, the position updater
     * callback will also be executed to choose a random position for the time display as well as
     * schedule future callbacks to move the time display each minute.
     */
    private fun startPositionUpdater() {
        mContentView?.viewTreeObserver?.addOnPreDrawListener(mStartPositionUpdater)
    }

    /**
     * This activity is no longer in the foreground; position callbacks should be removed.
     */
    private fun stopPositionUpdater() {
        mContentView?.viewTreeObserver?.removeOnPreDrawListener(mStartPositionUpdater)
        mPositionUpdater?.stop()
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
            if (mContentView!!.viewTreeObserver.isAlive) {
                // (Re)start the periodic position updater.
                mPositionUpdater?.start()

                // This listener must now be removed to avoid starting the position updater again.
                mContentView?.viewTreeObserver?.removeOnPreDrawListener(mStartPositionUpdater)
            }
            return true
        }
    }

    companion object {
        private val LOGGER = LogUtils.Logger("Screensaver")
    }
}