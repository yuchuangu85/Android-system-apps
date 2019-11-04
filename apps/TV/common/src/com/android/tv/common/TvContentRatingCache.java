/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.tv.common;

import android.media.tv.TvContentRating;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import com.android.tv.common.memory.MemoryManageable;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/** TvContentRating cache. */
public final class TvContentRatingCache implements MemoryManageable {
    private static final String TAG = "TvContentRatings";

    private static final TvContentRatingCache INSTANCE = new TvContentRatingCache();

    public static TvContentRatingCache getInstance() {
        return INSTANCE;
    }

    // @GuardedBy("TvContentRatingCache.this")
    private final Map<String, ImmutableList<TvContentRating>> mRatingsMultiMap = new ArrayMap<>();

    /**
     * Returns an array TvContentRatings from a string of comma separated set of rating strings
     * creating each from {@link TvContentRating#unflattenFromString(String)} if needed or an empty
     * list if the string is empty or contains no valid ratings.
     */
    public synchronized ImmutableList<TvContentRating> getRatings(
            @Nullable String commaSeparatedRatings) {
        if (TextUtils.isEmpty(commaSeparatedRatings)) {
            return ImmutableList.of();
        }
        ImmutableList<TvContentRating> tvContentRatings;
        if (mRatingsMultiMap.containsKey(commaSeparatedRatings)) {
            tvContentRatings = mRatingsMultiMap.get(commaSeparatedRatings);
        } else {
            String normalizedRatings =
                    TextUtils.join(",", getSortedSetFromCsv(commaSeparatedRatings));
            if (mRatingsMultiMap.containsKey(normalizedRatings)) {
                tvContentRatings = mRatingsMultiMap.get(normalizedRatings);
            } else {
                tvContentRatings = stringToContentRatings(commaSeparatedRatings);
                mRatingsMultiMap.put(normalizedRatings, tvContentRatings);
            }
            if (!normalizedRatings.equals(commaSeparatedRatings)) {
                // Add an entry so the non normalized entry points to the same result;
                mRatingsMultiMap.put(commaSeparatedRatings, tvContentRatings);
            }
        }
        return tvContentRatings;
    }

    /** Returns a sorted array of TvContentRatings from a comma separated string of ratings. */
    @VisibleForTesting
    static ImmutableList<TvContentRating> stringToContentRatings(
            @Nullable String commaSeparatedRatings) {
        if (TextUtils.isEmpty(commaSeparatedRatings)) {
            return ImmutableList.of();
        }
        Set<String> ratingStrings = getSortedSetFromCsv(commaSeparatedRatings);
        ImmutableList.Builder<TvContentRating> contentRatings = ImmutableList.builder();
        for (String rating : ratingStrings) {
            try {
                contentRatings.add(TvContentRating.unflattenFromString(rating));
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Can't parse the content rating: '" + rating + "'", e);
            }
        }
        return contentRatings.build();
    }

    private static Set<String> getSortedSetFromCsv(String commaSeparatedRatings) {
        String[] ratingStrings = commaSeparatedRatings.split("\\s*,\\s*");
        return toSortedSet(ratingStrings);
    }

    private static Set<String> toSortedSet(String[] ratingStrings) {
        if (ratingStrings.length == 0) {
            return Collections.EMPTY_SET;
        } else if (ratingStrings.length == 1) {
            return Collections.singleton(ratingStrings[0]);
        } else {
            // Using a TreeSet here is not very efficient, however it is good enough because:
            //  - the results are cached
            //  - in testing with multiple TISs, less than 50 entries are created
            SortedSet<String> set = new TreeSet<>();
            Collections.addAll(set, ratingStrings);
            return set;
        }
    }

    /**
     * Returns a string of each flattened content rating, sorted and concatenated together with a
     * comma.
     */
    @Nullable
    public static String contentRatingsToString(
            @Nullable ImmutableList<TvContentRating> contentRatings) {
        if (contentRatings == null) {
            return null;
        }
        SortedSet<String> ratingStrings = new TreeSet<>();
        for (TvContentRating rating : contentRatings) {
            ratingStrings.add(rating.flattenToString());
        }
        return TextUtils.join(",", ratingStrings);
    }

    @Override
    public synchronized void performTrimMemory(int level) {
        mRatingsMultiMap.clear();
    }

    private TvContentRatingCache() {}
}
