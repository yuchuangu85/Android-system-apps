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
import android.content.pm.PackageManager;

/**
 * Default implementation of {@link RotatingWallpaperComponentChecker}.
 */
public class DefaultRotatingWallpaperComponentChecker implements RotatingWallpaperComponentChecker {

    private static boolean isLiveWallpaperSupported(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LIVE_WALLPAPER);
    }

    @Override
    @RotatingWallpaperComponent
    public int getCurrentRotatingWallpaperComponent(Context context) {
        if (!isLiveWallpaperSupported(context)) {
            return ROTATING_WALLPAPER_COMPONENT_STATIC;
        }

        // If presentation mode is ROTATING but the live wallpaper is not set, then "legacy" rotation
        // from older APKs is in effect and the current rotating wallpaper component is a static WP.
        Injector injector = InjectorProvider.getInjector();
        WallpaperPreferences preferences = injector.getPreferences(context);
        LiveWallpaperStatusChecker liveWallpaperStatusChecker = injector
                .getLiveWallpaperStatusChecker(context);
        if (preferences.getWallpaperPresentationMode()
                == WallpaperPreferences.PRESENTATION_MODE_ROTATING
                && !liveWallpaperStatusChecker.isNoBackupImageWallpaperSet()) {
            return ROTATING_WALLPAPER_COMPONENT_STATIC;
        }

        return ROTATING_WALLPAPER_COMPONENT_LIVE;
    }

    @Override
    @RotatingWallpaperComponent
    public int getNextRotatingWallpaperComponent(Context context) {
        if (!isLiveWallpaperSupported(context)) {
            return ROTATING_WALLPAPER_COMPONENT_STATIC;
        }

        return ROTATING_WALLPAPER_COMPONENT_LIVE;
    }

    @Override
    @RotatingWallpaperSupport
    public int getRotatingWallpaperSupport(Context context) {
        FormFactorChecker formFactorChecker =
                InjectorProvider.getInjector().getFormFactorChecker(context);

        if (formFactorChecker.getFormFactor() == FormFactorChecker.FORM_FACTOR_DESKTOP) {
            return ROTATING_WALLPAPER_SUPPORT_SUPPORTED;
        }

        // While static daily rotation is supported on desktops, it isn't (yet?) supported on phones.
        // For phones which don't support live wallpapers thus we disallow daily rotation altogether.
        return isLiveWallpaperSupported(context) ? ROTATING_WALLPAPER_SUPPORT_SUPPORTED
                : ROTATING_WALLPAPER_SUPPORT_NOT_SUPPORTED;
    }
}
