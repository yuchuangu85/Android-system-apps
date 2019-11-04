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

import static android.content.pm.PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS;
import static android.content.pm.PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS_ASK;
import static android.content.pm.PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_NEVER;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.preference.ListPreference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;

/**
 * Business logic to define how the app should handle related domain links (whether related domain
 * links should be opened always, never, or after asking).
 */
public class AppLinkStatePreferenceController extends
        AppLaunchSettingsBasePreferenceController<ListPreference> {

    private static final Logger LOG = new Logger(AppLinkStatePreferenceController.class);

    private final PackageManager mPm;
    private boolean mHasDomainUrls;

    public AppLinkStatePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mPm = context.getPackageManager();
    }

    @Override
    protected Class<ListPreference> getPreferenceType() {
        return ListPreference.class;
    }

    @Override
    protected void onCreateInternal() {
        mHasDomainUrls =
                (getAppEntry().info.privateFlags & ApplicationInfo.PRIVATE_FLAG_HAS_DOMAIN_URLS)
                        != 0;
    }

    @Override
    protected void updateState(ListPreference preference) {
        if (isBrowserApp()) {
            preference.setEnabled(false);
        } else {
            preference.setEnabled(mHasDomainUrls);

            preference.setEntries(new CharSequence[]{
                    getContext().getString(R.string.app_link_open_always),
                    getContext().getString(R.string.app_link_open_ask),
                    getContext().getString(R.string.app_link_open_never),
            });
            preference.setEntryValues(new CharSequence[]{
                    Integer.toString(INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS),
                    Integer.toString(INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS_ASK),
                    Integer.toString(INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_NEVER),
            });

            if (mHasDomainUrls) {
                int state = mPm.getIntentVerificationStatusAsUser(getPackageName(),
                        getCurrentUserId());
                preference.setValueIndex(linkStateToIndex(state));
            }
        }
    }

    @Override
    protected boolean handlePreferenceChanged(ListPreference preference, Object newValue) {
        if (isBrowserApp()) {
            // We shouldn't get into this state, but if we do make sure
            // not to cause any permanent mayhem.
            return false;
        }

        int newState = Integer.parseInt((String) newValue);
        int priorState = mPm.getIntentVerificationStatusAsUser(getPackageName(),
                getCurrentUserId());
        if (priorState == newState) {
            return false;
        }

        boolean success = mPm.updateIntentVerificationStatusAsUser(getPackageName(), newState,
                getCurrentUserId());
        if (success) {
            // Read back the state to see if the change worked.
            int updatedState = mPm.getIntentVerificationStatusAsUser(getPackageName(),
                    getCurrentUserId());
            success = (newState == updatedState);
        } else {
            LOG.e("Couldn't update intent verification status!");
        }
        return success;
    }

    private int linkStateToIndex(int state) {
        switch (state) {
            case INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS:
                return getPreference().findIndexOfValue(
                        Integer.toString(INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS));
            case INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_NEVER:
                return getPreference().findIndexOfValue(
                        Integer.toString(INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_NEVER));
            case INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS_ASK:
            default:
                return getPreference().findIndexOfValue(
                        Integer.toString(INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS_ASK));
        }
    }
}
