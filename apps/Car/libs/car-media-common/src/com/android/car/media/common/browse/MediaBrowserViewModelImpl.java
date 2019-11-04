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

import static androidx.lifecycle.Transformations.map;

import static com.android.car.arch.common.LiveDataFunctions.dataOf;
import static com.android.car.arch.common.LiveDataFunctions.loadingSwitchMap;
import static com.android.car.arch.common.LiveDataFunctions.pair;
import static com.android.car.arch.common.LiveDataFunctions.split;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UiThread;
import android.app.Application;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.text.TextUtils;

import androidx.annotation.RestrictTo;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.android.car.arch.common.FutureData;
import com.android.car.arch.common.switching.SwitchingLiveData;
import com.android.car.media.common.MediaConstants;
import com.android.car.media.common.MediaItemMetadata;

import java.util.List;

/**
 * Contains observable data needed for displaying playback and browse/search UI. Instances can be
 * obtained via {@link MediaBrowserViewModel.Factory}
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class MediaBrowserViewModelImpl extends AndroidViewModel implements
        MediaBrowserViewModel.WithMutableBrowseId {

    private final SwitchingLiveData<MediaBrowserCompat> mMediaBrowserSwitch =
            SwitchingLiveData.newInstance();

    private final MutableLiveData<String> mCurrentBrowseId = new MutableLiveData<>();
    private final MutableLiveData<String> mCurrentSearchQuery = dataOf(null);
    private final LiveData<MediaBrowserCompat> mConnectedMediaBrowser =
            map(mMediaBrowserSwitch.asLiveData(), MediaBrowserViewModelImpl::requireConnected);

    private final LiveData<FutureData<List<MediaItemMetadata>>> mSearchedMediaItems;
    private final LiveData<FutureData<List<MediaItemMetadata>>> mBrowsedMediaItems;

    private final LiveData<BrowseState> mBrowseState;

    private final LiveData<String> mPackageName;

    public MediaBrowserViewModelImpl(@NonNull Application application) {
        super(application);

        mPackageName = map(mConnectedMediaBrowser,
                mediaBrowser -> {
                    if (mediaBrowser == null) return null;
                    return mediaBrowser.getServiceComponent().getPackageName();
                });

        mBrowsedMediaItems =
                loadingSwitchMap(pair(mConnectedMediaBrowser, mCurrentBrowseId),
                        split((mediaBrowser, browseId) ->
                                mediaBrowser == null
                                        ? null
                                        : new BrowsedMediaItems(mediaBrowser, browseId)));
        mSearchedMediaItems =
                loadingSwitchMap(pair(mConnectedMediaBrowser, mCurrentSearchQuery),
                        split((mediaBrowser, query) ->
                                (mediaBrowser == null || TextUtils.isEmpty(query))
                                        ? null
                                        : new SearchedMediaItems(mediaBrowser, query)));

        mBrowseState = new MediatorLiveData<BrowseState>() {
            {
                setValue(BrowseState.EMPTY);
                addSource(mBrowsedMediaItems, items -> update());
            }

            private void update() {
                setValue(getState());
            }

            private BrowseState getState() {
                if (mBrowsedMediaItems.getValue() == null) {
                    // Uninitialized
                    return BrowseState.EMPTY;
                }
                if (mBrowsedMediaItems.getValue().isLoading()) {
                    return BrowseState.LOADING;
                }
                List<MediaItemMetadata> items = mBrowsedMediaItems.getValue().getData();
                if (items == null) {
                    // Normally this could be null if it hasn't been initialized, but in that case
                    // isLoading would not be false, so this means it must have encountered an
                    // error.
                    return BrowseState.ERROR;
                }
                if (items.isEmpty()) {
                    return BrowseState.EMPTY;
                }
                return BrowseState.LOADED;
            }
        };

    }

    private static MediaBrowserCompat requireConnected(@Nullable MediaBrowserCompat mediaBrowser) {
        if (mediaBrowser != null && !mediaBrowser.isConnected()) {
            throw new IllegalStateException(
                    "Only connected MediaBrowsers may be provided to MediaBrowserViewModel.");
        }
        return mediaBrowser;
    }

    /**
     * Set the source {@link MediaBrowserCompat} to use for browsing. If {@code mediaBrowser} emits
     * non-null, the MediaBrowser emitted must already be in a connected state.
     */
    void setConnectedMediaBrowser(@Nullable LiveData<MediaBrowserCompat> mediaBrowser) {
        mMediaBrowserSwitch.setSource(mediaBrowser);
    }

    LiveData<? extends MediaBrowserCompat> getMediaBrowserSource() {
        return mMediaBrowserSwitch.getSource();
    }

    /**
     * Set the current item to be browsed. If available, the list of items will be emitted by {@link
     * #getBrowsedMediaItems()}.
     */
    @UiThread
    @Override
    public void setCurrentBrowseId(@Nullable String browseId) {
        mCurrentBrowseId.setValue(browseId);
    }

    /**
     * Set the current item to be searched for. If available, the list of items will be emitted
     * by {@link #getBrowsedMediaItems()}.
     */
    @UiThread
    @Override
    public void search(@Nullable String query) {
        mCurrentSearchQuery.setValue(query);
    }

    @Override
    public LiveData<String> getPackageName() {
        return mPackageName;
    }

    @Override
    public LiveData<BrowseState> getBrowseState() {
        return mBrowseState;
    }

    @Override
    public LiveData<FutureData<List<MediaItemMetadata>>> getBrowsedMediaItems() {
        return mBrowsedMediaItems;
    }

    @Override
    public LiveData<FutureData<List<MediaItemMetadata>>> getSearchedMediaItems() {
        return mSearchedMediaItems;
    }

    @SuppressWarnings("deprecation")
    @Override
    public LiveData<Boolean> supportsSearch() {
        return map(mConnectedMediaBrowser, mediaBrowserCompat -> {
            if (mediaBrowserCompat == null) {
                return false;
            }
            Bundle extras = mediaBrowserCompat.getExtras();
            if (extras == null) {
                return false;
            }
            if (extras.containsKey(MediaConstants.MEDIA_SEARCH_SUPPORTED)) {
                return extras.getBoolean(MediaConstants.MEDIA_SEARCH_SUPPORTED);
            }
            if (extras.containsKey(MediaConstants.MEDIA_SEARCH_SUPPORTED_PRERELEASE)) {
                return extras.getBoolean(MediaConstants.MEDIA_SEARCH_SUPPORTED_PRERELEASE);
            }
            return false;
        });
    }

    @SuppressWarnings("deprecation")
    @Override
    public LiveData<Integer> rootBrowsableHint() {
        return map(mConnectedMediaBrowser, mediaBrowserCompat -> {
            if (mediaBrowserCompat == null) {
                return 0;
            }
            Bundle extras = mediaBrowserCompat.getExtras();
            if (extras == null) {
                return 0;
            }
            if (extras.containsKey(MediaConstants.CONTENT_STYLE_BROWSABLE_HINT)) {
                return extras.getInt(MediaConstants.CONTENT_STYLE_BROWSABLE_HINT, 0);
            }
            if (extras.containsKey(MediaConstants.CONTENT_STYLE_BROWSABLE_HINT_PRERELEASE)) {
                return extras.getInt(MediaConstants.CONTENT_STYLE_BROWSABLE_HINT_PRERELEASE, 0);
            }
            return 0;
        });
    }

    @SuppressWarnings("deprecation")
    @Override
    public LiveData<Integer> rootPlayableHint() {
        return map(mConnectedMediaBrowser, mediaBrowserCompat -> {
            if (mediaBrowserCompat == null) {
                return 0;
            }
            Bundle extras = mediaBrowserCompat.getExtras();
            if (extras == null) {
                return 0;
            }
            if (extras.containsKey(MediaConstants.CONTENT_STYLE_PLAYABLE_HINT)) {
                return extras.getInt(MediaConstants.CONTENT_STYLE_PLAYABLE_HINT, 0);
            }
            if (extras.containsKey(MediaConstants.CONTENT_STYLE_PLAYABLE_HINT_PRERELEASE)) {
                return extras.getInt(MediaConstants.CONTENT_STYLE_PLAYABLE_HINT_PRERELEASE, 0);
            }
            return 0;
        });
    }
}
