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

import android.graphics.drawable.Drawable;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;

import androidx.annotation.Nullable;

/**
 * Custom Glide {@link ModelLoader} which can load {@link Drawable} objects from
 * {@link WallpaperModel} objects.
 */
public class WallpaperModelLoader implements ModelLoader<WallpaperModel, Drawable> {

    @Override
    public boolean handles(WallpaperModel wallpaperModel) {
        return true;
    }

    @Nullable
    @Override
    public LoadData<Drawable> buildLoadData(WallpaperModel wallpaperModel, int width, int height,
                                            Options options) {
        return new LoadData<>(wallpaperModel.getKey(),
                new WallpaperFetcher(wallpaperModel, width, height));
    }

    /**
     * Factory that constructs {@link WallpaperModelLoader} instances.
     */
    public static class WallpaperModelLoaderFactory
            implements ModelLoaderFactory<WallpaperModel, Drawable> {
        public WallpaperModelLoaderFactory() {
        }

        @Override
        public ModelLoader<WallpaperModel, Drawable> build(MultiModelLoaderFactory multiFactory) {
            return new WallpaperModelLoader();
        }

        @Override
        public void teardown() {
            // no-op
        }
    }

    /**
     * Fetcher class for fetching wallpaper image data from a {@link WallpaperModel}.
     */
    private static class WallpaperFetcher implements DataFetcher<Drawable> {

        private WallpaperModel mWallpaperModel;
        private int mWidth;
        private int mHeight;

        public WallpaperFetcher(WallpaperModel wallpaperModel, int width, int height) {
            mWallpaperModel = wallpaperModel;
            mWidth = width;
            mHeight = height;
        }

        @Override
        public void loadData(Priority priority, DataCallback<? super Drawable> callback) {
            callback.onDataReady(mWallpaperModel.getDrawable(mWidth, mHeight));
        }

        @Override
        public DataSource getDataSource() {
            return DataSource.LOCAL;
        }

        @Override
        public void cancel() {
            // no-op
        }

        @Override
        public void cleanup() {
            // no-op
        }

        @Override
        public Class<Drawable> getDataClass() {
            return Drawable.class;
        }
    }
}
