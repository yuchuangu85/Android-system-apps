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

import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.android.internal.app.LocaleStore;

import java.util.Locale;

/** Utilities to get/set LocaleInfo from preferences. */
public class LocaleUtil {
    /** This key is also used to add Locale to the bundle for {@link ChildLocalePickerFragment}. */
    public static final String LOCALE_BUNDLE_KEY = "locale_key";

    /** Private constructor to prevent others from instantiating this class. */
    private LocaleUtil() {
    }

    /** Extract the locale from the given locale info and add to preference arguments. */
    public static void setLocaleArgument(Preference preference, LocaleStore.LocaleInfo localeInfo) {
        preference.getExtras().putSerializable(LOCALE_BUNDLE_KEY, localeInfo.getLocale());
    }

    /** Extract the localeInfo from the preference, if it exists. */
    @Nullable
    public static LocaleStore.LocaleInfo getLocaleArgument(Preference preference) {
        Locale locale = (Locale) preference.getExtras().getSerializable(LOCALE_BUNDLE_KEY);
        if (locale == null) {
            return null;
        }
        return LocaleStore.getLocaleInfo(locale);
    }
}
