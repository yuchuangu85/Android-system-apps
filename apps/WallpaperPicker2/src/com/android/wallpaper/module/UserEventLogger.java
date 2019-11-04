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

import com.android.wallpaper.module.WallpaperPersister.WallpaperPosition;

import androidx.annotation.IntDef;

/**
 * Interface for logging user events in the wallpaper picker.
 */
public interface UserEventLogger {

    int ROTATION_STATUS_NOT_ATTEMPTED = 0;
    int ROTATION_STATUS_FAILED = 5;

    int WALLPAPER_SET_RESULT_SUCCESS = 0;
    int WALLPAPER_SET_RESULT_FAILURE = 1;
    int DAILY_WALLPAPER_UPDATE_RESULT_SUCCESS = 0;
    int DAILY_WALLPAPER_UPDATE_RESULT_FAILURE_LOAD_METADATA = 1;
    int DAILY_WALLPAPER_UPDATE_RESULT_FAILURE_LOAD_BITMAP = 2;
    int DAILY_WALLPAPER_UPDATE_RESULT_FAILURE_SET_WALLPAPER = 3;
    int DAILY_WALLPAPER_UPDATE_RESULT_FAILURE_CRASH = 4;
    int WALLPAPER_SET_FAILURE_REASON_OTHER = 0;
    int WALLPAPER_SET_FAILURE_REASON_OOM = 1;
    int DAILY_WALLPAPER_UPDATE_CRASH_GENERIC = 0;
    int DAILY_WALLPAPER_UPDATE_CRASH_OOM = 1;
    int DAILY_WALLPAPER_METADATA_FAILURE_UNKNOWN = 0;
    int DAILY_WALLPAPER_METADATA_FAILURE_NO_CONNECTION = 1;
    int DAILY_WALLPAPER_METADATA_FAILURE_PARSE_ERROR = 2;
    int DAILY_WALLPAPER_METADATA_FAILURE_SERVER_ERROR = 3;
    int DAILY_WALLPAPER_METADATA_FAILURE_TIMEOUT = 4;

    void logResumed(boolean provisioned, boolean wallpaper);

    void logStopped();

    void logAppLaunched();

    void logDailyRefreshTurnedOn();

    void logCurrentWallpaperPreviewed();

    void logActionClicked(String collectionId, int actionLabelResId);

    void logIndividualWallpaperSelected(String collectionId);

    void logCategorySelected(String collectionId);

    void logWallpaperSet(String collectionId, String wallpaperId);

    void logWallpaperSetResult(@WallpaperSetResult int result);

    /**
     * Logs that a particular failure to set an individual wallpaper occurred for the given reason.
     */
    void logWallpaperSetFailureReason(@WallpaperSetFailureReason int reason);

    /**
     * Logs the number of daily rotations that occurred in the last week if daily rotation has
     * been enabled for at least a week.
     */
    void logNumDailyWallpaperRotationsInLastWeek();

    /**
     * Logs the number of daily rotations that occurred during the previous day (24 hour period
     * midnight to midnight) if daily rotation has been enabled at least since midnight yesterday.
     */
    void logNumDailyWallpaperRotationsPreviousDay();

    /**
     * Logs given the hour of day that a successful "daily wallpaper" rotation occurred.
     *
     * @param hour An hour from 0 to 23.
     */
    void logDailyWallpaperRotationHour(int hour);

    /**
     * Logs whether the image file for the daily wallpaper "rotating image wallpaper" is successfully
     * decoded as a bitmap.
     *
     * @param decodes Whether the decode succeeded.
     */
    void logDailyWallpaperDecodes(boolean decodes);

    /**
     * Logs the last-known status of daily wallpapers on the device.
     */
    void logDailyWallpaperRotationStatus(int status);

    /**
     * Logs the result of an operation to update the daily wallpaper.
     */
    void logDailyWallpaperSetNextWallpaperResult(@DailyWallpaperUpdateResult int result);

