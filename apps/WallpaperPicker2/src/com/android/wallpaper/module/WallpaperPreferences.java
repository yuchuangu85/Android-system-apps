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

import android.annotation.TargetApi;
import android.os.Build;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * Interface for persisting and retrieving wallpaper specific preferences.
 */
public interface WallpaperPreferences {

    int PRESENTATION_MODE_STATIC = 1;
    int PRESENTATION_MODE_ROTATING = 2;
    int WALLPAPER_SET_NOT_PENDING = 0;
    int WALLPAPER_SET_PENDING = 1;
    int DAILY_WALLPAPER_UPDATE_NOT_PENDING = 0;
    int DAILY_WALLPAPER_UPDATE_PENDING = 1;

    /**
     * Returns the wallpaper presentation mode.
     */
    @PresentationMode
    int getWallpaperPresentationMode();

    /**
     * Sets the presentation mode of the current wallpaper.
     */
    void setWallpaperPresentationMode(@PresentationMode int presentationMode);

    /**
     * Returns the home attributions as a list.
     */
    List<String> getHomeWallpaperAttributions();

    /**
     * Sets the attributions for the current home wallpaper. Clears existing attributions if any
     * exist.
     */
    void setHomeWallpaperAttributions(List<String> attributions);

    /**
     * Returns the home wallpaper's action URL or null if there is none.
     */
    String getHomeWallpaperActionUrl();

    /**
     * Sets the home wallpaper's action URL.
     */
    void setHomeWallpaperActionUrl(String actionUrl);

    /**
     * Returns the resource id for the home wallpaper's action label.
     */
    int getHomeWallpaperActionLabelRes();

    /**
     * Sets the resource id for the home wallpaper's action label.
     */
    void setHomeWallpaperActionLabelRes(int resId);

    /**
     * Returns the resource id for the home wallpaper's action icon.
     */
    int getHomeWallpaperActionIconRes();

    /**
     * Sets the resource id for the home wallpaper's action icon.
     */
    void setHomeWallpaperActionIconRes(int resId);

    /**
     * Returns the home wallpaper's base image URL or if there is none.
     */
    String getHomeWallpaperBaseImageUrl();

    /**
     * Sets the home wallpaper's base image URL.
     */
    void setHomeWallpaperBaseImageUrl(String baseImageUrl);

    /**
     * Returns the home wallpaper's collection ID or null if there is none.
     */
    String getHomeWallpaperCollectionId();

    /**
     * Sets the home wallpaper's collection ID.
     */
    void setHomeWallpaperCollectionId(String collectionId);

    /**
     * Returns the home wallpaper's backing file name if there's one or null.
     */
    String getHomeWallpaperBackingFileName();

    /**
     * Sets the home wallpaper's backing file name
     */
    void setHomeWallpaperBackingFileName(String fileName);

    /**
     * Removes all home metadata from SharedPreferences.
     */
    void clearHomeWallpaperMetadata();

    /**
     * Returns the home wallpaper's bitmap hash code or 0 if there is none.
     */
    long getHomeWallpaperHashCode();

    /**
     * Sets the home wallpaper's bitmap hash code if it is an individual image.
     */
    void setHomeWallpaperHashCode(long hashCode);

    /**
     * Gets the home wallpaper's package name, which is present for live wallpapers.
     */
    String getHomeWallpaperPackageName();

    /**
     * Sets the home wallpaper's package name, which is present for live wallpapers.
     */
    void setHomeWallpaperPackageName(String packageName);

    /**
     * Gets the home wallpaper's ID, which is provided by WallpaperManager for static wallpapers.
     */
    @TargetApi(Build.VERSION_CODES.N)
    int getHomeWallpaperManagerId();

    /**
     * Sets the home wallpaper's ID, which is provided by WallpaperManager for static wallpapers.
     */
    @TargetApi(Build.VERSION_CODES.N)
    void setHomeWallpaperManagerId(int homeWallpaperId);

    /**
     * Gets the home wallpaper's remote identifier.
     */
    String getHomeWallpaperRemoteId();

    /**
     * Sets the home wallpaper's remote identifier to SharedPreferences. This should be a string
     * which uniquely identifies the currently set home wallpaper in the context of a remote wallpaper
     * collection.
     */
    void setHomeWallpaperRemoteId(String wallpaperRemoteId);

    /**
     * Returns the lock wallpaper's action URL or null if there is none.
     */
    String getLockWallpaperActionUrl();

