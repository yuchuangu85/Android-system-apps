/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.apps.wallpaper.asset;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.AsyncTask;

import androidx.annotation.Nullable;

import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.module.DrawableLayerResolver;
import com.android.wallpaper.module.InjectorProvider;

public class ThemeBundleThumbAsset extends Asset {
    private final Resources mRes;
    private final int mResId;
    private final DrawableLayerResolver mLayerResolver;

    public ThemeBundleThumbAsset(Resources res, int resId) {
        mRes = res;
        mResId = resId;
        mLayerResolver = InjectorProvider.getInjector().getDrawableLayerResolver();
    }

    @Override
    public void decodeBitmap(int targetWidth, int targetHeight, BitmapReceiver receiver) {
        // No scaling is needed, as the thumbnail is already a thumbnail.
        LoadThumbnailTask task = new LoadThumbnailTask(mRes, mResId, mLayerResolver, receiver);
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void decodeBitmapRegion(Rect rect, int targetWidth, int targetHeight,
            BitmapReceiver receiver) {

    }

    @Override
    public void decodeRawDimensions(@Nullable Activity activity, DimensionsReceiver receiver) {

    }

    @Override
    public boolean supportsTiling() {
        return false;
    }

    /**
     * AsyncTask subclass which loads the live wallpaper's thumbnail bitmap off the main UI thread.
     * Resolves with null if live wallpaper thumbnail is not a bitmap.
     */
    private static class LoadThumbnailTask extends AsyncTask<Void, Void, Bitmap> {
        private final DrawableLayerResolver mLayerResolver;
        private final Resources mResources;
        private final int mResId;
        private BitmapReceiver mReceiver;

        public LoadThumbnailTask(Resources res, int resId, DrawableLayerResolver resolver,
                BitmapReceiver receiver) {
            mLayerResolver = resolver;
            mReceiver = receiver;
            mResources = res;
            mResId = resId;
        }

        @Override
        protected Bitmap doInBackground(Void... unused) {
            Drawable thumb = mResources.getDrawable(mResId, null);

            // Live wallpaper components may or may not specify a thumbnail drawable.
            if (thumb instanceof BitmapDrawable) {
                return ((BitmapDrawable) thumb).getBitmap();
            } else if (thumb instanceof LayerDrawable) {
                Drawable layer = mLayerResolver.resolveLayer((LayerDrawable) thumb);
                if (layer instanceof BitmapDrawable) {
                    return ((BitmapDrawable) layer).getBitmap();
                }
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
