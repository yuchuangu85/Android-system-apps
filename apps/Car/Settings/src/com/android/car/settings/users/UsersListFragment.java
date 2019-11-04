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
 * limitations under the License
 */

package com.android.car.settings.users;

import android.car.drivingstate.CarUxRestrictions;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;

import androidx.annotation.LayoutRes;
import androidx.annotation.XmlRes;

import com.android.car.settings.R;
import com.android.car.settings.common.CarUxRestrictionsHelper;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.ErrorDialog;
import com.android.car.settings.common.SettingsFragment;
import com.android.internal.annotations.VisibleForTesting;

/**
 * Lists all Users available on this device.
 */
public class UsersListFragment extends SettingsFragment implements
        ConfirmExitRetailModeDialog.ConfirmExitRetailModeListener,
        AddNewUserTask.AddNewUserListener {
    private static final String FACTORY_RESET_PACKAGE_NAME = "android";
    private static final String FACTORY_RESET_REASON = "ExitRetailModeConfirmed";

    private CarUserManagerHelper mCarUserManagerHelper;

    private ProgressBar mProgressBar;
    private Button mAddUserButton;

    private AsyncTask mAddNewUserTask;
    /** Indicates that a task is running. */
    private boolean mIsBusy;
    private float mOpacityDisabled;
    private float mOpacityEnabled;
    private boolean mRestricted;

    @VisibleForTesting
    final ConfirmationDialogFragment.ConfirmListener mConfirmListener = arguments -> {
        mAddNewUserTask = new AddNewUserTask(mCarUserManagerHelper, /* addNewUserListener= */
                this).execute(getContext().getString(R.string.user_new_user_name));
        mIsBusy = true;
        updateUi();
    };

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.users_list_fragment;
    }

    @Override
    @LayoutRes
    protected int getActionBarLayoutId() {
        return R.layout.action_bar_with_button;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mOpacityDisabled = getContext().getResources().getFloat(R.dimen.opacity_disabled);
        mOpacityEnabled = getContext().getResources().getFloat(R.dimen.opacity_enabled);
        mCarUserManagerHelper = new CarUserManagerHelper(getContext());
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mProgressBar = requireActivity().findViewById(R.id.progress_bar);

        mAddUserButton = getActivity().findViewById(R.id.action_button1);
        mAddUserButton.setOnClickListener(v -> {
            if (mRestricted) {
                showBlockingMessage();
            } else {
                handleAddUserClicked();
            }
        });
        if (mCarUserManagerHelper.isCurrentProcessDemoUser()) {
            mAddUserButton.setText(R.string.exit_retail_button_text);
        } else if (mCarUserManagerHelper.canCurrentProcessAddUsers()) {
            mAddUserButton.setText(R.string.user_add_user_menu);
        }
    }

    @Override
    public void onUxRestrictionsChanged(CarUxRestrictions restrictionInfo) {
        mRestricted = CarUxRestrictionsHelper.isNoSetup(restrictionInfo);
        mAddUserButton.setAlpha(mRestricted ? mOpacityDisabled : mOpacityEnabled);
    }

    /**
     * Will perform a factory reset. Copied from
     * {@link com.android.settings.MasterClearConfirm#doMasterClear()}
     */
    @Override
    public void onExitRetailModeConfirmed() {
        Intent intent = new Intent(Intent.ACTION_FACTORY_RESET);
        intent.setPackage(FACTORY_RESET_PACKAGE_NAME);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        intent.putExtra(Intent.EXTRA_REASON, FACTORY_RESET_REASON);
        intent.putExtra(Intent.EXTRA_WIPE_EXTERNAL_STORAGE, true);
        intent.putExtra(Intent.EXTRA_WIPE_ESIMS, true);
        getActivity().sendBroadcast(intent);
        // Intent handling is asynchronous -- assume it will happen soon.
    }

    @Override
    public void onStart() {
        super.onStart();
        updateUi();
    }

    @Override
    public void onStop() {
        super.onStop();
        mProgressBar.setVisibility(View.GONE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mAddNewUserTask != null) {
            mAddNewUserTask.cancel(/* mayInterruptIfRunning= */ false);
        }
    }

    @Override
    public void onUserAddedSuccess() {
        mIsBusy = false;
        updateUi();
    }

    @Override
    public void onUserAddedFailure() {
        mIsBusy = false;
        updateUi();
        // Display failure dialog.
        ErrorDialog.show(this, R.string.add_user_error_title);
    }

    private void updateUi() {
        mAddUserButton.setEnabled(!mIsBusy);
        mProgressBar.setVisibility(mIsBusy ? View.VISIBLE : View.GONE);
    }

    private void handleAddUserClicked() {
        // If the user is a demo user, show a dialog asking if they want to exit retail/demo mode.
        if (mCarUserManagerHelper.isCurrentProcessDemoUser()) {
            ConfirmExitRetailModeDialog dialog = new ConfirmExitRetailModeDialog();
            dialog.setConfirmExitRetailModeListener(this);
            dialog.show(this);
            return;
        }

        // If no more users can be added because the maximum allowed number is reached, let the user
        // know.
        if (mCarUserManagerHelper.isUserLimitReached()) {
            MaxUsersLimitReachedDialog dialog = new MaxUsersLimitReachedDialog(
                    mCarUserManagerHelper.getMaxSupportedRealUsers());
            dialog.show(this);
            return;
        }

        // Only add the add user button if the current user is allowed to add a user.
        if (mCarUserManagerHelper.canCurrentProcessAddUsers()) {
            ConfirmationDialogFragment dialogFragment =
                    UsersDialogProvider.getConfirmCreateNewUserDialogFragment(getContext(),
                            mConfirmListener, null);

            dialogFragment.show(getFragmentManager(), ConfirmationDialogFragment.TAG);
        }
    }
}
