/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.documentsui.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;

/**
 * Methods for accessing the local preferences with regards to scoped directory access.
 * TODO(b/111892460): Delete this class after Q is released.
 */
@Deprecated
public class ScopedAccessLocalPreferences {

    private static final String TAG = "ScopedAccessLocalPreferences";

    private static SharedPreferences getPrefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    /** Clears all scoped directory access preferences. */
    public static void clearScopedAccessPreferences(Context context) {
        final String keySubstring = "|";
        final SharedPreferences prefs = getPrefs(context);
        Editor editor = null;
        for (final String key : prefs.getAll().keySet()) {
            if (key.contains(keySubstring)) {
                if (editor == null) {
                    editor = prefs.edit();
                }
                editor.remove(key);
            }
        }
        if (editor != null) {
            editor.apply();
        }
    }
}
