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
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;

/**
 * Asset implementation which represents the currently-set wallpaper on API 16 through 23 devices.
 */
public class CurrentWallpaperAssetV16 extends Asset {

    private static final boolean FILTER_SCALED_BITMAP = true;

    private Context mApplicationContext;

    public CurrentWallpaperAssetV16(Context context) {
        mApplicationContext = context.getApplicationContext();
    }

    @Override
    public void decodeBitmapRegion(Rect rect, int targetWidth, int targetHeight,
                                   BitmapReceiver receiver) {
        receiver.onBitmapDecoded(null);
    }

    @Override
    public void decodeBitmap(int targetWidth, int targetHeight,
                             BitmapReceiver receiver) {
        DecodeBitmapAsyncTask task = new DecodeBitmapAsyncTask(receiver, targetWidth, targetHeight);
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public boolean supportsTiling() {
        return false;
    }

    @Override
    public void decodeRawDimensions(Activity unused, DimensionsReceiver receiver) {
        DecodeDimensionsAsyncTask task = new DecodeDimensionsAsyncTask(receiver);
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private Drawable getCurrentWallpaperDrawable() {
        WallpaperManager wallpaperManager = WallpaperManager.getInstance(mApplicationContext);
        Drawable drawable;
        try {
            drawable = wallpaperManager.getDrawable();
        } catch (java.lang.SecurityException e) {
            // Work around Samsung bug where SecurityException is thrown if device is still using its
            // default wallpaper.
            drawable = wallpaperManager.getBuiltInDrawable();
        }
        return drawable;
    }

    /**
     * Decodes and then post-decode scales down the currently-set wallpaper bitmap.
     */
    private class DecodeBitmapAsyncTask extends AsyncTask<Void, Void, Bitmap> {

        private BitmapReceiver mReceiver;
        private int mTargetWidth;
        private int mTargetHeight;

        public DecodeBitmapAsyncTask(BitmapReceiver receiver, int width, int height) {
            mReceiver = receiver;
            mTargetWidth = width;
            mTargetHeight = height;
        }

        @Override
        protected Bitmap doInBackground(Void... unused) {
            Drawable wallpaperDrawable = getCurrentWallpaperDrawable();
            Bitmap bitmap = ((BitmapDrawable) wallpaperDrawable).getBitmap();

            // The final bitmap may be constrained by only one of height and width, so find the maximum
            // downscaling factor without having the final result bitmap's height or width dip below the
            // provided target height or width.
            float maxDownscaleFactor = Math.min((float) bitmap.getWidth() / mTargetWidth,
                    (float) bitmap.getHeight() / mTargetHeight);

            // Scale down full bitmap to save memory consumption post-decoding, while maintaining the
            // source bitmap's aspect ratio.
            int resultWidth = Math.round(bitmap.getWidth() / maxDownscaleFactor);
            int resultHeight = Math.round(bitmap.getHeight() / maxDownscaleFactor);
            return Bitmap.createScaledBitmap(bitmap, resultWidth, resultHeight, FILTER_SCALED_BITMAP);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            mReceiver.onBitmapDecoded(bitmap);
        }
    }

    /**
     * Decodes the raw dimensions of the currently-set wallpaper.
     */
    private class DecodeDimensionsAsyncTask extends AsyncTask<Void, Void, Point> {

        private DimensionsReceiver mReceiver;

        public DecodeDimensionsAsyncTask(DimensionsReceiver receiver) {
            mReceiver = receiver;
        }

        @Override
        protected Point doInBackground(Void... unused) {
            Drawable wallpaperDrawable = getCurrentWallpaperDrawable();
            return new Point(
                    wallpaperDrawable.getIntrinsicWidth(), wallpaperDrawable.getIntrinsicHeight());
        }

        @Override
        protected void onPostExecute(Point dimensions) {
            mReceiver.onDimensionsDecoded(dimensions);
        }
    }
}
