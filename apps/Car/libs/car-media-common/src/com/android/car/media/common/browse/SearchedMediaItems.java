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

package com.android.car.media.common.browse;

import static java.util.stream.Collectors.toList;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;

import androidx.lifecycle.LiveData;

import com.android.car.media.common.MediaItemMetadata;

import java.util.List;

/**
 * A LiveData that provides access to a MediaBrowser's search results for a given query
 */
public class SearchedMediaItems extends LiveData<List<MediaItemMetadata>> {

    private final MediaBrowserCompat mBrowser;
    private final String mQuery;

    private final MediaBrowserCompat.SearchCallback mCallback =
            new MediaBrowserCompat.SearchCallback() {
        @Override
        public void onSearchResult(@NonNull String query, Bundle extras,
                                   @NonNull List<MediaBrowserCompat.MediaItem> items) {
            super.onSearchResult(query, extras, items);
            setValue(items.stream().map(MediaItemMetadata::new).collect(toList()));
        }

        @Override
        public void onError(@NonNull String query, Bundle extras) {
            super.onError(query, extras);
            setValue(null);
        }
    };

    SearchedMediaItems(@NonNull MediaBrowserCompat mediaBrowser, @Nullable String query) {
        mBrowser = mediaBrowser;
        mQuery = query;
    }

    @Override
    protected void onActive() {
        super.onActive();
        mBrowser.search(mQuery, null, mCallback);
    }
}
