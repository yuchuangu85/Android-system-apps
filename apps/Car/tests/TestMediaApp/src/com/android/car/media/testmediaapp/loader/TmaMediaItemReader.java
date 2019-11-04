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

package com.android.car.media.testmediaapp.loader;

import static android.support.v4.media.MediaBrowserCompat.MediaItem.FLAG_BROWSABLE;
import static android.support.v4.media.MediaBrowserCompat.MediaItem.FLAG_PLAYABLE;

import static com.android.car.media.testmediaapp.TmaMediaEvent.INSTANT_PLAYBACK;
import static com.android.car.media.testmediaapp.loader.TmaLoaderUtils.enumNamesToValues;
import static com.android.car.media.testmediaapp.loader.TmaLoaderUtils.getArray;
import static com.android.car.media.testmediaapp.loader.TmaLoaderUtils.getEnum;
import static com.android.car.media.testmediaapp.loader.TmaLoaderUtils.getEnumArray;
import static com.android.car.media.testmediaapp.loader.TmaLoaderUtils.getString;

import android.util.Log;

import androidx.annotation.Nullable;

import com.android.car.media.testmediaapp.TmaMediaEvent;
import com.android.car.media.testmediaapp.TmaMediaItem.TmaCustomAction;
import com.android.car.media.testmediaapp.TmaMediaItem;
import com.android.car.media.testmediaapp.TmaMediaItem.ContentStyle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


class TmaMediaItemReader {

    private final static String TAG = "TmaMediaItemReader";

    /** The json keys to retrieve the properties. */
    private enum Keys {
        FLAGS,
        PLAYABLE_HINT,
        BROWSABLE_HINT,
        METADATA,
        CHILDREN,
        INCLUDE,
        CUSTOM_ACTIONS,
        EVENTS
    }

    private static TmaMediaItemReader sInstance;

    synchronized static TmaMediaItemReader getInstance() {
        if (sInstance == null) {
            sInstance = new TmaMediaItemReader();
        }
        return sInstance;
    }

    private final TmaMediaMetadataReader mMediaMetadataReader;
    private final TmaMediaEventReader mMediaEventReader;
    private final Map<String, Integer> mFlags = new HashMap<>(2);
    private final Map<String, ContentStyle> mContentStyles;
    private final Map<String, TmaCustomAction> mCustomActions;

    private TmaMediaItemReader() {
        mMediaMetadataReader = TmaMediaMetadataReader.getInstance();
        mMediaEventReader = TmaMediaEventReader.getInstance();
        mContentStyles = enumNamesToValues(ContentStyle.values());
        mCustomActions = enumNamesToValues(TmaMediaItem.TmaCustomAction.values());

        mFlags.put("browsable", FLAG_BROWSABLE);
        mFlags.put("playable", FLAG_PLAYABLE);
    }

    @Nullable
    TmaMediaItem fromJson(@Nullable JSONObject json) {
        if (json == null) return null;
        try {
            // Media events
            JSONArray events = getArray(json, Keys.EVENTS);
            int eventsCount = (events != null) ? events.length() : 0;
            List<TmaMediaEvent> mediaEvents = new ArrayList<>(eventsCount);
            for (int i = 0; i < eventsCount ; i++) {
                mediaEvents.add(mMediaEventReader.fromJson(events.getJSONObject(i)));
            }
            if (mediaEvents.size() <= 0) {
                mediaEvents.add(INSTANT_PLAYBACK);
            }

            // Child items
            JSONArray children = getArray(json, Keys.CHILDREN);
            int childrenCount = (children != null) ? children.length() : 0;
            List<TmaMediaItem> mediaItems = new ArrayList<>(childrenCount);
            for (int i = 0; i < childrenCount ; i++) {
                mediaItems.add(fromJson(children.getJSONObject(i)));
            }


            return new TmaMediaItem(TmaLoaderUtils.parseFlags(getString(json, Keys.FLAGS), mFlags),
                    getEnum(json, Keys.PLAYABLE_HINT, mContentStyles, ContentStyle.NONE),
                    getEnum(json, Keys.BROWSABLE_HINT, mContentStyles, ContentStyle.NONE),
                    mMediaMetadataReader.fromJson(json.getJSONObject(Keys.METADATA.name())),
                    getEnumArray(json, Keys.CUSTOM_ACTIONS, mCustomActions),
                    mediaEvents, mediaItems, getString(json, Keys.INCLUDE));
        } catch (JSONException e) {
            Log.e(TAG, "Json failure: " + e);
            return null;
        }
    }
}
