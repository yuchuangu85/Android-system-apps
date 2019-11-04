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

/**
 * {@link UserEventLogger} which does not do anything.
 */
public class NoOpUserEventLogger implements UserEventLogger {

    @Override
    public void logResumed(boolean provisioned, boolean wallpaper) {

    }

    @Override
    public void logStopped() {

    }

    @Override
    public void logAppLaunched() {
    }

    @Override
    public void logDailyRefreshTurnedOn() {
    }

    @Override
    public void logCurrentWallpaperPreviewed() {
    }

    @Override
    public void logActionClicked(String collectionId, int actionLabelResId) {
    }

    @Override
    public void logIndividualWallpaperSelected(String collectionId) {
    }

    @Override
    public void logCategorySelected(String collectionId) {
    }

    @Override
    public void logWallpaperSet(String collectionId, String wallpaperId) {
    }

    @Override
    public void logWallpaperSetResult(@WallpaperSetResult int result) {
    }

    @Override
    public void logWallpaperSetFailureReason(@WallpaperSetFailureReason int reason) {
    }

    @Override
    public void logNumDailyWallpaperRotationsInLastWeek() {
    }

    @Override
    public void logNumDailyWallpaperRotationsPreviousDay() {
    }

    @Override
    public void logDailyWallpaperRotationHour(int hour) {
    }

    @Override
    public void logDailyWallpaperDecodes(boolean decodes) {
    }

    @Override
    public void logRefreshDailyWallpaperButtonClicked() {
    }

    @Override
    public void logDailyWallpaperRotationStatus(int status) {
    }

    @Override
    public void logDailyWallpaperSetNextWallpaperResult(
            @DailyWallpaperUpdateResult int result) {
    }

    @Override
    public void logDailyWallpaperSetNextWallpaperCrash(@DailyWallpaperUpdateCrash int crash) {
    }

    @Override
    public void logNumDaysDailyRotationFailed(int days) {
    }

    @Override
    public void logDailyWallpaperMetadataRequestFailure(
            @DailyWallpaperMetadataFailureReason int reason) {
    }

    @Override
    public void logNumDaysDailyRotationNotAttempted(int days) {
    }

    @Override
    public void logStandalonePreviewLaunched() {
    }

    @Override
    public void logStandalonePreviewImageUriHasReadPermission(boolean isReadPermissionGranted) {
    }

    @Override
    public void logStandalonePreviewStorageDialogApproved(boolean isApproved) {
    }

    @Override
    public void logWallpaperPresentationMode() {
    }

    @Override
    public void logRestored() {
    }
}
