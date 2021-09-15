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

package com.android.deskclock.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

import com.android.deskclock.R
import com.android.deskclock.Utils

import java.util.TimeZone

/**
 * All settings data is accessed via this model.
 */
internal class SettingsModel(
    private val mContext: Context,
    private val mPrefs: SharedPreferences,
    /** The model from which time data are fetched.  */
    private val mTimeModel: TimeModel
) {

    /** The uri of the default ringtone to use for timers until the user explicitly chooses one.  */
    private var mDefaultTimerRingtoneUri: Uri? = null

    init {
        // Set the user's default display seconds preference if one has not yet been chosen.
        SettingsDAO.setDefaultDisplayClockSeconds(mContext, mPrefs)
    }

    val globalIntentId: Int
        get() = SettingsDAO.getGlobalIntentId(mPrefs)

    fun updateGlobalIntentId() {
        SettingsDAO.updateGlobalIntentId(mPrefs)
    }

    val citySort: DataModel.CitySort
        get() = SettingsDAO.getCitySort(mPrefs)

    fun toggleCitySort() {
        SettingsDAO.toggleCitySort(mPrefs)
    }

    val homeTimeZone: TimeZone
        get() = SettingsDAO.getHomeTimeZone(mContext, mPrefs, TimeZone.getDefault())

    val clockStyle: DataModel.ClockStyle
        get() = SettingsDAO.getClockStyle(mContext, mPrefs)

    var displayClockSeconds: Boolean
        get() = SettingsDAO.getDisplayClockSeconds(mPrefs)
        set(shouldDisplaySeconds) {
            SettingsDAO.setDisplayClockSeconds(mPrefs, shouldDisplaySeconds)
        }

    val screensaverClockStyle: DataModel.ClockStyle
        get() = SettingsDAO.getScreensaverClockStyle(mContext, mPrefs)

    val screensaverNightModeOn: Boolean
        get() = SettingsDAO.getScreensaverNightModeOn(mPrefs)

    val showHomeClock: Boolean
        get() {
            if (!SettingsDAO.getAutoShowHomeClock(mPrefs)) {
                return false
            }

            // Show the home clock if the current time and home time differ.
            // (By using UTC offset for this comparison the various DST rules are considered)
            val defaultTZ = TimeZone.getDefault()
            val homeTimeZone = SettingsDAO.getHomeTimeZone(mContext, mPrefs, defaultTZ)
            val now = System.currentTimeMillis()
            return homeTimeZone.getOffset(now) != defaultTZ.getOffset(now)
        }

    val defaultTimerRingtoneUri: Uri
        get() {
            if (mDefaultTimerRingtoneUri == null) {
                mDefaultTimerRingtoneUri = Utils.getResourceUri(mContext, R.raw.timer_expire)
            }

            return mDefaultTimerRingtoneUri!!
        }

    var timerRingtoneUri: Uri
        get() = SettingsDAO.getTimerRingtoneUri(mPrefs, defaultTimerRingtoneUri)
        set(uri) {
            SettingsDAO.setTimerRingtoneUri(mPrefs, uri)
        }

    val alarmVolumeButtonBehavior: DataModel.AlarmVolumeButtonBehavior
        get() = SettingsDAO.getAlarmVolumeButtonBehavior(mPrefs)

    val alarmTimeout: Int
        get() = SettingsDAO.getAlarmTimeout(mPrefs)

    val snoozeLength: Int
        get() = SettingsDAO.getSnoozeLength(mPrefs)

    var defaultAlarmRingtoneUri: Uri
        get() = SettingsDAO.getDefaultAlarmRingtoneUri(mPrefs)
        set(uri) {
            SettingsDAO.setDefaultAlarmRingtoneUri(mPrefs, uri)
        }

    val alarmCrescendoDuration: Long
        get() = SettingsDAO.getAlarmCrescendoDuration(mPrefs)

    val timerCrescendoDuration: Long
        get() = SettingsDAO.getTimerCrescendoDuration(mPrefs)

    val weekdayOrder: Weekdays.Order
        get() = SettingsDAO.getWeekdayOrder(mPrefs)

    var isRestoreBackupFinished: Boolean
        get() = SettingsDAO.isRestoreBackupFinished(mPrefs)
        set(finished) {
            SettingsDAO.setRestoreBackupFinished(mPrefs, finished)
        }

    var timerVibrate: Boolean
        get() = SettingsDAO.getTimerVibrate(mPrefs)
        set(enabled) {
            SettingsDAO.setTimerVibrate(mPrefs, enabled)
        }

    val timeZones: TimeZones
        get() = SettingsDAO.getTimeZones(mContext, mTimeModel.currentTimeMillis())
}