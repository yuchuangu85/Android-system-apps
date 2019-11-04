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

package com.android.car.settings.users;

import android.content.Context;
import android.content.pm.UserInfo;

import androidx.annotation.Nullable;

import com.android.car.settings.R;
import com.android.car.settings.common.ConfirmationDialogFragment;

/**
 * Provides common Users-related ConfirmationDialogFragments to ensure consistency
 */
public final class UsersDialogProvider {

    /** Argument key to store the user info that the device is trying to make an admin. */
    static final String KEY_USER_TO_MAKE_ADMIN = "USER_TO_MAKE_ADMIN";
    /** Argument key to store the user type that the device is trying to remove. */
    static final String KEY_USER_TYPE = "USER_TYPE";
    /** {@link KEY_USER_TYPE} value when removing the last admin on the device. */
    static final String LAST_ADMIN = "LAST_ADMIN";
    /** {@link KEY_USER_TYPE} value when removing the last user on the device. */
    static final String LAST_USER = "LAST_USER";
    /**
     * Default {@link KEY_USER_TYPE} value when removing a user that is neither {@link LAST_ADMIN}
     * nor {@link LAST_USER}.
     */
    static final String ANY_USER = "ANY_USER";

    private UsersDialogProvider() {
    }

    /** Gets a confirmation dialog fragment to confirm or reject adding a new user. */
    public static ConfirmationDialogFragment getConfirmCreateNewUserDialogFragment(Context context,
            @Nullable ConfirmationDialogFragment.ConfirmListener confirmListener,
            @Nullable ConfirmationDialogFragment.RejectListener rejectListener) {

        String message = context.getString(R.string.user_add_user_message_setup)
                .concat(System.lineSeparator())
                .concat(System.lineSeparator())
                .concat(context.getString(R.string.user_add_user_message_update));

        ConfirmationDialogFragment dialogFragment = new ConfirmationDialogFragment.Builder(context)
                .setTitle(R.string.user_add_user_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, confirmListener)
                .setNegativeButton(android.R.string.cancel, rejectListener)
                .build();

        return dialogFragment;
    }

    /** Gets a confirmation dialog fragment to confirm or reject making a user an admin. */
    public static ConfirmationDialogFragment getConfirmGrantAdminDialogFragment(Context context,
            @Nullable ConfirmationDialogFragment.ConfirmListener confirmListener,
            @Nullable ConfirmationDialogFragment.RejectListener rejectListener,
            UserInfo userToMakeAdmin) {

        String message = context.getString(R.string.grant_admin_permissions_message)
                .concat(System.lineSeparator())
                .concat(System.lineSeparator())
                .concat(context.getString(R.string.action_not_reversible_message));

        ConfirmationDialogFragment dialogFragment = new ConfirmationDialogFragment.Builder(context)
                .setTitle(R.string.grant_admin_permissions_title)
                .setMessage(message)
                .setPositiveButton(R.string.confirm_grant_admin, confirmListener)
                .setNegativeButton(android.R.string.cancel, rejectListener)
                .addArgumentParcelable(KEY_USER_TO_MAKE_ADMIN, userToMakeAdmin)
                .build();

        return dialogFragment;
    }

    /**
     * Gets a confirmation dialog fragment to confirm or reject removing the last user on the
     * device.
     */
    public static ConfirmationDialogFragment getConfirmRemoveLastUserDialogFragment(Context context,
            @Nullable ConfirmationDialogFragment.ConfirmListener confirmListener,
            @Nullable ConfirmationDialogFragment.RejectListener rejectListener) {

        String message = context.getString(R.string.delete_last_user_admin_created_message)
                .concat(System.lineSeparator())
                .concat(System.lineSeparator())
                .concat(context.getString(R.string.delete_last_user_system_setup_required_message));

        ConfirmationDialogFragment dialogFragment = new ConfirmationDialogFragment.Builder(context)
                .setTitle(R.string.delete_last_user_dialog_title)
                .setMessage(message)
                .setPositiveButton(R.string.delete_button, confirmListener)
                .setNegativeButton(android.R.string.cancel, rejectListener)
                .addArgumentString(KEY_USER_TYPE, LAST_USER)
                .build();

        return dialogFragment;
    }

    /**
     * Gets a confirmation dialog fragment to confirm or reject removing the last admin user on the
     * device.
     */
    public static ConfirmationDialogFragment getConfirmRemoveLastAdminDialogFragment(
            Context context,
            @Nullable ConfirmationDialogFragment.ConfirmListener confirmListener,
            @Nullable ConfirmationDialogFragment.RejectListener rejectListener) {

        ConfirmationDialogFragment dialogFragment = new ConfirmationDialogFragment.Builder(context)
                .setTitle(R.string.choose_new_admin_title)
                .setMessage(R.string.choose_new_admin_message)
                .setPositiveButton(R.string.choose_new_admin_label, confirmListener)
                .setNegativeButton(android.R.string.cancel, rejectListener)
                .addArgumentString(KEY_USER_TYPE, LAST_ADMIN)
                .build();

        return dialogFragment;
    }

    /**
     * Gets a confirmation dialog fragment to confirm or reject removing a user that is neither the
     * last admin nor the last user on the device.
     */
    public static ConfirmationDialogFragment getConfirmRemoveUserDialogFragment(Context context,
            @Nullable ConfirmationDialogFragment.ConfirmListener confirmListener,
            @Nullable ConfirmationDialogFragment.RejectListener rejectListener) {

        ConfirmationDialogFragment dialogFragment = new ConfirmationDialogFragment.Builder(context)
                .setTitle(R.string.delete_user_dialog_title)
                .setMessage(R.string.delete_user_dialog_message)
                .setPositiveButton(R.string.delete_button, confirmListener)
                .setNegativeButton(android.R.string.cancel, rejectListener)
                .addArgumentString(KEY_USER_TYPE, ANY_USER)
                .build();

        return dialogFragment;
    }
}
