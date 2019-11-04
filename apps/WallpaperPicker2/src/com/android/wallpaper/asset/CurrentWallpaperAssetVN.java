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

import android.app.WallpaperManager;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseInputStream;
import android.util.Log;
import android.widget.ImageView;

import com.android.wallpaper.compat.WallpaperManagerCompat;
import com.android.wallpaper.compat.WallpaperManagerCompat.WallpaperLocation;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.Key;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;

import java.io.InputStream;
import java.security.MessageDigest;

/**
 * Asset representing the currently-set image wallpaper on N+ devices, including when daily rotation
 * is set with a static wallpaper (but not when daily rotation uses a live wallpaper).
 */
public class CurrentWallpaperAssetVN extends StreamableAsset {

    private static final String TAG = "CurrentWallpaperAssetVN";
    int mWallpaperId;
    private WallpaperManager mWallpaperManager;
    private WallpaperManagerCompat mWallpaperManagerCompat;
    @WallpaperLocation
    private int mWallpaperManagerFlag;

    public CurrentWallpaperAssetVN(Context context, @WallpaperLocation int wallpaperManagerFlag) {
        mWallpaperManager = WallpaperManager.getInstance(context);
        mWallpaperManagerCompat = WallpaperManagerCompat.getInstance(context);
        mWallpaperManagerFlag = wallpaperManagerFlag;
        mWallpaperId = mWallpaperManagerCompat.getWallpaperId(mWallpaperManagerFlag);
    }

    @Override
    protected InputStream openInputStream() {
        ParcelFileDescriptor pfd = mWallpaperManagerCompat.getWallpaperFile(mWallpaperManagerFlag);

        if (pfd == null) {
            Log.e(TAG, "ParcelFileDescriptor for wallpaper " + mWallpaperManagerFlag + " is null, unable "
                    + "to open InputStream.");
            return null;
        }

        return new AutoCloseInputStream(pfd);
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = result * 31 + mWallpaperManagerFlag;
        result = result * 31 + mWallpaperId;
        return result;
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof CurrentWallpaperAssetVN) {
            CurrentWallpaperAssetVN otherAsset = (CurrentWallpaperAssetVN) object;
            return otherAsset.mWallpaperManagerFlag == mWallpaperManagerFlag
                    && otherAsset.mWallpaperId == mWallpaperId;

        }
        return false;
    }

    @Override
    public void loadDrawable(Context context, ImageView imageView,
                             int unusedPlaceholderColor) {
        Glide.with(context)
                .asDrawable()
                .load(CurrentWallpaperAssetVN.this)
                .apply(RequestOptions.centerCropTransform())
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(imageView);
    }

    public Key getKey() {
        return new CurrentWallpaperVNKey(mWallpaperManager, mWallpaperManagerFlag);
    }

    ParcelFileDescriptor getWallpaperPfd() {
        return mWallpaperManagerCompat.getWallpaperFile(mWallpaperManagerFlag);
    }

    /**
     * Glide caching key for currently-set wallpapers on Android N or later using wallpaper IDs
     * provided by WallpaperManager.
     */
    private static final class CurrentWallpaperVNKey implements Key {
        private WallpaperManager mWallpaperManager;
        private int mWallpaperFlag;

        public CurrentWallpaperVNKey(WallpaperManager wallpaperManager,
                                     @WallpaperLocation int wallpaperFlag) {
            mWallpaperManager = wallpaperManager;
            mWallpaperFlag = wallpaperFlag;
        }

        @Override
        public String toString() {
            return getCacheKey();
        }

        @Override
        public int hashCode() {
            return getCacheKey().hashCode();
        }

        @Override
        public boolean equals(Object object) {
            if (object instanceof CurrentWallpaperVNKey) {
                CurrentWallpaperVNKey otherKey = (CurrentWallpaperVNKey) object;
                return getCacheKey().equals(otherKey.getCacheKey());

            }
            return false;
        }

        @Override
        public void updateDiskCacheKey(MessageDigest messageDigest) {
            messageDigest.update(getCacheKey().getBytes(CHARSET));
        }

        /**
         * Returns an inexpensively calculated {@link String} suitable for use as a disk cache key.
         */
        private String getCacheKey() {
            return "CurrentWallpaperVNKey{"
                    + "flag=" + mWallpaperFlag
                    + ",id=" + mWallpaperManager.getWallpaperId(mWallpaperFlag)
                    + '}';
        }
    }
}
