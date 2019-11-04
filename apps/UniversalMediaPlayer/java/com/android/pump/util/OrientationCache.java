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
import androidx.collection.ArrayMap;

import java.util.Map;

@AnyThread
class OrientationCache {
    private final Map<Uri, Integer> mOrientationCache = new ArrayMap<>();

    void put(@NonNull Uri key, @NonNull Bitmap bitmap) {
        int orientation = bitmap.getWidth() < bitmap.getHeight() ?
                Orientation.PORTRAIT : Orientation.LANDSCAPE;
        mOrientationCache.put(key, orientation);
    }

    @Orientation int get(@NonNull Uri key) {
        Integer value = mOrientationCache.get(key);
        if (value != null) {
            return value;
        }
        return Orientation.UNKNOWN;
    }
}
