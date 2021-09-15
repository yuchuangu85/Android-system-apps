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
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Parcelable
import android.provider.AlarmClock
import android.text.TextUtils
import android.text.format.DateFormat
import android.text.format.DateUtils

import com.android.deskclock.AlarmUtils.popAlarmSetToast
import com.android.deskclock.alarms.AlarmStateManager
import com.android.deskclock.controller.Controller
import com.android.deskclock.data.DataModel
import com.android.deskclock.data.Timer
import com.android.deskclock.data.Weekdays
import com.android.deskclock.events.Events
import com.android.deskclock.provider.Alarm
import com.android.deskclock.provider.AlarmInstance
import com.android.deskclock.provider.ClockContract
import com.android.deskclock.provider.ClockContract.AlarmSettingColumns
import com.android.deskclock.provider.ClockContract.AlarmsColumns
import com.android.deskclock.timer.TimerFragment
import com.android.deskclock.timer.TimerService
import com.android.deskclock.uidata.UiDataModel

import java.util.Calendar
import java.util.Date

/**
 * This activity is never visible. It processes all public intents defined by [AlarmClock]
 * that apply to alarms and timers. Its definition in AndroidManifest.xml requires callers to hold
 * the com.android.alarm.permission.SET_ALARM permission to complete the requested action.
 */
// TODO(b/165664115) Replace deprecated AsyncTask calls
class HandleApiCalls : Activity() {
    private lateinit var mAppContext: Context

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        mAppContext = applicationContext