    /**
     * Logs that a particular crash occurred when trying to set the next wallpaper in a daily
     * rotation.
     */
    void logDailyWallpaperSetNextWallpaperCrash(@DailyWallpaperUpdateCrash int crash);

    /**
     * Logs that the request for metadata for the next wallpaper in a daily rotation failed for the
     * given reason.
     */
    void logDailyWallpaperMetadataRequestFailure(@DailyWallpaperMetadataFailureReason int reason);

    /**
     * Logs that the "refresh daily wallpaper" button was clicked.
     */
    void logRefreshDailyWallpaperButtonClicked();

    /**
     * Logs the number of consecutive days that daily rotation was attempted but failed.
     */
    void logNumDaysDailyRotationFailed(int days);

    /**
     * Logs the number of consecutive days that daily rotation was not attempted but should have been
     * attempted ("network conditions not met" doesn't count).
     */
    void logNumDaysDailyRotationNotAttempted(int days);

    /**
     * Logs that the StandalonePreviewActivity was launched.
     */
    void logStandalonePreviewLaunched();

    /**
     * Logs whether the image URI passed to StandalonePreviewActivity came properly preconfigured with
     * read permissions.
     */
    void logStandalonePreviewImageUriHasReadPermission(boolean isReadPermissionGranted);

    /**
     * Logs whether the user approved the runtime dialog to grant this app READ_EXTERNAL_STORAGE
     * permission in order to open an image URI.
     */
    void logStandalonePreviewStorageDialogApproved(boolean isApproved);

    /**
     * Logs the presentation mode of the current wallpaper.
     */
    void logWallpaperPresentationMode();

    /**
     * Logs that the app was restored from a backup set.
     */
    void logRestored();

    /**
     * Possible results of a "set wallpaper" operation.
     */
    @IntDef({
            WALLPAPER_SET_RESULT_SUCCESS,
            WALLPAPER_SET_RESULT_FAILURE})
    @interface WallpaperSetResult {
    }

    /**
     * Possible results of an operation to set the next wallpaper in a daily rotation.
     */
    @IntDef({
            DAILY_WALLPAPER_UPDATE_RESULT_SUCCESS,
            DAILY_WALLPAPER_UPDATE_RESULT_FAILURE_LOAD_METADATA,
            DAILY_WALLPAPER_UPDATE_RESULT_FAILURE_LOAD_BITMAP,
            DAILY_WALLPAPER_UPDATE_RESULT_FAILURE_SET_WALLPAPER,
            DAILY_WALLPAPER_UPDATE_RESULT_FAILURE_CRASH})
    @interface DailyWallpaperUpdateResult {
    }

    /**
     * Possible reasons setting an individual wallpaper failed.
     */
    @IntDef({
            WALLPAPER_SET_FAILURE_REASON_OTHER,
            WALLPAPER_SET_FAILURE_REASON_OOM})
    @interface WallpaperSetFailureReason {
    }

    /**
     * Possible crash types of a crashing failed "set next wallpaper" operation when daily rotation
     * is enabled and trying to set the next wallpaper.
     */
    @IntDef({
            DAILY_WALLPAPER_UPDATE_CRASH_GENERIC,
            DAILY_WALLPAPER_UPDATE_CRASH_OOM})
    @interface DailyWallpaperUpdateCrash {
    }

    /**
     * Possible reasons for a request for "next wallpaper" metadata in a daily rotation to fail.
     */
    @IntDef({
            DAILY_WALLPAPER_METADATA_FAILURE_UNKNOWN,
            DAILY_WALLPAPER_METADATA_FAILURE_NO_CONNECTION,
            DAILY_WALLPAPER_METADATA_FAILURE_PARSE_ERROR,
            DAILY_WALLPAPER_METADATA_FAILURE_SERVER_ERROR,
            DAILY_WALLPAPER_METADATA_FAILURE_TIMEOUT})
    @interface DailyWallpaperMetadataFailureReason {
    }
}
