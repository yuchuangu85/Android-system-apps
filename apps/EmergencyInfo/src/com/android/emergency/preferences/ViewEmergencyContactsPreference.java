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
package com.android.emergency.preferences;

import android.content.Context;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import android.util.AttributeSet;
import android.view.View;

import com.android.emergency.R;
import com.android.emergency.util.PreferenceUtils;

/**
 * Sets a listener to be called the contacts when tapping on the preference.
 */
public class ViewEmergencyContactsPreference extends EmergencyContactsPreference {
    public ViewEmergencyContactsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        // Hide preference title if has at least one medical info preference is set.
        if (PreferenceUtils.hasAtLeastOnePreferenceSet(getContext())) {
            View preferenceCategoryTitle = holder.findViewById(R.id.emergency_preference_category);
            preferenceCategoryTitle.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onBindContactView(final ContactPreference contactPreference) {
        contactPreference
                .setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(Preference preference) {
                                contactPreference.callContact();
                                return true;
                            }
                        }
                );
    }
}
