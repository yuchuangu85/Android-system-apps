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

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.Key;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;

import java.security.MessageDigest;

/**
 * Asset wrapping a drawable for a live wallpaper thumbnail.
 */
public class LiveWallpaperThumbAsset extends Asset {
    protected final Context mContext;
    protected final android.app.WallpaperInfo mInfo;

    public LiveWallpaperThumbAsset(Context context, android.app.WallpaperInfo info) {
        mContext = context.getApplicationContext();
        mInfo = info;
    }

    @Override
    public void decodeBitmap(int targetWidth, int targetHeight,
                             BitmapReceiver receiver) {
        // No scaling is needed, as the thumbnail is already a thumbnail.
        LoadThumbnailTask task = new LoadThumbnailTask(mContext, mInfo, receiver);
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void decodeBitmapRegion(Rect rect, int targetWidth, int targetHeight,
                                   BitmapReceiver receiver) {
        receiver.onBitmapDecoded(null);
    }

    @Override
    public void decodeRawDimensions(Activity unused, DimensionsReceiver receiver) {
        receiver.onDimensionsDecoded(null);
    }

    @Override
    public boolean supportsTiling() {
        return false;
    }

    @Override
    public void loadDrawable(Context context, ImageView imageView,
                             int placeholderColor) {
        Glide.with(context)
                .asDrawable()
                .load(LiveWallpaperThumbAsset.this)
                .apply(RequestOptions.centerCropTransform()
                        .placeholder(new ColorDrawable(placeholderColor)))
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(imageView);
    }

    /**
     * Returns a Glide cache key.
     */
    Key getKey() {
        return new LiveWallpaperThumbKey(mInfo);
    }

    /**
     * Returns the thumbnail drawable for the live wallpaper synchronously. Should not be called on
     * the main UI thread.
     */
    protected Drawable getThumbnailDrawable() {
        return mInfo.loadThumbnail(mContext.getPackageManager());
    }

    /**
     * Glide caching key for resources from any arbitrary package.
     */
    private static final class LiveWallpaperThumbKey implements Key {
        private android.app.WallpaperInfo mInfo;

        public LiveWallpaperThumbKey(android.app.WallpaperInfo info) {
            mInfo = info;
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
            if (!(object instanceof LiveWallpaperThumbKey)) {
                return false;
            }

            LiveWallpaperThumbKey otherKey = (LiveWallpaperThumbKey) object;
            return getCacheKey().equals(otherKey.getCacheKey());
        }

        @Override
        public void updateDiskCacheKey(MessageDigest messageDigest) {
            messageDigest.update(getCacheKey().getBytes(CHARSET));
        }

        /**
         * Returns an inexpensively calculated {@link String} suitable for use as a disk cache key,
         * based on the live wallpaper's package name and service name, which is enough to uniquely
         * identify a live wallpaper.
         */
        private String getCacheKey() {
            return "LiveWallpaperThumbKey{"
                    + "packageName=" + mInfo.getPackageName() + ","
                    + "serviceName=" + mInfo.getServiceName()
                    + '}';
        }
    }

    /**
     * AsyncTask subclass which loads the live wallpaper's thumbnail bitmap off the main UI thread.
     * Resolves with null if live wallpaper thumbnail is not a bitmap.
     */
    private static class LoadThumbnailTask extends AsyncTask<Void, Void, Bitmap> {
        private final PackageManager mPackageManager;
        private android.app.WallpaperInfo mInfo;
        private BitmapReceiver mReceiver;

        public LoadThumbnailTask(Context context, android.app.WallpaperInfo info,
                BitmapReceiver receiver) {
            mInfo = info;
            mReceiver = receiver;
            mPackageManager = context.getPackageManager();
        }

        @Override
        protected Bitmap doInBackground(Void... unused) {
            Drawable thumb = mInfo.loadThumbnail(mPackageManager);

            // Live wallpaper components may or may not specify a thumbnail drawable.
            if (thumb != null && thumb instanceof BitmapDrawable) {
                return ((BitmapDrawable) thumb).getBitmap();
            }

            // If no thumbnail was specified, return a null bitmap.
            return null;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            mReceiver.onBitmapDecoded(bitmap);
        }
    }
}
