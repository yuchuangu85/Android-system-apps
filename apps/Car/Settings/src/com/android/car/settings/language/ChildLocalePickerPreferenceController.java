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

import com.android.car.settings.common.FragmentController;
import com.android.internal.app.LocaleStore;

import java.util.Set;

/** Business logic for handling a secondary page for languages which have multiple locales. */
public class ChildLocalePickerPreferenceController extends LanguageBasePreferenceController {

    private LocaleStore.LocaleInfo mParentLocaleInfo;

    public ChildLocalePickerPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    /** Set the parent locale info which should be used to provide options in this second screen. */
    public void setParentLocaleInfo(LocaleStore.LocaleInfo parentLocaleInfo) {
        mParentLocaleInfo = parentLocaleInfo;
    }

    @Override
    protected LocalePreferenceProvider defineLocaleProvider() {
        Set<LocaleStore.LocaleInfo> mLocaleInfoSet = LocaleStore.getLevelLocales(
                getContext(),
                getExclusionSet(),
                mParentLocaleInfo,
                /* translatedOnly= */ true);

        return LocalePreferenceProvider.newInstance(getContext(), mLocaleInfoSet,
                mParentLocaleInfo);
    }
}
