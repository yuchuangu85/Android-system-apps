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
package com.android.car.settings.accounts;

import android.car.drivingstate.CarUxRestrictions;
import android.car.userlib.CarUserManagerHelper;
import android.content.ContentResolver;
import android.content.Context;
import android.os.UserHandle;

import androidx.annotation.VisibleForTesting;
import androidx.preference.TwoStatePreference;

import com.android.car.settings.R;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

/**
 * Controller for the preference that allows the user to toggle automatic syncing of accounts.
 *
 * <p>Copied from {@link com.android.settings.users.AutoSyncDataPreferenceController}
 */
public class AccountAutoSyncPreferenceController extends PreferenceController<TwoStatePreference> {

    private final UserHandle mUserHandle;
    /**
     * Argument key to store a value that indicates whether the account auto sync is being enabled
     * or disabled.
     */
    @VisibleForTesting
    static final String KEY_ENABLING = "ENABLING";
    /** Argument key to store user handle. */
    @VisibleForTesting
    static final String KEY_USER_HANDLE = "USER_HANDLE";

    @VisibleForTesting
    final ConfirmationDialogFragment.ConfirmListener mConfirmListener = arguments -> {
        boolean enabling = arguments.getBoolean(KEY_ENABLING);
        UserHandle userHandle = arguments.getParcelable(KEY_USER_HANDLE);
        ContentResolver.setMasterSyncAutomaticallyAsUser(enabling, userHandle.getIdentifier());
        getPreference().setChecked(enabling);
    };

    public AccountAutoSyncPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        CarUserManagerHelper carUserManagerHelper = new CarUserManagerHelper(context);
        mUserHandle = carUserManagerHelper.getCurrentProcessUserInfo().getUserHandle();
    }

    @Override
    protected Class<TwoStatePreference> getPreferenceType() {
        return TwoStatePreference.class;
    }

    @Override
    protected void updateState(TwoStatePreference preference) {
        preference.setChecked(ContentResolver.getMasterSyncAutomaticallyAsUser(
                mUserHandle.getIdentifier()));
    }

    @Override
    protected void onCreateInternal() {
        // If the dialog is still up, reattach the preference
        ConfirmationDialogFragment dialog =
                (ConfirmationDialogFragment) getFragmentController().findDialogByTag(
                        ConfirmationDialogFragment.TAG);

        ConfirmationDialogFragment.resetListeners(dialog, mConfirmListener, /* rejectListener= */
                null);
    }

    @Override
    protected boolean handlePreferenceChanged(TwoStatePreference preference, Object checked) {
        getFragmentController().showDialog(
                getAutoSyncChangeConfirmationDialogFragment((boolean) checked),
                ConfirmationDialogFragment.TAG);
        // The dialog will change the state of the preference if the user confirms, so don't handle
        // it here
        return false;
    }

    private ConfirmationDialogFragment getAutoSyncChangeConfirmationDialogFragment(
            boolean enabling) {
        int dialogTitle;
        int dialogMessage;

        if (enabling) {
            dialogTitle = R.string.data_usage_auto_sync_on_dialog_title;
            dialogMessage = R.string.data_usage_auto_sync_on_dialog;
        } else {
            dialogTitle = R.string.data_usage_auto_sync_off_dialog_title;
            dialogMessage = R.string.data_usage_auto_sync_off_dialog;
        }

        ConfirmationDialogFragment dialogFragment =
                new ConfirmationDialogFragment.Builder(getContext())
                        .setTitle(dialogTitle).setMessage(dialogMessage)
                        .setPositiveButton(android.R.string.ok, mConfirmListener)
                        .setNegativeButton(android.R.string.cancel, /* rejectListener= */ null)
                        .addArgumentBoolean(KEY_ENABLING, enabling)
                        .addArgumentParcelable(KEY_USER_HANDLE, mUserHandle)
                        .build();

        return dialogFragment;
    }
}