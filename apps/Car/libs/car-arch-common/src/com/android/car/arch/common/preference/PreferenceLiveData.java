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
import androidx.lifecycle.LiveData;

import java.util.Objects;

/**
 * A LiveData that populates its value from {@link SharedPreferences}. When active, this LiveData
 * will listen for changes to the supplied key and update its value.
 *
 * @param <T> The type this PreferenceLiveData will emit.
 */
public abstract class PreferenceLiveData<T> extends LiveData<T> {

    private final SharedPreferences mPreferences;
    private final String mKey;

    private final SharedPreferences.OnSharedPreferenceChangeListener mListener =
            (sharedPreferences, key) -> {
                if (Objects.equals(key, PreferenceLiveData.this.mKey)) {
                    setValue(fetchValue(sharedPreferences, key));
                }
            };

    public PreferenceLiveData(
            @NonNull SharedPreferences preferences, @NonNull String key) {
        mPreferences = preferences;
        mKey = key;
    }

    /**
     * Subclasses should extract the required value from {@code preferences} corresponding to the
     * given {@code key} and return it. Subclasses should not directly call {@link #setValue(T)}
     * since it will be called when appropriate.
     */
    protected abstract T fetchValue(@NonNull SharedPreferences preferences, @NonNull String key);

    @Override
    protected void onActive() {
        super.onActive();
        setValue(fetchValue(mPreferences, mKey));
        mPreferences.registerOnSharedPreferenceChangeListener(mListener);
    }

    @Override
    protected void onInactive() {
        super.onInactive();
        mPreferences.unregisterOnSharedPreferenceChangeListener(mListener);
    }
}
