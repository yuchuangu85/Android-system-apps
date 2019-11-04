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

package com.android.car.settings.applications.managedomainurls;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.util.ArraySet;

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.FragmentController;

/** Business logic to generate and see the list of supported domain urls. */
public class DomainUrlsPreferenceController extends
        AppLaunchSettingsBasePreferenceController<Preference> {

    private ArraySet<String> mDomains;

    public DomainUrlsPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    protected void onCreateInternal() {
        mDomains = DomainUrlsUtils.getHandledDomains(getContext().getPackageManager(),
                getPackageName());
    }

    @Override
    protected void updateState(Preference preference) {
        preference.setEnabled(!isBrowserApp());
        preference.setSummary(
                DomainUrlsUtils.getDomainsSummary(getContext(), getPackageName(),
                        getCurrentUserId(), mDomains));
    }

    @Override
    protected boolean handlePreferenceClicked(Preference preference) {
        String newLines = System.lineSeparator() + System.lineSeparator();
        String message = String.join(newLines, mDomains);

        // Not exactly a "confirmation" dialog, but reusing to remove the need for a custom
        // dialog fragment.
        ConfirmationDialogFragment dialogFragment = new ConfirmationDialogFragment.Builder(
                getContext())
                .setTitle(R.string.app_launch_supported_domain_urls_title)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, /* rejectListener= */ null)
                .build();
        getFragmentController().showDialog(dialogFragment, ConfirmationDialogFragment.TAG);
        return true;
    }
}
