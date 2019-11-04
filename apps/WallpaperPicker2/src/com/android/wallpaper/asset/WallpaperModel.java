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
package com.android.wallpaper.asset;

import android.annotation.TargetApi;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;

import com.bumptech.glide.load.Key;
import com.bumptech.glide.signature.ObjectKey;

import androidx.annotation.IntDef;

/**
 * Glide model representing wallpaper image data retrieved from {@link WallpaperManager}.
 * <p>
 * Instances of this class can be used to load wallpaper images normally retrieved directly from the
 * {@link WallpaperManager} by passing to Glide's {@link com.bumptech.glide.RequestBuilder#load} and
 * registering {@link WallpaperModelLoader} on Glide's {@link com.bumptech.glide.Registry} in a
 * custom {@link com.bumptech.glide.module.GlideModule}.
 */
public class WallpaperModel {
    public static final int SOURCE_BUILT_IN = 0;
    private static final String TAG = "WallpaperModel";
    private static final boolean SCALE_TO_FIT = true;
    private static final float HORIZONTAL_CENTER_ALIGNED = 0.5f;
    private static final float VERTICAL_CENTER_ALIGNED = 0.5f;
    @Source
    private int mWallpaperSource;
    private WallpaperManager mWallpaperManager;

    public WallpaperModel(Context context, @Source int wallpaperSource) {
        mWallpaperSource = wallpaperSource;
        mWallpaperManager = WallpaperManager.getInstance(context);
    }

    /**
     * Returns the {@link Drawable} for the wallpaper image represented by this object.
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public Drawable getDrawable(int width, int height) {
        if (mWallpaperSource != SOURCE_BUILT_IN) {
            Log.e(TAG, "Invalid wallpaper data source: " + mWallpaperSource);
            return null;
        }

        return mWallpaperManager.getBuiltInDrawable(
                width,
                height,
                SCALE_TO_FIT,
                HORIZONTAL_CENTER_ALIGNED,
                VERTICAL_CENTER_ALIGNED);
    }

    /**
     * Returns the Key used to cache the data loaded by this model.
     */
    public Key getKey() {
        if (mWallpaperSource != SOURCE_BUILT_IN) {
            Log.e(TAG, "Invalid wallpaper data source: " + mWallpaperSource);
        }

        // The built-in wallpaper image can only change via an OTA, so it cannot change over the
        // lifetime of this object so just use the object's signature as a cache key.
        return new ObjectKey(this);
    }

    /**
     * Possible sources of wallpaper image data from {@link android.app.WallpaperManager}.
     */
    @IntDef({
            SOURCE_BUILT_IN})
    public @interface Source {
    }
}
