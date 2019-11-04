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

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.internal.app.LocalePicker;
import com.android.internal.app.LocaleStore;

import java.util.HashSet;
import java.util.Set;

/**
 * Common business logic shared between the primary and secondary screens for language selection.
 */
public abstract class LanguageBasePreferenceController extends
        PreferenceController<PreferenceGroup> implements Preference.OnPreferenceClickListener {

    /** Actions that should be taken on selection of the preference. */
    public interface LocaleSelectedListener {
        /** Handle selection of locale. */
        void onLocaleSelected(LocaleStore.LocaleInfo localeInfo);
    }

    private Set<String> mExclusionSet = new HashSet<>();
    private LocaleSelectedListener mLocaleSelectedListener;

    public LanguageBasePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<PreferenceGroup> getPreferenceType() {
        return PreferenceGroup.class;
    }

    /** Register a listener for when a locale is selected. */
    public void setLocaleSelectedListener(LocaleSelectedListener listener) {
        mLocaleSelectedListener = listener;
    }

    /** Gets the exclusion set. */
    public Set<String> getExclusionSet() {
        return mExclusionSet;
    }

    /** Defines the locale provider that should be used by the given preference controller. */
    protected abstract LocalePreferenceProvider defineLocaleProvider();

    @Override
    protected void updateState(PreferenceGroup preferenceGroup) {
        // Only populate if the preference group is empty.
        if (preferenceGroup.getPreferenceCount() == 0) {
            defineLocaleProvider().populateBasePreference(preferenceGroup, this);
        }
    }

    /**
     * Defines the action that should be taken when a locale with children is clicked. By default,
     * does nothing.
     */
    protected void handleLocaleWithChildren(LocaleStore.LocaleInfo parentLocaleInfo) {
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        LocaleStore.LocaleInfo localeInfo = LocaleUtil.getLocaleArgument(preference);

        // Preferences without a associated locale should not be acted on.
        if (localeInfo == null) {
            return false;
        }

        if (localeInfo.getParent() == null) {
            // The locale only has the language info. Need to look up the sub-level
            // locale to get the country/region info as well.
            Set<LocaleStore.LocaleInfo> subLocales = LocaleStore.getLevelLocales(
                    getContext(),
                    getExclusionSet(),
                    /* parent */ localeInfo,
                    /* translatedOnly */ true);

            if (subLocales.size() > 1) {
                handleLocaleWithChildren(localeInfo);
                return true;
            }

            if (subLocales.size() < 1) {
                return false;
            }

            // If only 1 sublocale, just operate as if there are no sublocales.
            localeInfo = subLocales.iterator().next();
        }

        LocalePicker.updateLocale(localeInfo.getLocale());
        if (mLocaleSelectedListener != null) {
            mLocaleSelectedListener.onLocaleSelected(localeInfo);
        }
        getFragmentController().goBack();
        return true;
    }
}
