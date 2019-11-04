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

package com.android.car.settings.users;

import android.car.drivingstate.CarUxRestrictions;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.os.UserManager;

import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceGroup;
import androidx.preference.SwitchPreference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;

import java.util.ArrayList;
import java.util.List;

/**
 * Constructs and populates the permissions toggles for non admin users.
 *
 * <p>In order to add a new permission, it needs to be added to {@link
 * CarUserManagerHelper#OPTIONAL_NON_ADMIN_RESTRICTIONS} and the appropriate label needs to be added
 * to {@link #PERMISSIONS_LIST}.
 */
public class PermissionsPreferenceController extends
        UserDetailsBasePreferenceController<PreferenceGroup> {

    private static class UserPermission {
        private final String mPermissionKey;
        @StringRes
        private final int mPermissionTitle;

        UserPermission(String key, int title) {
            mPermissionKey = key;
            mPermissionTitle = title;
        }

        public String getPermissionKey() {
            return mPermissionKey;
        }

        public int getPermissionTitle() {
            return mPermissionTitle;
        }
    }

    @VisibleForTesting
    static final String PERMISSION_TYPE_KEY = "permission_type_key";
    private static final List<UserPermission> PERMISSIONS_LIST = new ArrayList<>();

    // Add additional preferences to show here (in the order they should appear).
    static {
        PERMISSIONS_LIST.add(new UserPermission(UserManager.DISALLOW_ADD_USER,
                R.string.create_user_permission_title));
        PERMISSIONS_LIST.add(new UserPermission(UserManager.DISALLOW_OUTGOING_CALLS,
                R.string.outgoing_calls_permission_title));
        PERMISSIONS_LIST.add(new UserPermission(UserManager.DISALLOW_SMS,
                R.string.sms_messaging_permission_title));
        PERMISSIONS_LIST.add(new UserPermission(UserManager.DISALLOW_INSTALL_APPS,
                R.string.install_apps_permission_title));
        PERMISSIONS_LIST.add(new UserPermission(UserManager.DISALLOW_UNINSTALL_APPS,
                R.string.uninstall_apps_permission_title));
    }

    private final List<SwitchPreference> mPermissionPreferences = new ArrayList<>();

    public PermissionsPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);

        for (UserPermission permission : PERMISSIONS_LIST) {
            SwitchPreference preference = new SwitchPreference(context);
            preference.setTitle(permission.getPermissionTitle());
            preference.getExtras().putString(PERMISSION_TYPE_KEY, permission.getPermissionKey());
            preference.setOnPreferenceChangeListener((pref, newValue) -> {
                boolean granted = (boolean) newValue;
                getCarUserManagerHelper().setUserRestriction(getUserInfo(),
                        pref.getExtras().getString(PERMISSION_TYPE_KEY), !granted);
                return true;
            });
            mPermissionPreferences.add(preference);
        }
    }

    @Override
    protected Class<PreferenceGroup> getPreferenceType() {
        return PreferenceGroup.class;
    }

    @Override
    protected void onCreateInternal() {
        for (SwitchPreference switchPreference : mPermissionPreferences) {
            getPreference().addPreference(switchPreference);
        }
    }

    @Override
    protected void updateState(PreferenceGroup preferenceGroup) {
        for (SwitchPreference switchPreference : mPermissionPreferences) {
            switchPreference.setChecked(!getCarUserManagerHelper().hasUserRestriction(
                    switchPreference.getExtras().getString(PERMISSION_TYPE_KEY), getUserInfo()));
        }
    }
}
