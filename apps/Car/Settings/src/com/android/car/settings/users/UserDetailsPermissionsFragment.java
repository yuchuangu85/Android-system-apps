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

import android.content.Context;

import androidx.annotation.XmlRes;

import com.android.car.settings.R;

/**
 * Business logic for the permissions fragment. This fragment is used when an admin user views the
 * user details of a non-admin user.
 */
public class UserDetailsPermissionsFragment extends UserDetailsBaseFragment {

    /**
     * Creates instance of UserDetailsPermissionsFragment.
     */
    public static UserDetailsPermissionsFragment newInstance(int userId) {
        return (UserDetailsPermissionsFragment) UserDetailsBaseFragment
                .addUserIdToFragmentArguments(
                new UserDetailsPermissionsFragment(), userId);
    }

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.user_details_permissions_fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        use(MakeAdminPreferenceController.class, R.string.pk_make_user_admin).setUserInfo(
                getUserInfo());
        use(PermissionsPreferenceController.class, R.string.pk_user_permissions).setUserInfo(
                getUserInfo());
    }

    @Override
    protected String getTitleText() {
        return getContext().getString(R.string.user_details_admin_title,
                UserUtils.getUserDisplayName(getContext(), getCarUserManagerHelper(),
                        getUserInfo()));
    }
}