        try {
            val intent = intent
            val action = intent?.action ?: return
            LOGGER.i("onCreate: $intent")

            when (action) {
                AlarmClock.ACTION_SET_ALARM -> handleSetAlarm(intent)
                AlarmClock.ACTION_SHOW_ALARMS -> handleShowAlarms()
                AlarmClock.ACTION_SET_TIMER -> handleSetTimer(intent)
                AlarmClock.ACTION_SHOW_TIMERS -> handleShowTimers(intent)
                AlarmClock.ACTION_DISMISS_ALARM -> handleDismissAlarm(intent)
                AlarmClock.ACTION_SNOOZE_ALARM -> handleSnoozeAlarm(intent)
                AlarmClock.ACTION_DISMISS_TIMER -> handleDismissTimer(intent)
            }
        } catch (e: Exception) {
            LOGGER.wtf(e)
        } finally {
            finish()
        }
    }

    private fun handleDismissAlarm(intent: Intent) {
        // Change to the alarms tab.
        UiDataModel.uiDataModel.selectedTab = UiDataModel.Tab.ALARMS

        // Open DeskClock which is now positioned on the alarms tab.
        startActivity(Intent(mAppContext, DeskClock::class.java))

        DismissAlarmAsync(mAppContext, intent, this).execute()
    }

    private class DismissAlarmAsync(
        private val mContext: Context,
        private val mIntent: Intent,
        private val mActivity: Activity
    ) : AsyncTask<Void?, Void?, Void?>() {
        override fun doInBackground(vararg parameters: Void?): Void? {
            val cr = mContext.contentResolver
            val alarms = getEnabledAlarms(mContext)
            if (alarms.isEmpty()) {
                val reason = mContext.getString(R.string.no_scheduled_alarms)
                Controller.getController().notifyVoiceFailure(mActivity, reason)
                LOGGER.i("No scheduled alarms")
                return null
            }

            // remove Alarms in MISSED, DISMISSED, and PREDISMISSED states
            val i: MutableIterator<Alarm> = alarms.toMutableList().listIterator()
            while (i.hasNext()) {
                val instance = AlarmInstance.getNextUpcomingInstanceByAlarmId(cr, i.next().id)
                if (instance == null ||
                        instance.mAlarmState > ClockContract.InstancesColumns.FIRED_STATE) {
                    i.remove()
                }
            }

            val searchMode = mIntent.getStringExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE)
            if (searchMode == null && alarms.size > 1) {
                // shows the UI where user picks which alarm they want to DISMISS
                val pickSelectionIntent = Intent(mContext,
                        AlarmSelectionActivity::class.java)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(AlarmSelectionActivity.EXTRA_ACTION,
                                AlarmSelectionActivity.ACTION_DISMISS)
                        .putExtra(AlarmSelectionActivity.EXTRA_ALARMS,
                                alarms.toTypedArray<Parcelable>())
                mContext.startActivity(pickSelectionIntent)
                val voiceMessage = mContext.getString(R.string.pick_alarm_to_dismiss)
                Controller.getController().notifyVoiceSuccess(mActivity, voiceMessage)
                return null
            }

            // fetch the alarms that are specified by the intent
            val fmaa = FetchMatchingAlarmsAction(mContext, alarms, mIntent, mActivity)
            fmaa.run()
            val matchingAlarms: List<Alarm> = fmaa.matchingAlarms

            // If there are multiple matching alarms and it wasn't expected
            // disambiguate what the user meant
            if (AlarmClock.ALARM_SEARCH_MODE_ALL != searchMode && matchingAlarms.size > 1) {
                val pickSelectionIntent = Intent(mContext, AlarmSelectionActivity::class.java)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(AlarmSelectionActivity.EXTRA_ACTION,
                                AlarmSelectionActivity.ACTION_DISMISS)
                        .putExtra(AlarmSelectionActivity.EXTRA_ALARMS,
                                matchingAlarms.toTypedArray<Parcelable>())
                mContext.startActivity(pickSelectionIntent)
                val voiceMessage = mContext.getString(R.string.pick_alarm_to_dismiss)
                Controller.getController().notifyVoiceSuccess(mActivity, voiceMessage)
                return null
            }

            // Apply the action to the matching alarms
            for (alarm in matchingAlarms) {
                dismissAlarm(alarm, mActivity)
                LOGGER.i("Alarm dismissed: $alarm")
            }
            return null
        }

        companion object {
            private fun getEnabledAlarms(context: Context): List<Alarm> {
                val selection = String.format("%s=?", AlarmsColumns.ENABLED)
                val args = arrayOf("1")
                return Alarm.getAlarms(context.contentResolver, selection, *args)
            }
        }
    }

    private fun handleSnoozeAlarm(intent: Intent) {
        SnoozeAlarmAsync(intent, this).execute()
    }

    private class SnoozeAlarmAsync(
        private val mIntent: Intent,
        private val mActivity: Activity
    ) : AsyncTask<Void?, Void?, Void?>() {
        private val mContext: Context = mActivity.applicationContext

        override fun doInBackground(vararg parameters: Void?): Void? {
            val cr = mContext.contentResolver
            val alarmInstances = AlarmInstance.getInstancesByState(
                    cr, ClockContract.InstancesColumns.FIRED_STATE)
            if (alarmInstances.isEmpty()) {
                val reason = mContext.getString(R.string.no_firing_alarms)
                Controller.getController().notifyVoiceFailure(mActivity, reason)
                LOGGER.i("No firing alarms")
                return null
            }

            for (firingAlarmInstance in alarmInstances) {
                snoozeAlarm(firingAlarmInstance, mContext, mActivity)
            }
            return null
        }
    }

    /**
     * Processes the SET_ALARM intent
     * @param intent Intent passed to the app
     */
    private fun handleSetAlarm(intent: Intent) {
        // Validate the hour, if one was given.
        var hour = -1
        if (intent.hasExtra(AlarmClock.EXTRA_HOUR)) {
            hour = intent.getIntExtra(AlarmClock.EXTRA_HOUR, hour)
            if (hour < 0 || hour > 23) {
                val mins = intent.getIntExtra(AlarmClock.EXTRA_MINUTES, 0)
                val voiceMessage = getString(R.string.invalid_time, hour, mins, " ")
                Controller.getController().notifyVoiceFailure(this, voiceMessage)
                LOGGER.i("Illegal hour: $hour")
                return
            }
        }

        // Validate the minute, if one was given.
        val minutes = intent.getIntExtra(AlarmClock.EXTRA_MINUTES, 0)
        if (minutes < 0 || minutes > 59) {
            val voiceMessage = getString(R.string.invalid_time, hour, minutes, " ")
            Controller.getController().notifyVoiceFailure(this, voiceMessage)
            LOGGER.i("Illegal minute: $minutes")
            return
        }

        val skipUi = intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false)
        val cr = contentResolver

        // If time information was not provided an existing alarm cannot be located and a new one
        // cannot be created so show the UI for creating the alarm from scratch per spec.
        if (hour == -1) {
            // Change to the alarms tab.
            UiDataModel.uiDataModel.selectedTab = UiDataModel.Tab.ALARMS

            // Intent has no time or an invalid time, open the alarm creation UI.
            val createAlarm = Alarm.createIntent(this, DeskClock::class.java, Alarm.INVALID_ID)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(AlarmClockFragment.ALARM_CREATE_NEW_INTENT_EXTRA, true)

            // Open DeskClock which is now positioned on the alarms tab.
            startActivity(createAlarm)
            val voiceMessage = getString(R.string.invalid_time, hour, minutes, " ")
            Controller.getController().notifyVoiceFailure(this, voiceMessage)
            LOGGER.i("Missing alarm time; opening UI")
            return
        }

        val selection = StringBuilder()
        val argsList: MutableList<String> = ArrayList()
        setSelectionFromIntent(intent, hour, minutes, selection, argsList)

        // Try to locate an existing alarm using the intent data.
        val args = argsList.toTypedArray()
        val alarms = Alarm.getAlarms(cr, selection.toString(), *args)

        val alarm: Alarm
        if (alarms.isNotEmpty()) {
            // Enable the first matching alarm.
            alarm = alarms[0]
            alarm.enabled = true
            Alarm.updateAlarm(cr, alarm)

            // Delete all old instances.
            AlarmStateManager.deleteAllInstances(this, alarm.id)

            Events.sendAlarmEvent(R.string.action_update, R.string.label_intent)
            LOGGER.i("Updated alarm: $alarm")
        } else {
            // No existing alarm could be located; create one using the intent data.
            alarm = Alarm()
            updateAlarmFromIntent(alarm, intent)
            alarm.deleteAfterUse = !alarm.daysOfWeek.isRepeating && skipUi

            // Save the new alarm.
            Alarm.addAlarm(cr, alarm)

            Events.sendAlarmEvent(R.string.action_create, R.string.label_intent)
            LOGGER.i("Created new alarm: $alarm")
        }

        // Schedule the next instance.
        val now: Calendar = DataModel.dataModel.calendar
        val alarmInstance = alarm.createInstanceAfter(now)
        setupInstance(alarmInstance, skipUi)

        val time = DateFormat.getTimeFormat(this).format(alarmInstance.alarmTime.time)
        Controller.getController().notifyVoiceSuccess(this, getString(R.string.alarm_is_set, time))
    }

    private fun handleDismissTimer(intent: Intent) {
        val dataUri = intent.data
        if (dataUri != null) {
            val selectedTimer = getSelectedTimer(dataUri)
            if (selectedTimer != null) {
                DataModel.dataModel.resetOrDeleteTimer(selectedTimer, R.string.label_intent)
                Controller.getController().notifyVoiceSuccess(this,
                        resources.getQuantityString(R.plurals.expired_timers_dismissed, 1))
                LOGGER.i("Timer dismissed: $selectedTimer")
            } else {
                Controller.getController().notifyVoiceFailure(this,
                        getString(R.string.invalid_timer))
                LOGGER.e("Could not dismiss timer: invalid URI")
            }
        } else {
            val expiredTimers: List<Timer> = DataModel.dataModel.expiredTimers
            if (expiredTimers.isNotEmpty()) {
                for (timer in expiredTimers) {
                    DataModel.dataModel.resetOrDeleteTimer(timer, R.string.label_intent)
                }
                val numberOfTimers = expiredTimers.size
                val timersDismissedMessage = resources.getQuantityString(
                        R.plurals.expired_timers_dismissed, numberOfTimers, numberOfTimers)
                Controller.getController().notifyVoiceSuccess(this, timersDismissedMessage)
                LOGGER.i(timersDismissedMessage)
            } else {
                Controller.getController().notifyVoiceFailure(this,
                        getString(R.string.no_expired_timers))
                LOGGER.e("Could not dismiss timer: no expired timers")
            }
        }
    }

    private fun getSelectedTimer(dataUri: Uri): Timer? {
        return try {
            val timerId = ContentUris.parseId(dataUri).toInt()
            DataModel.dataModel.getTimer(timerId)
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun handleShowAlarms() {
        Events.sendAlarmEvent(R.string.action_show, R.string.label_intent)

        // Open DeskClock positioned on the alarms tab.
        UiDataModel.uiDataModel.selectedTab = UiDataModel.Tab.ALARMS
        startActivity(Intent(this, DeskClock::class.java))
    }

    private fun handleShowTimers(intent: Intent) {
        Events.sendTimerEvent(R.string.action_show, R.string.label_intent)

        val showTimersIntent = Intent(this, DeskClock::class.java)

        val timers: List<Timer> = DataModel.dataModel.timers
        if (timers.isNotEmpty()) {
            val newestTimer = timers[timers.size - 1]
            showTimersIntent.putExtra(TimerService.EXTRA_TIMER_ID, newestTimer.id)
        }

        // Open DeskClock positioned on the timers tab.
        UiDataModel.uiDataModel.selectedTab = UiDataModel.Tab.TIMERS
        startActivity(showTimersIntent)
    }

    private fun handleSetTimer(intent: Intent) {
        // If no length is supplied, show the timer setup view.
        if (!intent.hasExtra(AlarmClock.EXTRA_LENGTH)) {
            // Change to the timers tab.
            UiDataModel.uiDataModel.selectedTab = UiDataModel.Tab.TIMERS

            // Open DeskClock which is now positioned on the timers tab and show the timer setup.
            startActivity(TimerFragment.createTimerSetupIntent(this))
            LOGGER.i("Showing timer setup")
            return
        }

        // Verify that the timer length is between one second and one day.
        val lengthMillis =
                DateUtils.SECOND_IN_MILLIS * intent.getIntExtra(AlarmClock.EXTRA_LENGTH, 0)
        if (lengthMillis < Timer.MIN_LENGTH) {
            val voiceMessage = getString(R.string.invalid_timer_length)
            Controller.getController().notifyVoiceFailure(this, voiceMessage)
            LOGGER.i("Invalid timer length requested: $lengthMillis")
            return
        }

        val label = getLabelFromIntent(intent, "")
        val skipUi = intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false)

        // Attempt to reuse an existing timer that is Reset with the same length and label.
        var timer: Timer? = null
        for (t in DataModel.dataModel.timers) {
            if (!t.isReset) {
                continue
            }
            if (t.length != lengthMillis) {
                continue
            }
            if (!TextUtils.equals(label, t.label)) {
                continue
            }

            timer = t
            break
        }

        // Create a new timer if one could not be reused.
        if (timer == null) {
            timer = DataModel.dataModel.addTimer(lengthMillis, label, skipUi)
            Events.sendTimerEvent(R.string.action_create, R.string.label_intent)
        }

        // Start the selected timer.
        DataModel.dataModel.startTimer(timer)
        Events.sendTimerEvent(R.string.action_start, R.string.label_intent)
        Controller.getController().notifyVoiceSuccess(this, getString(R.string.timer_created))

        // If not instructed to skip the UI, display the running timer.
        if (!skipUi) {
            // Change to the timers tab.
            UiDataModel.uiDataModel.selectedTab = UiDataModel.Tab.TIMERS

            // Open DeskClock which is now positioned on the timers tab.
            startActivity(Intent(this, DeskClock::class.java)
                    .putExtra(TimerService.EXTRA_TIMER_ID, timer.id))
        }
    }

    private fun setupInstance(instance: AlarmInstance, skipUi: Boolean) {
        var variableInstance = instance
        variableInstance = AlarmInstance.addInstance(this.contentResolver, variableInstance)
        AlarmStateManager.registerInstance(this, variableInstance, true)
        popAlarmSetToast(this, variableInstance.alarmTime.timeInMillis)
        if (!skipUi) {
            // Change to the alarms tab.
            UiDataModel.uiDataModel.selectedTab = UiDataModel.Tab.ALARMS

            // Open DeskClock which is now positioned on the alarms tab.
            val showAlarm =
                    Alarm.createIntent(this, DeskClock::class.java, variableInstance.mAlarmId!!)
                    .putExtra(AlarmClockFragment.SCROLL_TO_ALARM_INTENT_EXTRA,
                            variableInstance.mAlarmId!!)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(showAlarm)
        }
    }

    /**
     * Assemble a database where clause to search for an alarm matching the given `hour` and
     * `minutes` as well as all of the optional information within the `intent`
     * including:
     * <ul>
     *     <li>alarm message</li>
     *     <li>repeat days</li>
     *     <li>vibration setting</li>
     *     <li>ringtone uri</li>
     * </ul>
     *
     * @param intent contains details of the alarm to be located
     * @param hour the hour of the day of the alarm
     * @param minutes the minute of the hour of the alarm
     * @param selection an out parameter containing a SQL where clause
     * @param args an out parameter containing the values to substitute into the `selection`
     */
    private fun setSelectionFromIntent(
        intent: Intent,
        hour: Int,
        minutes: Int,
        selection: StringBuilder,
        args: MutableList<String>
    ) {
        selection.append(AlarmsColumns.HOUR).append("=?")
        args.add(hour.toString())
        selection.append(" AND ").append(AlarmsColumns.MINUTES).append("=?")
        args.add(minutes.toString())
        if (intent.hasExtra(AlarmClock.EXTRA_MESSAGE)) {
            selection.append(" AND ").append(AlarmSettingColumns.LABEL).append("=?")
            args.add(getLabelFromIntent(intent, ""))
        }

        // Days is treated differently than other fields because if days is not specified, it
        // explicitly means "not recurring".
        selection.append(" AND ").append(AlarmsColumns.DAYS_OF_WEEK).append("=?")
        args.add(getDaysFromIntent(intent, Weekdays.NONE).bits.toString())
        if (intent.hasExtra(AlarmClock.EXTRA_VIBRATE)) {
            selection.append(" AND ").append(AlarmSettingColumns.VIBRATE).append("=?")
            args.add(if (intent.getBooleanExtra(AlarmClock.EXTRA_VIBRATE, false)) "1" else "0")
        }
        if (intent.hasExtra(AlarmClock.EXTRA_RINGTONE)) {
            selection.append(" AND ").append(AlarmSettingColumns.RINGTONE).append("=?")

            // If the intent explicitly specified a NULL ringtone, treat it as the default ringtone.
            val defaultRingtone: Uri = DataModel.dataModel.defaultAlarmRingtoneUri
            val ringtone = getAlertFromIntent(intent, defaultRingtone)
            args.add(ringtone.toString())
        }
    }

    companion object {
        private val LOGGER = LogUtils.Logger("HandleApiCalls")

        fun dismissAlarm(alarm: Alarm, activity: Activity) {
            val context = activity.applicationContext
            val instance = AlarmInstance.getNextUpcomingInstanceByAlarmId(
                    context.contentResolver, alarm.id)
            if (instance == null) {
                val reason = context.getString(R.string.no_alarm_scheduled_for_this_time)
                Controller.getController().notifyVoiceFailure(activity, reason)
                LOGGER.i("No alarm instance to dismiss")
                return
            }

            dismissAlarmInstance(instance, activity)
        }

        private fun dismissAlarmInstance(instance: AlarmInstance, activity: Activity) {
            Utils.enforceNotMainLooper()

            val context = activity.applicationContext
            val alarmTime: Date = instance.alarmTime.time
            val time = DateFormat.getTimeFormat(context).format(alarmTime)

            if (instance.mAlarmState == ClockContract.InstancesColumns.FIRED_STATE ||
                    instance.mAlarmState == ClockContract.InstancesColumns.SNOOZE_STATE) {
                // Always dismiss alarms that are fired or snoozed.
                AlarmStateManager.deleteInstanceAndUpdateParent(context, instance)
            } else if (Utils.isAlarmWithin24Hours(instance)) {
                // Upcoming alarms are always predismissed.
                AlarmStateManager.setPreDismissState(context, instance)
            } else {
                // Otherwise the alarm cannot be dismissed at this time.
                val reason = context.getString(
                        R.string.alarm_cant_be_dismissed_still_more_than_24_hours_away, time)
                Controller.getController().notifyVoiceFailure(activity, reason)
                LOGGER.i("Can't dismiss alarm more than 24 hours in advance")
            }

            // Log the successful dismissal.
            val reason = context.getString(R.string.alarm_is_dismissed, time)
            Controller.getController().notifyVoiceSuccess(activity, reason)
            LOGGER.i("Alarm dismissed: $instance")
            Events.sendAlarmEvent(R.string.action_dismiss, R.string.label_intent)
        }

        fun snoozeAlarm(alarmInstance: AlarmInstance, context: Context, activity: Activity) {
            Utils.enforceNotMainLooper()

            val time = DateFormat.getTimeFormat(context).format(
                    alarmInstance.alarmTime.time)
            val reason = context.getString(R.string.alarm_is_snoozed, time)
            AlarmStateManager.setSnoozeState(context, alarmInstance, true)

            Controller.getController().notifyVoiceSuccess(activity, reason)
            LOGGER.i("Alarm snoozed: $alarmInstance")
            Events.sendAlarmEvent(R.string.action_snooze, R.string.label_intent)
        }

        /**
         * @param alarm the alarm to be updated
         * @param intent the intent containing new alarm field values to merge into the `alarm`
         */
        private fun updateAlarmFromIntent(alarm: Alarm, intent: Intent) {
            alarm.enabled = true
            alarm.hour = intent.getIntExtra(AlarmClock.EXTRA_HOUR, alarm.hour)
            alarm.minutes = intent.getIntExtra(AlarmClock.EXTRA_MINUTES, alarm.minutes)
            alarm.vibrate = intent.getBooleanExtra(AlarmClock.EXTRA_VIBRATE, alarm.vibrate)
            alarm.alert = getAlertFromIntent(intent, alarm.alert!!)
            alarm.label = getLabelFromIntent(intent, alarm.label)
            alarm.daysOfWeek = getDaysFromIntent(intent, alarm.daysOfWeek)
        }

        private fun getLabelFromIntent(intent: Intent?, defaultLabel: String?): String {
            val message = intent!!.extras!!.getString(AlarmClock.EXTRA_MESSAGE, defaultLabel)
            return message ?: ""
        }

        private fun getDaysFromIntent(intent: Intent, defaultWeekdays: Weekdays): Weekdays {
            if (!intent.hasExtra(AlarmClock.EXTRA_DAYS)) {
                return defaultWeekdays
            }

            val days: List<Int>? = intent.getIntegerArrayListExtra(AlarmClock.EXTRA_DAYS)
            if (days != null) {
                val daysArray = IntArray(days.size)
                for (i in days.indices) {
                    daysArray[i] = days[i]
                }
                return Weekdays.fromCalendarDays(*daysArray)
            } else {
                // API says to use an ArrayList<Integer> but we allow the user to use a int[] too.
                val daysArray = intent.getIntArrayExtra(AlarmClock.EXTRA_DAYS)
                if (daysArray != null) {
                    return Weekdays.fromCalendarDays(*daysArray)
                }
            }
            return defaultWeekdays
        }

        private fun getAlertFromIntent(intent: Intent, defaultUri: Uri): Uri {
            val alert = intent.getStringExtra(AlarmClock.EXTRA_RINGTONE)
            if (alert == null) {
                return defaultUri
            } else if (AlarmClock.VALUE_RINGTONE_SILENT == alert || alert.isEmpty()) {
                return AlarmSettingColumns.NO_RINGTONE_URI
            }

            return Uri.parse(alert)
        }
    }
}