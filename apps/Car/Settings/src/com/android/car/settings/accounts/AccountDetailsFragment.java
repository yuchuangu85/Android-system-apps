/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.car.settings.accounts;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.XmlRes;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.android.car.settings.R;
import com.android.car.settings.common.ErrorDialog;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.SettingsFragment;
import com.android.settingslib.accounts.AuthenticatorHelper;

import java.io.IOException;
import java.util.Arrays;

/**
 * Shows account details, and delete account option.
 */
public class AccountDetailsFragment extends SettingsFragment implements
        AuthenticatorHelper.OnAccountsUpdateListener {
    public static final String EXTRA_ACCOUNT = "extra_account";
    public static final String EXTRA_ACCOUNT_LABEL = "extra_account_label";
    public static final String EXTRA_USER_INFO = "extra_user_info";

    private Account mAccount;
    private UserInfo mUserInfo;
    private AuthenticatorHelper mAuthenticatorHelper;

    /**
     * Creates a new AccountDetailsFragment.
     *
     * <p>Passes the provided account, label, and user info to the fragment via fragment arguments.
     */
    public static AccountDetailsFragment newInstance(Account account, CharSequence label,
            UserInfo userInfo) {
        AccountDetailsFragment
                accountDetailsFragment = new AccountDetailsFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(EXTRA_ACCOUNT, account);
        bundle.putCharSequence(EXTRA_ACCOUNT_LABEL, label);
        bundle.putParcelable(EXTRA_USER_INFO, userInfo);
        accountDetailsFragment.setArguments(bundle);
        return accountDetailsFragment;
    }

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.account_details_fragment;
    }

    @Override
    @LayoutRes
    protected int getActionBarLayoutId() {
        return R.layout.action_bar_with_button;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        mAccount = getArguments().getParcelable(EXTRA_ACCOUNT);
        mUserInfo = getArguments().getParcelable(EXTRA_USER_INFO);

        use(AccountDetailsPreferenceController.class, R.string.pk_account_details)
                .setAccount(mAccount);
        use(AccountDetailsPreferenceController.class, R.string.pk_account_details)
                .setUserHandle(mUserInfo.getUserHandle());

        use(AccountSyncPreferenceController.class, R.string.pk_account_sync)
                .setAccount(mAccount);
        use(AccountDetailsSettingController.class, R.string.pk_account_settings)
                .setAccount(mAccount);
        use(AccountSyncPreferenceController.class, R.string.pk_account_sync)
                .setUserHandle(mUserInfo.getUserHandle());
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Set the fragment's title
        TextView titleView = requireActivity().findViewById(R.id.title);
        titleView.setText(getArguments().getCharSequence(EXTRA_ACCOUNT_LABEL));

        // Enable the remove account button if the user is allowed to modify accounts.
        Button removeAccountButton = requireActivity().findViewById(R.id.action_button1);
        if (new CarUserManagerHelper(getContext()).canCurrentProcessModifyAccounts()) {
            removeAccountButton.setText(R.string.remove_button);
            removeAccountButton.setOnClickListener(v -> onRemoveAccountClicked());
        } else {
            removeAccountButton.setVisibility(View.GONE);
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        mAuthenticatorHelper = new AuthenticatorHelper(getContext(), mUserInfo.getUserHandle(),
                this);
        mAuthenticatorHelper.listenToAccountUpdates();
    }

    @Override
    public void onStop() {
        super.onStop();
        mAuthenticatorHelper.stopListeningToAccountUpdates();
    }

    @Override
    public void onAccountsUpdate(UserHandle userHandle) {
        if (!accountExists()) {
            // The account was deleted. Pop back.
            goBack();
        }
    }

    /** Returns whether the account being shown by this fragment still exists. */
    @VisibleForTesting
    boolean accountExists() {
        if (mAccount == null) {
            return false;
        }

        Account[] accounts = AccountManager.get(getContext()).getAccountsByTypeAsUser(mAccount.type,
                mUserInfo.getUserHandle());

        return Arrays.asList(accounts).contains(mAccount);
    }

    private void onRemoveAccountClicked() {
        ConfirmRemoveAccountDialogFragment.show(this, mAccount, mUserInfo.getUserHandle());
    }

    /**
     * Dialog to confirm with user about account removal
     */
    public static class ConfirmRemoveAccountDialogFragment extends DialogFragment implements
            DialogInterface.OnClickListener {
        private static final String KEY_ACCOUNT = "account";
        private static final String DIALOG_TAG = "confirmRemoveAccount";
        private static final Logger LOG = new Logger(ConfirmRemoveAccountDialogFragment.class);
        private final AccountManagerCallback<Bundle> mCallback =
                future -> {
                    // If already out of this screen, don't proceed.
                    if (!getTargetFragment().isResumed()) {
                        return;
                    }

                    boolean success = false;
                    try {
                        success =
                                future.getResult().getBoolean(
                                        AccountManager.KEY_BOOLEAN_RESULT);
                    } catch (OperationCanceledException | IOException | AuthenticatorException e) {
                        LOG.v("removeAccount error: " + e);
                    }
                    final Activity activity = getTargetFragment().getActivity();
                    if (!success && activity != null && !activity.isFinishing()) {
                        ErrorDialog.show(getTargetFragment(),
                                R.string.remove_account_error_title);
                    } else {
                        getTargetFragment().getFragmentManager().popBackStack();
                    }
                };
        private Account mAccount;
        private UserHandle mUserHandle;

        public static void show(
                Fragment parent, Account account, UserHandle userHandle) {
            final ConfirmRemoveAccountDialogFragment dialog =
                    new ConfirmRemoveAccountDialogFragment();
            Bundle bundle = new Bundle();
            bundle.putParcelable(KEY_ACCOUNT, account);
            bundle.putParcelable(Intent.EXTRA_USER, userHandle);
            dialog.setArguments(bundle);
            dialog.setTargetFragment(parent, 0);
            dialog.show(parent.getFragmentManager(), DIALOG_TAG);
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            final Bundle arguments = getArguments();
            mAccount = arguments.getParcelable(KEY_ACCOUNT);
            mUserHandle = arguments.getParcelable(Intent.EXTRA_USER);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getContext())
                    .setTitle(R.string.really_remove_account_title)
                    .setMessage(R.string.really_remove_account_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.remove_account_title, this)
                    .create();
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            Activity activity = getTargetFragment().getActivity();
            AccountManager.get(activity).removeAccountAsUser(
                    mAccount, activity, mCallback, null, mUserHandle);
            dialog.dismiss();
        }
    }
}
