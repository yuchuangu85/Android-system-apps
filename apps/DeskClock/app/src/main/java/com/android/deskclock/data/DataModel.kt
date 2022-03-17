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

import android.app.Service
import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.AudioManager.FLAG_SHOW_UI
import android.media.AudioManager.STREAM_ALARM
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import android.provider.Settings.ACTION_SOUND_SETTINGS
import android.view.View
import androidx.annotation.Keep
import androidx.annotation.StringRes

import com.android.deskclock.Predicate
import com.android.deskclock.R
import com.android.deskclock.Utils
import com.android.deskclock.timer.TimerService

import java.util.Calendar

import kotlin.Comparator
import kotlin.math.roundToInt

/**
 * All application-wide data is accessible through this singleton.
 */
class DataModel private constructor() {

    /** Indicates the display style of clocks.  */
    enum class ClockStyle {
        ANALOG, DIGITAL
    }

    /** Indicates the preferred sort order of cities.  */
    enum class CitySort {
        NAME, UTC_OFFSET
    }

    /** Indicates the preferred behavior of hardware volume buttons when firing alarms.  */
    enum class AlarmVolumeButtonBehavior {
        NOTHING, SNOOZE, DISMISS
    }

    /** Indicates the reason alarms may not fire or may fire silently.  */
    enum class SilentSetting(
        @field:StringRes @get:StringRes val labelResId: Int,
        @field:StringRes @get:StringRes val actionResId: Int,
        private val mActionEnabled: Predicate<Context>,
        private val mActionListener: View.OnClickListener?
    ) {

        DO_NOT_DISTURB(R.string.alarms_blocked_by_dnd,
                0,
                Predicate.FALSE as Predicate<Context>,
                mActionListener = null),
        MUTED_VOLUME(R.string.alarm_volume_muted,
                R.string.unmute_alarm_volume,
                Predicate.TRUE as Predicate<Context>,
                UnmuteAlarmVolumeListener()),
        SILENT_RINGTONE(R.string.silent_default_alarm_ringtone,
                R.string.change_setting_action,
                ChangeSoundActionPredicate(),
                ChangeSoundSettingsListener()),
        BLOCKED_NOTIFICATIONS(R.string.app_notifications_blocked,
                R.string.change_setting_action,
                Predicate.TRUE as Predicate<Context>,
                ChangeAppNotificationSettingsListener());

        val actionListener: View.OnClickListener?
            get() = mActionListener

        fun isActionEnabled(context: Context): Boolean {
            return labelResId != 0 && mActionEnabled.apply(context)
        }

        private class UnmuteAlarmVolumeListener : View.OnClickListener {
            override fun onClick(v: View) {
                // Set the alarm volume to 11/16th of max and show the slider UI.
                // 11/16th of max is the initial volume of the alarm stream on a fresh install.
                val context: Context = v.context
                val am: AudioManager = context.getSystemService(AUDIO_SERVICE) as AudioManager
                val index = (am.getStreamMaxVolume(STREAM_ALARM) * 11f / 16f).roundToInt()
                am.setStreamVolume(STREAM_ALARM, index, FLAG_SHOW_UI)
            }
        }

        private class ChangeSoundSettingsListener : View.OnClickListener {
            override fun onClick(v: View) {
                val context: Context = v.context
                context.startActivity(Intent(ACTION_SOUND_SETTINGS)
                        .addFlags(FLAG_ACTIVITY_NEW_TASK))
            }
        }

        private class ChangeSoundActionPredicate : Predicate<Context> {
            override fun apply(context: Context): Boolean {
                val intent = Intent(ACTION_SOUND_SETTINGS)
                return intent.resolveActivity(context.packageManager) != null
            }
        }

        private class ChangeAppNotificationSettingsListener : View.OnClickListener {
            override fun onClick(v: View) {
                val context: Context = v.context
                if (Utils.isLOrLater) {
                    try {
                        // Attempt to open the notification settings for this app.
                        context.startActivity(
                                Intent("android.settings.APP_NOTIFICATION_SETTINGS")
                                        .putExtra("app_package", context.packageName)
                                        .putExtra("app_uid", context.applicationInfo.uid)
                                        .addFlags(FLAG_ACTIVITY_NEW_TASK))
                        return
                    } catch (ignored: Exception) {
                        // best attempt only; recovery code below
                    }
                }

                // Fall back to opening the app settings page.
                context.startActivity(Intent(ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", context.packageName, null))
                        .addFlags(FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    private var mHandler: Handler? = null
    private var mContext: Context? = null

    /** The model from which settings are fetched.  */
    private var mSettingsModel: SettingsModel? = null

    /** The model from which city data are fetched.  */
    private var mCityModel: CityModel? = null

    /** The model from which timer data are fetched.  */
    private var mTimerModel: TimerModel? = null

    /** The model from which alarm data are fetched.  */
    private var mAlarmModel: AlarmModel? = null

    /** The model from which widget data are fetched.  */
    private var mWidgetModel: WidgetModel? = null

    /** The model from which data about settings that silence alarms are fetched.  */
    private var mSilentSettingsModel: SilentSettingsModel? = null

    /** The model from which stopwatch data are fetched.  */
    private var mStopwatchModel: StopwatchModel? = null

    /** The model from which notification data are fetched.  */
    private var mNotificationModel: NotificationModel? = null

    /** The model from which time data are fetched.  */
    private var mTimeModel: TimeModel? = null

    /** The model from which ringtone data are fetched.  */
    private var mRingtoneModel: RingtoneModel? = null

    /**
     * Initializes the data model with the context and shared preferences to be used.
     */
    fun init(context: Context, prefs: SharedPreferences) {
        if (mContext !== context) {
            mContext = context.applicationContext
            mTimeModel = TimeModel(mContext!!)
            mWidgetModel = WidgetModel(prefs)
            mNotificationModel = NotificationModel()
            mRingtoneModel = RingtoneModel(mContext!!, prefs)
            mSettingsModel = SettingsModel(mContext!!, prefs, mTimeModel!!)
            mCityModel = CityModel(mContext!!, prefs, mSettingsModel!!)
            mAlarmModel = AlarmModel(mContext!!, mSettingsModel!!)
            mSilentSettingsModel = SilentSettingsModel(mContext!!, mNotificationModel!!)
            mStopwatchModel = StopwatchModel(mContext!!, prefs, mNotificationModel!!)
            mTimerModel = TimerModel(mContext!!, prefs, mSettingsModel!!, mRingtoneModel!!,
                    mNotificationModel!!)
        }
    }

    /**
     * Convenience for `run(runnable, 0)`, i.e. waits indefinitely.
     */
    fun run(runnable: Runnable) {
        try {
            run(runnable, 0 /* waitMillis */)
        } catch (ignored: InterruptedException) {
        }
    }

    /**
     * Updates all timers and the stopwatch after the device has shutdown and restarted.
     */
    fun updateAfterReboot() {
        Utils.enforceMainLooper()
        mTimerModel!!.updateTimersAfterReboot()
        mStopwatchModel!!.setStopwatch(stopwatch.updateAfterReboot())
    }

    /**
     * Updates all timers and the stopwatch after the device's time has changed.
     */
    fun updateAfterTimeSet() {
        Utils.enforceMainLooper()
        mTimerModel!!.updateTimersAfterTimeSet()
        mStopwatchModel!!.setStopwatch(stopwatch.updateAfterTimeSet())
    }

    /**
     * Posts a runnable to the main thread and blocks until the runnable executes. Used to access
     * the data model from the main thread.
     */
    @Throws(InterruptedException::class)
    fun run(runnable: Runnable, waitMillis: Long) {
        if (Looper.myLooper() === Looper.getMainLooper()) {
            runnable.run()
            return
        }

        val er = ExecutedRunnable(runnable)
        handler.post(er)

        // Wait for the data to arrive, if it has not.
        synchronized(er) {
            if (!er.isExecuted) {
                er.wait(waitMillis)
            }
        }
    }

    /**
     * @return a handler associated with the main thread
     */
    @get:Synchronized
    private val handler: Handler
        get() {
            if (mHandler == null) {
                mHandler = Handler(Looper.getMainLooper())
            }
            return mHandler!!
        }

    //
    // Application
    //

    var isApplicationInForeground: Boolean
        /**
         * @return `true` when the application is open in the foreground; `false` otherwise
         */
        get() {
            Utils.enforceMainLooper()
            return mNotificationModel!!.isApplicationInForeground
        }
        /**
         * @param inForeground `true` to indicate the application is open in the foreground
         */
        set(inForeground) {
            Utils.enforceMainLooper()
            if (mNotificationModel!!.isApplicationInForeground != inForeground) {
                mNotificationModel!!.isApplicationInForeground = inForeground

                // Refresh all notifications in response to a change in app open state.
                mTimerModel!!.updateNotification()
                mTimerModel!!.updateMissedNotification()
                mStopwatchModel!!.updateNotification()
                mSilentSettingsModel!!.updateSilentState()
            }
        }

    /**
     * Called when the notifications may be stale or absent from the notification manager and must
     * be rebuilt. e.g. after upgrading the application
     */
    fun updateAllNotifications() {
        Utils.enforceMainLooper()
        mTimerModel!!.updateNotification()
        mTimerModel!!.updateMissedNotification()
        mStopwatchModel!!.updateNotification()
    }

    //
    // Cities
    //

    /**
     * @return a list of all cities in their display order
     */
    val allCities: List<City>
        get() {
            Utils.enforceMainLooper()
            return mCityModel!!.allCities
        }

    /**
     * @return a city representing the user's home timezone
     */
    val homeCity: City
        get() {
            Utils.enforceMainLooper()
            return mCityModel!!.homeCity
        }

    /**
     * @return a list of cities not selected for display
     */
    val unselectedCities: List<City>
        get() {
            Utils.enforceMainLooper()
            return mCityModel!!.unselectedCities
        }

    var selectedCities: Collection<City>
        /**
         * @return a list of cities selected for display
         */
        get() {
            Utils.enforceMainLooper()
            return mCityModel!!.selectedCities
        }
        /**
         * @param cities the new collection of cities selected for display by the user
         */
        set(cities) {
            Utils.enforceMainLooper()
            mCityModel?.setSelectedCities(cities)
        }

    /**
     * @return a comparator used to locate index positions
     */
    val cityIndexComparator: Comparator<City>
        get() {
            Utils.enforceMainLooper()
            return mCityModel!!.cityIndexComparator
        }

    /**
     * @return the order in which cities are sorted
     */
    val citySort: CitySort
        get() {
            Utils.enforceMainLooper()
            return mCityModel!!.citySort
        }

    /**
     * Adjust the order in which cities are sorted.
     */
    fun toggleCitySort() {
        Utils.enforceMainLooper()
        mCityModel?.toggleCitySort()
    }

    /**
     * @param cityListener listener to be notified when the world city list changes
     */
    fun addCityListener(cityListener: CityListener) {
        Utils.enforceMainLooper()
        mCityModel?.addCityListener(cityListener)
    }

    /**
     * @param cityListener listener that no longer needs to be notified of world city list changes
     */
    fun removeCityListener(cityListener: CityListener) {
        Utils.enforceMainLooper()
        mCityModel?.removeCityListener(cityListener)
    }

    //
    // Timers
    //

    /**
     * @param timerListener to be notified when timers are added, updated and removed
     */
    fun addTimerListener(timerListener: TimerListener) {
        Utils.enforceMainLooper()
        mTimerModel?.addTimerListener(timerListener)
    }

    /**
     * @param timerListener to no longer be notified when timers are added, updated and removed
     */
    fun removeTimerListener(timerListener: TimerListener) {
        Utils.enforceMainLooper()
        mTimerModel?.removeTimerListener(timerListener)
    }

    /**
     * @return a list of timers for display
     */
    val timers: List<Timer>
        get() {
            Utils.enforceMainLooper()
            return mTimerModel!!.timers
        }

    /**
     * @return a list of expired timers for display
     */
    val expiredTimers: List<Timer>
        get() {
            Utils.enforceMainLooper()
            return mTimerModel!!.expiredTimers
        }

    /**
     * @param timerId identifies the timer to return
     * @return the timer with the given `timerId`
     */
    fun getTimer(timerId: Int): Timer? {
        Utils.enforceMainLooper()
        return mTimerModel?.getTimer(timerId)
    }

    /**
     * @return the timer that last expired and is still expired now; `null` if no timers are
     * expired
     */
    val mostRecentExpiredTimer: Timer?
        get() {
            Utils.enforceMainLooper()
            return mTimerModel?.mostRecentExpiredTimer
        }

    /**
     * @param length the length of the timer in milliseconds
     * @param label describes the purpose of the timer
     * @param deleteAfterUse `true` indicates the timer should be deleted when it is reset
     * @return the newly added timer
     */
    fun addTimer(length: Long, label: String?, deleteAfterUse: Boolean): Timer {
        Utils.enforceMainLooper()
        return mTimerModel!!.addTimer(length, label, deleteAfterUse)
    }

    /**
     * @param timer the timer to be removed
     */
    fun removeTimer(timer: Timer) {
        Utils.enforceMainLooper()
        mTimerModel?.removeTimer(timer)
    }

    /**
     * @param timer the timer to be started
     */
    fun startTimer(timer: Timer) {
        startTimer(null, timer)
    }

    /**
     * @param service used to start foreground notifications for expired timers
     * @param timer the timer to be started
     */
    fun startTimer(service: Service?, timer: Timer) {
        Utils.enforceMainLooper()
        val started = timer.start()
        mTimerModel?.updateTimer(started)
        if (timer.remainingTime <= 0) {
            if (service != null) {
                expireTimer(service, started)
            } else {
                mContext!!.startService(TimerService.createTimerExpiredIntent(mContext!!, started))
            }
        }
    }

    /**
     * @param timer the timer to be paused
     */
    fun pauseTimer(timer: Timer) {
        Utils.enforceMainLooper()
        mTimerModel?.updateTimer(timer.pause())
    }

    /**
     * @param service used to start foreground notifications for expired timers
     * @param timer the timer to be expired
     */
    fun expireTimer(service: Service?, timer: Timer) {
        Utils.enforceMainLooper()
        mTimerModel?.expireTimer(service, timer)
    }

    /**
     * @param timer the timer to be reset
     * @return the reset `timer`
     */
    @Keep
    fun resetTimer(timer: Timer): Timer? {
        Utils.enforceMainLooper()
        return mTimerModel?.resetTimer(timer, false /* allowDelete */, 0 /* eventLabelId */)
    }

    /**
     * If the given `timer` is expired and marked for deletion after use then this method
     * removes the timer. The timer is otherwise transitioned to the reset state and continues
     * to exist.
     *
     * @param timer the timer to be reset
     * @param eventLabelId the label of the timer event to send; 0 if no event should be sent
     * @return the reset `timer` or `null` if the timer was deleted
     */
    fun resetOrDeleteTimer(timer: Timer, @StringRes eventLabelId: Int): Timer? {
        Utils.enforceMainLooper()
        return mTimerModel?.resetTimer(timer, true /* allowDelete */, eventLabelId)
    }

    /**
     * Resets all expired timers.
     *
     * @param eventLabelId the label of the timer event to send; 0 if no event should be sent
     */
    fun resetOrDeleteExpiredTimers(@StringRes eventLabelId: Int) {
        Utils.enforceMainLooper()
        mTimerModel?.resetOrDeleteExpiredTimers(eventLabelId)
    }

    /**
     * Resets all unexpired timers.
     *
     * @param eventLabelId the label of the timer event to send; 0 if no event should be sent
     */
    fun resetUnexpiredTimers(@StringRes eventLabelId: Int) {
        Utils.enforceMainLooper()
        mTimerModel?.resetUnexpiredTimers(eventLabelId)
    }

    /**
     * Resets all missed timers.
     *
     * @param eventLabelId the label of the timer event to send; 0 if no event should be sent
     */
    fun resetMissedTimers(@StringRes eventLabelId: Int) {
        Utils.enforceMainLooper()
        mTimerModel?.resetMissedTimers(eventLabelId)
    }

    /**
     * @param timer the timer to which a minute should be added to the remaining time
     */
    fun addTimerMinute(timer: Timer) {
        Utils.enforceMainLooper()
        mTimerModel?.updateTimer(timer.addMinute())
    }

    /**
     * @param timer the timer to which the new `label` belongs
     * @param label the new label to store for the `timer`
     */
    fun setTimerLabel(timer: Timer, label: String?) {
        Utils.enforceMainLooper()
        mTimerModel?.updateTimer(timer.setLabel(label))
    }

    /**
     * @param timer the timer whose `length` to change
     * @param length the new length of the timer in milliseconds
     */
    fun setTimerLength(timer: Timer, length: Long) {
        Utils.enforceMainLooper()
        mTimerModel?.updateTimer(timer.setLength(length))
    }

    /**
     * @param timer the timer whose `remainingTime` to change
     * @param remainingTime the new remaining time of the timer in milliseconds
     */
    fun setRemainingTime(timer: Timer, remainingTime: Long) {
        Utils.enforceMainLooper()

        val updated = timer.setRemainingTime(remainingTime)
        mTimerModel?.updateTimer(updated)
        if (timer.isRunning && timer.remainingTime <= 0) {
            mContext?.startService(TimerService.createTimerExpiredIntent(mContext!!, updated))
        }
    }

    /**
     * Updates the timer notifications to be current.
     */
    fun updateTimerNotification() {
        Utils.enforceMainLooper()
        mTimerModel?.updateNotification()
    }

    /**
     * @return the uri of the default ringtone to play for all timers when no user selection exists
     */
    val defaultTimerRingtoneUri: Uri
        get() {
            Utils.enforceMainLooper()
            return mTimerModel!!.defaultTimerRingtoneUri
        }

    /**
     * @return `true` iff the ringtone to play for all timers is the silent ringtone
     */
    val isTimerRingtoneSilent: Boolean
        get() {
            Utils.enforceMainLooper()
            return mTimerModel!!.isTimerRingtoneSilent
        }

    var timerRingtoneUri: Uri
        /**
         * @return the uri of the ringtone to play for all timers
         */
        get() {
            Utils.enforceMainLooper()
            return mTimerModel!!.timerRingtoneUri
        }
        /**
         * @param uri the uri of the ringtone to play for all timers
         */
        set(uri) {
            Utils.enforceMainLooper()
            mTimerModel!!.timerRingtoneUri = uri
        }

    /**
     * @return the title of the ringtone that is played for all timers
     */
    val timerRingtoneTitle: String
        get() {
            Utils.enforceMainLooper()
            return mTimerModel!!.timerRingtoneTitle
        }

    /**
     * @return the duration, in milliseconds, of the crescendo to apply to timer ringtone playback;
     * `0` implies no crescendo should be applied
     */
    val timerCrescendoDuration: Long
        get() {
            Utils.enforceMainLooper()
            return mTimerModel!!.timerCrescendoDuration
        }

    var timerVibrate: Boolean
        /**
         * @return whether vibrate is enabled for all timers.
         */
        get() {
            Utils.enforceMainLooper()
            return mTimerModel!!.timerVibrate
        }
        /**
         * @param enabled whether vibrate is enabled for all timers.
         */
        set(enabled) {
            Utils.enforceMainLooper()
            mTimerModel!!.timerVibrate = enabled
        }

    //
    // Alarms
    //

    var defaultAlarmRingtoneUri: Uri
        /**
         * @return the uri of the ringtone to which all new alarms default
         */
        get() {
            Utils.enforceMainLooper()
            return mAlarmModel!!.defaultAlarmRingtoneUri
        }
        /**
         * @param uri the uri of the ringtone to which future new alarms will default
         */
        set(uri) {
            Utils.enforceMainLooper()
            mAlarmModel!!.defaultAlarmRingtoneUri = uri
        }

    /**
     * @return the duration, in milliseconds, of the crescendo to apply to alarm ringtone playback;
     * `0` implies no crescendo should be applied
     */
    val alarmCrescendoDuration: Long
        get() {
            Utils.enforceMainLooper()
            return mAlarmModel!!.alarmCrescendoDuration
        }

    /**
     * @return the behavior to execute when volume buttons are pressed while firing an alarm
     */
    val alarmVolumeButtonBehavior: AlarmVolumeButtonBehavior
        get() {
            Utils.enforceMainLooper()
            return mAlarmModel!!.alarmVolumeButtonBehavior
        }

    /**
     * @return the number of minutes an alarm may ring before it has timed out and becomes missed
     */
    val alarmTimeout: Int
        get() = mAlarmModel!!.alarmTimeout

    /**
     * @return the number of minutes an alarm will remain snoozed before it rings again
     */
    val snoozeLength: Int
        get() = mAlarmModel!!.snoozeLength

    //
    // Stopwatch
    //

    /**
     * @param stopwatchListener to be notified when stopwatch changes or laps are added
     */
    fun addStopwatchListener(stopwatchListener: StopwatchListener) {
        Utils.enforceMainLooper()
        mStopwatchModel?.addStopwatchListener(stopwatchListener)
    }

    /**
     * @param stopwatchListener to no longer be notified when stopwatch changes or laps are added
     */
    fun removeStopwatchListener(stopwatchListener: StopwatchListener) {
        Utils.enforceMainLooper()
        mStopwatchModel?.removeStopwatchListener(stopwatchListener)
    }

    /**
     * @return the current state of the stopwatch
     */
    val stopwatch: Stopwatch
        get() {
            Utils.enforceMainLooper()
            return mStopwatchModel!!.stopwatch
        }

    /**
     * @return the stopwatch after being started
     */
    fun startStopwatch(): Stopwatch {
        Utils.enforceMainLooper()
        return mStopwatchModel!!.setStopwatch(stopwatch.start())
    }

    /**
     * @return the stopwatch after being paused
     */
    fun pauseStopwatch(): Stopwatch {
        Utils.enforceMainLooper()
        return mStopwatchModel!!.setStopwatch(stopwatch.pause())
    }

    /**
     * @return the stopwatch after being reset
     */
    fun resetStopwatch(): Stopwatch {
        Utils.enforceMainLooper()
        return mStopwatchModel!!.setStopwatch(stopwatch.reset())
    }

    /**
     * @return the laps recorded for this stopwatch
     */
    val laps: List<Lap>
        get() {
            Utils.enforceMainLooper()
            return mStopwatchModel!!.laps
        }

    /**
     * @return a newly recorded lap completed now; `null` if no more laps can be added
     */
    fun addLap(): Lap? {
        Utils.enforceMainLooper()
        return mStopwatchModel!!.addLap()
    }

    /**
     * @return `true` iff more laps can be recorded
     */
    fun canAddMoreLaps(): Boolean {
        Utils.enforceMainLooper()
        return mStopwatchModel!!.canAddMoreLaps()
    }

    /**
     * @return the longest lap time of all recorded laps and the current lap
     */
    val longestLapTime: Long
        get() {
            Utils.enforceMainLooper()
            return mStopwatchModel!!.longestLapTime
        }

    /**
     * @param time a point in time after the end of the last lap
     * @return the elapsed time between the given `time` and the end of the previous lap
     */
    fun getCurrentLapTime(time: Long): Long {
        Utils.enforceMainLooper()
        return mStopwatchModel!!.getCurrentLapTime(time)
    }

    //
    // Time
    // (Time settings/values are accessible from any Thread so no Thread-enforcement exists.)
    //

    /**
     * @return the current time in milliseconds
     */
    fun currentTimeMillis(): Long {
        return mTimeModel!!.currentTimeMillis()
    }

    /**
     * @return milliseconds since boot, including time spent in sleep
     */
    fun elapsedRealtime(): Long {
        return mTimeModel!!.elapsedRealtime()
    }

    /**
     * @return `true` if 24 hour time format is selected; `false` otherwise
     */
    fun is24HourFormat(): Boolean {
        return mTimeModel!!.is24HourFormat()
    }

    /**
     * @return a new calendar object initialized to the [.currentTimeMillis]
     */
    val calendar: Calendar
        get() = mTimeModel!!.calendar

    //
    // Ringtones
    //

    /**
     * Ringtone titles are cached because loading them is expensive. This method
     * **must** be called on a background thread and is responsible for priming the
     * cache of ringtone titles to avoid later fetching titles on the main thread.
     */
    fun loadRingtoneTitles() {
        Utils.enforceNotMainLooper()
        mRingtoneModel?.loadRingtoneTitles()
    }

    /**
     * Recheck the permission to read each custom ringtone.
     */
    fun loadRingtonePermissions() {
        Utils.enforceNotMainLooper()
        mRingtoneModel?.loadRingtonePermissions()
    }

    /**
     * @param uri the uri of a ringtone
     * @return the title of the ringtone with the `uri`; `null` if it cannot be fetched
     */
    fun getRingtoneTitle(uri: Uri): String? {
        Utils.enforceMainLooper()
        return mRingtoneModel?.getRingtoneTitle(uri)
    }

    /**
     * @param uri the uri of an audio file to use as a ringtone
     * @param title the title of the audio content at the given `uri`
     * @return the ringtone instance created for the audio file
     */
    fun addCustomRingtone(uri: Uri, title: String?): CustomRingtone? {
        Utils.enforceMainLooper()
        return mRingtoneModel?.addCustomRingtone(uri, title)
    }

    /**
     * @param uri identifies the ringtone to remove
     */
    fun removeCustomRingtone(uri: Uri) {
        Utils.enforceMainLooper()
        mRingtoneModel?.removeCustomRingtone(uri)
    }

    /**
     * @return all available custom ringtones
     */
    val customRingtones: List<CustomRingtone>
        get() {
            Utils.enforceMainLooper()
            return mRingtoneModel!!.customRingtones
        }

    //
    // Widgets
    //

    /**
     * @param widgetClass indicates the type of widget being counted
     * @param count the number of widgets of the given type
     * @param eventCategoryId identifies the category of event to send
     */
    fun updateWidgetCount(widgetClass: Class<*>?, count: Int, @StringRes eventCategoryId: Int) {
        Utils.enforceMainLooper()
        mWidgetModel!!.updateWidgetCount(widgetClass!!, count, eventCategoryId)
    }

    //
    // Settings
    //

    /**
     * @param silentSettingsListener to be notified when alarm-silencing settings change
     */
    fun addSilentSettingsListener(silentSettingsListener: OnSilentSettingsListener) {
        Utils.enforceMainLooper()
        mSilentSettingsModel?.addSilentSettingsListener(silentSettingsListener)
    }

    /**
     * @param silentSettingsListener to no longer be notified when alarm-silencing settings change
     */
    fun removeSilentSettingsListener(silentSettingsListener: OnSilentSettingsListener) {
        Utils.enforceMainLooper()
        mSilentSettingsModel?.removeSilentSettingsListener(silentSettingsListener)
    }

    /**
     * @return the id used to discriminate relevant AlarmManager callbacks from defunct ones
     */
    val globalIntentId: Int
        get() = mSettingsModel!!.globalIntentId

    /**
     * Update the id used to discriminate relevant AlarmManager callbacks from defunct ones
     */
    fun updateGlobalIntentId() {
        Utils.enforceMainLooper()
        mSettingsModel!!.updateGlobalIntentId()
    }

    /**
     * @return the style of clock to display in the clock application
     */
    val clockStyle: ClockStyle
        get() {
            Utils.enforceMainLooper()
            return mSettingsModel!!.clockStyle
        }

    var displayClockSeconds: Boolean
        /**
         * @return the style of clock to display in the clock application
         */
        get() {
            Utils.enforceMainLooper()
            return mSettingsModel!!.displayClockSeconds
        }
        /**
         * @param displaySeconds whether or not to display seconds for main clock
         */
        set(displaySeconds) {
            Utils.enforceMainLooper()
            mSettingsModel!!.displayClockSeconds = displaySeconds
        }

    /**
     * @return the style of clock to display in the clock screensaver
     */
    val screensaverClockStyle: ClockStyle
        get() {
            Utils.enforceMainLooper()
            return mSettingsModel!!.screensaverClockStyle
        }

    /**
     * @return `true` if the screen saver should be dimmed for lower contrast at night
     */
    val screensaverNightModeOn: Boolean
        get() {
            Utils.enforceMainLooper()
            return mSettingsModel!!.screensaverNightModeOn
        }

    /**
     * @return `true` if the users wants to automatically show a clock for their home timezone
     * when they have travelled outside of that timezone
     */
    val showHomeClock: Boolean
        get() {
            Utils.enforceMainLooper()
            return mSettingsModel!!.showHomeClock
        }

    /**
     * @return the display order of the weekdays, which can start with [Calendar.SATURDAY],
     * [Calendar.SUNDAY] or [Calendar.MONDAY]
     */
    val weekdayOrder: Weekdays.Order
        get() {
            Utils.enforceMainLooper()
            return mSettingsModel!!.weekdayOrder
        }

    var isRestoreBackupFinished: Boolean
        /**
         * @return `true` if the restore process (of backup and restore) has completed
         */
        get() = mSettingsModel!!.isRestoreBackupFinished
        /**
         * @param finished `true` means the restore process (of backup and restore) has completed
         */
        set(finished) {
            mSettingsModel!!.isRestoreBackupFinished = finished
        }

    /**
     * @return a description of the time zones available for selection
     */
    val timeZones: TimeZones
        get() {
            Utils.enforceMainLooper()
            return mSettingsModel!!.timeZones
        }

    /**
     * Used to execute a delegate runnable and track its completion.
     */
    private class ExecutedRunnable(private val mDelegate: Runnable) : Runnable, java.lang.Object() {
        var isExecuted = false
        override fun run() {
            mDelegate.run()
            synchronized(this) {
                isExecuted = true
                notifyAll()
            }
        }
    }

    companion object {
        const val ACTION_WORLD_CITIES_CHANGED = "com.android.deskclock.WORLD_CITIES_CHANGED"

        /** The single instance of this data model that exists for the life of the application.  */
        val sDataModel = DataModel()

        @get:JvmStatic
        @get:Keep
        val dataModel
            get() = sDataModel
    }
}