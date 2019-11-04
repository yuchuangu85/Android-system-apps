/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.emergency.view;

import android.view.View;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.android.emergency.PreferenceKeys;
import com.android.emergency.R;
import com.android.emergency.ReloadablePreferenceInterface;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment that displays personal and medical information.
 */
public class ViewEmergencyInfoFragment extends PreferenceFragmentCompat {
    /** A list with all the preferences. */
    private final List<Preference> mPreferences = new ArrayList<Preference>();

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.view_emergency_info, rootKey);

        for (String preferenceKey : PreferenceKeys.KEYS_VIEW_EMERGENCY_INFO) {
            Preference preference = findPreference(preferenceKey);
            mPreferences.add(preference);

            if (((ReloadablePreferenceInterface) preference).isNotSet()) {
                getPreferenceScreen().removePreference(preference);
            }
        }
    }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
      super.onViewCreated(view, savedInstanceState);

      // Set a top padding for the EmergencyInfo View.
      int topPadding = (int) getResources().getDimension(R.dimen.view_emergency_info_top_padding);
      view.setPadding(view.getPaddingLeft(), view.getPaddingTop() + topPadding,
          view.getPaddingRight(), view.getPaddingBottom());
  }

    @Override
    public void onResume() {
        super.onResume();
        for (Preference preference : mPreferences) {
            ReloadablePreferenceInterface reloadablePreference =
                    (ReloadablePreferenceInterface) preference;
            reloadablePreference.reloadFromPreference();
            if (reloadablePreference.isNotSet()) {
                getPreferenceScreen().removePreference(preference);
            } else {
                // Note: this preference won't be added it if it already exists.
                getPreferenceScreen().addPreference(preference);
            }
        }
    }

    public static Fragment newInstance() {
        return new ViewEmergencyInfoFragment();
    }
}
