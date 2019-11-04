/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.customization.model.clock;

import android.content.ContentResolver;
import android.provider.Settings.Secure;
import android.text.TextUtils;

import com.android.customization.module.ThemesUserEventLogger;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * {@link CustomizationManager} for clock faces that implements apply by writing to secure settings.
 */
public class ClockManager extends BaseClockManager {

    // TODO: use constant from Settings.Secure
    static final String CLOCK_FACE_SETTING = "lock_screen_custom_clock_face";
    private static final String CLOCK_FIELD = "clock";
    private static final String TIMESTAMP_FIELD = "_applied_timestamp";
    private final ContentResolver mContentResolver;
    private final ThemesUserEventLogger mEventLogger;

    public ClockManager(ContentResolver resolver, ClockProvider provider,
            ThemesUserEventLogger logger) {
        super(provider);
        mContentResolver = resolver;
        mEventLogger = logger;
    }

    @Override
    protected void handleApply(Clockface option, Callback callback) {
        boolean stored;
        try {
            final JSONObject json = new JSONObject();
            json.put(CLOCK_FIELD, option.getId());
            json.put(TIMESTAMP_FIELD, System.currentTimeMillis());
            stored = Secure.putString(mContentResolver, CLOCK_FACE_SETTING, json.toString());
        } catch (JSONException ex) {
            stored = false;
        }
        if (stored) {
            mEventLogger.logClockApplied(option);
            callback.onSuccess();
        } else {
            callback.onError(null);
        }
    }

    @Override
    protected String lookUpCurrentClock() {
        final String value = Secure.getString(mContentResolver, CLOCK_FACE_SETTING);
        if (TextUtils.isEmpty(value)) {
            return value;
        }
        try {
            final JSONObject json = new JSONObject(value);
            return json.getString(CLOCK_FIELD);
        } catch (JSONException ex) {
            return value;
        }
    }
}
