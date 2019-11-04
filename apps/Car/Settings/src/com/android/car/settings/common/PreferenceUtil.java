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

package com.android.car.settings.common;

import androidx.preference.Preference;

/** Contains utility function to operate on Preferences. */
public class PreferenceUtil {

    /** Ensures that the preference of given type. */
    public static boolean checkPreferenceType(Preference preference, Class expectedType) {
        return expectedType.isInstance(preference);
    }

    /**
     * Requires that the preference is of given type.
     *
     * @throws IllegalArgumentException if the preference is not of the given type.
     */
    public static void requirePreferenceType(Preference preference, Class expectedType) {
        if (!checkPreferenceType(preference, expectedType)) {
            throw new IllegalArgumentException(
                    "Preference should be of type " + expectedType.getName());
        }
    }
}
