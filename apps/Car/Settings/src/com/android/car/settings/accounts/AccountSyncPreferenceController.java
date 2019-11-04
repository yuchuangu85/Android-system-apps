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
import android.car.drivingstate.CarUxRestrictions;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncAdapterType;
import android.os.UserHandle;

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;

/**
 * Controller for the account syncing preference.
 *
 * <p>Largely derived from {@link com.android.settings.accounts.AccountSyncPreferenceController}.
 */
public class AccountSyncPreferenceController extends PreferenceController<Preference> {
    private static final Logger LOG = new Logger(AccountSyncPreferenceController.class);
    private Account mAccount;
    private UserHandle mUserHandle;

    public AccountSyncPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    /** Sets the account that the sync preferences are being shown for. */
    public void setAccount(Account account) {
        mAccount = account;
    }

    /** Sets the user handle used by the controller. */
    public void setUserHandle(UserHandle userHandle) {
        mUserHandle = userHandle;
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    /**
     * Verifies that the controller was properly initialized with
     * {@link #setAccount(Account)} and {@link #setUserHandle(UserHandle)}.
     *
     * @throws IllegalStateException if the account is {@code null}
     */
    @Override
    protected void checkInitialized() {
        LOG.v("checkInitialized");
        if (mAccount == null) {
            throw new IllegalStateException(
                    "AccountSyncPreferenceController must be initialized by calling "
                            + "setAccount(Account)");
        }
        if (mUserHandle == null) {
            throw new IllegalStateException(
                    "AccountSyncPreferenceController must be initialized by calling "
                            + "setUserHandle(UserHandle)");
        }
    }

    @Override
    protected void updateState(Preference preference) {
        preference.setSummary(getSummary());
    }

    @Override
    protected boolean handlePreferenceClicked(Preference preference) {
        getFragmentController().launchFragment(
                AccountSyncDetailsFragment.newInstance(mAccount, mUserHandle));
        return true;
    }

    private CharSequence getSummary() {
        int userId = mUserHandle.getIdentifier();
        SyncAdapterType[] syncAdapters = ContentResolver.getSyncAdapterTypesAsUser(userId);
        int total = 0;
        int enabled = 0;
        if (syncAdapters != null) {
            for (int i = 0, n = syncAdapters.length; i < n; i++) {
                SyncAdapterType sa = syncAdapters[i];
                // If the sync adapter isn't for this account type or if the user is not visible,
                // don't show it.
                if (!sa.accountType.equals(mAccount.type) || !sa.isUserVisible()) {
                    continue;
                }
                int syncState =
                        ContentResolver.getIsSyncableAsUser(mAccount, sa.authority, userId);
                if (syncState > 0) {
                    // If the sync adapter is syncable, add it to the count of items that can be
                    // synced.
                    total++;

                    // If sync is enabled for the sync adapter at the master level or at the account
                    // level, add it to the count of items that are enabled.
                    boolean syncEnabled = ContentResolver.getSyncAutomaticallyAsUser(
                            mAccount, sa.authority, userId);
                    boolean oneTimeSyncMode =
                            !ContentResolver.getMasterSyncAutomaticallyAsUser(userId);
                    if (oneTimeSyncMode || syncEnabled) {
                        enabled++;
                    }
                }
            }
        }
        if (enabled == 0) {
            return getContext().getText(R.string.account_sync_summary_all_off);
        } else if (enabled == total) {
            return getContext().getText(R.string.account_sync_summary_all_on);
        } else {
            return getContext().getString(R.string.account_sync_summary_some_on, enabled, total);
        }
    }
}
