/*
 * Copyright (C) 2019 The Android Open Source Project.
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

package com.android.car.themeplayground;

import android.app.Activity;
import android.content.Intent;

/**
 * Utility class for changing the theme of the app at run time.
 */
public class Utils {
    static String sThemeName = "";
    private static int sThemeResId = 0;

    /**
     * Set the theme of the Activity, and restart it by creating a new Activity of the same type.
     */
    public static void changeToTheme(Activity activity, String themeName, int themeResId) {
        sThemeName = themeName;
        sThemeResId = themeResId;
        activity.finish();
        activity.startActivity(new Intent(activity, activity.getClass()));
    }

    /** Set the theme of the activity, according to the configuration. */
    public static void onActivityCreateSetTheme(Activity activity) {
        if (sThemeName.equals("")) {
            activity.setTheme(android.R.style.Theme_DeviceDefault);
        } else {
            activity.setTheme(sThemeResId);
        }
    }
}
