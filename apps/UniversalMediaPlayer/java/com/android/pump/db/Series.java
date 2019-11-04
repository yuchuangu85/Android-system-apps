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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.android.pump.util.Collections.binarySearch;

@AnyThread
public class Series {
    private final String mTitle;
    private final int mYear;

    // TODO(b/123706949) Lock mutable fields to ensure consistent updates
    private Uri mPosterUri;
    private String mDescription;
    private final List<List<Episode>> mSeasons = new ArrayList<>();
    private boolean mLoaded;

    Series(@NonNull String title) {
        mTitle = title;
        mYear = Integer.MIN_VALUE;
    }

    Series(@NonNull String title, int year) {
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

    public @NonNull List<List<Episode>> getSeasons() {
        return Collections.unmodifiableList(mSeasons);
    }

    boolean addEpisode(@NonNull Episode episode) {
        int seriesLocation = binarySearch(mSeasons, episode.getSeason(),
                (season) -> season.get(0).getSeason());
        if (seriesLocation >= 0) {
            List<Episode> series = mSeasons.get(seriesLocation);
            int episodeLocation = binarySearch(series, episode.getEpisode(), Episode::getEpisode);
            if (episodeLocation >= 0) {
                if (episode.equals(series.get(episodeLocation))) {
                    return false;
                }
                // TODO(b/127524752) This should kind of be okay (i.e. handle gracefully)
                throw new IllegalStateException("Two episodes with the same season & episode #");
            } else {
                series.add(~episodeLocation, episode);
            }
        } else {
            List<Episode> series = new ArrayList<>();
            series.add(episode);
            mSeasons.add(~seriesLocation, series);
        }
        return true;
    }

    boolean isLoaded() {
        return mLoaded;
    }

    void setLoaded() {
        mLoaded = true;
    }

    @Override
    public final boolean equals(@Nullable Object obj) {
        return obj instanceof Series && mTitle.equals(((Series) obj).mTitle)
                && mYear == ((Series) obj).mYear;
    }

    @Override
    public final int hashCode() {
        return mTitle.hashCode() ^ mYear;
    }
}
