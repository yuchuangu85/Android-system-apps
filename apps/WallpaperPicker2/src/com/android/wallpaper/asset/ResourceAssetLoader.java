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
 * Glide ModelLoader which loads InputStreams from ResourceAssets.
 */
public class ResourceAssetLoader implements ModelLoader<ResourceAsset, InputStream> {

    @Override
    public boolean handles(ResourceAsset resourceAsset) {
        return true;
    }

    @Nullable
    @Override
    public LoadData<InputStream> buildLoadData(ResourceAsset resourceAsset, int unusedWidth,
                                               int unusedHeight, Options options) {
        return new LoadData<>(resourceAsset.getKey(), new ResourceAssetFetcher(resourceAsset));
    }

    /**
     * Factory that constructs {@link ResourceAssetLoader} instances.
     */
    public static class ResourceAssetLoaderFactory
            implements ModelLoaderFactory<ResourceAsset, InputStream> {
        public ResourceAssetLoaderFactory() {
        }

        @Override
        public ModelLoader<ResourceAsset, InputStream> build(MultiModelLoaderFactory multiFactory) {
            return new ResourceAssetLoader();
        }

        @Override
        public void teardown() {
            // no-op
        }
    }

    /**
     * Glide DataFetcher for ResourceAsset.
     */
    protected static class ResourceAssetFetcher implements DataFetcher<InputStream> {

        private ResourceAsset mResourceAsset;

        public ResourceAssetFetcher(ResourceAsset resourceAsset) {
            mResourceAsset = resourceAsset;
        }

        @Override
        public void loadData(Priority priority, final DataCallback<? super InputStream> callback) {
            callback.onDataReady(
                    mResourceAsset.getResources().openRawResource(mResourceAsset.getResId()));
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
