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

import android.annotation.TargetApi
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.os.SystemClock
import android.text.TextUtils
import android.text.format.DateUtils.MINUTE_IN_MILLIS
import android.text.format.DateUtils.SECOND_IN_MILLIS
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.Action
import androidx.core.app.NotificationCompat.Builder
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

import com.android.deskclock.AlarmUtils
import com.android.deskclock.R
import com.android.deskclock.Utils
import com.android.deskclock.events.Events
import com.android.deskclock.timer.ExpiredTimersActivity
import com.android.deskclock.timer.TimerService

/**
 * Builds notifications to reflect the latest state of the timers.
 */
internal class TimerNotificationBuilder {

    fun buildChannel(context: Context, notificationManager: NotificationManagerCompat) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                    TIMER_MODEL_NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.default_label),
                    NotificationManagerCompat.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun build(context: Context, nm: NotificationModel, unexpired: List<Timer>): Notification {
        val timer = unexpired[0]
        val count = unexpired.size

        // Compute some values required below.
        val running = timer.isRunning
        val res: Resources = context.getResources()

        val base = getChronometerBase(timer)
        val pname: String = context.getPackageName()

        val actions: MutableList<Action> = ArrayList<Action>(2)

        val stateText: CharSequence
        if (count == 1) {
            if (running) {
                // Single timer is running.
                stateText = if (timer.label.isNullOrEmpty()) {
                    res.getString(R.string.timer_notification_label)
                } else {
                    timer.label
                }

                // Left button: Pause
                val pause: Intent = Intent(context, TimerService::class.java)
                        .setAction(TimerService.ACTION_PAUSE_TIMER)
                        .putExtra(TimerService.EXTRA_TIMER_ID, timer.id)

                @DrawableRes val icon1: Int = R.drawable.ic_pause_24dp
                val title1: CharSequence = res.getText(R.string.timer_pause)
                val intent1: PendingIntent = Utils.pendingServiceIntent(context, pause)
                actions.add(Action.Builder(icon1, title1, intent1).build())

                // Right Button: +1 Minute
                val addMinute: Intent = Intent(context, TimerService::class.java)
                        .setAction(TimerService.ACTION_ADD_MINUTE_TIMER)
                        .putExtra(TimerService.EXTRA_TIMER_ID, timer.id)

                @DrawableRes val icon2: Int = R.drawable.ic_add_24dp
                val title2: CharSequence = res.getText(R.string.timer_plus_1_min)
                val intent2: PendingIntent = Utils.pendingServiceIntent(context, addMinute)
                actions.add(Action.Builder(icon2, title2, intent2).build())
            } else {
                // Single timer is paused.
                stateText = res.getString(R.string.timer_paused)

                // Left button: Start
                val start: Intent = Intent(context, TimerService::class.java)
                        .setAction(TimerService.ACTION_START_TIMER)
                        .putExtra(TimerService.EXTRA_TIMER_ID, timer.id)

                @DrawableRes val icon1: Int = R.drawable.ic_start_24dp
                val title1: CharSequence = res.getText(R.string.sw_resume_button)
                val intent1: PendingIntent = Utils.pendingServiceIntent(context, start)
                actions.add(Action.Builder(icon1, title1, intent1).build())

                // Right Button: Reset
                val reset: Intent = Intent(context, TimerService::class.java)
                        .setAction(TimerService.ACTION_RESET_TIMER)
                        .putExtra(TimerService.EXTRA_TIMER_ID, timer.id)

                @DrawableRes val icon2: Int = R.drawable.ic_reset_24dp
                val title2: CharSequence = res.getText(R.string.sw_reset_button)
                val intent2: PendingIntent = Utils.pendingServiceIntent(context, reset)
                actions.add(Action.Builder(icon2, title2, intent2).build())
            }
        } else {
            stateText = if (running) {
                // At least one timer is running.
                res.getString(R.string.timers_in_use, count)
            } else {
                // All timers are paused.
                res.getString(R.string.timers_stopped, count)
            }

            val reset: Intent = TimerService.createResetUnexpiredTimersIntent(context)

            @DrawableRes val icon1: Int = R.drawable.ic_reset_24dp
            val title1: CharSequence = res.getText(R.string.timer_reset_all)
            val intent1: PendingIntent = Utils.pendingServiceIntent(context, reset)
            actions.add(Action.Builder(icon1, title1, intent1).build())
        }

        // Intent to load the app and show the timer when the notification is tapped.
        val showApp: Intent = Intent(context, TimerService::class.java)
                .setAction(TimerService.ACTION_SHOW_TIMER)
                .putExtra(TimerService.EXTRA_TIMER_ID, timer.id)
                .putExtra(Events.EXTRA_EVENT_LABEL, R.string.label_notification)

        val pendingShowApp: PendingIntent =
                PendingIntent.getService(context, REQUEST_CODE_UPCOMING, showApp,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification: Builder = Builder(
                context, TIMER_MODEL_NOTIFICATION_CHANNEL_ID)
                .setOngoing(true)
                .setLocalOnly(true)
                .setShowWhen(false)
                .setAutoCancel(false)
                .setContentIntent(pendingShowApp)
                .setPriority(NotificationManager.IMPORTANCE_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setSmallIcon(R.drawable.stat_notify_timer)
                .setSortKey(nm.timerNotificationSortKey)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setColor(ContextCompat.getColor(context, R.color.default_background))

        for (action in actions) {
            notification.addAction(action)
        }

        if (Utils.isNOrLater) {
            notification.setCustomContentView(buildChronometer(pname, base, running, stateText))
                    .setGroup(nm.timerNotificationGroupKey)
        } else {
            val contentTextPreN: CharSequence?
            contentTextPreN = when {
                count == 1 -> {
                    TimerStringFormatter.formatTimeRemaining(context, timer.remainingTime, false)
                }
                running -> {
                    val timeRemaining = TimerStringFormatter.formatTimeRemaining(context,
                            timer.remainingTime, false)
                    context.getString(R.string.next_timer_notif, timeRemaining)
                }
                else -> context.getString(R.string.all_timers_stopped_notif)
            }

            notification.setContentTitle(stateText).setContentText(contentTextPreN)

            val am: AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val updateNotification: Intent = TimerService.createUpdateNotificationIntent(context)
            val remainingTime = timer.remainingTime
            if (timer.isRunning && remainingTime > MINUTE_IN_MILLIS) {
                // Schedule a callback to update the time-sensitive information of the running timer
                val pi: PendingIntent =
                        PendingIntent.getService(context, REQUEST_CODE_UPCOMING, updateNotification,
                        PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT)

                val nextMinuteChange: Long = remainingTime % MINUTE_IN_MILLIS
                val triggerTime: Long = SystemClock.elapsedRealtime() + nextMinuteChange
                TimerModel.schedulePendingIntent(am, triggerTime, pi)
            } else {
                // Cancel the update notification callback.
                val pi: PendingIntent? = PendingIntent.getService(context, 0, updateNotification,
                        PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_NO_CREATE)
                if (pi != null) {
                    am.cancel(pi)
                    pi.cancel()
                }
            }
        }
        return notification.build()
    }

    fun buildHeadsUp(context: Context, expired: List<Timer>): Notification {
        val timer = expired[0]

        // First action intent is to reset all timers.
        @DrawableRes val icon1: Int = R.drawable.ic_stop_24dp
        val reset: Intent = TimerService.createResetExpiredTimersIntent(context)
        val intent1: PendingIntent = Utils.pendingServiceIntent(context, reset)

        // Generate some descriptive text, a title, and an action name based on the timer count.
        val stateText: CharSequence
        val count = expired.size
        val actions: MutableList<Action> = ArrayList<Action>(2)
        if (count == 1) {
            val label = timer.label
            stateText = if (label.isNullOrEmpty()) {
                context.getString(R.string.timer_times_up)
            } else {
                label
            }

            // Left button: Reset single timer
            val title1: CharSequence = context.getString(R.string.timer_stop)
            actions.add(Action.Builder(icon1, title1, intent1).build())

            // Right button: Add minute
            val addTime: Intent = TimerService.createAddMinuteTimerIntent(context, timer.id)
            val intent2: PendingIntent = Utils.pendingServiceIntent(context, addTime)
            @DrawableRes val icon2: Int = R.drawable.ic_add_24dp
            val title2: CharSequence = context.getString(R.string.timer_plus_1_min)
            actions.add(Action.Builder(icon2, title2, intent2).build())
        } else {
            stateText = context.getString(R.string.timer_multi_times_up, count)

            // Left button: Reset all timers
            val title1: CharSequence = context.getString(R.string.timer_stop_all)
            actions.add(Action.Builder(icon1, title1, intent1).build())
        }

        val base = getChronometerBase(timer)

        val pname: String = context.getPackageName()

        // Content intent shows the timer full screen when clicked.
        val content = Intent(context, ExpiredTimersActivity::class.java)
        val contentIntent: PendingIntent = Utils.pendingActivityIntent(context, content)

        // Full screen intent has flags so it is different than the content intent.
        val fullScreen: Intent = Intent(context, ExpiredTimersActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        val pendingFullScreen: PendingIntent = Utils.pendingActivityIntent(context, fullScreen)

        val notification: Builder = Builder(
                context, TIMER_MODEL_NOTIFICATION_CHANNEL_ID)
                .setOngoing(true)
                .setLocalOnly(true)
                .setShowWhen(false)
                .setAutoCancel(false)
                .setContentIntent(contentIntent)
                .setPriority(NotificationManager.IMPORTANCE_HIGH)
                .setDefaults(Notification.DEFAULT_LIGHTS)
                .setSmallIcon(R.drawable.stat_notify_timer)
                .setFullScreenIntent(pendingFullScreen, true)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setColor(ContextCompat.getColor(context, R.color.default_background))

        for (action in actions) {
            notification.addAction(action)
        }

        if (Utils.isNOrLater) {
            notification.setCustomContentView(buildChronometer(pname, base, true, stateText))
        } else {
            val contentTextPreN: CharSequence = if (count == 1) {
                context.getString(R.string.timer_times_up)
            } else {
                context.getString(R.string.timer_multi_times_up, count)
            }
            notification.setContentTitle(stateText).setContentText(contentTextPreN)
        }

        return notification.build()
    }

    fun buildMissed(
        context: Context,
        nm: NotificationModel,
        missedTimers: List<Timer>
    ): Notification {
        val timer = missedTimers[0]
        val count = missedTimers.size

        // Compute some values required below.
        val base = getChronometerBase(timer)
        val pname: String = context.getPackageName()
        val res: Resources = context.getResources()

        val action: Action

        val stateText: CharSequence
        if (count == 1) {
            // Single timer is missed.
            stateText = if (TextUtils.isEmpty(timer.label)) {
                res.getString(R.string.missed_timer_notification_label)
            } else {
                res.getString(R.string.missed_named_timer_notification_label,
                        timer.label)
            }

            // Reset button
            val reset: Intent = Intent(context, TimerService::class.java)
                    .setAction(TimerService.ACTION_RESET_TIMER)
                    .putExtra(TimerService.EXTRA_TIMER_ID, timer.id)

            @DrawableRes val icon1: Int = R.drawable.ic_reset_24dp
            val title1: CharSequence = res.getText(R.string.timer_reset)
            val intent1: PendingIntent = Utils.pendingServiceIntent(context, reset)
            action = Action.Builder(icon1, title1, intent1).build()
        } else {
            // Multiple missed timers.
            stateText = res.getString(R.string.timer_multi_missed, count)

            val reset: Intent = TimerService.createResetMissedTimersIntent(context)

            @DrawableRes val icon1: Int = R.drawable.ic_reset_24dp
            val title1: CharSequence = res.getText(R.string.timer_reset_all)
            val intent1: PendingIntent = Utils.pendingServiceIntent(context, reset)
            action = Action.Builder(icon1, title1, intent1).build()
        }

        // Intent to load the app and show the timer when the notification is tapped.
        val showApp: Intent = Intent(context, TimerService::class.java)
                .setAction(TimerService.ACTION_SHOW_TIMER)
                .putExtra(TimerService.EXTRA_TIMER_ID, timer.id)
                .putExtra(Events.EXTRA_EVENT_LABEL, R.string.label_notification)

        val pendingShowApp: PendingIntent =
                PendingIntent.getService(context, REQUEST_CODE_MISSING, showApp,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification: Builder = Builder(
                context, TIMER_MODEL_NOTIFICATION_CHANNEL_ID)
                .setLocalOnly(true)
                .setShowWhen(false)
                .setAutoCancel(false)
                .setContentIntent(pendingShowApp)
                .setPriority(NotificationManager.IMPORTANCE_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setSmallIcon(R.drawable.stat_notify_timer)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSortKey(nm.timerNotificationMissedSortKey)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .addAction(action)
                .setColor(ContextCompat.getColor(context, R.color.default_background))

        if (Utils.isNOrLater) {
            notification.setCustomContentView(buildChronometer(pname, base, true, stateText))
                    .setGroup(nm.timerNotificationGroupKey)
        } else {
            val contentText: CharSequence = AlarmUtils.getFormattedTime(context,
                    timer.wallClockExpirationTime)
            notification.setContentText(contentText).setContentTitle(stateText)
        }

        return notification.build()
    }

    @TargetApi(Build.VERSION_CODES.N)
    private fun buildChronometer(
        pname: String,
        base: Long,
        running: Boolean,
        stateText: CharSequence
    ): RemoteViews {
        val content = RemoteViews(pname, R.layout.chronometer_notif_content)
        content.setChronometerCountDown(R.id.chronometer, true)
        content.setChronometer(R.id.chronometer, base, null, running)
        content.setTextViewText(R.id.state, stateText)
        return content
    }

    companion object {
        /**
         * Notification channel containing all TimerModel notifications.
         */
        private const val TIMER_MODEL_NOTIFICATION_CHANNEL_ID = "TimerModelNotification"

        private const val REQUEST_CODE_UPCOMING = 0
        private const val REQUEST_CODE_MISSING = 1

        /**
         * @param timer the timer on which to base the chronometer display
         * @return the time at which the chronometer will/did reach 0:00 in realtime
         */
        private fun getChronometerBase(timer: Timer): Long {
            // The in-app timer display rounds *up* to the next second for positive timer values.
            // Mirror that behavior in the notification's Chronometer by padding in an extra second
            // as needed.
            val remaining = timer.remainingTime
            val adjustedRemaining = if (remaining < 0) remaining else remaining + SECOND_IN_MILLIS

            // Chronometer will/did reach 0:00 adjustedRemaining milliseconds from now.
            return SystemClock.elapsedRealtime() + adjustedRemaining
        }
    }
}