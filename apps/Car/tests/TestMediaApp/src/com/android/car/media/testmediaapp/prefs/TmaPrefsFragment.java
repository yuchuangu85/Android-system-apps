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
import android.os.Bundle;

import androidx.preference.DropDownPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.android.car.media.testmediaapp.prefs.TmaEnumPrefs.TmaAccountType;
import com.android.car.media.testmediaapp.prefs.TmaEnumPrefs.TmaBrowseNodeType;
import com.android.car.media.testmediaapp.prefs.TmaEnumPrefs.TmaNodeReplyDelay;
import com.android.car.media.testmediaapp.prefs.TmaPrefs.PrefEntry;

public class TmaPrefsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        Context context = getPreferenceManager().getContext();
        TmaPrefs prefs = TmaPrefs.getInstance(context);

        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        screen.addPreference(createEnumPref(context, "Account Type", prefs.mAccountType,
                TmaAccountType.values()));
        screen.addPreference(createEnumPref(context, "Root node type", prefs.mRootNodeType,
                TmaBrowseNodeType.values()));
        screen.addPreference(createEnumPref(context, "Root reply delay", prefs.mRootReplyDelay,
                TmaNodeReplyDelay.values()));

        setPreferenceScreen(screen);
    }

    private <T extends TmaEnumPrefs.EnumPrefValue> Preference createEnumPref(
            Context context, String title, PrefEntry pref, T[] enumValues) {
        DropDownPreference prefWidget = new DropDownPreference(context);
        prefWidget.setKey(pref.mKey);
        prefWidget.setTitle(title);
        prefWidget.setSummary("%s");
        prefWidget.setPersistent(true);

        int count = enumValues.length;
        CharSequence[] entries = new CharSequence[count];
        CharSequence[] entryValues = new CharSequence[count];
        for (int i = 0; i < count; i++) {
            entries[i] = enumValues[i].getTitle();
            entryValues[i] = enumValues[i].getId();
        }
        prefWidget.setEntries(entries);
        prefWidget.setEntryValues(entryValues);
        return prefWidget;
    }
}
