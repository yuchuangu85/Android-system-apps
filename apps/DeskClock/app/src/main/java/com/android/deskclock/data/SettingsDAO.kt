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
import android.content.res.Resources
import android.net.Uri
import android.provider.Settings
import android.text.format.DateUtils
import android.text.format.DateUtils.HOUR_IN_MILLIS
import android.text.format.DateUtils.MINUTE_IN_MILLIS

import com.android.deskclock.R
import com.android.deskclock.data.DataModel.AlarmVolumeButtonBehavior
import com.android.deskclock.data.DataModel.CitySort
import com.android.deskclock.data.DataModel.ClockStyle
import com.android.deskclock.data.Weekdays.Order
import com.android.deskclock.settings.ScreensaverSettingsActivity
import com.android.deskclock.settings.SettingsActivity

import java.util.Arrays
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

import kotlin.math.abs

/**
 * This class encapsulates the storage of application preferences in [SharedPreferences].
 */
internal object SettingsDAO {
    /** Key to a preference that stores the preferred sort order of world cities.  */
    private const val KEY_SORT_PREFERENCE = "sort_preference"

    /** Key to a preference that stores the default ringtone for new alarms.  */
    private const val KEY_DEFAULT_ALARM_RINGTONE_URI = "default_alarm_ringtone_uri"

    /** Key to a preference that stores the global broadcast id.  */
    private const val KEY_ALARM_GLOBAL_ID = "intent.extra.alarm.global.id"

    /** Key to a preference that indicates whether restore (of backup and restore) has completed. */
    private const val KEY_RESTORE_BACKUP_FINISHED = "restore_finished"

    /**
     * @return the id used to discriminate relevant AlarmManager callbacks from defunct ones
     */
    fun getGlobalIntentId(prefs: SharedPreferences): Int {
        return prefs.getInt(KEY_ALARM_GLOBAL_ID, -1)
    }

    /**
     * Update the id used to discriminate relevant AlarmManager callbacks from defunct ones
     */
    fun updateGlobalIntentId(prefs: SharedPreferences) {
        val globalId: Int = prefs.getInt(KEY_ALARM_GLOBAL_ID, -1) + 1
        prefs.edit().putInt(KEY_ALARM_GLOBAL_ID, globalId).apply()
    }

    /**
     * @return an enumerated value indicating the order in which cities are ordered
     */
    fun getCitySort(prefs: SharedPreferences): CitySort {
        val defaultSortOrdinal = CitySort.NAME.ordinal
        val citySortOrdinal: Int = prefs.getInt(KEY_SORT_PREFERENCE, defaultSortOrdinal)
        return CitySort.values()[citySortOrdinal]
    }

    /**
     * Adjust the sort order of cities.
     */
    fun toggleCitySort(prefs: SharedPreferences) {
        val oldSort = getCitySort(prefs)
        val newSort = if (oldSort == CitySort.NAME) CitySort.UTC_OFFSET else CitySort.NAME
        prefs.edit().putInt(KEY_SORT_PREFERENCE, newSort.ordinal).apply()
    }

    /**
     * @return `true` if a clock for the user's home timezone should be automatically
     * displayed when it doesn't match the current timezone
     */
    fun getAutoShowHomeClock(prefs: SharedPreferences): Boolean {
        return prefs.getBoolean(SettingsActivity.KEY_AUTO_HOME_CLOCK, true)
    }

    /**
     * @return the user's home timezone
     */
    fun getHomeTimeZone(context: Context, prefs: SharedPreferences, defaultTZ: TimeZone): TimeZone {
        var timeZoneId: String? = prefs.getString(SettingsActivity.KEY_HOME_TZ, null)

        // If the recorded home timezone is legal, use it.
        val timeZones = getTimeZones(context, System.currentTimeMillis())
        if (timeZones.contains(timeZoneId)) {
            return TimeZone.getTimeZone(timeZoneId)
        }

        // No legal home timezone has yet been recorded, attempt to record the default.
        timeZoneId = defaultTZ.id
        if (timeZones.contains(timeZoneId)) {
            prefs.edit().putString(SettingsActivity.KEY_HOME_TZ, timeZoneId).apply()
        }

        // The timezone returned here may be valid or invalid. When it matches TimeZone.getDefault()
        // the Home city will not show, regardless of its validity.
        return defaultTZ
    }

