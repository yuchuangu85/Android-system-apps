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
package com.android.wallpaper.widget;

import android.content.Context;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.widget.ImageView;

import androidx.annotation.Nullable;

/**
 * Custom ImageView that mimics the home launcher screen wallpaper position by aligning start and
 * centering vertically its drawable. Scales down the image as much as possible without
 * letterboxing.
 */
public class WallpaperThumbnailView extends ImageView {

    public WallpaperThumbnailView(Context context) {
        super(context);
        setScaleType(ScaleType.MATRIX);
    }

    public WallpaperThumbnailView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setScaleType(ScaleType.MATRIX);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        recomputeImageMatrix();
    }

    private void recomputeImageMatrix() {
        // The drawable may be null on the first layout pass.
        if (getDrawable() == null) {
            return;
        }

        final Matrix matrix = getImageMatrix();

        final int viewWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        final int viewHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        final int drawableWidth = getDrawable().getIntrinsicWidth();
        final int drawableHeight = getDrawable().getIntrinsicHeight();

        float scale;
        if (drawableWidth * viewHeight > drawableHeight * viewWidth) {
            scale = (float) viewHeight / drawableHeight;
        } else {
            scale = (float) viewWidth / drawableWidth;
        }

        // Set scale such that the maximum area of the drawable is shown without letterboxing.
        matrix.setScale(scale, scale);

        // Center the drawable vertically within the view.
        if ((drawableHeight * scale) > viewHeight) {
            float dy = -(((drawableHeight * scale) - viewHeight) / 2f);
            matrix.postTranslate(0, dy);
        }

        // If layout direction is RTL, then matrix should align the image towards the right.
        if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) {
            float dx = -((drawableWidth * scale) - viewWidth);
            matrix.postTranslate(dx, 0);
        }

        setImageMatrix(matrix);
        invalidate();
    }
}
