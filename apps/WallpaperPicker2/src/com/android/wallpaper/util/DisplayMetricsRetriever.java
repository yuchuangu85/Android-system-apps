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
package com.android.wallpaper.util;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;

/**
 * Retrieves copies of DisplayMetrics from Display objects and caches them in memory. All clients
 * that need a copy of DisplayMetrics should use this retriever class to avoid excessive binder
 * calls to the system server.
 */
public class DisplayMetricsRetriever {

    private static final String TAG = "DisplayMetricsRetriever";

    private static DisplayMetricsRetriever sInstance;

    private DisplayMetrics mPortraitDisplayMetrics;
    private DisplayMetrics mLandscapeDisplayMetrics;

    public static DisplayMetricsRetriever getInstance() {
        if (sInstance == null) {
            sInstance = new DisplayMetricsRetriever();
        }
        return sInstance;
    }

    /**
     * Clears the static instance of DisplayMetricsRetriever. Used in test when display metrics are
     * manipulated between test cases.
     */
    static void clearInstance() {
        sInstance = null;
    }

    /**
     * Returns the DisplayMetrics for the provided Display.
     */
    public DisplayMetrics getDisplayMetrics(Resources resources, Display display) {
        switch (resources.getConfiguration().orientation) {
            case Configuration.ORIENTATION_PORTRAIT:
                return getPortraitDisplayMetrics(display);
            case Configuration.ORIENTATION_LANDSCAPE:
                return getLandscapeDisplayMetrics(display);
            default:
                Log.e(TAG, "Unknown device orientation: " + resources.getConfiguration().orientation);
                return getPortraitDisplayMetrics(display);
        }
    }

    private DisplayMetrics getPortraitDisplayMetrics(Display display) {
        if (mPortraitDisplayMetrics == null) {
            mPortraitDisplayMetrics = new DisplayMetrics();
            writeDisplayMetrics(display, mPortraitDisplayMetrics);
        }
        return mPortraitDisplayMetrics;
    }

    private DisplayMetrics getLandscapeDisplayMetrics(Display display) {
        if (mLandscapeDisplayMetrics == null) {
            mLandscapeDisplayMetrics = new DisplayMetrics();
            writeDisplayMetrics(display, mLandscapeDisplayMetrics);
        }
        return mLandscapeDisplayMetrics;
    }

    private void writeDisplayMetrics(Display display, DisplayMetrics outMetrics) {
        display.getMetrics(outMetrics);
    }
}