    /**
     * @return a value indicating whether analog or digital clocks are displayed in the app
     */
    fun getClockStyle(context: Context, prefs: SharedPreferences): ClockStyle {
        return getClockStyle(context, prefs, SettingsActivity.KEY_CLOCK_STYLE)
    }

    /**
     * @return a value indicating whether analog or digital clocks are displayed in the app
     */
    fun getDisplayClockSeconds(prefs: SharedPreferences): Boolean {
        return prefs.getBoolean(SettingsActivity.KEY_CLOCK_DISPLAY_SECONDS, false)
    }

    /**
     * @param displaySeconds whether or not to display seconds on main clock
     */
    fun setDisplayClockSeconds(prefs: SharedPreferences, displaySeconds: Boolean) {
        prefs.edit().putBoolean(SettingsActivity.KEY_CLOCK_DISPLAY_SECONDS, displaySeconds).apply()
    }

    /**
     * Sets the user's display seconds preference based on the currently selected clock if one has
     * not yet been manually chosen.
     */
    fun setDefaultDisplayClockSeconds(context: Context, prefs: SharedPreferences) {
        if (!prefs.contains(SettingsActivity.KEY_CLOCK_DISPLAY_SECONDS)) {
            // If on analog clock style on upgrade, default to true. Otherwise, default to false.
            val isAnalog = getClockStyle(context, prefs) == ClockStyle.ANALOG
            setDisplayClockSeconds(prefs, isAnalog)
        }
    }

    /**
     * @return a value indicating whether analog or digital clocks are displayed on the screensaver
     */
    fun getScreensaverClockStyle(context: Context, prefs: SharedPreferences): ClockStyle {
        return getClockStyle(context, prefs, ScreensaverSettingsActivity.KEY_CLOCK_STYLE)
    }

    /**
     * @return `true` if the screen saver should be dimmed for lower contrast at night
     */
    fun getScreensaverNightModeOn(prefs: SharedPreferences): Boolean {
        return prefs.getBoolean(ScreensaverSettingsActivity.KEY_NIGHT_MODE, false)
    }

    /**
     * @return the uri of the selected ringtone or the `defaultUri` if no explicit selection
     * has yet been made
     */
    fun getTimerRingtoneUri(prefs: SharedPreferences, defaultUri: Uri): Uri {
        val uriString: String? = prefs.getString(SettingsActivity.KEY_TIMER_RINGTONE, null)
        return if (uriString == null) defaultUri else Uri.parse(uriString)
    }

    /**
     * @return whether timer vibration is enabled. false by default.
     */
    fun getTimerVibrate(prefs: SharedPreferences): Boolean {
        return prefs.getBoolean(SettingsActivity.KEY_TIMER_VIBRATE, false)
    }

    /**
     * @param enabled whether vibration will be turned on for all timers.
     */
    fun setTimerVibrate(prefs: SharedPreferences, enabled: Boolean) {
        prefs.edit().putBoolean(SettingsActivity.KEY_TIMER_VIBRATE, enabled).apply()
    }

    /**
     * @param uri the uri of the ringtone to play for all timers
     */
    fun setTimerRingtoneUri(prefs: SharedPreferences, uri: Uri) {
        prefs.edit().putString(SettingsActivity.KEY_TIMER_RINGTONE, uri.toString()).apply()
    }

