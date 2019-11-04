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
package com.android.car.apps.common;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Size;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

/**
 * A View to place a large, blurred image in the background.
 * Intended for Car's Dialer and Media apps.
 */
public class BackgroundImageView extends ConstraintLayout {

    private CrossfadeImageView mImageView;

    /** Configuration (controlled from resources) */
    private Size mBitmapTargetSize;
    private float mBitmapBlurPercent;

    private View mDarkeningScrim;

    public BackgroundImageView(Context context) {
        this(context, null);
    }

    public BackgroundImageView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.backgroundImageViewStyle);
    }

    public BackgroundImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        inflate(getContext(), R.layout.background_image, this);

        mImageView = findViewById(R.id.background_image_image);
        mDarkeningScrim = findViewById(R.id.background_image_darkening_scrim);

        int size = getResources().getInteger(R.integer.background_bitmap_target_size_px);
        mBitmapTargetSize = new Size(size, size);
        mBitmapBlurPercent = getResources().getFloat(R.dimen.background_bitmap_blur_percent);

        TypedArray a = context.getTheme().obtainStyledAttributes(attrs,
                R.styleable.BackgroundImageView, defStyle, 0);

        try {
            setImageAdditionalScale(a.getFloat(R.styleable.BackgroundImageView_imageAdditionalScale,
                    1.05f));
        } finally {
            a.recycle();
        }
    }

    /**
     * @deprecated Use {@link #setBackgroundDrawable} instead, and make sure to only call when the
     * image is actually different! TODO(b/139387273).
     * Sets the image to display to a bitmap
     * @param bitmap The image to show. It will be scaled to the correct size and blurred.
     * @param showAnimation Whether or not to cross fade to the new image
     */
    @Deprecated
    public void setBackgroundImage(@Nullable Bitmap bitmap, boolean showAnimation) {
        Drawable drawable = (bitmap != null) ? new BitmapDrawable(bitmap) : null;
        updateBlur(drawable, showAnimation);
    }

    /** Sets the drawable that will be displayed blurred by this view. */
    public void setBackgroundDrawable(@Nullable Drawable drawable) {
        updateBlur(drawable, true);
    }

    private void updateBlur(@Nullable Drawable drawable, boolean showAnimation) {
        if (drawable == null) {
            mImageView.setImageBitmap(null, false);
            return;
        }

        Bitmap src = BitmapUtils.fromDrawable(drawable, mBitmapTargetSize);
        Bitmap blurred = ImageUtils.blur(getContext(), src, mBitmapTargetSize, mBitmapBlurPercent);
        mImageView.setImageBitmap(blurred, showAnimation);
        invalidate();
        requestLayout();
    }

    /** Sets the background to a color */
    public void setBackgroundColor(int color) {
        mImageView.setBackgroundColor(color);
    }

    /** Dims/undims the background image by 30% */
    public void setDimmed(boolean dim) {
        mDarkeningScrim.setVisibility(dim ? View.VISIBLE : View.GONE);
    }

    /**
     * Sets a scale to be applied on top of the scaling that was used to fit the
     * image to the frame of the view.
     *
     * See {@link
     * com.android.car.apps.common.CropAlignedImageView#setImageAdditionalScale(float)}
     * for more details.
     */
    public void setImageAdditionalScale(float scale) {
        mImageView.setImageAdditionalScale(scale);
    }
}
