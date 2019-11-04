/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.car.apps.common.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;

import androidx.annotation.StyleRes;

/**
 * Various utility methods associated with theming.
 */
public class Themes {

    /** Returns the color assigned to the given attribute. */
    public static int getAttrColor(Context context, int attr) {
        return getAttrColor(context, /*styleResId=*/ 0, attr);
    }

    /** Returns the color assigned to the given attribute defined in the given style. */
    public static int getAttrColor(Context context, @StyleRes int styleResId, int attr) {
        TypedArray ta = context.obtainStyledAttributes(styleResId, new int[]{attr});
        int colorAccent = ta.getColor(0, 0);
        ta.recycle();
        return colorAccent;
    }

    /**
     * Retrieve the ColorStateList for the given attribute. The value may be either a single solid
     * color or a reference to a color or complex {@link android.content.res.ColorStateList}
     * description.
     */
    public static ColorStateList getAttrColorStateList(Context context, int attr) {
        return getAttrColorStateList(context, /*styleResId=*/ 0, attr);
    }

    /**
     * Retrieve the ColorStateList for the given attribute defined in the given style. The value
     * may be either a single solid color or a reference to a color or complex {@link
     * android.content.res.ColorStateList} description.
     */
    public static ColorStateList getAttrColorStateList(Context context, @StyleRes int styleResId,
            int attr) {
        TypedArray ta = context.obtainStyledAttributes(styleResId, new int[]{attr});
        ColorStateList colorStateList = ta.getColorStateList(0);
        ta.recycle();
        return colorStateList;
    }

    /**
     * Returns the boolean assigned to the given attribute.
     */
    public static boolean getAttrBoolean(Context context, int attr) {
        return getAttrBoolean(context, /*styleResId=*/ 0, attr);
    }

    /** Returns the boolean assigned to the given attribute defined in the given style. */
    public static boolean getAttrBoolean(Context context, @StyleRes int styleResId, int attr) {
        TypedArray ta = context.obtainStyledAttributes(styleResId, new int[]{attr});
        boolean value = ta.getBoolean(0, false);
        ta.recycle();
        return value;
    }

    /** Returns the Drawable assigned to the given attribute. */
    public static Drawable getAttrDrawable(Context context, int attr) {
        return getAttrDrawable(context, /*styleResId=*/ 0, attr);
    }

    /** Returns the Drawable assigned to the given attribute defined in the given style. */
    public static Drawable getAttrDrawable(Context context, @StyleRes int styleResId, int attr) {
        TypedArray ta = context.obtainStyledAttributes(styleResId, new int[]{attr});
        Drawable value = ta.getDrawable(0);
        ta.recycle();
        return value;
    }

    /** Returns the Integer assigned to the given attribute. */
    public static int getAttrInteger(Context context, int attr) {
        return getAttrInteger(context, /*styleResId=*/ 0, attr);
    }

    /** Returns the Integer assigned to the given attribute defined in the given style. */
    public static int getAttrInteger(Context context, @StyleRes int styleResId, int attr) {
        TypedArray ta = context.obtainStyledAttributes(styleResId, new int[]{attr});
        int value = ta.getInteger(0, 0);
        ta.recycle();
        return value;
    }

    /** Returns the identifier of the resolved resource assigned to the given attribute. */
    public static int getAttrResourceId(Context context, int attr) {
        return getAttrResourceId(context, /*styleResId=*/ 0, attr);
    }

    /**
     * Returns the identifier of the resolved resource assigned to the given attribute defined in
     * the given style.
     */
    public static int getAttrResourceId(Context context, @StyleRes int styleResId, int attr) {
        TypedArray ta = context.obtainStyledAttributes(styleResId, new int[]{attr});
        int resId = ta.getResourceId(0, 0);
        ta.recycle();
        return resId;
    }

    /** Returns the dimension pixel size assigned to the given attribute. */
    public static int getAttrDimensionPixelSize(Context context, int attr) {
        return getAttrDimensionPixelSize(context, /*styleResId=*/ 0, attr);
    }

    /**
     * Returns the dimension pixel size assigned to the given attribute defined in the given
     * style.
     */
    public static int getAttrDimensionPixelSize(Context context, @StyleRes int styleResId,
            int attr) {
        TypedArray ta = context.obtainStyledAttributes(styleResId, new int[]{attr});
        int dimensionPixelSize = ta.getDimensionPixelSize(0, 0);
        ta.recycle();
        return dimensionPixelSize;
    }
}
