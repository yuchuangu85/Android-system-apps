/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.car.settings.applications.specialaccess;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

/**
 * Launches into additional special access settings if they are supported by the permission
 * controller package.
 */
public class MoreSpecialAccessPreferenceController extends PreferenceController<Preference> {

    @Nullable
    private final Intent mIntent;

    public MoreSpecialAccessPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        PackageManager packageManager = getContext().getPackageManager();
        String packageName = packageManager.getPermissionControllerPackageName();
        if (packageName != null) {
            Intent intent = new Intent(Intent.ACTION_MANAGE_SPECIAL_APP_ACCESSES).setPackage(
                    packageName);
            ResolveInfo resolveInfo = packageManager.resolveActivity(intent,
                    PackageManager.MATCH_DEFAULT_ONLY);
            mIntent = (resolveInfo != null) ? intent : null;
        } else {
            mIntent = null;
        }
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    protected int getAvailabilityStatus() {
        return (mIntent != null) ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    protected boolean handlePreferenceClicked(Preference preference) {
        if (mIntent != null) {
            getContext().startActivity(mIntent);
            return true;
        }
        return false;
    }
}
