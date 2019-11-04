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

import static com.android.car.media.testmediaapp.loader.TmaLoaderUtils.enumNamesToValues;
import static com.android.car.media.testmediaapp.loader.TmaLoaderUtils.getEnum;
import static com.android.car.media.testmediaapp.loader.TmaLoaderUtils.getInt;
import static com.android.car.media.testmediaapp.loader.TmaLoaderUtils.getString;

import android.util.Log;

import androidx.annotation.Nullable;

import com.android.car.media.testmediaapp.TmaMediaEvent;
import com.android.car.media.testmediaapp.TmaMediaEvent.EventState;
import com.android.car.media.testmediaapp.TmaMediaEvent.ResolutionIntent;
import com.android.car.media.testmediaapp.TmaMediaEvent.StateErrorCode;

import org.json.JSONObject;

import java.util.Map;

/**
 * Converts a json object into a {@link TmaMediaEvent}. Example:
 * {
 *   "STATE": "ERROR",
 *   "ERROR_CODE": "PREMIUM_ACCOUNT_REQUIRED",
 *   "ERROR_MESSAGE": "A longer message explaining what's going on",
 *   "ACTION_LABEL": "Upgrade now",
 *   "POST_DELAY_MS": 500
 * }
 */
class TmaMediaEventReader {

    /** The json keys to retrieve the properties. */
    private enum Keys {
        STATE,
        ERROR_CODE,
        ERROR_MESSAGE,
        ACTION_LABEL,
        INTENT,
        /** How long to wait before sending the event to the app. */
        POST_DELAY_MS,
        THROW_EXCEPTION
    }

    private static TmaMediaEventReader sInstance;

    synchronized static TmaMediaEventReader getInstance() {
        if (sInstance == null) {
            sInstance = new TmaMediaEventReader();
        }
        return sInstance;
    }

    private final Map<String, EventState> mEventStates;
    private final Map<String, StateErrorCode> mErrorCodes;
    private final Map<String, ResolutionIntent> mResolutionIntents;

    private TmaMediaEventReader() {
        mEventStates = enumNamesToValues(EventState.values());
        mErrorCodes = enumNamesToValues(StateErrorCode.values());
        mResolutionIntents = enumNamesToValues(ResolutionIntent.values());
    }

    @Nullable
    TmaMediaEvent fromJson(@Nullable JSONObject json) {
        if (json == null) return null;
        return new TmaMediaEvent(
                getEnum(json, Keys.STATE, mEventStates, EventState.NONE),
                getEnum(json, Keys.ERROR_CODE, mErrorCodes, StateErrorCode.UNKNOWN_ERROR),
                getString(json, Keys.ERROR_MESSAGE),
                getString(json, Keys.ACTION_LABEL),
                getEnum(json, Keys.INTENT, mResolutionIntents, ResolutionIntent.NONE),
                getInt(json, Keys.POST_DELAY_MS),
                getString(json, Keys.THROW_EXCEPTION));
    }
}
