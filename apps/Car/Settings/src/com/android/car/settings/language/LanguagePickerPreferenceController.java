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
import android.os.Build;

import com.android.car.settings.common.FragmentController;
import com.android.internal.app.LocaleStore;

import java.util.Locale;
import java.util.Set;

/** Business logic for showing and acting on languages in the language settings screen. */
public class LanguagePickerPreferenceController extends LanguageBasePreferenceController {

    public LanguagePickerPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected LocalePreferenceProvider defineLocaleProvider() {
        Set<LocaleStore.LocaleInfo> localeInfoSet = LocaleStore.getLevelLocales(
                getContext(),
                getExclusionSet(),
                /* parent= */ null,
                /* translatedOnly= */ true);
        maybeAddPseudoLocale(localeInfoSet);

        return LocalePreferenceProvider.newInstance(getContext(), localeInfoSet,
                /* parentLocale= */ null);
    }

    @Override
    protected void handleLocaleWithChildren(LocaleStore.LocaleInfo parentLocaleInfo) {
        ChildLocalePickerFragment fragment = ChildLocalePickerFragment.newInstance(
                parentLocaleInfo);
        fragment.registerChildLocaleSelectedListener(
                localeInfo -> getFragmentController().goBack());
        getFragmentController().launchFragment(fragment);
    }

    /**
     * Add a pseudo locale in debug build for testing RTL.
     *
     * @param localeInfos the set of {@link LocaleStore.LocaleInfo} to which the locale is added.
     */
    private void maybeAddPseudoLocale(Set<LocaleStore.LocaleInfo> localeInfos) {
        if (Build.IS_USERDEBUG) {
            // The ar-XB pseudo-locale is RTL.
            localeInfos.add(LocaleStore.getLocaleInfo(new Locale("ar", "XB")));
        }
    }
}
