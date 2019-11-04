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

import android.content.res.Resources;

import java.io.File;

/**
 * Provides content from the partner customization on the device.
 */
public interface PartnerProvider {

    /**
     * Marker action used to discover partner.
     */
    public static final String ACTION_PARTNER_CUSTOMIZATION =
            "com.android.launcher3.action.PARTNER_CUSTOMIZATION";

    /**
     * The resource ID in the partner APK for its list of wallpapers.
     */
    public static final String WALLPAPER_RES_ID = "partner_wallpapers";

    /**
     * Directory for system wallpapers in legacy versions of the partner APK.
     */
    public static final String RES_LEGACY_SYSTEM_WALLPAPER_DIR = "system_wallpaper_directory";

    /**
     * Boolean indicating the OEM does not want the picker to show the built-in Android system
     * wallpaper because they've provided their own wallpapers instead.
     * NOTE: The typo here "wallpapper" is intentional. The typo was made in legacy versions of the
     * customization scheme so we can't fix it without breaking existing devices.
     */
    public static final String RES_DEFAULT_WALLPAPER_HIDDEN = "default_wallpapper_hidden";

    /**
     * Returns the Resources object for the partner APK, or null if there is no partner APK on the
     * device.
     */
    public Resources getResources();

    /**
     * Returns the directory containing wallpapers, or null if the directory is not found on the
     * device. The directory is only present and populated in legacy versions of the partner
     * customization scheme.
     */
    public File getLegacyWallpaperDirectory();

    /**
     * Returns the package name of the partner APK, or null if there is no partner APK on the
     * device.
     */
    public String getPackageName();

    /**
     * Returns whether the OEM has specified that the built-in system default wallpaper should be
     * hidden (because OEM has provided their own wallpaper). If no partner customization exists on
     * the device, returns false.
     */
    public boolean shouldHideDefaultWallpaper();
}
