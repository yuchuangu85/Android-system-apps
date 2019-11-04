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
import android.content.SyncInfo;
import android.content.SyncStatusInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.TextUtils;

import com.android.car.settings.common.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Helper that provides utility methods for account syncing. */
class AccountSyncHelper {
    private static final Logger LOG = new Logger(AccountSyncHelper.class);

    private AccountSyncHelper() {
    }

    /** Returns the visible sync adapters available for an account. */
    static Set<SyncAdapterType> getVisibleSyncAdaptersForAccount(Context context, Account account,
            UserHandle userHandle) {
        Set<SyncAdapterType> syncableAdapters = getSyncableSyncAdaptersForAccount(account,
                userHandle);

        syncableAdapters.removeIf(
                (SyncAdapterType syncAdapter) -> !isVisible(context, syncAdapter, userHandle));

        return syncableAdapters;
    }

    /** Returns the syncable sync adapters available for an account. */
    static Set<SyncAdapterType> getSyncableSyncAdaptersForAccount(Account account,
            UserHandle userHandle) {
        Set<SyncAdapterType> adapters = new HashSet<>();

        SyncAdapterType[] syncAdapters = ContentResolver.getSyncAdapterTypesAsUser(
                userHandle.getIdentifier());
        for (int i = 0; i < syncAdapters.length; i++) {
            SyncAdapterType syncAdapter = syncAdapters[i];
            String authority = syncAdapter.authority;

            // If the sync adapter is not for this account type, don't include it
            if (!syncAdapter.accountType.equals(account.type)) {
                continue;
            }

            boolean isSyncable = ContentResolver.getIsSyncableAsUser(account, authority,
                    userHandle.getIdentifier()) > 0;
            // If the adapter is not syncable, don't include it
            if (!isSyncable) {
                continue;
            }

            adapters.add(syncAdapter);
        }

        return adapters;
    }

    /**
     * Requests a sync if it is allowed.
     *
     * <p>Derived from
     * {@link com.android.settings.accounts.AccountSyncSettings#requestOrCancelSync}.
     */
    static void requestSyncIfAllowed(Account account, String authority, int userId) {
        if (!syncIsAllowed(account, authority, userId)) {
            return;
        }

        Bundle extras = new Bundle();
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        ContentResolver.requestSyncAsUser(account, authority, userId, extras);
    }

    /**
     * Returns the label for a given sync authority.
     *
     * @return the title if available, and an empty CharSequence otherwise
     */
    static CharSequence getTitle(Context context, String authority, UserHandle userHandle) {
        PackageManager packageManager = context.getPackageManager();
        ProviderInfo providerInfo = packageManager.resolveContentProviderAsUser(
                authority, /* flags= */ 0, userHandle.getIdentifier());
        if (providerInfo == null) {
            return "";
        }

        return providerInfo.loadLabel(packageManager);
    }

    /** Returns whether a sync adapter is currently syncing for the account being shown. */
    static boolean isSyncing(Account account, List<SyncInfo> currentSyncs, String authority) {
        for (SyncInfo syncInfo : currentSyncs) {
            if (syncInfo.account.equals(account) && syncInfo.authority.equals(authority)) {
                return true;
            }
        }
        return false;
    }

    /** Returns the current sync state based on sync status information. */
    static SyncState getSyncState(SyncStatusInfo status, boolean syncEnabled,
            boolean activelySyncing) {
        boolean initialSync = status != null && status.initialize;
        boolean syncIsPending = status != null && status.pending;
        boolean lastSyncFailed = syncEnabled && status != null && status.lastFailureTime != 0
                && status.getLastFailureMesgAsInt(0)
                != ContentResolver.SYNC_ERROR_SYNC_ALREADY_IN_PROGRESS;
        if (activelySyncing && !initialSync) {
            return SyncState.ACTIVE;
        } else if (syncIsPending && !initialSync) {
            return SyncState.PENDING;
        } else if (lastSyncFailed) {
            return SyncState.FAILED;
        }
        return SyncState.NONE;
    }

    private static boolean syncIsAllowed(Account account, String authority, int userId) {
        boolean oneTimeSyncMode = !ContentResolver.getMasterSyncAutomaticallyAsUser(userId);
        boolean syncEnabled = ContentResolver.getSyncAutomaticallyAsUser(account, authority,
                userId);
        return oneTimeSyncMode || syncEnabled;
    }

    private static boolean isVisible(Context context, SyncAdapterType syncAdapter,
            UserHandle userHandle) {
        String authority = syncAdapter.authority;

        if (!syncAdapter.isUserVisible()) {
            // If the sync adapter is not visible, don't show it
            return false;
        }

        try {
            context.getPackageManager().getPackageUidAsUser(syncAdapter.getPackageName(),
                    userHandle.getIdentifier());
        } catch (PackageManager.NameNotFoundException e) {
            LOG.e("No uid for package" + syncAdapter.getPackageName(), e);
            // If we can't get the Uid for the package hosting the sync adapter, don't show it
            return false;
        }

        CharSequence title = getTitle(context, authority, userHandle);
        if (TextUtils.isEmpty(title)) {
            return false;
        }

        return true;
    }

    /** Denotes a sync adapter state. */
    public enum SyncState {
        /** The sync adapter is actively syncing. */
        ACTIVE,
        /** The sync adapter is waiting to start syncing. */
        PENDING,
        /** The sync adapter's last attempt to sync failed. */
        FAILED,
        /** Nothing to note about the sync adapter's sync state. */
        NONE;
    }
}
