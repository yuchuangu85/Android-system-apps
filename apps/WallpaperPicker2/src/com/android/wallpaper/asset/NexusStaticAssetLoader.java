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

import com.android.wallpaper.asset.ResourceAssetLoader.ResourceAssetFetcher;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;

import java.io.InputStream;

import androidx.annotation.Nullable;

/**
 * Glide ModelLoader which loads InputStreams from NexusStaticAssets.
 */
public class NexusStaticAssetLoader implements ModelLoader<NexusStaticAsset, InputStream> {

    @Override
    public boolean handles(NexusStaticAsset nexusStaticAsset) {
        return true;
    }

    @Nullable
    @Override
    public LoadData<InputStream> buildLoadData(NexusStaticAsset nexusStaticAsset, int unusedWidth,
                                               int unusedHeight, Options options) {
        return new LoadData<>(nexusStaticAsset.getKey(), new ResourceAssetFetcher(nexusStaticAsset));
    }

    /**
     * Factory that constructs {@link NexusStaticAssetLoader} instances.
     */
    public static class NexusStaticAssetLoaderFactory
            implements ModelLoaderFactory<NexusStaticAsset, InputStream> {
        public NexusStaticAssetLoaderFactory() {
        }

        @Override
        public ModelLoader<NexusStaticAsset, InputStream> build(MultiModelLoaderFactory multiFactory) {
            return new NexusStaticAssetLoader();
        }

        @Override
        public void teardown() {
            // no-op
        }
    }
}
