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

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.ResourceDecoder;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.resource.drawable.DrawableResource;

import java.io.IOException;

import androidx.annotation.Nullable;

/**
 * Identity {@link ResourceDecoder} implementation that simply passes through a Drawable.
 * <p>
 * Register this class in a {@link com.bumptech.glide.module.GlideModule} on Glide's
 * {@link com.bumptech.glide.Registry} in order to support loading {@link Drawable} objects for
 * {@link com.bumptech.glide.load.model.ModelLoader} implementations that directly load
 * {@link Drawable} data as an output type.
 */
public class DrawableResourceDecoder implements ResourceDecoder<Drawable, Drawable> {

    @Override
    public boolean handles(Drawable source, Options options) throws IOException {
        return true;
    }

    @Nullable
    @Override
    public Resource<Drawable> decode(Drawable source, int width, int height, Options options)
            throws IOException {
        return new DrawableResource<Drawable>(source) {
            @Override
            public Class<Drawable> getResourceClass() {
                return Drawable.class;
            }

            @Override
            public int getSize() {
                // Return size assuming that each pixel takes 4 bytes of memory (Bitmap.Config.ARGB_8888).
                return get().getIntrinsicWidth() * get().getIntrinsicHeight() * 4;
            }

            @Override
            public void recycle() {
                // no-op
            }
        };
    }
}
