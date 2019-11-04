/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car.dialer.ui.settings;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.android.car.dialer.R;
import com.android.car.dialer.ui.settings.common.SettingsListPreferenceDialogFragment;

/**
 * A fragment that displays the settings page
 */
public class DialerSettingsFragment extends PreferenceFragmentCompat {
    private static final String TAG = "CD.SettingsFragment";
    private static final String DIALOG_FRAGMENT_TAG = "DIALOG";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings_page, rootKey);
    }

    /**
     * Settings needs to launch custom dialog types in order to extend the Device Default theme.
     *
     * @param preference The Preference object requesting the dialog.
     */
    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        // check if dialog is already showing
        if (findDialogByTag(DIALOG_FRAGMENT_TAG) != null) {
            return;
        }

        DialogFragment dialogFragment;
        if (preference instanceof ListPreference) {
            dialogFragment = SettingsListPreferenceDialogFragment.newInstance(preference.getKey());
        } else {
            throw new IllegalArgumentException(
                    "Tried to display dialog for unknown preference type. Did you forget to "
                            + "override onDisplayPreferenceDialog()?");
        }

        dialogFragment.setTargetFragment(/* fragment= */ this, /* requestCode= */ 0);
        showDialog(dialogFragment, DIALOG_FRAGMENT_TAG);
    }

    @Nullable
    private DialogFragment findDialogByTag(String tag) {
        Fragment fragment = getFragmentManager().findFragmentByTag(tag);
        if (fragment instanceof DialogFragment) {
            return (DialogFragment) fragment;
        }
        return null;
    }

    private void showDialog(DialogFragment dialogFragment, @Nullable String tag) {
        dialogFragment.show(getFragmentManager(), tag);
    }
}
