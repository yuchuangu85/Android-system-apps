/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.wallpaper.module;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * Schedules and cancels alarms on Android's {@link AlarmManager} to occur every 24 hours to perform
 * daily logging.
 */
public class DailyLoggingAlarmScheduler {

    private static final int UNUSED_REQUEST_CODE = 0;
    private static final int UNUSED_REQUEST_FLAGS = 0;

    /**
     * Sets a new alarm to fire approximately 24 hours after the last one, or immediately if it has
     * not fired in over 24 hours.
     */
    public static void setAlarm(Context appContext) {
        Injector injector = InjectorProvider.getInjector();
        AlarmManagerWrapper alarmManagerWrapper = injector.getAlarmManagerWrapper(appContext);
        WallpaperPreferences preferences = injector.getPreferences(appContext);

        long lastTimestamp = preferences.getLastDailyLogTimestamp();

        Calendar oneDayAgo = Calendar.getInstance();
        long currentTimeMillis = System.currentTimeMillis();
        oneDayAgo.setTimeInMillis(currentTimeMillis);
        oneDayAgo.add(Calendar.DAY_OF_YEAR, -1);

        long triggerAtMillis;
        if (lastTimestamp == -1 || lastTimestamp < oneDayAgo.getTimeInMillis()) {
            // Schedule for right now (a minute from now, to ensure the trigger time is in the future) and
            // then every ~24 hours later.
            Calendar oneMinuteFromNow = Calendar.getInstance();
            oneMinuteFromNow.setTimeInMillis(currentTimeMillis);
            oneMinuteFromNow.add(Calendar.MINUTE, 1);
            triggerAtMillis = oneMinuteFromNow.getTimeInMillis();
        } else {
            // Schedule for 24 hours after the last daily log, and every ~24 hours after that.
            Calendar oneDayFromNow = Calendar.getInstance();
            oneDayFromNow.setTimeInMillis(lastTimestamp);
            oneDayFromNow.add(Calendar.DAY_OF_YEAR, 1);
            triggerAtMillis = oneDayFromNow.getTimeInMillis();
        }

        // Cancel any existing daily logging alarms. Then do the actual scheduling of the new alarm.
        PendingIntent pendingIntent = createAlarmReceiverPendingIntent(appContext);
        alarmManagerWrapper.cancel(pendingIntent);

        pendingIntent = createAlarmReceiverPendingIntent(appContext);
        alarmManagerWrapper.setInexactRepeating(
                AlarmManager.RTC /* type */,
                triggerAtMillis /* triggerAtMillis */,
                TimeUnit.MILLISECONDS.convert(24, TimeUnit.HOURS) /* intervalMillis */,
                pendingIntent /* operation */);
    }

    private static PendingIntent createAlarmReceiverPendingIntent(Context appContext) {
        Intent intent = new Intent(appContext, DailyLoggingAlarmReceiver.class);
        return PendingIntent.getBroadcast(
                appContext, UNUSED_REQUEST_CODE, intent, UNUSED_REQUEST_FLAGS);
    }

}
