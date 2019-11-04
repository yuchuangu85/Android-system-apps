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
import android.content.Context;
import android.content.pm.UserInfo;

import com.android.car.settings.common.FragmentController;

/** Business logic for populating the users for the users list settings. */
public class UsersListPreferenceController extends UsersBasePreferenceController {

    public UsersListPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected void userClicked(UserInfo userInfo) {
        if (UserUtils.isAdminViewingNonAdmin(getCarUserManagerHelper(), userInfo)) {
            // Admin viewing non admin.
            getFragmentController().launchFragment(
                    UserDetailsPermissionsFragment.newInstance(userInfo.id));
        } else {
            getFragmentController().launchFragment(UserDetailsFragment.newInstance(userInfo.id));
        }
    }
}
