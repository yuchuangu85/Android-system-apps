/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.settings.language;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceUtil;
import com.android.car.settingslib.R;
import com.android.car.settingslib.language.LanguagePickerUtils;
import com.android.internal.app.LocaleStore;
import com.android.internal.app.SuggestedLocaleAdapter;

import java.util.Set;

/**
 * Provides a wrapper around the {@link SuggestedLocaleAdapter} to create Preferences to populate
 * the Language Settings screen.
 */
public class LocalePreferenceProvider {

    private static final Logger LOG = new Logger(LanguagePickerPreferenceController.class);

    /** Creates a new instance of the preference provider. */
    public static LocalePreferenceProvider newInstance(Context context,
            Set<LocaleStore.LocaleInfo> localeInfoSet,
            @Nullable LocaleStore.LocaleInfo parentLocale) {
        SuggestedLocaleAdapter adapter = LanguagePickerUtils.createSuggestedLocaleAdapter(context,
                localeInfoSet, parentLocale);
        return new LocalePreferenceProvider(context, adapter);
    }

    /**
     * Header types are copied from {@link SuggestedLocaleAdapter} in order to be able to
     * determine the header rows.
     */
    @VisibleForTesting
    static final int TYPE_HEADER_SUGGESTED = 0;
    @VisibleForTesting
    static final int TYPE_HEADER_ALL_OTHERS = 1;
    @VisibleForTesting
    static final int TYPE_LOCALE = 2;

    private final Context mContext;
    private SuggestedLocaleAdapter mSuggestedLocaleAdapter;

    @VisibleForTesting
    LocalePreferenceProvider(Context context, SuggestedLocaleAdapter localeAdapter) {
        mContext = context;
        mSuggestedLocaleAdapter = localeAdapter;
    }

    /**
     * Populates the base preference group based on the hierarchy provided by this provider.
     *
     * @param base     the preference container which will hold the language preferences created by
     *                 this provider
     * @param listener the click listener registered to the language/locale preferences contained in
     *                 the base preference group
     */
    public void populateBasePreference(PreferenceGroup base,
            Preference.OnPreferenceClickListener listener) {
        /*
         * LocalePreferenceProvider can give elements to be represented in 2 ways. In the first
         * way, it simply provides the LocalePreferences which lists the available options. In the
         * second way, this provider may also provide PreferenceCategories to break up the
         * options into "Suggested" and "All others". The screen is constructed by taking a look
         * at the type of Preference that is provided through LocalePreferenceProvider.
         *
         * In the first case (no subcategories), preferences are added directly to the base
         * container. Otherwise, elements are added to the last category that was provided
         * (stored in "category").
         */
        PreferenceCategory category = null;
        for (int position = 0; position < mSuggestedLocaleAdapter.getCount(); position++) {
            Preference preference = getPreference(position);
            if (PreferenceUtil.checkPreferenceType(preference, PreferenceCategory.class)) {
                category = (PreferenceCategory) preference;
                base.addPreference(category);
            } else {
                preference.setOnPreferenceClickListener(listener);
                if (category == null) {
                    base.addPreference(preference);
                } else {
                    category.addPreference(preference);
                }
            }
        }
    }

    /**
     * Constructs a PreferenceCategory or Preference with locale arguments based on the type of item
     * provided.
     */
    private Preference getPreference(int position) {
        int type = mSuggestedLocaleAdapter.getItemViewType(position);
        switch (type) {
            case TYPE_HEADER_SUGGESTED:
            case TYPE_HEADER_ALL_OTHERS:
                PreferenceCategory category = new PreferenceCategory(mContext);
                category.setTitle(type == TYPE_HEADER_SUGGESTED
                        ? R.string.language_picker_list_suggested_header
                        : R.string.language_picker_list_all_header);
                return category;
            case TYPE_LOCALE:
                LocaleStore.LocaleInfo info =
                        (LocaleStore.LocaleInfo) mSuggestedLocaleAdapter.getItem(position);
                Preference preference = new Preference(mContext);
                preference.setTitle(info.getFullNameNative());
                LocaleUtil.setLocaleArgument(preference, info);
                return preference;
            default:
                LOG.d("Attempting to get unknown type: " + type);
                throw new IllegalStateException("Unknown locale list item type");
        }
    }
}
