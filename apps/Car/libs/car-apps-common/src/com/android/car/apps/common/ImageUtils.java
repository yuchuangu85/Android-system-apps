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

package com.android.car.apps.common;

import android.annotation.NonNull;
import android.content.Context;
import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.Size;

/**
 * Utility methods to manipulate images.
 */
public class ImageUtils {

    private static final float MIN_BLUR = 0.1f;
    private static final float MAX_BLUR = 25f;

    /**
     * Blurs the given image by scaling it down by the given factor and applying the given
     * blurring radius.
     */
    @NonNull
    public static Bitmap blur(Context context, @NonNull Bitmap image, Size bitmapTargetSize,
            float bitmapBlurPercent) {
        image = maybeResize(image, bitmapTargetSize);
        float blurRadius = bitmapBlurPercent * getBitmapDimension(image);

        if (blurRadius <= MIN_BLUR) return image;
        if (blurRadius > MAX_BLUR) blurRadius = MAX_BLUR;

        if (image.getConfig() != Bitmap.Config.ARGB_8888) {
            image = image.copy(Bitmap.Config.ARGB_8888, true);
        }

        Bitmap outputBitmap = Bitmap.createBitmap(image);

        RenderScript rs = RenderScript.create(context);
        ScriptIntrinsicBlur theIntrinsic = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
        Allocation tmpIn = Allocation.createFromBitmap(rs, image);
        Allocation tmpOut = Allocation.createFromBitmap(rs, outputBitmap);
        theIntrinsic.setRadius(blurRadius);
        theIntrinsic.setInput(tmpIn);
        theIntrinsic.forEach(tmpOut);
        tmpOut.copyTo(outputBitmap);

        return outputBitmap;
    }

    private static Bitmap maybeResize(@NonNull Bitmap image, Size bitmapTargetSize) {
        if (image.getWidth() > bitmapTargetSize.getWidth()
                || image.getHeight() > bitmapTargetSize.getHeight()) {
            int imageDim = getBitmapDimension(image);
            float scale = getAverage(bitmapTargetSize) / (float) imageDim;
            int width = Math.round(scale * image.getWidth());
            int height = Math.round(scale * image.getHeight());
            return Bitmap.createScaledBitmap(image, width, height, false);
        } else {
            return image;
        }
    }

    private static int getAverage(@NonNull Size size) {
        return (size.getWidth() + size.getHeight()) / 2;
    }

    private static int getBitmapDimension(@NonNull Bitmap image) {
        return (image.getWidth() + image.getHeight()) / 2;
    }
}
