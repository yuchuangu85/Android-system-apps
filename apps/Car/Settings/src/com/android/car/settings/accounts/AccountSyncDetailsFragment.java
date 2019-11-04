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

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncAdapterType;
import android.content.SyncStatusObserver;
import android.os.Bundle;
import android.os.UserHandle;
import android.widget.Button;

import androidx.annotation.LayoutRes;
import androidx.annotation.XmlRes;

import com.android.car.settings.R;
import com.android.car.settings.common.SettingsFragment;
import com.android.settingslib.utils.ThreadUtils;

import java.util.Set;

/**
 * Shows details for syncing an account.
 */
public class AccountSyncDetailsFragment extends SettingsFragment {
    private static final String EXTRA_ACCOUNT = "extra_account";
    private static final String EXTRA_USER_HANDLE = "extra_user_handle";
    private boolean mIsStarted = false;
    private Object mStatusChangeListenerHandle;
    private SyncStatusObserver mSyncStatusObserver =
            which -> ThreadUtils.postOnMainThread(() -> {
                // The observer call may occur even if the fragment hasn't been started, so
                // only force an update if the fragment hasn't been stopped.
                if (mIsStarted) {
                    updateSyncButton();
                }
            });

    /**
     * Creates a new AccountSyncDetailsFragment.
     *
     * <p>Passes the provided account and user handle to the fragment via fragment arguments.
     */
    public static AccountSyncDetailsFragment newInstance(Account account, UserHandle userHandle) {
        AccountSyncDetailsFragment accountSyncDetailsFragment = new AccountSyncDetailsFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(EXTRA_ACCOUNT, account);
        bundle.putParcelable(EXTRA_USER_HANDLE, userHandle);
        accountSyncDetailsFragment.setArguments(bundle);
        return accountSyncDetailsFragment;
    }

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.account_sync_details_fragment;
    }

    @Override
    @LayoutRes
    protected int getActionBarLayoutId() {
        return R.layout.action_bar_with_button;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        Account account = getArguments().getParcelable(EXTRA_ACCOUNT);
        UserHandle userHandle = getArguments().getParcelable(EXTRA_USER_HANDLE);

        use(AccountDetailsWithSyncStatusPreferenceController.class,
                R.string.pk_account_details_with_sync)
                .setAccount(account);
        use(AccountDetailsWithSyncStatusPreferenceController.class,
                R.string.pk_account_details_with_sync)
                .setUserHandle(userHandle);

        use(AccountSyncDetailsPreferenceController.class, R.string.pk_account_sync_details)
                .setAccount(account);
        use(AccountSyncDetailsPreferenceController.class, R.string.pk_account_sync_details)
                .setUserHandle(userHandle);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        updateSyncButton();
    }

    @Override
    public void onStart() {
        super.onStart();
        mIsStarted = true;
        mStatusChangeListenerHandle = ContentResolver.addStatusChangeListener(
                ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE
                        | ContentResolver.SYNC_OBSERVER_TYPE_STATUS
                        | ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS, mSyncStatusObserver);
    }

    @Override
    public void onStop() {
        super.onStop();
        mIsStarted = false;
        if (mStatusChangeListenerHandle != null) {
            ContentResolver.removeStatusChangeListener(mStatusChangeListenerHandle);
        }
    }

    private void updateSyncButton() {
        // Set the action button to either request or cancel sync, depending on the current state
        Button syncButton = requireActivity().findViewById(R.id.action_button1);

        UserHandle userHandle = getArguments().getParcelable(EXTRA_USER_HANDLE);
        boolean hasActiveSyncs = !ContentResolver.getCurrentSyncsAsUser(
                userHandle.getIdentifier()).isEmpty();

        // If there are active syncs, clicking the button with cancel them. Otherwise, clicking the
        // button will start them.
        syncButton.setText(
                hasActiveSyncs ? R.string.sync_button_sync_cancel : R.string.sync_button_sync_now);
        syncButton.setOnClickListener(v -> {
            if (hasActiveSyncs) {
                cancelSyncForEnabledProviders();
            } else {
                requestSyncForEnabledProviders();
            }
        });
    }

    private void requestSyncForEnabledProviders() {
        Account account = getArguments().getParcelable(EXTRA_ACCOUNT);
        UserHandle userHandle = getArguments().getParcelable(EXTRA_USER_HANDLE);
        int userId = userHandle.getIdentifier();

        Set<SyncAdapterType> adapters = AccountSyncHelper.getSyncableSyncAdaptersForAccount(account,
                userHandle);
        for (SyncAdapterType adapter : adapters) {
            AccountSyncHelper.requestSyncIfAllowed(account, adapter.authority, userId);
        }
    }

    private void cancelSyncForEnabledProviders() {
        Account account = getArguments().getParcelable(EXTRA_ACCOUNT);
        UserHandle userHandle = getArguments().getParcelable(EXTRA_USER_HANDLE);
        int userId = userHandle.getIdentifier();

        Set<SyncAdapterType> adapters = AccountSyncHelper.getSyncableSyncAdaptersForAccount(account,
                userHandle);
        for (SyncAdapterType adapter : adapters) {
            ContentResolver.cancelSyncAsUser(account, adapter.authority, userId);
        }
    }
}
