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
package com.android.wallpaper.picker;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

import com.android.wallpaper.R;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.WallpaperPreferences;
import com.android.wallpaper.module.WallpaperPreferences.PresentationMode;

import java.util.List;

/**
 * Methods for formatting attribution-related strings.
 */
public class AttributionFormatter {
    private static final String TAG = "AttributionFormatter";

    /**
     * Returns human-readable subtitle based on 2nd and 3rd items in wallpaper attributions or an
     * empty string if neither attribution line is available.
     */
    public static String formatWallpaperSubtitle(Context context, WallpaperInfo wallpaper) {
        List<String> attributions = wallpaper.getAttributions(context);
        String subtitle = "";
        if (attributions.size() > 1 && attributions.get(1) != null) {
            subtitle += attributions.get(1);
        }
        if (attributions.size() > 2 && attributions.get(2) != null) {
            subtitle += " • " + attributions.get(2);
        }
        return subtitle;
    }

    /**
     * Returns human-readable string for the given wallpaper presentation mode.
     */
    public static String getHumanReadableWallpaperPresentationMode(
            Context context, @PresentationMode int presentationMode) {

        Resources resources = context.getResources();

        switch (presentationMode) {
            case WallpaperPreferences.PRESENTATION_MODE_STATIC:
                return "";
            case WallpaperPreferences.PRESENTATION_MODE_ROTATING:
                return resources.getString(R.string.rotating_wallpaper_presentation_mode_message);
            default:
                Log.e(TAG, "No matching human-readable string for wallpaper presentation mode: "
                        + presentationMode);
                return "";
        }
    }
}
