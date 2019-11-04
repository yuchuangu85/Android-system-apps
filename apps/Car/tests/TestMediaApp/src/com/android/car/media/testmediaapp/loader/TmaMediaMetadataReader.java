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

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ADVERTISEMENT;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ART;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ART_URI;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_AUTHOR;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_BT_FOLDER_TYPE;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_COMPILATION;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_COMPOSER;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DATE;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISC_NUMBER;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DOWNLOAD_STATUS;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_GENRE;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_MEDIA_ID;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_MEDIA_URI;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_NUM_TRACKS;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_RATING;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_USER_RATING;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_WRITER;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_YEAR;

import static com.android.car.media.testmediaapp.loader.TmaLoaderUtils.enumNamesToValues;

import android.support.v4.media.MediaMetadataCompat;
import android.util.Log;

import com.android.car.media.testmediaapp.TmaAssetProvider;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;


class TmaMediaMetadataReader {

    private final static String TAG = "TmaMetadataReader";

    private enum ValueType {
        LONG,
        TEXT,
        BITMAP,
        RATING
    }

    /** The name of each entry is the key used in the json file. */
    private enum MetadataKey {
        TITLE               (METADATA_KEY_TITLE,                ValueType.TEXT),
        ARTIST              (METADATA_KEY_ARTIST,               ValueType.TEXT),
        DURATION            (METADATA_KEY_DURATION,             ValueType.LONG),
        ALBUM               (METADATA_KEY_ALBUM,                ValueType.TEXT),
        AUTHOR              (METADATA_KEY_AUTHOR,               ValueType.TEXT),
        WRITER              (METADATA_KEY_WRITER,               ValueType.TEXT),
        COMPOSER            (METADATA_KEY_COMPOSER,             ValueType.TEXT),
        COMPILATION         (METADATA_KEY_COMPILATION,          ValueType.TEXT),
        DATE                (METADATA_KEY_DATE,                 ValueType.TEXT),
        YEAR                (METADATA_KEY_YEAR,                 ValueType.LONG),
        GENRE               (METADATA_KEY_GENRE,                ValueType.TEXT),
        TRACK_NUMBER        (METADATA_KEY_TRACK_NUMBER,         ValueType.LONG),
        NUM_TRACKS          (METADATA_KEY_NUM_TRACKS,           ValueType.LONG),
        DISC_NUMBER         (METADATA_KEY_DISC_NUMBER,          ValueType.LONG),
        ALBUM_ARTIST        (METADATA_KEY_ALBUM_ARTIST,         ValueType.TEXT),
        ART                 (METADATA_KEY_ART,                  ValueType.BITMAP),
        ART_URI             (METADATA_KEY_ART_URI,              ValueType.TEXT),
        ALBUM_ART           (METADATA_KEY_ALBUM_ART,            ValueType.BITMAP),
        ALBUM_ART_URI       (METADATA_KEY_ALBUM_ART_URI,        ValueType.TEXT),
        USER_RATING         (METADATA_KEY_USER_RATING,          ValueType.RATING),
        RATING              (METADATA_KEY_RATING,               ValueType.RATING),
        DISPLAY_TITLE       (METADATA_KEY_DISPLAY_TITLE,        ValueType.TEXT),
        DISPLAY_SUBTITLE    (METADATA_KEY_DISPLAY_SUBTITLE,     ValueType.TEXT),
        DISPLAY_DESCRIPTION (METADATA_KEY_DISPLAY_DESCRIPTION,  ValueType.TEXT),
        DISPLAY_ICON        (METADATA_KEY_DISPLAY_ICON,         ValueType.BITMAP),
        DISPLAY_ICON_URI    (METADATA_KEY_DISPLAY_ICON_URI,     ValueType.TEXT),
        MEDIA_ID            (METADATA_KEY_MEDIA_ID,             ValueType.TEXT),
        BT_FOLDER_TYPE      (METADATA_KEY_BT_FOLDER_TYPE,       ValueType.LONG),
        MEDIA_URI           (METADATA_KEY_MEDIA_URI,            ValueType.TEXT),
        ADVERTISEMENT       (METADATA_KEY_ADVERTISEMENT,        ValueType.LONG),
        DOWNLOAD_STATUS     (METADATA_KEY_DOWNLOAD_STATUS,      ValueType.LONG);

        /** The full name of the key in {@link MediaMetadataCompat}. */
        final String mLongName;
        /** The type of the key's value in {@link MediaMetadataCompat}. */
        final ValueType mKeyType;

        MetadataKey(String longName, ValueType valueType) {
            mLongName = longName;
            mKeyType = valueType;
        }
    }

    private static TmaMediaMetadataReader sInstance;

    synchronized static TmaMediaMetadataReader getInstance() {
        if (sInstance == null) {
            sInstance = new TmaMediaMetadataReader();
        }
        return sInstance;
    }

    private final Map<String, MetadataKey> mMetadataKeys;
    private final Set<MetadataKey> mUriKeys;

    private TmaMediaMetadataReader() {
        mMetadataKeys = enumNamesToValues(MetadataKey.values());
        mUriKeys = EnumSet.of(MetadataKey.ART_URI, MetadataKey.ALBUM_ART_URI,
                MetadataKey.DISPLAY_ICON_URI, MetadataKey.MEDIA_URI);
    }


    MediaMetadataCompat fromJson(JSONObject object) throws JSONException {
        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
        for (String jsonKey : object.keySet()) {
            MetadataKey key = mMetadataKeys.get(jsonKey);
            if (key != null) {
                switch (key.mKeyType) {
                    case LONG:
                        builder.putLong(key.mLongName, object.getLong(jsonKey));
                        break;
                    case TEXT:
                        String value = object.getString(jsonKey);
                        if (mUriKeys.contains(key)) {
                            value = TmaAssetProvider.buildUriString(value);
                        }
                        builder.putString(key.mLongName, value);
                        break;
                    case BITMAP:
                    case RATING:
                        Log.e(TAG, "Ignoring unsupported type: " + key.mKeyType + " for key: "
                        + jsonKey + " / " + key.mLongName);
                }
            } else {
                Log.e(TAG, "Ignoring unsupported key: " + jsonKey);
            }
        }
        return builder.build();
    }
}
