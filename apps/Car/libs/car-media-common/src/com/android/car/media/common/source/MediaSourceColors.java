/*
 * Copyright 2018 The Android Open Source Project
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

package com.android.car.media.common.source;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Contains the colors for a {@link MediaSource}
 */
public class MediaSourceColors {
    /**
     * Mark used to indicate that we couldn't find a color and the default one should be used.
     */
    private static final int FLAG_USE_DEFAULT_COLOR = 0;

    /**
     * A MediaSourceColors that contains no colors and always returns default colors provided by
     * callers.
     */
    private static final MediaSourceColors EMPTY =
            new MediaSourceColors(FLAG_USE_DEFAULT_COLOR, FLAG_USE_DEFAULT_COLOR,
                    FLAG_USE_DEFAULT_COLOR);

    private final int mPrimaryColor;
    private final int mAccentColor;
    private final int mPrimaryColorDark;

    public MediaSourceColors(int primaryColor, int accentColor, int primaryColorDark) {
        this.mPrimaryColor = primaryColor;
        this.mAccentColor = accentColor;
        this.mPrimaryColorDark = primaryColorDark;
    }

    /**
     * Returns primary color, or the given default color if the source metadata is not available.
     */
    public int getPrimaryColor(int defaultColor) {
        return mPrimaryColor == FLAG_USE_DEFAULT_COLOR ? defaultColor : mPrimaryColor;
    }

    /** Returns accent color, or the given default color if the source metadata is not available. */
    public int getAccentColor(int defaultColor) {
        return mAccentColor == FLAG_USE_DEFAULT_COLOR ? defaultColor : mAccentColor;
    }

    /**
     * Returns primary dark color, or the given default color if the source metadata is not
     * available.
     */
    public int getPrimaryColorDark(int defaultColor) {
        return mPrimaryColorDark == FLAG_USE_DEFAULT_COLOR ? defaultColor : mPrimaryColorDark;
    }

    /** Extracts colors needed for a given package name to create a MediaSourceColors object */
    public static class Factory {
        private static final String TAG = "MediaSourceColors.Factory";

        /** Third-party defined application theme to use */
        private static final String THEME_META_DATA_NAME =
                "com.google.android.gms.car.application.theme";

        private final Context mContext;

        public Factory(@NonNull Context context) {
            mContext = context;
        }

        /** Extract colors for (@code mediaSource} and create a MediaSourceColors for it */
        @NonNull
        public MediaSourceColors extractColors(@Nullable MediaSource mediaSource) {
            if (mediaSource == null) {
                return EMPTY;
            }
            return extractColors(mediaSource.getPackageName());
        }

        /** Extract colors for {@code packageName} and create a MediaSourceColors for it */
        @NonNull
        public MediaSourceColors extractColors(@Nullable String packageName) {
            if (packageName == null) {
                return EMPTY;
            }
            TypedArray ta = null;
            try {
                ApplicationInfo applicationInfo =
                        mContext.getPackageManager().getApplicationInfo(packageName,
                                PackageManager.GET_META_DATA);

                // Get the proper theme, check theme for service, then application.
                Context packageContext = mContext.createPackageContext(packageName, 0);
                int appTheme = applicationInfo.metaData != null
                        ? applicationInfo.metaData.getInt(THEME_META_DATA_NAME)
                        : 0;
                appTheme = appTheme == 0
                        ? applicationInfo.theme
                        : appTheme;
                packageContext.setTheme(appTheme);
                Resources.Theme theme = packageContext.getTheme();
                ta = theme.obtainStyledAttributes(new int[]{
                        android.R.attr.colorPrimary,
                        android.R.attr.colorAccent,
                        android.R.attr.colorPrimaryDark
                });
                return new MediaSourceColors(
                        ta.getColor(0, FLAG_USE_DEFAULT_COLOR),
                        ta.getColor(1, FLAG_USE_DEFAULT_COLOR),
                        ta.getColor(2, FLAG_USE_DEFAULT_COLOR));
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "Unable to update media client package attributes.", e);
                return EMPTY;
            } finally {
                if (ta != null) {
                    ta.recycle();
                }
            }
        }
    }
}
