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

import androidx.annotation.VisibleForTesting;

import com.android.car.settings.R;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.ErrorDialog;
import com.android.car.settings.common.FragmentController;

/**
 * Business logic for when the last admin is about to be removed from the device and a new
 * administrator needs to be chosen.
 */
public class ChooseNewAdminPreferenceController extends UsersBasePreferenceController {

    private final ConfirmationDialogFragment.ConfirmListener mConfirmListener = arguments -> {
        UserInfo userToMakeAdmin = (UserInfo) arguments.get(
                UsersDialogProvider.KEY_USER_TO_MAKE_ADMIN);
        assignNewAdminAndRemoveOldAdmin(userToMakeAdmin);
        getFragmentController().goBack();
    };

    private UserInfo mAdminInfo;

    public ChooseNewAdminPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        getPreferenceProvider().setIncludeCurrentUser(false);
        getPreferenceProvider().setIncludeGuest(false);
    }

    /** Setter for the user info of the admin we're deleting. */
    public void setAdminInfo(UserInfo adminInfo) {
        mAdminInfo = adminInfo;
    }

    @Override
    protected void checkInitialized() {
        if (mAdminInfo == null) {
            throw new IllegalStateException("Admin info should be set by this point");
        }
    }

    @Override
    protected void onCreateInternal() {
        super.onCreateInternal();
        ConfirmationDialogFragment dialogFragment =
                (ConfirmationDialogFragment) getFragmentController().findDialogByTag(
                        ConfirmationDialogFragment.TAG);

        ConfirmationDialogFragment.resetListeners(dialogFragment,
                mConfirmListener, /* rejectListener= */ null);
    }

    @Override
    protected void userClicked(UserInfo userToMakeAdmin) {

        ConfirmationDialogFragment dialogFragment =
                UsersDialogProvider.getConfirmGrantAdminDialogFragment(getContext(),
                        mConfirmListener, /* rejectListener= */ null, userToMakeAdmin);

        getFragmentController().showDialog(dialogFragment, ConfirmationDialogFragment.TAG);
    }

    @VisibleForTesting
    void assignNewAdminAndRemoveOldAdmin(UserInfo userToMakeAdmin) {
        getCarUserManagerHelper().grantAdminPermissions(userToMakeAdmin);
        removeOldAdmin();
    }

    private void removeOldAdmin() {
        if (!getCarUserManagerHelper().removeUser(mAdminInfo,
                getContext().getString(R.string.user_guest))) {
            // If failed, need to show error dialog for users.
            getFragmentController().showDialog(
                    ErrorDialog.newInstance(R.string.delete_user_error_title), /* tag= */ null);
        }
    }
}
