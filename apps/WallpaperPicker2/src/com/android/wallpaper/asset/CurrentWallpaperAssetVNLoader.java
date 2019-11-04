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

import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseInputStream;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;

import java.io.InputStream;

import androidx.annotation.Nullable;

/**
 * Glide custom model loader for {@link CurrentWallpaperAssetVN}.
 */
public class CurrentWallpaperAssetVNLoader implements
        ModelLoader<CurrentWallpaperAssetVN, InputStream> {

    @Override
    public boolean handles(CurrentWallpaperAssetVN currentWallpaperAssetVN) {
        return true;
    }

    @Nullable
    @Override
    public LoadData<InputStream> buildLoadData(CurrentWallpaperAssetVN currentWallpaperAssetVN,
                                               int width, int height, Options options) {
        return new LoadData<>(currentWallpaperAssetVN.getKey(),
                new CurrentWallpaperAssetVNDataFetcher(currentWallpaperAssetVN));
    }

    /**
     * Factory that constructs {@link ResourceAssetLoader} instances.
     */
    public static class CurrentWallpaperAssetVNLoaderFactory
            implements ModelLoaderFactory<CurrentWallpaperAssetVN, InputStream> {
        public CurrentWallpaperAssetVNLoaderFactory() {
        }

        @Override
        public ModelLoader<CurrentWallpaperAssetVN, InputStream> build(
                MultiModelLoaderFactory multiFactory) {
            return new CurrentWallpaperAssetVNLoader();
        }

        @Override
        public void teardown() {
            // no-op
        }
    }

    private static class CurrentWallpaperAssetVNDataFetcher implements DataFetcher<InputStream> {

        private CurrentWallpaperAssetVN mAsset;

        public CurrentWallpaperAssetVNDataFetcher(CurrentWallpaperAssetVN asset) {
            mAsset = asset;
        }

        @Override
        public void loadData(Priority priority, final DataCallback<? super InputStream> callback) {
            ParcelFileDescriptor pfd = mAsset.getWallpaperPfd();

            if (pfd == null) {
                callback.onLoadFailed(new Exception("ParcelFileDescriptor for wallpaper is null, unable "
                        + "to open InputStream."));
                return;
            }

            callback.onDataReady(new AutoCloseInputStream(pfd));
        }

        @Override
        public DataSource getDataSource() {
            return DataSource.LOCAL;
        }

        @Override
        public void cancel() {
            // no op
        }

        @Override
        public void cleanup() {
            // no op
        }

        @Override
        public Class<InputStream> getDataClass() {
            return InputStream.class;
        }
    }
}
