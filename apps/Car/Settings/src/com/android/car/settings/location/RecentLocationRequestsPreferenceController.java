/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.settings.location;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.R;
import com.android.car.settings.applications.ApplicationDetailsFragment;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.settingslib.location.RecentLocationApps;
import com.android.settingslib.location.RecentLocationApps.Request;

import java.util.List;

/**
 * Displays all apps that have requested location recently.
 */
public class RecentLocationRequestsPreferenceController extends
        PreferenceController<PreferenceGroup> {
    private RecentLocationApps mRecentLocationApps;
    // This list will always be sorted by most recent first.
    private List<Request> mRecentLocationRequests;

    public RecentLocationRequestsPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mRecentLocationApps = new RecentLocationApps(context);
    }

    @VisibleForTesting
    void setRecentLocationApps(RecentLocationApps apps) {
        mRecentLocationApps = apps;
    }

    @Override
    protected Class<PreferenceGroup> getPreferenceType() {
        return PreferenceGroup.class;
    }

    @Override
    protected void updateState(PreferenceGroup group) {
        if (mRecentLocationRequests == null) {
            // First time displaying a list.
            mRecentLocationRequests =
                    mRecentLocationApps.getAppListSorted(/* showSystemApps= */ true);
        } else {
            // If preferences were already added to the screen, get a new list
            // and only update the displayed app-list if there is a difference.
            List<Request> newRequests = mRecentLocationApps.getAppListSorted(true);
            // listsEqual compares by elements' package names only, using List.equals() will
            // not work because it will always return false since it also compares the time.
            if (listsEqual(newRequests, mRecentLocationRequests)) {
                return;
            }
            mRecentLocationRequests = newRequests;
        }
        if (mRecentLocationRequests.isEmpty()) {
            Preference emptyMessagePref = new Preference(getContext());
            emptyMessagePref.setTitle(R.string.location_settings_recent_requests_empty_message);
            group.addPreference(emptyMessagePref);
        } else {
            group.removeAll();
            for (Request request : mRecentLocationRequests) {
                Preference appPref = createPreference(request);
                group.addPreference(appPref);
            }
        }
    }

    private Preference createPreference(Request request) {
        Preference pref = new Preference(getContext());
        pref.setSummary(request.contentDescription);
        pref.setIcon(request.icon);
        pref.setTitle(request.label);
        Intent intent = new Intent();
        intent.setPackage(request.packageName);
        ResolveInfo resolveInfo = getContext().getPackageManager().resolveActivity(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        pref.setOnPreferenceClickListener(p -> {
            getFragmentController().launchFragment(
                    ApplicationDetailsFragment.getInstance(resolveInfo.activityInfo.packageName));
            return true;
        });
        return pref;
    }

    /**
     * Compares two {@link Request} lists by the elements' package names.
     *
     * @param a The first list.
     * @param b The second list.
     * @return {@code true} if both lists have the same elements (by package name) and order.
     */
    private boolean listsEqual(List<Request> a, List<Request> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).packageName.equals(b.get(i).packageName)) {
                return false;
            }
        }
        return true;
    }
}
