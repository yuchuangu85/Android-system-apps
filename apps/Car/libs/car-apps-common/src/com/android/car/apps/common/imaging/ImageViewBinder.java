/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.car.apps.common.imaging;

import android.annotation.Nullable;
import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Size;
import android.view.View;
import android.widget.ImageView;

import com.android.car.apps.common.CommonFlags;
import com.android.car.apps.common.R;

/**
 * Binds images to an image view.
 * @param <T> see {@link ImageRef}.
 */
public class ImageViewBinder<T extends ImageBinder.ImageRef> extends ImageBinder<T> {

    @Nullable
    private final ImageView mImageView;
    private final boolean mFlagBitmaps;

    /** See {@link ImageViewBinder} and {@link ImageBinder}. */
    public ImageViewBinder(Size maxImageSize, @Nullable ImageView imageView) {
        this(PlaceholderType.FOREGROUND, maxImageSize, imageView, false);
    }

    /**
     * See {@link ImageViewBinder} and {@link ImageBinder}.
     * @param flagBitmaps whether this binder should flag bitmap drawables if flagging is enabled.
     */
    public ImageViewBinder(PlaceholderType type, Size maxImageSize,
            @Nullable ImageView imageView, boolean flagBitmaps) {
        super(type, maxImageSize);
        mImageView = imageView;
        mFlagBitmaps = flagBitmaps;
    }

    @Override
    protected void setDrawable(@Nullable Drawable drawable) {
        if (mImageView != null) {
            mImageView.setImageDrawable(drawable);
            mImageView.setVisibility((drawable != null) ? View.VISIBLE : View.GONE);
            if (mFlagBitmaps) {
                CommonFlags flags = CommonFlags.getInstance(mImageView.getContext());
                if (flags.shouldFlagImproperImageRefs()) {
                    if (drawable instanceof BitmapDrawable) {
                        int tint = mImageView.getContext().getColor(
                                R.color.improper_image_refs_tint_color);
                        mImageView.setColorFilter(tint);
                    } else {
                        mImageView.clearColorFilter();
                    }
                }
            }
        }
    }

    @Override
    public void setImage(Context context, @Nullable T newRef) {
        if (mImageView != null) {
            super.setImage(context, newRef);
        }
    }

    @Override
    protected void prepareForNewBinding(Context context) {
        super.prepareForNewBinding(context);
        mImageView.setImageBitmap(null);
        mImageView.setImageDrawable(null);
        mImageView.clearColorFilter();
    }

}
