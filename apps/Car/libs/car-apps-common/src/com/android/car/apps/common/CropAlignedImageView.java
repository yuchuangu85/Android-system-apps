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

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * A {@link ImageView} that scales in a similar way as {@link ScaleType#CENTER_CROP} but aligning
 * the image to the specified edge of the view.
 */
public class CropAlignedImageView extends ImageView {

    private static final int ALIGN_HORIZONTAL_CENTER = 0;
    private static final int ALIGN_HORIZONTAL_LEFT = 1;
    private static final int ALIGN_HORIZONTAL_RIGHT = 2;

    private int mAlignHorizontal;
    private float mAdditionalScale = 1f;
    private int mFrameWidth;
    private int mFrameHeight;

    public CropAlignedImageView(Context context) {
        this(context, null);
    }

    public CropAlignedImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CropAlignedImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public CropAlignedImageView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs, defStyleAttr, defStyleRes);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        TypedArray ta = context.obtainStyledAttributes(
                attrs, R.styleable.CrossfadeImageView, defStyleAttr, defStyleRes);
        mAlignHorizontal = ta.getInt(R.styleable.CrossfadeImageView_align_horizontal,
                ALIGN_HORIZONTAL_CENTER);
        ta.recycle();
        setScaleType(ScaleType.MATRIX);
    }

    @Override
    protected boolean setFrame(int frameLeft, int frameTop, int frameRight, int frameBottom) {
        mFrameWidth = frameRight - frameLeft;
        mFrameHeight = frameBottom - frameTop;

        setMatrix();

        return super.setFrame(frameLeft, frameTop, frameRight, frameBottom);
    }

    private void setMatrix() {
        if (getDrawable() != null) {
            float originalImageWidth = (float) getDrawable().getIntrinsicWidth();
            float originalImageHeight = (float) getDrawable().getIntrinsicHeight();
            float fitHorizontallyScaleFactor = mFrameWidth / originalImageWidth;
            float fitVerticallyScaleFactor = mFrameHeight / originalImageHeight;
            float usedScaleFactor = Math.max(fitHorizontallyScaleFactor, fitVerticallyScaleFactor);

            // mAdditionalScale isn't factored into the fittedImageWidth
            // because we want to scale from the center of the fitted image, so our translations
            // shouldn't take it into effect
            float fittedImageWidth = originalImageWidth * usedScaleFactor;

            Matrix matrix = new Matrix();
            matrix.setTranslate(-originalImageWidth / 2f, -originalImageHeight / 2f);
            matrix.postScale(usedScaleFactor * mAdditionalScale,
                    usedScaleFactor * mAdditionalScale);
            float dx = 0;
            switch (mAlignHorizontal) {
                case ALIGN_HORIZONTAL_CENTER:
                    dx = mFrameWidth / 2f;
                    break;
                case ALIGN_HORIZONTAL_LEFT:
                    dx = fittedImageWidth / 2f;
                    break;
                case ALIGN_HORIZONTAL_RIGHT:
                    dx = (mFrameWidth - fittedImageWidth / 2f);
                    break;
            }
            matrix.postTranslate(dx, mFrameHeight / 2f);
            setImageMatrix(matrix);
        }
    }

    /**
     * Sets a scale to be applied on top of the scaling that was used to fit the
     * image to the frame of the view.
     *
     * This will scale the image from its center. This means it won't translate the image
     * any further, so if it was aligned to the left, the left of the image will expand past
     * the left edge of the view. Values <1 will cause black bars to appear.
     */
    public void setImageAdditionalScale(float scale) {
        mAdditionalScale = scale;
        setMatrix();
    }
}
