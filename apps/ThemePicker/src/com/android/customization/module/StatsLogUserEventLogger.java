/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.customization.module;

import android.stats.style.nano.StyleEnums;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.systemui.shared.system.StatsLogCompat;
import com.android.customization.model.clock.Clockface;
import com.android.customization.model.grid.GridOption;
import com.android.customization.model.theme.ThemeBundle;
import com.android.wallpaper.module.NoOpUserEventLogger;

import java.util.Map;
import java.util.Objects;

import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_COLOR;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_FONT;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_SHAPE;

/**
 * StatsLog-backed implementation of {@link ThemesUserEventLogger}.
 */
public class StatsLogUserEventLogger extends NoOpUserEventLogger implements ThemesUserEventLogger {

    private static final String TAG = "StatsLogUserEventLogger";
    private static final int CODE = 179;

    @Override
    public void logResumed(boolean provisioned, boolean wallpaper) {
        WallpaperStatsLog.write(CODE, StyleEnums.ONRESUME, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    @Override
    public void logStopped() {
        WallpaperStatsLog.write(CODE, StyleEnums.ONSTOP, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    @Override
    public void logActionClicked(String collectionId, int actionLabelResId) {
        WallpaperStatsLog.write(CODE, StyleEnums.WALLPAPER_EXPLORE, 0, 0, 0, 0, 0, collectionId.hashCode(),
                0, 0, 0);
    }

    @Override
    public void logIndividualWallpaperSelected(String collectionId) {
        WallpaperStatsLog.write(CODE, StyleEnums.WALLPAPER_SELECT, 0, 0, 0, 0, 0, 0,
                collectionId.hashCode(), 0, 0);
    }

    @Override
    public void logCategorySelected(String collectionId) {
        WallpaperStatsLog.write(CODE, StyleEnums.WALLPAPER_SELECT,
                0, 0, 0, 0, 0,
                collectionId.hashCode(),
                0, 0, 0);
    }

    @Override
    public void logWallpaperSet(String collectionId, @Nullable String wallpaperId) {
        WallpaperStatsLog.write(CODE, StyleEnums.WALLPAPER_SELECT,
                0, 0, 0, 0, 0,
                collectionId.hashCode(),
                0, 0, 0);
    }

    @Nullable
    private String getThemePackage(ThemeBundle theme, String category) {
        Map<String, String> packages = theme.getPackagesByCategory();
        return packages.get(category);
    }

    @Override
    public void logThemeSelected(ThemeBundle theme, boolean isCustomTheme) {
        WallpaperStatsLog.write(CODE, StyleEnums.PICKER_SELECT,
                Objects.hashCode(getThemePackage(theme, OVERLAY_CATEGORY_COLOR)),
                Objects.hashCode(getThemePackage(theme,OVERLAY_CATEGORY_FONT)),
                Objects.hashCode(getThemePackage(theme, OVERLAY_CATEGORY_SHAPE)),
                0, 0, 0, 0, 0, 0);
    }

    @Override
    public void logThemeApplied(ThemeBundle theme, boolean isCustomTheme) {
        WallpaperStatsLog.write(CODE, StyleEnums.PICKER_APPLIED,
                Objects.hashCode(getThemePackage(theme, OVERLAY_CATEGORY_COLOR)),
                Objects.hashCode(getThemePackage(theme,OVERLAY_CATEGORY_FONT)),
                Objects.hashCode(getThemePackage(theme, OVERLAY_CATEGORY_SHAPE)),
                0, 0, 0, 0, 0, 0);
    }

    @Override
    public void logClockSelected(Clockface clock) {
        WallpaperStatsLog.write(CODE, StyleEnums.PICKER_SELECT,
                0, 0, 0,
                Objects.hashCode(clock.getId()),
                0, 0, 0, 0, 0);
    }

    @Override
    public void logClockApplied(Clockface clock) {
        WallpaperStatsLog.write(CODE, StyleEnums.PICKER_APPLIED,
                0, 0, 0,
                Objects.hashCode(clock.getId()),
                0, 0, 0, 0, 0);
    }

    @Override
    public void logGridSelected(GridOption grid) {
        WallpaperStatsLog.write(CODE, StyleEnums.PICKER_SELECT,
                0, 0, 0, 0,
                grid.cols,
                0, 0, 0, 0);
    }

    @Override
    public void logGridApplied(GridOption grid) {
        WallpaperStatsLog.write(CODE, StyleEnums.PICKER_APPLIED,
                0, 0, 0, 0,
                grid.cols,
                0, 0, 0, 0);
    }
}
