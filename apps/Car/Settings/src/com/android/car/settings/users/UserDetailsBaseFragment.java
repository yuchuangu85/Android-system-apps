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

import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.LayoutRes;

import com.android.car.settings.R;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.ErrorDialog;
import com.android.car.settings.common.SettingsFragment;

/** Common logic shared for controlling the action bar which contains a button to delete a user. */
public abstract class UserDetailsBaseFragment extends SettingsFragment {

    private CarUserManagerHelper mCarUserManagerHelper;
    private UserInfo mUserInfo;

    private final ConfirmationDialogFragment.ConfirmListener mConfirmListener = arguments -> {
        String userType = arguments.getString(UsersDialogProvider.KEY_USER_TYPE);
        if (userType.equals(UsersDialogProvider.LAST_ADMIN)) {
            launchFragment(ChooseNewAdminFragment.newInstance(mUserInfo));
        } else {
            if (mCarUserManagerHelper.removeUser(
                    mUserInfo, getContext().getString(R.string.user_guest))) {
                getActivity().onBackPressed();
            } else {
                // If failed, need to show error dialog for users.
                ErrorDialog.show(this, R.string.delete_user_error_title);
            }
        }
    };

    /** Adds user id to fragment arguments. */
    protected static UserDetailsBaseFragment addUserIdToFragmentArguments(
            UserDetailsBaseFragment fragment, int userId) {
        Bundle bundle = new Bundle();
        bundle.putInt(Intent.EXTRA_USER_ID, userId);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    @LayoutRes
    protected int getActionBarLayoutId() {
        return R.layout.action_bar_with_button;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        int userId = getArguments().getInt(Intent.EXTRA_USER_ID);
        mCarUserManagerHelper = new CarUserManagerHelper(getContext());
        mUserInfo = UserUtils.getUserInfo(getContext(), userId);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ConfirmationDialogFragment dialogFragment =
                (ConfirmationDialogFragment) findDialogByTag(ConfirmationDialogFragment.TAG);
        ConfirmationDialogFragment.resetListeners(dialogFragment,
                mConfirmListener, /* rejectListener= */ null);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        showRemoveUserButton();

        TextView titleView = getActivity().findViewById(R.id.title);
        titleView.setText(getTitleText());
    }

    /** Make CarUserManagerHelper available to subclasses. */
    protected CarUserManagerHelper getCarUserManagerHelper() {
        return mCarUserManagerHelper;
    }

    /** Make UserInfo available to subclasses. */
    protected UserInfo getUserInfo() {
        return mUserInfo;
    }

    /** Refresh UserInfo in case it becomes invalid. */
    protected void refreshUserInfo() {
        mUserInfo = UserUtils.getUserInfo(getContext(), mUserInfo.id);
    }

    /** Defines the text that should be shown in the action bar. */
    protected abstract String getTitleText();

    private void showRemoveUserButton() {
        Button removeUserBtn = getActivity().findViewById(R.id.action_button1);
        // If the current user is not allowed to remove users, the user trying to be removed
        // cannot be removed, or the current user is a demo user, do not show delete button.
        if (!mCarUserManagerHelper.canCurrentProcessRemoveUsers()
                || !mCarUserManagerHelper.canUserBeRemoved(mUserInfo)
                || mCarUserManagerHelper.isCurrentProcessDemoUser()) {
            removeUserBtn.setVisibility(View.GONE);
            return;
        }
        removeUserBtn.setVisibility(View.VISIBLE);
        removeUserBtn.setText(R.string.delete_button);
        removeUserBtn.setOnClickListener(v -> showConfirmRemoveUserDialog());
    }

    private void showConfirmRemoveUserDialog() {
        boolean isLastUser = mCarUserManagerHelper.getAllPersistentUsers().size() == 1;
        boolean isLastAdmin = mUserInfo.isAdmin()
                && mCarUserManagerHelper.getAllAdminUsers().size() == 1;

        ConfirmationDialogFragment dialogFragment;

        if (isLastUser) {
            dialogFragment = UsersDialogProvider.getConfirmRemoveLastUserDialogFragment(
                    getContext(), mConfirmListener, /* rejectListener= */ null);
        } else if (isLastAdmin) {
            dialogFragment = UsersDialogProvider.getConfirmRemoveLastAdminDialogFragment(
                    getContext(), mConfirmListener, /* rejectListener= */ null);
        } else {
            dialogFragment = UsersDialogProvider.getConfirmRemoveUserDialogFragment(getContext(),
                    mConfirmListener, /* rejectListener= */ null);
        }

        dialogFragment.show(getFragmentManager(), ConfirmationDialogFragment.TAG);
    }
}
