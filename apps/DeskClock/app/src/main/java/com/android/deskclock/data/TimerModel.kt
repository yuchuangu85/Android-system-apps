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

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.net.Uri
import android.text.format.DateUtils.MINUTE_IN_MILLIS
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat

import com.android.deskclock.AlarmAlertWakeLock
import com.android.deskclock.LogUtils
import com.android.deskclock.R
import com.android.deskclock.Utils
import com.android.deskclock.events.Events
import com.android.deskclock.settings.SettingsActivity
import com.android.deskclock.timer.TimerKlaxon
import com.android.deskclock.timer.TimerService

/**
 * All [Timer] data is accessed via this model.
 */
internal class TimerModel(
    private val mContext: Context,
    private val mPrefs: SharedPreferences,
    /** The model from which settings are fetched.  */
    private val mSettingsModel: SettingsModel,
    /** The model from which ringtone data are fetched.  */
    private val mRingtoneModel: RingtoneModel,
    /** The model from which notification data are fetched.  */
    private val mNotificationModel: NotificationModel
) {
    /** The alarm manager system service that calls back when timers expire.  */
    private val mAlarmManager = mContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Used to create and destroy system notifications related to timers.  */
    private val mNotificationManager = NotificationManagerCompat.from(mContext)

    /** Update timer notification when locale changes.  */
    private val mLocaleChangedReceiver: BroadcastReceiver = LocaleChangedReceiver()

    /**
     * Retain a hard reference to the shared preference observer to prevent it from being garbage
     * collected. See [SharedPreferences.registerOnSharedPreferenceChangeListener] for detail.
     */
    private val mPreferenceListener: OnSharedPreferenceChangeListener = PreferenceListener()

    /** The listeners to notify when a timer is added, updated or removed.  */
    private val mTimerListeners: MutableList<TimerListener> = mutableListOf()

    /** Delegate that builds platform-specific timer notifications.  */
    private val mNotificationBuilder = TimerNotificationBuilder()

    /**
     * The ids of expired timers for which the ringer is ringing. Not all expired timers have their
     * ids in this collection. If a timer was already expired when the app was started its id will
     * be absent from this collection.
     */
    @SuppressLint("NewApi")
    private val mRingingIds: MutableSet<Int> = mutableSetOf()

    /** The uri of the ringtone to play for timers.  */
    private var mTimerRingtoneUri: Uri? = null

    /** The title of the ringtone to play for timers.  */
    private var mTimerRingtoneTitle: String? = null

    /** A mutable copy of the timers.  */
    private var mTimers: MutableList<Timer>? = null

    /** A mutable copy of the expired timers.  */
    private var mExpiredTimers: MutableList<Timer>? = null

    /** A mutable copy of the missed timers.  */
    private var mMissedTimers: MutableList<Timer>? = null

    /**
     * The service that keeps this application in the foreground while a heads-up timer
     * notification is displayed. Marking the service as foreground prevents the operating system
     * from killing this application while expired timers are actively firing.
     */
    private var mService: Service? = null

    init {
        // Clear caches affected by preferences when preferences change.
        mPrefs.registerOnSharedPreferenceChangeListener(mPreferenceListener)

        // Update timer notification when locale changes.
        val localeBroadcastFilter = IntentFilter(Intent.ACTION_LOCALE_CHANGED)
        mContext.registerReceiver(mLocaleChangedReceiver, localeBroadcastFilter)
    }

    /**
     * @param timerListener to be notified when timers are added, updated and removed
     */
    fun addTimerListener(timerListener: TimerListener) {
        mTimerListeners.add(timerListener)
    }

    /**
     * @param timerListener to no longer be notified when timers are added, updated and removed
     */
    fun removeTimerListener(timerListener: TimerListener) {
        mTimerListeners.remove(timerListener)
    }

    /**
     * @return all defined timers in their creation order
     */
    val timers: List<Timer>
        get() = mutableTimers

    /**
     * @return all expired timers in their expiration order
     */
    val expiredTimers: List<Timer>
        get() = mutableExpiredTimers

    /**
     * @return all missed timers in their expiration order
     */
    private val missedTimers: List<Timer>
        get() = mutableMissedTimers

    /**
     * @param timerId identifies the timer to return
     * @return the timer with the given `timerId`
     */
    fun getTimer(timerId: Int): Timer? {
        for (timer in mutableTimers) {
            if (timer.id == timerId) {
                return timer
            }
        }

        return null
    }

    /**
     * @return the timer that last expired and is still expired now; `null` if no timers are
     * expired
     */
    val mostRecentExpiredTimer: Timer?
        get() {
            val timers = mutableExpiredTimers
            return if (timers.isEmpty()) null else timers[timers.size - 1]
        }

    /**
     * @param length the length of the timer in milliseconds
     * @param label describes the purpose of the timer
     * @param deleteAfterUse `true` indicates the timer should be deleted when it is reset
     * @return the newly added timer
     */
    fun addTimer(length: Long, label: String?, deleteAfterUse: Boolean): Timer {
        // Create the timer instance.
        var timer =
                Timer(-1, Timer.State.RESET, length, length, Timer.UNUSED, Timer.UNUSED, length,
                label, deleteAfterUse)

        // Add the timer to permanent storage.
        timer = TimerDAO.addTimer(mPrefs, timer)

        // Add the timer to the cache.
        mutableTimers.add(0, timer)

        // Update the timer notification.
        updateNotification()
        // Heads-Up notification is unaffected by this change

        // Notify listeners of the change.
        for (timerListener in mTimerListeners) {
            timerListener.timerAdded(timer)
        }

        return timer
    }

    /**
     * @param service used to start foreground notifications related to expired timers
     * @param timer the timer to be expired
     */
    fun expireTimer(service: Service?, timer: Timer) {
        if (mService == null) {
            // If this is the first expired timer, retain the service that will be used to start
            // the heads-up notification in the foreground.
            mService = service
        } else if (mService != service) {
            // If this is not the first expired timer, the service should match the one given when
            // the first timer expired.
            LogUtils.wtf("Expected TimerServices to be identical")
        }

        updateTimer(timer.expire())
    }

    /**
     * @param timer an updated timer to store
     */
    fun updateTimer(timer: Timer) {
        val before = doUpdateTimer(timer)

        // Update the notification after updating the timer data.
        updateNotification()

        // If the timer started or stopped being expired, update the heads-up notification.
        if (before.state != timer.state) {
            if (before.isExpired || timer.isExpired) {
                updateHeadsUpNotification()
            }
        }
    }

    /**
     * @param timer an existing timer to be removed
     */
    fun removeTimer(timer: Timer) {
        doRemoveTimer(timer)

        // Update the timer notifications after removing the timer data.
        if (timer.isExpired) {
            updateHeadsUpNotification()
        } else {
            updateNotification()
        }
    }

    /**
     * If the given `timer` is expired and marked for deletion after use then this method
     * removes the timer. The timer is otherwise transitioned to the reset state and continues
     * to exist.
     *
     * @param timer the timer to be reset
     * @param allowDelete `true` if the timer is allowed to be deleted instead of reset
     * (e.g. one use timers)
     * @param eventLabelId the label of the timer event to send; 0 if no event should be sent
     * @return the reset `timer` or `null` if the timer was deleted
     */
    fun resetTimer(timer: Timer, allowDelete: Boolean, @StringRes eventLabelId: Int): Timer? {
        val result = doResetOrDeleteTimer(timer, allowDelete, eventLabelId)

        // Update the notification after updating the timer data.
        when {
            timer.isMissed -> updateMissedNotification()
            timer.isExpired -> updateHeadsUpNotification()
            else -> updateNotification()
        }

        return result
    }

    /**
     * Update timers after system reboot.
     */
    fun updateTimersAfterReboot() {
        for (timer in timers) {
            doUpdateAfterRebootTimer(timer)
        }

        // Update the notifications once after all timers are updated.
        updateNotification()
        updateMissedNotification()
        updateHeadsUpNotification()
    }

    /**
     * Update timers after time set.
     */
    fun updateTimersAfterTimeSet() {
        for (timer in timers) {
            doUpdateAfterTimeSetTimer(timer)
        }

        // Update the notifications once after all timers are updated.
        updateNotification()
        updateMissedNotification()
        updateHeadsUpNotification()
    }

    /**
     * Reset all expired timers. Exactly one parameter should be filled, with preference given to
     * eventLabelId.
     *
     * @param eventLabelId the label of the timer event to send; 0 if no event should be sent
     */
    fun resetOrDeleteExpiredTimers(@StringRes eventLabelId: Int) {
        for (timer in timers) {
            if (timer.isExpired) {
                doResetOrDeleteTimer(timer, true /* allowDelete */, eventLabelId)
            }
        }

        // Update the notifications once after all timers are updated.
        updateHeadsUpNotification()
    }

    /**
     * Reset all missed timers.
     *
     * @param eventLabelId the label of the timer event to send; 0 if no event should be sent
     */
    fun resetMissedTimers(@StringRes eventLabelId: Int) {
        for (timer in timers) {
            if (timer.isMissed) {
                doResetOrDeleteTimer(timer, true /* allowDelete */, eventLabelId)
            }
        }

        // Update the notifications once after all timers are updated.
        updateMissedNotification()
    }

    /**
     * Reset all unexpired timers.
     *
     * @param eventLabelId the label of the timer event to send; 0 if no event should be sent
     */
    fun resetUnexpiredTimers(@StringRes eventLabelId: Int) {
        for (timer in timers) {
            if (timer.isRunning || timer.isPaused) {
                doResetOrDeleteTimer(timer, true /* allowDelete */, eventLabelId)
            }
        }

        // Update the notification once after all timers are updated.
        updateNotification()
        // Heads-Up notification is unaffected by this change
    }

    /**
     * @return the uri of the default ringtone to play for all timers when no user selection exists
     */
    val defaultTimerRingtoneUri: Uri
        get() = mSettingsModel.defaultTimerRingtoneUri

    /**
     * @return `true` iff the ringtone to play for all timers is the silent ringtone
     */
    val isTimerRingtoneSilent: Boolean
        get() = Uri.EMPTY.equals(timerRingtoneUri)

    var timerRingtoneUri: Uri
        /**
         * @return the uri of the ringtone to play for all timers
         */
        get() {
            if (mTimerRingtoneUri == null) {
                mTimerRingtoneUri = mSettingsModel.timerRingtoneUri
            }

            return mTimerRingtoneUri!!
        }
        /**
         * @param uri the uri of the ringtone to play for all timers
         */
        set(uri) {
            mSettingsModel.timerRingtoneUri = uri
        }

    /**
     * @return the title of the ringtone that is played for all timers
     */
    val timerRingtoneTitle: String
        get() {
            if (mTimerRingtoneTitle == null) {
                mTimerRingtoneTitle = if (isTimerRingtoneSilent) {
                    // Special case: no ringtone has a title of "Silent".
                    mContext.getString(R.string.silent_ringtone_title)
                } else {
                    val defaultUri: Uri = defaultTimerRingtoneUri
                    val uri: Uri = timerRingtoneUri
                    if (defaultUri.equals(uri)) {
                        // Special case: default ringtone has a title of "Timer Expired".
                        mContext.getString(R.string.default_timer_ringtone_title)
                    } else {
                        mRingtoneModel.getRingtoneTitle(uri)
                    }
                }
            }

            return mTimerRingtoneTitle!!
        }

    /**
     * @return the duration, in milliseconds, of the crescendo to apply to timer ringtone playback;
     * `0` implies no crescendo should be applied
     */
    val timerCrescendoDuration: Long
        get() = mSettingsModel.timerCrescendoDuration

    var timerVibrate: Boolean
        /**
         * @return `true` if the device vibrates when timers expire
         */
        get() = mSettingsModel.timerVibrate
        /**
         * @param enabled `true` if the device should vibrate when timers expire
         */
        set(enabled) {
            mSettingsModel.timerVibrate = enabled
        }

    private val mutableTimers: MutableList<Timer>
        get() {
            if (mTimers == null) {
                mTimers = TimerDAO.getTimers(mPrefs)
                mTimers!!.sortWith(Timer.ID_COMPARATOR)
            }

            return mTimers!!
        }

    private val mutableExpiredTimers: List<Timer>
        get() {
            if (mExpiredTimers == null) {
                mExpiredTimers = mutableListOf()
                for (timer in mutableTimers) {
                    if (timer.isExpired) {
                        mExpiredTimers!!.add(timer)
                    }
                }
                mExpiredTimers!!.sortWith(Timer.EXPIRY_COMPARATOR)
            }

            return mExpiredTimers!!
        }

    private val mutableMissedTimers: List<Timer>
        get() {
            if (mMissedTimers == null) {
                mMissedTimers = mutableListOf()
                for (timer in mutableTimers) {
                    if (timer.isMissed) {
                        mMissedTimers!!.add(timer)
                    }
                }
                mMissedTimers!!.sortWith(Timer.EXPIRY_COMPARATOR)
            }

            return mMissedTimers!!
        }

    /**
     * This method updates timer data without updating notifications. This is useful in bulk-update
     * scenarios so the notifications are only rebuilt once.
     *
     * @param timer an updated timer to store
     * @return the state of the timer prior to the update
     */
    private fun doUpdateTimer(timer: Timer): Timer {
        // Retrieve the cached form of the timer.
        val timers = mutableTimers
        val index = timers.indexOf(timer)
        val before = timers[index]

        // If no change occurred, ignore this update.
        if (timer === before) {
            return timer
        }

        // Update the timer in permanent storage.
        TimerDAO.updateTimer(mPrefs, timer)

        // Update the timer in the cache.
        val oldTimer = timers.set(index, timer)

        // Clear the cache of expired timers if the timer changed to/from expired.
        if (before.isExpired || timer.isExpired) {
            mExpiredTimers = null
        }
        // Clear the cache of missed timers if the timer changed to/from missed.
        if (before.isMissed || timer.isMissed) {
            mMissedTimers = null
        }

        // Update the timer expiration callback.
        updateAlarmManager()

        // Update the timer ringer.
        updateRinger(before, timer)

        // Notify listeners of the change.
        for (timerListener in mTimerListeners) {
            timerListener.timerUpdated(before, timer)
        }

        return oldTimer
    }

    /**
     * This method removes timer data without updating notifications. This is useful in bulk-remove
     * scenarios so the notifications are only rebuilt once.
     *
     * @param timer an existing timer to be removed
     */
    private fun doRemoveTimer(timer: Timer) {
        // Remove the timer from permanent storage.
        var timerVar = timer
        TimerDAO.removeTimer(mPrefs, timerVar)

        // Remove the timer from the cache.
        val timers: MutableList<Timer> = mutableTimers
        val index = timers.indexOf(timerVar)

        // If the timer cannot be located there is nothing to remove.
        if (index == -1) {
            return
        }
        timerVar = timers.removeAt(index)

        // Clear the cache of expired timers if a new expired timer was added.
        if (timerVar.isExpired) {
            mExpiredTimers = null
        }

        // Clear the cache of missed timers if a new missed timer was added.
        if (timerVar.isMissed) {
            mMissedTimers = null
        }

        // Update the timer expiration callback.
        updateAlarmManager()

        // Update the timer ringer.
        updateRinger(timerVar, null)

        // Notify listeners of the change.
        for (timerListener in mTimerListeners) {
            timerListener.timerRemoved(timerVar)
        }
    }

    /**
     * This method updates/removes timer data without updating notifications. This is useful in
     * bulk-update scenarios so the notifications are only rebuilt once.
     *
     * If the given `timer` is expired and marked for deletion after use then this method
     * removes the timer. The timer is otherwise transitioned to the reset state and continues
     * to exist.
     *
     * @param timer the timer to be reset
     * @param allowDelete `true` if the timer is allowed to be deleted instead of reset
     * (e.g. one use timers)
     * @param eventLabelId the label of the timer event to send; 0 if no event should be sent
     * @return the reset `timer` or `null` if the timer was deleted
     */
    private fun doResetOrDeleteTimer(
        timer: Timer,
        allowDelete: Boolean,
        @StringRes eventLabelId: Int
    ): Timer? {
        if (allowDelete &&
                (timer.isExpired || timer.isMissed) &&
                timer.deleteAfterUse) {
            doRemoveTimer(timer)
            if (eventLabelId != 0) {
                Events.sendTimerEvent(R.string.action_delete, eventLabelId)
            }
            return null
        } else if (!timer.isReset) {
            val reset = timer.reset()
            doUpdateTimer(reset)
            if (eventLabelId != 0) {
                Events.sendTimerEvent(R.string.action_reset, eventLabelId)
            }
            return reset
        }
        return timer
    }

    /**
     * This method updates/removes timer data after a reboot without updating notifications.
     *
     * @param timer the timer to be updated
     */
    private fun doUpdateAfterRebootTimer(timer: Timer) {
        var updated = timer.updateAfterReboot()
        if (updated.remainingTime < MISSED_THRESHOLD && updated.isRunning) {
            updated = updated.miss()
        }
        doUpdateTimer(updated)
    }

    private fun doUpdateAfterTimeSetTimer(timer: Timer) {
        val updated = timer.updateAfterTimeSet()
        doUpdateTimer(updated)
    }

    /**
     * Updates the callback given to this application from the [AlarmManager] that signals the
     * expiration of the next timer. If no timers are currently set to expire (i.e. no running
     * timers exist) then this method clears the expiration callback from AlarmManager.
     */
    private fun updateAlarmManager() {
        // Locate the next firing timer if one exists.
        var nextExpiringTimer: Timer? = null
        for (timer in mutableTimers) {
            if (timer.isRunning) {
                if (nextExpiringTimer == null) {
                    nextExpiringTimer = timer
                } else if (timer.expirationTime < nextExpiringTimer.expirationTime) {
                    nextExpiringTimer = timer
                }
            }
        }

        // Build the intent that signals the timer expiration.
        val intent: Intent = TimerService.createTimerExpiredIntent(mContext, nextExpiringTimer)
        if (nextExpiringTimer == null) {
            // Cancel the existing timer expiration callback.
            val pi: PendingIntent? = PendingIntent.getService(mContext,
                    0, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_NO_CREATE)
            if (pi != null) {
                mAlarmManager.cancel(pi)
                pi.cancel()
            }
        } else {
            // Update the existing timer expiration callback.
            val pi: PendingIntent = PendingIntent.getService(mContext,
                    0, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT)
            schedulePendingIntent(mAlarmManager, nextExpiringTimer.expirationTime, pi)
        }
    }

    /**
     * Starts and stops the ringer for timers if the change to the timer demands it.
     *
     * @param before the state of the timer before the change; `null` indicates added
     * @param after the state of the timer after the change; `null` indicates delete
     */
    private fun updateRinger(before: Timer?, after: Timer?) {
        // Retrieve the states before and after the change.
        val beforeState = before?.state
        val afterState = after?.state

        // If the timer state did not change, the ringer state is unchanged.
        if (beforeState == afterState) {
            return
        }

        // If the timer is the first to expire, start ringing.
        if (afterState == Timer.State.EXPIRED && mRingingIds.add(after.id) &&
                mRingingIds.size == 1) {
            AlarmAlertWakeLock.acquireScreenCpuWakeLock(mContext)
            TimerKlaxon.start(mContext)
        }

        // If the expired timer was the last to reset, stop ringing.
        if (beforeState == Timer.State.EXPIRED && mRingingIds.remove(before.id) &&
                mRingingIds.isEmpty()) {
            TimerKlaxon.stop(mContext)
            AlarmAlertWakeLock.releaseCpuLock()
        }
    }

    /**
     * Updates the notification controlling unexpired timers. This notification is only displayed
     * when the application is not open.
     */
    fun updateNotification() {
        // Notifications should be hidden if the app is open.
        if (mNotificationModel.isApplicationInForeground) {
            mNotificationManager.cancel(mNotificationModel.unexpiredTimerNotificationId)
            return
        }

        // Filter the timers to just include unexpired ones.
        val unexpired: MutableList<Timer> = mutableListOf()
        for (timer in mutableTimers) {
            if (timer.isRunning || timer.isPaused) {
                unexpired.add(timer)
            }
        }

        // If no unexpired timers exist, cancel the notification.
        if (unexpired.isEmpty()) {
            mNotificationManager.cancel(mNotificationModel.unexpiredTimerNotificationId)
            return
        }

        // Sort the unexpired timers to locate the next one scheduled to expire.
        unexpired.sortWith(Timer.EXPIRY_COMPARATOR)

        // Otherwise build and post a notification reflecting the latest unexpired timers.
        val notification: Notification =
                mNotificationBuilder.build(mContext, mNotificationModel, unexpired)
        val notificationId = mNotificationModel.unexpiredTimerNotificationId
        mNotificationBuilder.buildChannel(mContext, mNotificationManager)
        mNotificationManager.notify(notificationId, notification)
    }

    /**
     * Updates the notification controlling missed timers. This notification is only displayed when
     * the application is not open.
     */
    fun updateMissedNotification() {
        // Notifications should be hidden if the app is open.
        if (mNotificationModel.isApplicationInForeground) {
            mNotificationManager.cancel(mNotificationModel.missedTimerNotificationId)
            return
        }

        val missed = missedTimers

        if (missed.isEmpty()) {
            mNotificationManager.cancel(mNotificationModel.missedTimerNotificationId)
            return
        }

        val notification: Notification = mNotificationBuilder.buildMissed(mContext,
                mNotificationModel, missed)
        val notificationId = mNotificationModel.missedTimerNotificationId
        mNotificationManager.notify(notificationId, notification)
    }

    /**
     * Updates the heads-up notification controlling expired timers. This heads-up notification is
     * displayed whether the application is open or not.
     */
    private fun updateHeadsUpNotification() {
        // Nothing can be done with the heads-up notification without a valid service reference.
        if (mService == null) {
            return
        }

        val expired = expiredTimers

        // If no expired timers exist, stop the service (which cancels the foreground notification).
        if (expired.isEmpty()) {
            mService!!.stopSelf()
            mService = null
            return
        }

        // Otherwise build and post a foreground notification reflecting the latest expired timers.
        val notification: Notification = mNotificationBuilder.buildHeadsUp(mContext, expired)
        val notificationId = mNotificationModel.expiredTimerNotificationId
        mService!!.startForeground(notificationId, notification)
    }

    /**
     * Update the timer notification in response to a locale change.
     */
    private inner class LocaleChangedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            mTimerRingtoneTitle = null
            updateNotification()
            updateMissedNotification()
            updateHeadsUpNotification()
        }
    }

    /**
     * This receiver is notified when shared preferences change. Cached information built on
     * preferences must be cleared.
     */
    private inner class PreferenceListener : OnSharedPreferenceChangeListener {
        override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
            when (key) {
                SettingsActivity.KEY_TIMER_RINGTONE -> {
                    mTimerRingtoneUri = null
                    mTimerRingtoneTitle = null
                }
            }
        }
    }

    companion object {
        /**
         * Running timers less than this threshold are left running/expired; greater than this
         * threshold are considered missed.
         */
        private val MISSED_THRESHOLD: Long = -MINUTE_IN_MILLIS

        fun schedulePendingIntent(am: AlarmManager, triggerTime: Long, pi: PendingIntent?) {
            if (Utils.isMOrLater) {
                // Ensure the timer fires even if the device is dozing.
                am.setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP, triggerTime, pi)
            } else {
                am.setExact(ELAPSED_REALTIME_WAKEUP, triggerTime, pi)
            }
        }
    }
}