/*
 * Copyright 2018 The Android Open Source Project
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

package com.android.pump.util;

import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LruCache;
import androidx.core.graphics.BitmapCompat;

@AnyThread
class BitmapCache {
    private static final int CACHE_SIZE =
            (int) Math.min(Runtime.getRuntime().maxMemory() / 8, Integer.MAX_VALUE / 4);

    private final MemoryCache mMemoryCache = new MemoryCache(CACHE_SIZE);

    void put(@NonNull Uri key, @NonNull Bitmap bitmap) {
        mMemoryCache.put(key, bitmap);
    }

    @Nullable Bitmap get(@NonNull Uri key) {
        return mMemoryCache.get(key);
    }

    void clear() {
        mMemoryCache.evictAll();
    }

    private static class MemoryCache extends LruCache<Uri, Bitmap> {
        private MemoryCache(int maxSize) {
            super(maxSize);
        }

        @Override
        protected int sizeOf(@NonNull Uri key, @NonNull Bitmap bitmap) {
            return BitmapCompat.getAllocationByteCount(bitmap);
        }
    }
}
