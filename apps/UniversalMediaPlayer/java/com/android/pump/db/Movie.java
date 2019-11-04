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

package com.android.pump.db;

import android.net.Uri;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@AnyThread
public class Movie extends Video {
    private final String mTitle;
    private final int mYear;

    // TODO(b/123706949) Lock mutable fields to ensure consistent updates
    private Uri mThumbnailUri;
    private Uri mPosterUri;
    private String mSynopsis;
    private String mDescription;
    private boolean mLoaded;

    Movie(long id, @NonNull String mimeType, @NonNull String title) {
        super(id, mimeType);

        mTitle = title;
        mYear = Integer.MIN_VALUE;
    }

    Movie(long id, @NonNull String mimeType, @NonNull String title, int year) {
        super(id, mimeType);

        mTitle = title;
        if (year <= 0) {
            throw new IllegalArgumentException();
        }
        mYear = year;
    }

    public @NonNull String getTitle() {
        return mTitle;
    }

    public boolean hasYear() {
        return mYear > 0;
    }

    public int getYear() {
        if (!hasYear()) {
            throw new IllegalStateException();
        }
        return mYear;
    }

    public @Nullable Uri getThumbnailUri() {
        return mThumbnailUri;
    }

    public boolean setThumbnailUri(@NonNull Uri thumbnailUri) {
        if (thumbnailUri.equals(mThumbnailUri)) {
            return false;
        }
        mThumbnailUri = thumbnailUri;
        return true;
    }

    public @Nullable Uri getPosterUri() {
        return mPosterUri;
    }

    public boolean setPosterUri(@NonNull Uri posterUri) {
        if (posterUri.equals(mPosterUri)) {
            return false;
        }
        mPosterUri = posterUri;
        return true;
    }

    public @Nullable String getSynopsis() {
        return mSynopsis;
    }

    public boolean setSynopsis(@NonNull String synopsis) {
        if (synopsis.equals(mSynopsis)) {
            return false;
        }
        mSynopsis = synopsis;
        return true;
    }

    public @Nullable String getDescription() {
        return mDescription;
    }

    public boolean setDescription(@NonNull String description) {
        if (description.equals(mDescription)) {
            return false;
        }
        mDescription = description;
        return true;
    }

    boolean isLoaded() {
        return mLoaded;
    }

    void setLoaded() {
        mLoaded = true;
    }
}
