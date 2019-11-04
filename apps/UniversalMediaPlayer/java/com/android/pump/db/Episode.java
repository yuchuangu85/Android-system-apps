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
public class Episode extends Video {
    private final Series mSeries;
    private final int mSeason;
    private final int mEpisode;

    // TODO(b/123706949) Lock mutable fields to ensure consistent updates
    private Uri mThumbnailUri;
    private Uri mPosterUri;
    private String mDescription;
    private boolean mLoaded;

    Episode(long id, @NonNull String mimeType, @NonNull Series series,
            int season, int episode) {
        super(id, mimeType);

        mSeries = series;
        if (season <= 0 || episode <= 0) {
            throw new IllegalArgumentException();
        }
        mSeason = season;
        mEpisode = episode;
    }

    public @NonNull Series getSeries() {
        return mSeries;
    }

    public int getSeason() {
        return mSeason;
    }

    public int getEpisode() {
        return mEpisode;
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
