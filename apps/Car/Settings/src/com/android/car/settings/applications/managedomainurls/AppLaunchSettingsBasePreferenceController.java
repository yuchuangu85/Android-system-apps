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
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.settingslib.applications.ApplicationsState;

import java.util.List;

/**
 * Shared logic for preference controllers related to app launch settings.
 *
 * @param <V> the upper bound on the type of {@link Preference} on which the controller
 *            expects to operate.
 */
public abstract class AppLaunchSettingsBasePreferenceController<V extends Preference> extends
        PreferenceController<V> {

    @VisibleForTesting
    static final Intent sBrowserIntent = new Intent()
            .setAction(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.parse("http:"));

    private final PackageManager mPm;
    private final CarUserManagerHelper mCarUserManagerHelper;

    private String mPackageName;
    private ApplicationsState.AppEntry mAppEntry;

    public AppLaunchSettingsBasePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mPm = context.getPackageManager();
        mCarUserManagerHelper = new CarUserManagerHelper(context);
    }

    /** Sets the app entry associated with this settings screen. */
    public void setAppEntry(ApplicationsState.AppEntry entry) {
        mAppEntry = entry;
    }

    /** Returns the app entry. */
    public ApplicationsState.AppEntry getAppEntry() {
        return mAppEntry;
    }

    /** Returns the package name. */
    public String getPackageName() {
        return mAppEntry.info.packageName;
    }

    /** Returns the current user id. */
    protected int getCurrentUserId() {
        return mCarUserManagerHelper.getCurrentProcessUserId();
    }

    /** Returns {@code true} if the current package is a browser app. */
    protected boolean isBrowserApp() {
        sBrowserIntent.setPackage(getPackageName());
        List<ResolveInfo> list = mPm.queryIntentActivitiesAsUser(sBrowserIntent,
                PackageManager.MATCH_ALL, getCurrentUserId());
        for (ResolveInfo info : list) {
            if (info.activityInfo != null && info.handleAllWebDataURI) {
                return true;
            }
        }
        return false;
    }
}
