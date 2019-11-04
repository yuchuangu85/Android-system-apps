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

package com.android.car.settings.testutils;

import com.android.internal.app.LocalePicker;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;

import java.util.Locale;

@Implements(LocalePicker.class)
public class ShadowLocalePicker {

    private static boolean sLocaleUpdated = false;

    @Resetter
    public static void reset() {
        sLocaleUpdated = false;
    }

    @Implementation
    protected static void updateLocale(Locale locale) {
        sLocaleUpdated = true;
    }

    public static boolean localeWasUpdated() {
        return sLocaleUpdated;
    }
}