    /**
     * @return the uri of the selected ringtone or the `defaultUri` if no explicit selection
     * has yet been made
     */
    fun getDefaultAlarmRingtoneUri(prefs: SharedPreferences): Uri {
        val uriString: String? = prefs.getString(KEY_DEFAULT_ALARM_RINGTONE_URI, null)
        return if (uriString == null) {
            Settings.System.DEFAULT_ALARM_ALERT_URI
        } else {
            Uri.parse(uriString)
        }
    }

    /**
     * @param uri identifies the default ringtone to play for new alarms
     */
    fun setDefaultAlarmRingtoneUri(prefs: SharedPreferences, uri: Uri) {
        prefs.edit().putString(KEY_DEFAULT_ALARM_RINGTONE_URI, uri.toString()).apply()
    }

    /**
     * @return the duration, in milliseconds, of the crescendo to apply to alarm ringtone playback;
     * `0` implies no crescendo should be applied
     */
    fun getAlarmCrescendoDuration(prefs: SharedPreferences): Long {
        val crescendoSeconds: String = prefs.getString(SettingsActivity.KEY_ALARM_CRESCENDO, "0")!!
        return crescendoSeconds.toInt() * DateUtils.SECOND_IN_MILLIS
    }

    /**
     * @return the duration, in milliseconds, of the crescendo to apply to timer ringtone playback;
     * `0` implies no crescendo should be applied
     */
    fun getTimerCrescendoDuration(prefs: SharedPreferences): Long {
        val crescendoSeconds: String = prefs.getString(SettingsActivity.KEY_TIMER_CRESCENDO, "0")!!
        return crescendoSeconds.toInt() * DateUtils.SECOND_IN_MILLIS
    }

    /**
     * @return the display order of the weekdays, which can start with [Calendar.SATURDAY],
     * [Calendar.SUNDAY] or [Calendar.MONDAY]
     */
    fun getWeekdayOrder(prefs: SharedPreferences): Order {
        val defaultValue = Calendar.getInstance().firstDayOfWeek.toString()
        val value: String = prefs.getString(SettingsActivity.KEY_WEEK_START, defaultValue)!!
        return when (val firstCalendarDay = value.toInt()) {
            Calendar.SATURDAY -> Order.SAT_TO_FRI
            Calendar.SUNDAY -> Order.SUN_TO_SAT
            Calendar.MONDAY -> Order.MON_TO_SUN
            else -> throw IllegalArgumentException("Unknown weekday: $firstCalendarDay")
        }
    }

    /**
     * @return `true` if the restore process (of backup and restore) has completed
     */
    fun isRestoreBackupFinished(prefs: SharedPreferences): Boolean {
        return prefs.getBoolean(KEY_RESTORE_BACKUP_FINISHED, false)
    }

    /**
     * @param finished `true` means the restore process (of backup and restore) has completed
     */
    fun setRestoreBackupFinished(prefs: SharedPreferences, finished: Boolean) {
        if (finished) {
            prefs.edit().putBoolean(KEY_RESTORE_BACKUP_FINISHED, true).apply()
        } else {
            prefs.edit().remove(KEY_RESTORE_BACKUP_FINISHED).apply()
        }
    }

    /**
     * @return the behavior to execute when volume buttons are pressed while firing an alarm
     */
    fun getAlarmVolumeButtonBehavior(prefs: SharedPreferences): AlarmVolumeButtonBehavior {
        val defaultValue = SettingsActivity.DEFAULT_VOLUME_BEHAVIOR
        val value: String = prefs.getString(SettingsActivity.KEY_VOLUME_BUTTONS, defaultValue)!!
        return when (value) {
            SettingsActivity.DEFAULT_VOLUME_BEHAVIOR -> AlarmVolumeButtonBehavior.NOTHING
            SettingsActivity.VOLUME_BEHAVIOR_SNOOZE -> AlarmVolumeButtonBehavior.SNOOZE
            SettingsActivity.VOLUME_BEHAVIOR_DISMISS -> AlarmVolumeButtonBehavior.DISMISS
            else -> throw IllegalArgumentException("Unknown volume button behavior: $value")
        }
    }