    /**
     * Sets the lock wallpaper's action URL.
     */
    void setLockWallpaperActionUrl(String actionUrl);

    /**
     * Returns the resource id for the lock wallpaper's action label.
     */
    int getLockWallpaperActionLabelRes();

    /**
     * Sets the resource id for the lock wallpaper's action label.
     */
    void setLockWallpaperActionLabelRes(int resId);

    /**
     * Returns the resource id for the lock wallpaper's action icon.
     */
    int getLockWallpaperActionIconRes();

    /**
     * Sets the resource id for the lock wallpaper's action icon.
     */
    void setLockWallpaperActionIconRes(int resId);

    /**
     * Returns the lock wallpaper's collection ID or null if there is none.
     */
    String getLockWallpaperCollectionId();

    /**
     * Sets the lock wallpaper's collection ID.
     */
    void setLockWallpaperCollectionId(String collectionId);

    /**
     * Returns the home wallpaper's backing file name if there's one or null.
     */
    String getLockWallpaperBackingFileName();

    /**
     * Sets the home wallpaper's backing file name
     */
    void setLockWallpaperBackingFileName(String fileName);

    /**
     * Returns the lock screen attributions as a list.
     */
    List<String> getLockWallpaperAttributions();

    /**
     * Sets the attributions for the current lock screen wallpaper. Clears existing attributions if
     * any exist.
     *
     * @param attributions
     */
    void setLockWallpaperAttributions(List<String> attributions);

    /**
     * Removes all lock screen metadata from SharedPreferences.
     */
    void clearLockWallpaperMetadata();

    /**
     * Returns the lock screen wallpaper's bitmap hash code or 0 if there is none.
     */
    long getLockWallpaperHashCode();

    /**
     * Sets the lock screen wallpaper's bitmap hash code if it is an individual image.
     */
    void setLockWallpaperHashCode(long hashCode);

    /**
     * Gets the lock wallpaper's ID, which is provided by WallpaperManager for static wallpapers.
     */
    @TargetApi(Build.VERSION_CODES.N)
    int getLockWallpaperId();

    /**
     * Sets the lock wallpaper's ID, which is provided by WallpaperManager for static wallpapers.
     */
    @TargetApi(Build.VERSION_CODES.N)
    void setLockWallpaperId(int lockWallpaperId);

    /**
     * Persists the timestamp of a daily wallpaper rotation that just occurred.
     */
    void addDailyRotation(long timestamp);

    /**
     * Returns the timestamp of the last wallpaper daily rotation or -1 if there has never been a
     * daily wallpaper rotation on the user's device.
     */
    long getLastDailyRotationTimestamp();

    /**
     * Gets a list of the daily rotation timestamps that occurred in the last week, from least
     * recent at the start of the list to most recent at the end of the list.
     * The timestamps are in milliseconds since Unix epoch.
     * If daily rotation has been enabled for less than one week, returns null instead.
     */
    @Nullable
    List<Long> getDailyRotationsInLastWeek();

    /**
     * Gets a list of the daily rotation timestamps that occurred the previous day (midnight to
     * midnight in the user's timezone). Timestamps are in milliseconds since Unix epoch. Returns null
     * if daily rotation was enabled earlier than midnight yesterday.
     */
    @Nullable
    List<Long> getDailyRotationsPreviousDay();

    /**
     * Returns the daily wallpaper enabled timestamp in milliseconds since Unix epoch, or -1 if
     * daily wallpaper is not currently enabled.
     */
    long getDailyWallpaperEnabledTimestamp();

    /**
     * Persists the timestamp when daily wallpaper feature was last enabled.
     *
     * @param timestamp Milliseconds since Unix epoch.
     */
    void setDailyWallpaperEnabledTimestamp(long timestamp);

    /**
     * Clears the persisted daily rotation timestamps and the "daily wallpaper enabled" timestamp.
     * Called if daily rotation is disabled.
     */
    void clearDailyRotations();

    /**
     * Returns the timestamp of the most recent daily logging event, in milliseconds since Unix
     * epoch. Returns -1 if the very first daily logging event has not occurred yet.
     */
    long getLastDailyLogTimestamp();

    /**
     * Sets the timestamp of the most recent daily logging event.
     *
     * @param timestamp Milliseconds since Unix epoch.
     */
    void setLastDailyLogTimestamp(long timestamp);

    /**
     * Returns the timestamp of the last time the app was noted to be active; i.e. the last time an
     * activity entered the foreground (milliseconds since Unix epoch).
     */
    long getLastAppActiveTimestamp();

