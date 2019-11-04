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

import android.content.Context;

import androidx.annotation.IntDef;

/**
 * Checks what component is responsible for presenting the rotating wallpaper image under the
 * device's launcher or keyguard for current and future rotations.
 */
public interface RotatingWallpaperComponentChecker {

    int ROTATING_WALLPAPER_COMPONENT_LIVE = 1;
    int ROTATING_WALLPAPER_COMPONENT_STATIC = 2;

    int ROTATING_WALLPAPER_SUPPORT_NOT_SUPPORTED = 0;
    int ROTATING_WALLPAPER_SUPPORT_SUPPORTED = 1;

    /**
     * Returns the rotating wallpaper component for the current wallpaper rotation.
     */
    @RotatingWallpaperComponent
    int getCurrentRotatingWallpaperComponent(Context context);

    /**
     * Returns the rotating wallpaper component for future wallpaper rotations.
     */
    @RotatingWallpaperComponent
    int getNextRotatingWallpaperComponent(Context context);

    /**
     * Returns the support level for rotating wallpaper.
     */
    @RotatingWallpaperSupport
    int getRotatingWallpaperSupport(Context context);

    /**
     * Possible components for presenting the rotating wallpaper image.
     */
    @IntDef({
            ROTATING_WALLPAPER_COMPONENT_LIVE,
            ROTATING_WALLPAPER_COMPONENT_STATIC})
    @interface RotatingWallpaperComponent {
    }

    /**
     * Possible support levels for rotating wallpaper.
     */
    @IntDef({
            ROTATING_WALLPAPER_SUPPORT_NOT_SUPPORTED,
            ROTATING_WALLPAPER_SUPPORT_SUPPORTED})
    @interface RotatingWallpaperSupport {
    }
}
