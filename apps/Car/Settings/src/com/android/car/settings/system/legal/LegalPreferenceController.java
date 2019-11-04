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

package com.android.car.settings.system.legal;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

import java.util.List;

/**
 *  Base class for legal preferences. Locates an activity coupled with the given intent and updates
 *  the preference accordingly.
 */
public abstract class LegalPreferenceController extends PreferenceController<Preference> {
    private final PackageManager mPackageManager;
    private ResolveInfo mResolveInfo;

    public LegalPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mPackageManager = context.getPackageManager();
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    public int getAvailabilityStatus() {
        mResolveInfo = findMatchingSpecificActivity();
        return mResolveInfo != null ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    protected void updateState(Preference preference) {
        if (mResolveInfo == null) {
            return;
        }

        // Replace the intent with this specific activity.
        preference.setIntent(new Intent().setClassName(
                mResolveInfo.activityInfo.packageName,
                mResolveInfo.activityInfo.name));

        preference.setTitle(mResolveInfo.loadLabel(mPackageManager));
    }

    /** Intent with a matching system activity to display legal disclaimers or licenses. */
    protected abstract Intent getIntent();

    private ResolveInfo findMatchingSpecificActivity() {
        Intent intent = getIntent();
        if (intent == null) {
            return null;
        }

        // Find the activity that is in the system image.
        List<ResolveInfo> list = mPackageManager.queryIntentActivities(intent, /* flags= */ 0);
        if (list == null) {
            return null;
        }

        for (ResolveInfo resolveInfo : list) {
            if ((resolveInfo.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM)
                    != 0) {
                return resolveInfo;
            }
        }
        return null;
    }
}
