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
package com.android.wallpaper.model;

import android.app.WallpaperInfo;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.List;

/**
 * Lightweight wrapper for user-facing wallpaper metadata.
 */
public class WallpaperMetadata {

    private final List<String> mAttributions;
    private final String mActionUrl;
    private final String mCollectionId;
    private final String mBackingFileName;
    private final android.app.WallpaperInfo mWallpaperComponent;
    @StringRes private final int mActionLabelRes;
    @DrawableRes private final int mActionIconRes;

    public WallpaperMetadata(List<String> attributions, String actionUrl,
                             @StringRes int actionLabelRes,
                             @DrawableRes int actionIconRes, String collectionId,
                             String backingFileName,
                             android.app.WallpaperInfo wallpaperComponent) {
        mAttributions = attributions;
        mActionUrl = actionUrl;
        mActionLabelRes = actionLabelRes;
        mActionIconRes = actionIconRes;
        mCollectionId = collectionId;
        mBackingFileName = backingFileName;
        mWallpaperComponent = wallpaperComponent;
    }

    /**
     * Returns wallpaper's attributions.
     */
    public List<String> getAttributions() {
        return mAttributions;
    }

    /**
     * Returns the wallpaper's action URL or null if there is none.
     */
    public String getActionUrl() {
        return mActionUrl;
    }

    /**
     * Returns the wallpaper's action label.
     */
    @StringRes
    public int getActionLabelRes() {
        return mActionLabelRes;
    }

    /**
     * Returns the wallpaper's action icon.
     */
    @DrawableRes
    public int getActionIconRes() {
        return mActionIconRes;
    }

    /**
     * Returns the wallpaper's collection ID or null if there is none.
     */
    public String getCollectionId() {
        return mCollectionId;
    }

    /**
     * Returns the name of a private file corresponding to a copy of the full image used as
     * wallpaper if this is a static wallpaper.
     */
    @Nullable
    public String getBackingFileName() {
        return mBackingFileName;
    }

    /**
     * Returns the {@link android.app.WallpaperInfo} if a live wallpaper, or null if the metadata
     * describes an image wallpaper.
     */
    public WallpaperInfo getWallpaperComponent() {
        return mWallpaperComponent;
    }
}
