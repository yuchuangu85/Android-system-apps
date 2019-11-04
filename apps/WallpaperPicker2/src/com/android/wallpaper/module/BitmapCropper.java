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
package com.android.wallpaper.module;

import android.graphics.Bitmap;
import android.graphics.Rect;

import com.android.wallpaper.asset.Asset;

import androidx.annotation.Nullable;

/**
 * Interface for classes which perform crop operations on bitmaps.
 */
public interface BitmapCropper {

    /**
     * Crops and scales a bitmap per the given scale factor and crop area (at target scale) from the
     * source asset.
     */
    void cropAndScaleBitmap(Asset asset, float scale, Rect cropRect, Callback callback);

    /**
     * Interface for receiving the output bitmap of crop operations.
     */
    interface Callback {
        void onBitmapCropped(Bitmap croppedBitmap);

        /**
         * Called on an error during the crop. If a Throwable was caught along the way, it is passed
         * here.
         */
        void onError(@Nullable Throwable e);
    }
}
