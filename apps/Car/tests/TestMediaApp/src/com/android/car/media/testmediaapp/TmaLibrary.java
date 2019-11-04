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

package com.android.car.media.testmediaapp;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.car.media.testmediaapp.loader.TmaLoader;
import com.android.car.media.testmediaapp.prefs.TmaEnumPrefs.TmaBrowseNodeType;

import java.util.HashMap;
import java.util.Map;

/**
 * Delegates the loading of {@link TmaMediaItem}s to {@link TmaLoader} and caches the results
 * for {@link TmaBrowser}.
 */
class TmaLibrary {

    private static final String TAG = "TmaLibrary";

    private final TmaLoader mLoader;
    private final Map<TmaBrowseNodeType, String> mRootAssetPaths = new HashMap<>(5);

    /** Stores the root item of each loaded media asset file, keyed by the file's path. */
    private final Map<String, TmaMediaItem> mCachedFilesByPath = new HashMap<>(50);

    /** Stores every item of every loaded media asset file, keyed by the media id. */
    private final Map<String, TmaMediaItem> mMediaItemsByMediaId = new HashMap<>(500);

    TmaLibrary(TmaLoader loader) {
        mLoader = loader;
        mRootAssetPaths.put(TmaBrowseNodeType.NULL, null);
        mRootAssetPaths.put(TmaBrowseNodeType.EMPTY, "media_items/empty.json");
        mRootAssetPaths.put(TmaBrowseNodeType.NODE_CHILDREN, "media_items/only_nodes.json");
        mRootAssetPaths.put(TmaBrowseNodeType.LEAF_CHILDREN, "media_items/simple_leaves.json");
        mRootAssetPaths.put(TmaBrowseNodeType.MIXED_CHILDREN, "media_items/mixed.json");
    }

    @Nullable
    TmaMediaItem getRoot(TmaBrowseNodeType rootType) {
        String filePath = mRootAssetPaths.get(rootType);
        return (filePath != null) ? loadAssetFile(filePath) : null;
    }

    @Nullable
    TmaMediaItem getMediaItemById(String mediaId) {
        TmaMediaItem result = mMediaItemsByMediaId.get(mediaId);
        // Processing includes only on request allows recursive structures :-)
        if (result != null && !TextUtils.isEmpty(result.mInclude)) {
            result = result.append(loadAssetFile(result.mInclude).mChildren);
        }
        return result;
    }

    private TmaMediaItem loadAssetFile(String filePath) {
        TmaMediaItem result = mCachedFilesByPath.get(filePath);
        if (result == null) {
            result = mLoader.loadAssetFile(filePath);
            if (result != null) {
                mCachedFilesByPath.put(filePath, result);
                cacheMediaItem(result);
            } else {
                Log.e(TAG, "Unable to load: " + filePath);
            }
        }
        return result;
    }

    private void cacheMediaItem(TmaMediaItem item) {
        String key = item.getMediaId();
        if (mMediaItemsByMediaId.putIfAbsent(key, item) == null) {
            for (TmaMediaItem child : item.mChildren) {
                cacheMediaItem(child);
            }
        } else {
            Log.e(TAG, "Ignoring item with duplicate media id: " + key);
        }
    }
}