    /**
     * @return the number of minutes an alarm may ring before it has timed out and becomes missed
     */
    fun getAlarmTimeout(prefs: SharedPreferences): Int {
        // Default value must match the one in res/xml/settings.xml
        val string: String = prefs.getString(SettingsActivity.KEY_AUTO_SILENCE, "10")!!
        return string.toInt()
    }

    /**
     * @return the number of minutes an alarm will remain snoozed before it rings again
     */
    fun getSnoozeLength(prefs: SharedPreferences): Int {
        // Default value must match the one in res/xml/settings.xml
        val string: String = prefs.getString(SettingsActivity.KEY_ALARM_SNOOZE, "10")!!
        return string.toInt()
    }

    /**
     * @param currentTime timezone offsets created relative to this time
     * @return a description of the time zones available for selection
     */
    fun getTimeZones(context: Context, currentTime: Long): TimeZones {
        val locale = Locale.getDefault()
        val resources: Resources = context.getResources()
        val timeZoneIds: Array<String> = resources.getStringArray(R.array.timezone_values)
        val timeZoneNames: Array<String> = resources.getStringArray(R.array.timezone_labels)

        // Verify the data is consistent.
        if (timeZoneIds.size != timeZoneNames.size) {
            val message = String.format(Locale.US,
                    "id count (%d) does not match name count (%d) for locale %s",
                    timeZoneIds.size, timeZoneNames.size, locale)
            throw IllegalStateException(message)
        }

        // Create TimeZoneDescriptors for each TimeZone so they can be sorted.
        val descriptors = arrayOfNulls<TimeZoneDescriptor>(timeZoneIds.size)
        for (i in timeZoneIds.indices) {
            val id = timeZoneIds[i]
            val name = timeZoneNames[i].replace("\"".toRegex(), "")
            descriptors[i] = TimeZoneDescriptor(locale, id, name, currentTime)
        }
        Arrays.sort(descriptors)

        // Transfer the TimeZoneDescriptors into parallel arrays for easy consumption by the caller.
        val tzIds = arrayOfNulls<CharSequence>(descriptors.size)
        val tzNames = arrayOfNulls<CharSequence>(descriptors.size)
        for (i in descriptors.indices) {
            val descriptor = descriptors[i]
            tzIds[i] = descriptor!!.mTimeZoneId
            tzNames[i] = descriptor.mTimeZoneName
        }

        return TimeZones(tzIds.requireNoNulls(), tzNames.requireNoNulls())
    }

    private fun getClockStyle(context: Context, prefs: SharedPreferences, key: String): ClockStyle {
        val defaultStyle: String = context.getString(R.string.default_clock_style)
        val clockStyle: String = prefs.getString(key, defaultStyle)!!
        // Use hardcoded locale to perform toUpperCase, because in some languages toUpperCase adds
        // accent to character, which breaks the enum conversion.
        return ClockStyle.valueOf(clockStyle.toUpperCase(Locale.US))
    }

    /**
     * These descriptors have a natural order from furthest ahead of GMT to furthest behind GMT.
     */
    private class TimeZoneDescriptor(
        locale: Locale,
        val mTimeZoneId: String,
        name: String,
        currentTime: Long
    ) : Comparable<TimeZoneDescriptor> {
        private val mOffset: Int
        val mTimeZoneName: String

        init {
            val tz = TimeZone.getTimeZone(mTimeZoneId)
            mOffset = tz.getOffset(currentTime)

            val sign = if (mOffset < 0) '-' else '+'
            val absoluteGMTOffset = abs(mOffset)
            val hour: Long = absoluteGMTOffset / HOUR_IN_MILLIS
            val minute: Long = absoluteGMTOffset / MINUTE_IN_MILLIS % 60
            mTimeZoneName = String.format(locale, "(GMT%s%d:%02d) %s", sign, hour, minute, name)
        }

        override fun compareTo(other: TimeZoneDescriptor): Int {
            return mOffset - other.mOffset
        }
    }
}