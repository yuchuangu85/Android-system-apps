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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import com.android.wallpaper.model.WallpaperMetadata;
import com.android.wallpaper.module.WallpaperPreferences.PresentationMode;
import com.android.wallpaper.module.WallpaperRefresher.RefreshListener;
import com.android.wallpaper.util.DiskBasedLogger;

import java.util.Calendar;

import androidx.annotation.Nullable;

/**
 * Performs daily logging operations when alarm is received.
 */
public class DailyLoggingAlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "DailyLoggingAlarm";

    /**
     * Releases the provided WakeLock if and only if it's currently held as to avoid throwing a
     * "WakeLock under-locked" RuntimeException.
     */
    private static void releaseWakeLock(WakeLock wakeLock) {
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Context appContext = context.getApplicationContext();
        Injector injector = InjectorProvider.getInjector();
        UserEventLogger logger = injector.getUserEventLogger(appContext);
        WallpaperPreferences preferences = injector.getPreferences(appContext);

        logger.logNumDailyWallpaperRotationsInLastWeek();
        logger.logNumDailyWallpaperRotationsPreviousDay();
        logger.logWallpaperPresentationMode();

        preferences.setLastDailyLogTimestamp(System.currentTimeMillis());

        logDailyWallpaperRotationStatus(appContext);

        // Clear disk-based logs older than 7 days if they exist.
        DiskBasedLogger.clearOldLogs(appContext);
    }

    /**
     * If daily wallpapers are currently in effect and were enabled more than 24 hours ago, then log
     * the last-known rotation status as reported by the periodic background rotation components
     * (BackdropAlarmReceiver and BackdropRotationTask), or if there wasn't any status update in the
     * last 24 hours then log a "not attempted" status to the UserEventLogger.
     */
    private void logDailyWallpaperRotationStatus(Context appContext) {
        // Acquire a partial wakelock because logging the daily rotation requires doing some work on
        // another thread (via AsyncTask) after #onReceive returns, after which the kernel may power
        // down and prevent our daily rotation log from being sent.
        PowerManager powerManager = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
        final WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wakeLock.acquire(10000 /* timeout */);

        final Injector injector = InjectorProvider.getInjector();

        // First check if rotation is still in effect.
        injector.getWallpaperRefresher(appContext).refresh(new RefreshListener() {
            @Override
            public void onRefreshed(WallpaperMetadata homeWallpaperMetadata,
                                    @Nullable WallpaperMetadata lockWallpaperMetadata,
                                    @PresentationMode int presentationMode) {
                // Don't log or do anything else if presentation mode is not rotating.
                if (presentationMode != WallpaperPreferences.PRESENTATION_MODE_ROTATING) {
                    releaseWakeLock(wakeLock);
                    return;
                }

                WallpaperPreferences preferences = injector.getPreferences(appContext);

                long dailyWallpaperEnabledTimestamp = preferences.getDailyWallpaperEnabledTimestamp();
                // Validate the daily wallpaper enabled timestamp.
                if (dailyWallpaperEnabledTimestamp < 0) {
                    Log.e(TAG, "There's no valid daily wallpaper enabled timestamp");
                    releaseWakeLock(wakeLock);
                    return;
                }

                Calendar midnightYesterday = Calendar.getInstance();
                midnightYesterday.add(Calendar.DAY_OF_MONTH, -1);
                midnightYesterday.set(Calendar.HOUR_OF_DAY, 0);
                midnightYesterday.set(Calendar.MINUTE, 0);

                // Exclude rotations that were put into affect later than midnight yesterday because the
                // background task may not have had a chance to execute yet.
                if (dailyWallpaperEnabledTimestamp > midnightYesterday.getTimeInMillis()) {
                    releaseWakeLock(wakeLock);
                    return;
                }

                try {
                    long lastRotationStatusTimestamp =
                            preferences.getDailyWallpaperLastRotationStatusTimestamp();

                    UserEventLogger logger = injector.getUserEventLogger(appContext);

                    // If a rotation status was reported more recently than midnight yesterday, then log it.
                    // Otherwise, log a "not attempted" rotation status.
                    if (lastRotationStatusTimestamp > midnightYesterday.getTimeInMillis()) {
                        int lastDailyWallpaperRotationStatus =
                                preferences.getDailyWallpaperLastRotationStatus();

                        logger.logDailyWallpaperRotationStatus(lastDailyWallpaperRotationStatus);

                        // If the daily rotation status is "failed", increment the num days failed in
                        // SharedPreferences and log it, otherwise reset the counter in SharedPreferences to 0.
                        if (UserEventLogger.ROTATION_STATUS_FAILED == lastDailyWallpaperRotationStatus) {
                            preferences.incrementNumDaysDailyRotationFailed();
                            logger.logNumDaysDailyRotationFailed(preferences.getNumDaysDailyRotationFailed());
                        } else {
                            preferences.resetNumDaysDailyRotationFailed();
                        }

                        // If there was a valid rotation status reported since midnight yesterday, then reset
                        // the counter for consecutive days of "not attempted".
                        preferences.resetNumDaysDailyRotationNotAttempted();
                    } else {
                        logger.logDailyWallpaperRotationStatus(UserEventLogger.ROTATION_STATUS_NOT_ATTEMPTED);

                        // Increment and log the consecutive # days in a row that daily rotation was not
                        // attempted.
                        preferences.incrementNumDaysDailyRotationNotAttempted();
                        logger.logNumDaysDailyRotationNotAttempted(
                                preferences.getNumDaysDailyRotationNotAttempted());

                        // Reset the disk-based counter for number of consecutive days daily rotation failed
                        // because if rotation was not attempted but restarts tomorrow after a boot and fails
                        // then, we want to report that as 1 day of failure instead of 3 consecutive days.
                        preferences.resetNumDaysDailyRotationFailed();
                    }
                } finally {
                    releaseWakeLock(wakeLock);
                }
            }
        });
    }
}
