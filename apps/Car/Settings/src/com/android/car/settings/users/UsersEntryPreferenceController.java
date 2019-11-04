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

import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;

/**
 * Controller which determines if the top level entry into User settings should direct to a list
 * of all users or a user details page based on the current user's admin status.
 */
public class UsersEntryPreferenceController extends PreferenceController<Preference> {

    private static final Logger LOG = new Logger(UsersEntryPreferenceController.class);

    private final CarUserManagerHelper mCarUserManagerHelper;

    public UsersEntryPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mCarUserManagerHelper = new CarUserManagerHelper(context);
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    public boolean handlePreferenceClicked(Preference preference) {
        if (mCarUserManagerHelper.isCurrentProcessAdminUser()) {
            // Admins can see a full list of users in Settings.
            LOG.v("Creating UsersListFragment for admin user.");
            getFragmentController().launchFragment(new UsersListFragment());
        } else {
            // Non-admins can only manage themselves in Settings.
            LOG.v("Creating UserDetailsFragment for non-admin.");
            getFragmentController().launchFragment(UserDetailsFragment.newInstance(
                    mCarUserManagerHelper.getCurrentProcessUserId()));
        }
        return true;
    }
}
