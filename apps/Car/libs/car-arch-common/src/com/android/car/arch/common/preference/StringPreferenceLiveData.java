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

package com.android.car.arch.common.preference;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A LiveData whose value is backed by a String preference in a {@link SharedPreferences}
 */
public class StringPreferenceLiveData extends PreferenceLiveData<String> {

    private final String mDefaultValue;

    /**
     * Creates a new StringPreferenceLiveData.
     *
     * @param preferences  The SharedPreferences to fetch data from.
     * @param key          The String key to find the data.
     * @param defaultValue The value to emit when {@code preferences} does not contain data for the
     *                     given key
     */
    public StringPreferenceLiveData(
            @NonNull SharedPreferences preferences, @NonNull String key,
            @Nullable String defaultValue) {
        super(preferences, key);
        mDefaultValue = defaultValue;
    }

    @Override
    protected String fetchValue(@NonNull SharedPreferences preferences, @NonNull String key) {
        return preferences.getString(key, mDefaultValue);
    }
}
