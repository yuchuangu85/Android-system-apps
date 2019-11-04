/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.tv.settings;

import android.app.Fragment;

import androidx.annotation.NonNull;
import androidx.leanback.preference.LeanbackSettingsFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceDialogFragment;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceScreen;

import com.android.tv.settings.system.LeanbackPickerDialogFragment;
import com.android.tv.settings.system.LeanbackPickerDialogPreference;

/**
 * Base class for settings fragments. Handles launching fragments and dialogs in a reasonably
 * generic way. Subclasses should only override onPreferenceStartInitialScreen.
 */

public abstract class BaseSettingsFragment extends LeanbackSettingsFragment {
    @Override
    public final boolean onPreferenceStartFragment(PreferenceFragment caller, Preference pref) {
        final Fragment f =
                Fragment.instantiate(getActivity(), pref.getFragment(), pref.getExtras());
        f.setTargetFragment(caller, 0);
        if (f instanceof PreferenceFragment || f instanceof PreferenceDialogFragment) {
            startPreferenceFragment(f);
        } else {
            startImmersiveFragment(f);
        }
        return true;
    }

    @Override
    public final boolean onPreferenceStartScreen(PreferenceFragment caller, PreferenceScreen pref) {
        return false;
    }

    @Override
    public boolean onPreferenceDisplayDialog(@NonNull PreferenceFragment caller, Preference pref) {
        final Fragment f;
        if (pref instanceof LeanbackPickerDialogPreference) {
            final LeanbackPickerDialogPreference dialogPreference = (LeanbackPickerDialogPreference)
                    pref;
            f = dialogPreference.getType().equals("date")
                    ? LeanbackPickerDialogFragment.newDatePickerInstance(pref.getKey())
                    : LeanbackPickerDialogFragment.newTimePickerInstance(pref.getKey());
            f.setTargetFragment(caller, 0);
            startPreferenceFragment(f);
            return true;
        } else {
            return super.onPreferenceDisplayDialog(caller, pref);
        }
    }
}
