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


import static com.android.internal.util.Preconditions.checkNotNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Size;

import com.android.car.apps.common.UriUtils;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A helper class to bind an image to a UI element, updating the image when needed.
 * @param <T> see {@link ImageRef}.
 */
public class ImageBinder<T extends ImageBinder.ImageRef> {

    public enum PlaceholderType {
        /** For elements that don't want to display a placeholder (like tabs). */
        NONE,
        /** A placeholder displayed in the foreground, typically has more details. */
        FOREGROUND,
        /** A placeholder displayed in the background, typically has less details. */
        BACKGROUND
    }

    /**
     * Interface to define keys for identifying images.
     */
    public interface ImageRef {

        /** Returns whether the given {@link ImageRef} and this one reference the same image. */
        boolean equals(Context context, Object other);

        /** Returns the uri to use to retrieve the image. */
        @Nullable Uri getImageURI();

        /** For when the image ref doesn't always use a uri. */
        default @Nullable Drawable getImage(Context context) {
            return null;
        }

        /** Returns a placeholder for images that can't be found. */
        Drawable getPlaceholder(Context context, @NonNull PlaceholderType type);
    }

    private final PlaceholderType mPlaceholderType;
    private final Size mMaxImageSize;
    @Nullable
    private final Consumer<Drawable> mClient;

    private T mCurrentRef;
    private ImageKey mCurrentKey;
    private BiConsumer<ImageKey, Drawable> mFetchReceiver;


    public ImageBinder(@NonNull PlaceholderType type, @NonNull Size maxImageSize,
            @NonNull Consumer<Drawable> consumer) {
        mPlaceholderType = checkNotNull(type, "Need a type");
        mMaxImageSize = checkNotNull(maxImageSize, "Need a size");
        mClient = checkNotNull(consumer, "Cannot bind a null consumer");
    }

    protected ImageBinder(@NonNull PlaceholderType type, @NonNull Size maxImageSize) {
        mPlaceholderType = checkNotNull(type, "Need a type");
        mMaxImageSize = checkNotNull(maxImageSize, "Need a size");
        mClient = null;
    }

    protected void setDrawable(@Nullable Drawable drawable) {
        if (mClient != null) {
            mClient.accept(drawable);
        }
    }

    /** Fetches a new image if needed. */
    public void setImage(Context context, @Nullable T newRef) {
        if (isSameImage(context, newRef)) {
            return;
        }

        prepareForNewBinding(context);

        mCurrentRef = newRef;

        if (mCurrentRef == null) {
            setDrawable(null);
        } else {
            Drawable image = mCurrentRef.getImage(context);
            if (image != null) {
                setDrawable(image);
                return;
            }

            mFetchReceiver = (key, drawable) -> {
                if (Objects.equals(mCurrentKey, key)) {
                    Drawable displayed =
                            (drawable == null && mPlaceholderType != PlaceholderType.NONE)
                                    ? mCurrentRef.getPlaceholder(context, mPlaceholderType)
                                    : drawable;
                    setDrawable(displayed);
                    onRequestFinished();
                }
            };

            if (UriUtils.isEmpty(mCurrentRef.getImageURI())) {
                mCurrentKey = null;
                mFetchReceiver.accept(null, null);
            } else {
                mCurrentKey = new ImageKey(mCurrentRef.getImageURI(), mMaxImageSize);
                getImageFetcher(context).getImage(context, mCurrentKey, mFetchReceiver);
            }
        }
    }

    private boolean isSameImage(Context context, @Nullable T newRef) {
        if (mCurrentRef == null && newRef == null) return true;

        if (mCurrentRef != null && newRef != null) {
            return mCurrentRef.equals(context, newRef);
        }

        return false;
    }

    private LocalImageFetcher getImageFetcher(Context context) {
        return LocalImageFetcher.getInstance(context);
    }

    protected void prepareForNewBinding(Context context) {
        if (mCurrentKey != null) {
            getImageFetcher(context).cancelRequest(mCurrentKey, mFetchReceiver);
            onRequestFinished();
        }
    }

    private void onRequestFinished() {
        mCurrentKey = null;
        mFetchReceiver = null;
    }
}
