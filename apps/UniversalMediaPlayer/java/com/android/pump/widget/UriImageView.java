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

package com.android.pump.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;

import androidx.annotation.AttrRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.pump.util.Globals;
import com.android.pump.util.ImageLoader;
import com.android.pump.util.Scheme;

@UiThread
public class UriImageView extends PlaceholderImageView {
    private Uri mUri;

    public UriImageView(@NonNull Context context) {
        super(context);
    }

    public UriImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public UriImageView(@NonNull Context context, @Nullable AttributeSet attrs,
            @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setImageResource(@DrawableRes int resId) {
        super.setImageResource(resId);
        mUri = null;
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);
        mUri = null;
    }

    @Override
    public void setImageBitmap(@Nullable Bitmap bm) {
        super.setImageBitmap(bm);
        mUri = null;
    }

    @Override
    public void setImageURI(@Nullable Uri uri) {
        setImageDrawable(null);
        if (uri == null) {
            return;
        }
        if (Scheme.isFile(uri) || Scheme.isHttp(uri) || Scheme.isHttps(uri)) {
            mUri = uri;
            loadImage();
        } else {
            super.setImageURI(uri);
        }
    }

    private void loadImage() {
        ImageLoader imageLoader = Globals.getImageLoader(getContext());
        imageLoader.loadImage(mUri, (loadedUri, bitmap) -> {
            if (mUri != null && mUri.equals(loadedUri)) {
                setImageBitmap(bitmap);
                mUri = loadedUri;
            }
        });
    }
}
