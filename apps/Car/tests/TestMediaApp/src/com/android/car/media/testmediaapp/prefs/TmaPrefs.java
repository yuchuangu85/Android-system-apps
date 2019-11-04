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

package com.android.car.media.testmediaapp.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;

import androidx.preference.PreferenceManager;

import com.android.car.media.testmediaapp.prefs.TmaEnumPrefs.TmaAccountType;
import com.android.car.media.testmediaapp.prefs.TmaEnumPrefs.TmaBrowseNodeType;
import com.android.car.media.testmediaapp.prefs.TmaEnumPrefs.TmaNodeReplyDelay;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


/** Singleton class to access the application's preferences. */
public class TmaPrefs {

    private static TmaPrefs sPrefs;

    public final PrefEntry<TmaAccountType> mAccountType;
    public final PrefEntry<TmaBrowseNodeType> mRootNodeType;

    /** Wait time before sending a node reply, unless overridden in json (when supported). */
    public final PrefEntry<TmaNodeReplyDelay> mRootReplyDelay;


    public synchronized static TmaPrefs getInstance(Context context) {
        if (sPrefs == null) {
            sPrefs = new TmaPrefs(context);
        }
        return sPrefs;
    }

    public interface PrefValueChangedListener<T> {
        void onValueChanged(T oldValue, T newValue);
    }

    /** The set of keys used to store the preferences. */
    private enum TmaPrefKey {
        ACCOUNT_TYPE_KEY,
        ROOT_NODE_TYPE_KEY,
        ROOT_REPLY_DELAY_KEY
    }

    /**
     *   Represents a entry in the prefs
     */
    public abstract class PrefEntry<T> {

        protected final String mKey;

        PrefEntry(TmaPrefKey prefKey) {
            mKey = prefKey.name();
        }

        public abstract T getValue();
        public abstract void setValue(T value);

        public void registerChangeListener(PrefValueChangedListener<T> listener) {
            if (mListeners.get(listener) != null) return;

            T currentValue = getValue();
            listener.onValueChanged(currentValue, currentValue);

            OnSharedPreferenceChangeListener listenerWrapper =
                    new OnSharedPreferenceChangeListener() {
                        private T mOldValue = currentValue;

                        @Override
                        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                String key) {
                            if (mKey.equals(key)) {
                                T newValue = getValue();
                                if (!Objects.equals(mOldValue, newValue)) {
                                    listener.onValueChanged(mOldValue, newValue);
                                    mOldValue = newValue;
                                }
                            }
                        }
                    };

            mSharedPrefs.registerOnSharedPreferenceChangeListener(listenerWrapper);
            mListeners.put(listener, listenerWrapper);
        }
    }


    private final Map<PrefValueChangedListener, OnSharedPreferenceChangeListener> mListeners
            = new HashMap<>(5);

    private final SharedPreferences mSharedPrefs;


    private TmaPrefs(Context context) {
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);

        mAccountType = new EnumPrefEntry<>(TmaPrefKey.ACCOUNT_TYPE_KEY,
                TmaAccountType.values(), TmaAccountType.NONE);

        mRootNodeType = new EnumPrefEntry<>(TmaPrefKey.ROOT_NODE_TYPE_KEY,
                TmaBrowseNodeType.values(), TmaBrowseNodeType.NULL);

        mRootReplyDelay = new EnumPrefEntry<>(TmaPrefKey.ROOT_REPLY_DELAY_KEY,
                TmaNodeReplyDelay.values(), TmaNodeReplyDelay.NONE);
    }


    /** Handles the conversion between the enum values and the shared preferences. */
    private class EnumPrefEntry<T extends Enum & TmaEnumPrefs.EnumPrefValue>
            extends PrefEntry<T> {

        private final T[] mEnumValues;
        private final T mDefaultValue;

        EnumPrefEntry(TmaPrefKey prefKey, T[] enumValues, T defaultValue) {
            super(prefKey);
            mEnumValues = enumValues;
            mDefaultValue = defaultValue;
        }

        @Override
        public T getValue() {
            String id = mSharedPrefs.getString(mKey, null);
            if (id != null) {
                for (T value : mEnumValues) {
                    if (value.getId().equals(id)) {
                        return value;
                    }
                }
            }
            return mDefaultValue;
        }

        @Override
        public void setValue(T value) {
            mSharedPrefs.edit().putString(mKey, value.getId()).commit();
        }
    }

}
