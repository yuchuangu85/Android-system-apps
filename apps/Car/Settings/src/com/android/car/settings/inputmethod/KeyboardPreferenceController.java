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
import android.content.Context;
import android.content.pm.PackageManager;
import android.icu.text.ListFormatter;
import android.text.BidiFormatter;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

import java.util.ArrayList;
import java.util.List;

/** Updates the keyboard settings entry summary with the currently enabled keyboard. */
public class KeyboardPreferenceController extends PreferenceController<Preference> {
    private static final String SUMMARY_EMPTY = "";

    private final InputMethodManager mInputMethodManager;
    private final DevicePolicyManager mDevicePolicyManager;
    private final PackageManager mPackageManager;

    public KeyboardPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mPackageManager = context.getPackageManager();
        mDevicePolicyManager =
                (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        mInputMethodManager =
                (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    protected void updateState(Preference preference) {
        List<InputMethodInfo> inputMethodInfos =
                mInputMethodManager.getEnabledInputMethodList();
        if (inputMethodInfos == null) {
            preference.setSummary(SUMMARY_EMPTY);
            return;
        }

        // permittedList == null means all input methods are allowed.
        List<String> permittedList =
                mDevicePolicyManager.getPermittedInputMethodsForCurrentUser();
        List<String> labels = new ArrayList<>();

        for (InputMethodInfo inputMethodInfo : inputMethodInfos) {
            boolean isAllowedByOrganization = permittedList == null
                    || permittedList.contains(inputMethodInfo.getPackageName());
            if (!isAllowedByOrganization) {
                continue;
            }
            labels.add(InputMethodUtil.getPackageLabel(mPackageManager, inputMethodInfo));
        }
        if (labels.isEmpty()) {
            preference.setSummary(SUMMARY_EMPTY);
            return;
        }

        BidiFormatter bidiFormatter = BidiFormatter.getInstance();

        List<String> summaries = new ArrayList<>();
        for (String label : labels) {
            summaries.add(bidiFormatter.unicodeWrap(label));
        }
        preference.setSummary(ListFormatter.getInstance().format(summaries));
    }
}
