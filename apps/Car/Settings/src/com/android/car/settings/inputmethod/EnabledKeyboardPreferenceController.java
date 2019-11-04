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

package com.android.car.settings.inputmethod;

import android.app.admin.DevicePolicyManager;
import android.car.drivingstate.CarUxRestrictions;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.ArrayMap;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Updates the enabled keyboard list. */
public class EnabledKeyboardPreferenceController extends
        PreferenceController<PreferenceGroup> {
    private static final Logger LOG = new Logger(EnabledKeyboardPreferenceController.class);

    private final Map<String, Preference> mPreferences = new ArrayMap<>();
    private final InputMethodManager mInputMethodManager;
    private final DevicePolicyManager mDevicePolicyManager;
    private final PackageManager mPackageManager;

    public EnabledKeyboardPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mPackageManager = context.getPackageManager();
        mDevicePolicyManager =
                (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        mInputMethodManager =
                (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    @Override
    protected Class<PreferenceGroup> getPreferenceType() {
        return PreferenceGroup.class;
    }

    @Override
    protected void updateState(PreferenceGroup preferenceGroup) {
        List<Preference> preferencesToDisplay = new ArrayList<>();
        Set<String> preferencesToRemove = new HashSet<>(mPreferences.keySet());
        List<String> permittedList = mDevicePolicyManager.getPermittedInputMethodsForCurrentUser();
        List<InputMethodInfo> inputMethodInfos = mInputMethodManager.getEnabledInputMethodList();
        int size = (inputMethodInfos == null) ? 0 : inputMethodInfos.size();
        for (int i = 0; i < size; ++i) {
            InputMethodInfo inputMethodInfo = inputMethodInfos.get(i);
            // permittedList is Null means that all input methods are allowed.
            boolean isAllowedByOrganization = (permittedList == null)
                    || permittedList.contains(inputMethodInfo.getPackageName());
            if (!isAllowedByOrganization) {
                continue;
            }

            Preference preference = createPreference(inputMethodInfo);
            if (mPreferences.containsKey(preference.getKey())) {
                Preference displayedPreference = mPreferences.get(preference.getKey());
                if (arePreferencesDifferent(displayedPreference, preference)) {
                    preferencesToDisplay.add(preference);
                } else {
                    preferencesToRemove.remove(preference.getKey());
                }
            } else {
                preferencesToDisplay.add(preference);
            }
        }
        updatePreferenceGroup(preferenceGroup, preferencesToDisplay, preferencesToRemove);
    }

    private void updatePreferenceGroup(
            PreferenceGroup preferenceGroup, List<Preference> preferencesToDisplay,
            Set<String> preferencesToRemove) {
        Collections.sort(preferencesToDisplay, Comparator.comparing(
                (Preference a) -> a.getTitle().toString())
                .thenComparing((Preference a) -> a.getSummary().toString()));

        for (String key : preferencesToRemove) {
            preferenceGroup.removePreference(mPreferences.get(key));
            mPreferences.remove(key);
        }

        for (int i = 0; i < preferencesToDisplay.size(); ++i) {
            Preference pref = preferencesToDisplay.get(i);
            pref.setOrder(i);
            mPreferences.put(pref.getKey(), pref);
            preferenceGroup.addPreference(pref);
        }
    }

    /**
     * Creates a preference.
     */
    private Preference createPreference(InputMethodInfo inputMethodInfo) {
        Preference preference = new Preference(getContext());
        preference.setKey(String.valueOf(inputMethodInfo.hashCode()));
        preference.setIcon(InputMethodUtil.getPackageIcon(mPackageManager, inputMethodInfo));
        preference.setTitle(InputMethodUtil.getPackageLabel(mPackageManager, inputMethodInfo));
        preference.setSummary(InputMethodUtil.getSummaryString(
                getContext(), mInputMethodManager, inputMethodInfo));
        preference.setOnPreferenceClickListener(pref -> {
            try {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                String settingsActivity = inputMethodInfo.getSettingsActivity();
                intent.setClassName(inputMethodInfo.getPackageName(), settingsActivity);
                // Invoke a settings activity of an input method.
                getContext().startActivity(intent);
            } catch (final ActivityNotFoundException e) {
                LOG.d("IME's Settings Activity Not Found. " + e);
            }
            return true;
        });
        return preference;
    }

    private boolean arePreferencesDifferent(Preference lhs, Preference rhs) {
        return !Objects.equals(lhs.getTitle(), rhs.getTitle())
                || !Objects.equals(lhs.getSummary(), rhs.getSummary());
    }
}
