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

package com.android.car.settings.applications.defaultapps;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;

import androidx.preference.Preference;

/** Utilities related to default apps. */
public class DefaultAppUtils {

    private DefaultAppUtils() {}

    /**
     * Sets the preference icon with a drawable that is scaled down to to avoid crashing Settings if
     * it's too big.
     */
    public static void setSafeIcon(Preference pref, Drawable icon, int maxDimension) {
        Drawable safeIcon = icon;
        if ((icon != null) && !(icon instanceof VectorDrawable)) {
            safeIcon = getSafeDrawable(icon, maxDimension);
        }
        pref.setIcon(safeIcon);
    }

    /**
     * Gets a drawable with a limited size to avoid crashing Settings if it's too big.
     *
     * @param original     original drawable, typically an app icon.
     * @param maxDimension maximum width/height, in pixels.
     */
    private static Drawable getSafeDrawable(Drawable original, int maxDimension) {
        int actualWidth = original.getMinimumWidth();
        int actualHeight = original.getMinimumHeight();

        if (actualWidth <= maxDimension && actualHeight <= maxDimension) {
            return original;
        }

        float scaleWidth = ((float) maxDimension) / actualWidth;
        float scaleHeight = ((float) maxDimension) / actualHeight;
        float scale = Math.min(scaleWidth, scaleHeight);
        int width = (int) (actualWidth * scale);
        int height = (int) (actualHeight * scale);

        Bitmap bitmap;
        if (original instanceof BitmapDrawable) {
            bitmap = Bitmap.createScaledBitmap(((BitmapDrawable) original).getBitmap(), width,
                    height, false);
        } else {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            original.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            original.draw(canvas);
        }
        return new BitmapDrawable(null, bitmap);
    }
}
