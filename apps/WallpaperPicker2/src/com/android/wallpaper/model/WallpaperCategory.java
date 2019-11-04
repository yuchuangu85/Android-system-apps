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

import android.app.Activity;
import android.content.Context;

import com.android.wallpaper.asset.Asset;

import java.util.ArrayList;
import java.util.List;

/**
 * Default category for a collection of WallpaperInfo objects.
 */
public class WallpaperCategory extends Category {

    protected final Object mWallpapersLock;
    private final List<WallpaperInfo> mWallpapers;
    private Asset mThumbAsset;
    private int mFeaturedThumbnailIndex;

    public WallpaperCategory(String title, String collectionId, List<WallpaperInfo> wallpapers,
                             int priority) {
        this(title, collectionId, 0, wallpapers, priority);
    }

    public WallpaperCategory(String title, String collectionId, int featuredThumbnailIndex,
                             List<WallpaperInfo> wallpapers, int priority) {
        super(title, collectionId, priority);
        mWallpapers = wallpapers;
        mWallpapersLock = new Object();
        mFeaturedThumbnailIndex = featuredThumbnailIndex;
    }

    /**
     * Fetches wallpapers for this category and passes them to the receiver. Subclasses may use a
     * context to fetch wallpaper info.
     */
    public void fetchWallpapers(Context unused, WallpaperReceiver receiver, boolean forceReload) {
        // Perform a shallow clone so as not to pass the reference to the list along to clients.
        receiver.onWallpapersReceived(new ArrayList<>(mWallpapers));
    }

    @Override
    public void show(Activity srcActivity, PickerIntentFactory factory, int requestCode) {
        srcActivity.startActivityForResult(
                factory.newIntent(srcActivity, getCollectionId()), requestCode);
    }

    @Override
    public boolean isEnumerable() {
        return true;
    }

    /**
     * Returns the mutable list of wallpapers backed by this WallpaperCategory. All reads and writes
     * on the returned list must be synchronized with {@code mWallpapersLock}.
     */
    protected List<WallpaperInfo> getMutableWallpapers() {
        return mWallpapers;
    }

    @Override
    public Asset getThumbnail(Context context) {
        synchronized (mWallpapersLock) {
            if (mThumbAsset == null && mWallpapers.size() > 0) {
                mThumbAsset = mWallpapers.get(mFeaturedThumbnailIndex).getThumbAsset(context);
            }
        }
        return mThumbAsset;
    }

    @Override
    public boolean supportsThirdParty() {
        return false;
    }

    @Override
    public boolean containsThirdParty(String packageName) {
        return false;
    }
}
