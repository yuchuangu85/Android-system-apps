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

package com.android.car.settings.applications;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.icu.text.ListFormatter;
import android.util.ArraySet;

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Updates the summary of the entry preference for app permissions to show up to a fixed number of
 * permission groups currently permitted.
 */
public class AppPermissionsEntryPreferenceController extends PreferenceController<Preference> {

    private static final Logger LOG = new Logger(AppPermissionsEntryPreferenceController.class);

    private static final String[] PERMISSION_GROUPS = new String[]{
            "android.permission-group.LOCATION",
            "android.permission-group.MICROPHONE",
            "android.permission-group.CAMERA",
            "android.permission-group.SMS",
            "android.permission-group.CONTACTS",
            "android.permission-group.PHONE"};

    private static final int NUM_PERMISSION_TO_USE = 3;

    private PackageManager mPackageManager;

    public AppPermissionsEntryPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mPackageManager = context.getPackageManager();
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    protected void updateState(Preference preference) {
        Set<String> permissions = getAllPermissionsInGroups();
        Set<String> grantedPermissionGroups = getGrantedPermissionGroups(permissions);
        int count = 0;
        final List<String> summaries = new ArrayList<>();

        // Iterate over array instead of set to show sensitive permissions first.
        for (String group : PERMISSION_GROUPS) {
            if (!grantedPermissionGroups.contains(group)) {
                continue;
            }
            summaries.add(getPermissionGroupLabel(group).toString().toLowerCase());
            if (++count >= NUM_PERMISSION_TO_USE) {
                break;
            }
        }
        String summary = (count > 0) ? getContext().getString(R.string.app_permissions_summary,
                ListFormatter.getInstance().format(summaries)) : null;

        preference.setSummary(summary);
    }

    private Set<String> getAllPermissionsInGroups() {
        Set<String> result = new ArraySet<>();
        for (String group : PERMISSION_GROUPS) {
            try {
                List<PermissionInfo> permissions = mPackageManager.queryPermissionsByGroup(
                        group, /* flags= */ 0);
                for (PermissionInfo permissionInfo : permissions) {
                    result.add(permissionInfo.name);
                }
            } catch (PackageManager.NameNotFoundException e) {
                LOG.e("Error getting permissions in group " + group, e);
            }
        }
        return result;
    }

    private Set<String> getGrantedPermissionGroups(Set<String> permissions) {
        Set<String> grantedPermissionGroups = new ArraySet<>();
        List<PackageInfo> installedPackages =
                mPackageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS);
        for (PackageInfo installedPackage : installedPackages) {
            if (installedPackage.permissions == null) {
                continue;
            }
            for (PermissionInfo permissionInfo : installedPackage.permissions) {
                if (permissions.contains(permissionInfo.name)) {
                    grantedPermissionGroups.add(permissionInfo.group);
                }
            }
        }
        return grantedPermissionGroups;
    }

    private CharSequence getPermissionGroupLabel(String group) {
        try {
            PermissionGroupInfo groupInfo = mPackageManager.getPermissionGroupInfo(
                    group, /* flags= */ 0);
            return groupInfo.loadLabel(mPackageManager);
        } catch (PackageManager.NameNotFoundException e) {
            LOG.e("Error getting permissions label.", e);
        }
        return group;
    }
}