    /**
     * Sets the timestamp of the last time the app was noted to be active; i.e. the last time an
     * activity entered the foreground.
     *
     * @param timestamp Milliseconds since Unix epoch.
     */
    void setLastAppActiveTimestamp(long timestamp);

    /**
     * Sets the last rotation status for daily wallpapers with a timestamp.
     *
     * @param status    Last status code of daily rotation.
     * @param timestamp Milliseconds since Unix epoch.
     */
    void setDailyWallpaperRotationStatus(int status, long timestamp);

    /**
     * Gets the last daily wallpapers rotation status or -1 if no rotation status has ever been
     * persisted to preferences.
     */
    int getDailyWallpaperLastRotationStatus();

    /**
     * Gets the timestamp of the last set daily wallpapers rotation status in milliseconds since the
     * Unix epoch or 0 if no rotation status has ever been persisted to preferences.
     */
    long getDailyWallpaperLastRotationStatusTimestamp();

    /**
     * Gets the timestamp of the last time a sync occurred of wallpaper data to or from this device.
     * Returns 0 if a sync has never occurred before.
     */
    long getLastSyncTimestamp();

    /**
     * Sets the timestamp of the latest sync received or sent.
     */
    void setLastSyncTimestamp(long timestamp);

    /**
     * Sets the status of whether a wallpaper is currently pending being set (i.e., user tapped the
     * UI to set a wallpaper but it has not yet been actually set on the device). Does so in a
     * synchronous manner so a caller may be assured that the underlying store has been updated when
     * this method returns.
     */
    void setPendingWallpaperSetStatusSync(@PendingWallpaperSetStatus int setStatus);

    /**
     * Gets the status of whether a wallpaper is currently pending being set.
     */
    @PendingWallpaperSetStatus
    int getPendingWallpaperSetStatus();

    /**
     * Sets the status of whether a wallpaper is currently pending being set (i.e., user tapped the
     * UI to set a wallpaper but it has not yet been actually set on the device). Does so in an
     * asynchronous manner so writing the preference to the underlying store doesn't block the calling
     * thread.
     */
    void setPendingWallpaperSetStatus(@PendingWallpaperSetStatus int setStatus);

    /**
     * Sets whether a daily wallpaper update is pending. Writes status to memory and also to disk
     * before returning.
     */
    void setPendingDailyWallpaperUpdateStatusSync(
            @PendingDailyWallpaperUpdateStatus int updateStatus);

    /**
     * Returns whether a daily wallpaper update is pending.
     */
    @PendingDailyWallpaperUpdateStatus
    int getPendingDailyWallpaperUpdateStatus();

    /**
     * Sets whether a daily wallpaper update is pending. Writes status to memory immediately and to
     * disk after returning.
     */
    void setPendingDailyWallpaperUpdateStatus(@PendingDailyWallpaperUpdateStatus int updateStatus);

    /**
     * Increments the number of consecutive days daily rotation has failed.
     */
    void incrementNumDaysDailyRotationFailed();

    /**
     * Gets the number of days daily rotation failed.
     */
    int getNumDaysDailyRotationFailed();

    /**
     * Resets the consecutive number of days daily rotation failed to 0.
     */
    void resetNumDaysDailyRotationFailed();

    /**
     * Increments the number of consecutive days daily rotation was not attempted.
     */
    void incrementNumDaysDailyRotationNotAttempted();

    /**
     * Gets the number ofconsecutive days daily rotation was not attempted.
     */
    int getNumDaysDailyRotationNotAttempted();

    /**
     * Resets the consecutive number of days daily rotation was not attempted to 0.
     */
    void resetNumDaysDailyRotationNotAttempted();

    /**
     * The possible wallpaper presentation modes, i.e., either "static" or "rotating".
     */
    @IntDef({
            PRESENTATION_MODE_STATIC,
            PRESENTATION_MODE_ROTATING})
    @interface PresentationMode {
    }

    /**
     * Possible status of whether a wallpaper set operation is pending or not.
     */
    @IntDef({
            WALLPAPER_SET_NOT_PENDING,
            WALLPAPER_SET_PENDING})
    @interface PendingWallpaperSetStatus {
    }

    /**
     * Possible status of whether a wallpaper set operation is pending or not.
     */
    @IntDef({
            DAILY_WALLPAPER_UPDATE_NOT_PENDING,
            DAILY_WALLPAPER_UPDATE_PENDING})
    @interface PendingDailyWallpaperUpdateStatus {
    }
}
