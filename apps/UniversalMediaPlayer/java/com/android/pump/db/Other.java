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
public class Other extends Video {
    // TODO(b/123706949) Lock mutable fields to ensure consistent updates
    private String mTitle;
    private long mDuration;
    private long mDateTaken;
    private double mLatitude;
    private double mLongitude;
    private Uri mThumbnailUri;
    private boolean mLoaded;

    Other(long id, @NonNull String mimeType, @NonNull String title) {
        super(id, mimeType);

        mTitle = title;
        mDuration = Long.MIN_VALUE;
        mDateTaken = Long.MIN_VALUE;
        mLatitude = Double.NaN;
        mLongitude = Double.NaN;
    }

    public @NonNull String getTitle() {
        return mTitle;
    }

    public boolean setTitle(@NonNull String title) {
        if (title.equals(mTitle)) {
            return false;
        }
        mTitle = title;
        return true;
    }

    public boolean hasDuration() {
        return mDuration >= 0;
    }

    public long getDuration() {
        if (!hasDuration()) {
            throw new IllegalStateException();
        }
        return mDuration;
    }

    public boolean setDuration(long duration) {
        if (duration == mDuration) {
            return false;
        }
        mDuration = duration;
        return true;
    }

    public boolean hasDateTaken() {
        return mDateTaken >= 0;
    }

    public long getDateTaken() {
        if (!hasDateTaken()) {
            throw new IllegalStateException();
        }
        return mDateTaken;
    }

    public boolean setDateTaken(long dateTaken) {
        if (dateTaken == mDateTaken) {
            return false;
        }
        mDateTaken = dateTaken;
        return true;
    }

    public boolean hasLatLong() {
        return !Double.isNaN(mLatitude) && !Double.isNaN(mLongitude);
    }

    public boolean setLatLong(double latitude, double longitude) {
        if (latitude == mLatitude || longitude == mLongitude) {
            return false;
        }
        mLatitude = latitude;
        mLongitude = longitude;
        return true;
    }

    public double getLatitude() {
        if (!hasLatLong()) {
            throw new IllegalStateException();
        }
        return mLatitude;
    }

    public double getLongitude() {
        if (!hasLatLong()) {
            throw new IllegalStateException();
        }
        return mLongitude;
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

    boolean isLoaded() {
        return mLoaded;
    }

    void setLoaded() {
        mLoaded = true;
    }
}
