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
import android.graphics.drawable.Drawable;

import androidx.annotation.VisibleForTesting;

import com.android.car.settings.common.ButtonPreference;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.FragmentController;

/** Business Logic for preference which promotes a regular user to an admin user. */
public class MakeAdminPreferenceController extends
        UserDetailsBasePreferenceController<ButtonPreference> {

    @VisibleForTesting
    final ConfirmationDialogFragment.ConfirmListener mConfirmListener =
            arguments -> {
                UserInfo userToMakeAdmin = (UserInfo) arguments.get(
                        UsersDialogProvider.KEY_USER_TO_MAKE_ADMIN);
                getCarUserManagerHelper().grantAdminPermissions(userToMakeAdmin);
                getFragmentController().goBack();
            };

    public MakeAdminPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<ButtonPreference> getPreferenceType() {
        return ButtonPreference.class;
    }


    /** Ensure that the listener is reset if the dialog was open during a configuration change. */
    @Override
    protected void onCreateInternal() {
        ConfirmationDialogFragment dialog =
                (ConfirmationDialogFragment) getFragmentController().findDialogByTag(
                        ConfirmationDialogFragment.TAG);

        ConfirmationDialogFragment.resetListeners(dialog, mConfirmListener, /* rejectListener= */
                null);
    }

    @Override
    protected void updateState(ButtonPreference preference) {
        preference.setOnButtonClickListener(pref -> {

            ConfirmationDialogFragment dialogFragment =
                    UsersDialogProvider.getConfirmGrantAdminDialogFragment(getContext(),
                            mConfirmListener, /* rejectListener= */ null, getUserInfo());

            getFragmentController().showDialog(dialogFragment, ConfirmationDialogFragment.TAG);
        });

        Drawable icon = new UserIconProvider(getCarUserManagerHelper()).getUserIcon(getUserInfo(),
                getContext());
        preference.setIcon(icon);
    }
}
