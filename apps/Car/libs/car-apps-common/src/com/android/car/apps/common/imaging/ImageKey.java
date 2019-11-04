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

import android.net.Uri;
import android.util.Size;

import com.android.car.apps.common.UriUtils;
import com.android.internal.util.Preconditions;

import java.util.Objects;

/** Class to identify image load requests. */
@SuppressWarnings("WeakerAccess")
class ImageKey {
    public final Uri mImageUri;
    public final Size mMaxImageSize;

    /** imageUri must NOT be {@link com.android.car.apps.common.UriUtils#isEmpty}*/
    ImageKey(Uri imageUri, Size maxImageSize) {
        Preconditions.checkArgument(!UriUtils.isEmpty(imageUri), "Empty uri!");
        mImageUri = imageUri;
        mMaxImageSize = maxImageSize;
    }

    /** Auto generated. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ImageKey imageKey = (ImageKey) o;
        return mImageUri.equals(imageKey.mImageUri)
                && mMaxImageSize.equals(imageKey.mMaxImageSize);
    }

    /** Auto generated. */
    @Override
    public int hashCode() {
        return Objects.hash(mImageUri, mMaxImageSize);
    }

    /** Auto generated. */
    @Override
    public String toString() {
        return "ImageKey{"
                + "mImageUri=" + mImageUri
                + ", mMaxImageSize=" + mMaxImageSize
                + '}';
    }
}
