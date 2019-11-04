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

package com.android.car.settings.accounts;

import android.car.drivingstate.CarUxRestrictions;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncAdapterType;
import android.content.SyncInfo;
import android.content.SyncStatusInfo;
import android.content.SyncStatusObserver;

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.settingslib.utils.ThreadUtils;

import java.util.List;
import java.util.Set;

/**
 * Controller for the preference that shows information about an account, including info about
 * failures.
 */
public class AccountDetailsWithSyncStatusPreferenceController extends
        AccountDetailsPreferenceController {
    private boolean mIsStarted = false;
    private Object mStatusChangeListenerHandle;
    private SyncStatusObserver mSyncStatusObserver =
            which -> ThreadUtils.postOnMainThread(() -> {
                // The observer call may occur even if the fragment hasn't been started, so
                // only force an update if the fragment hasn't been stopped.
                if (mIsStarted) {
                    refreshUi();
                }
            });

    public AccountDetailsWithSyncStatusPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }


    /**
     * Registers the account update and sync status change callbacks.
     */
    @Override
    protected void onStartInternal() {
        mIsStarted = true;
        mStatusChangeListenerHandle = ContentResolver.addStatusChangeListener(
                ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE
                        | ContentResolver.SYNC_OBSERVER_TYPE_STATUS
                        | ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS, mSyncStatusObserver);
    }

    /**
     * Unregisters the account update and sync status change callbacks.
     */
    @Override
    protected void onStopInternal() {
        mIsStarted = false;
        if (mStatusChangeListenerHandle != null) {
            ContentResolver.removeStatusChangeListener(mStatusChangeListenerHandle);
        }
    }

    @Override
    protected void updateState(Preference preference) {
        super.updateState(preference);
        if (isSyncFailing()) {
            preference.setSummary(R.string.sync_is_failing);
        } else {
            preference.setSummary("");
        }
    }

    private boolean isSyncFailing() {
        int userId = getUserHandle().getIdentifier();
        List<SyncInfo> currentSyncs = ContentResolver.getCurrentSyncsAsUser(userId);
        boolean syncIsFailing = false;

        Set<SyncAdapterType> syncAdapters = AccountSyncHelper.getVisibleSyncAdaptersForAccount(
                getContext(), getAccount(), getUserHandle());
        for (SyncAdapterType syncAdapter : syncAdapters) {
            String authority = syncAdapter.authority;

            SyncStatusInfo status = ContentResolver.getSyncStatusAsUser(getAccount(), authority,
                    userId);
            boolean syncEnabled = ContentResolver.getSyncAutomaticallyAsUser(getAccount(),
                    authority, userId);
            boolean activelySyncing = AccountSyncHelper.isSyncing(getAccount(), currentSyncs,
                    authority);

            AccountSyncHelper.SyncState syncState = AccountSyncHelper.getSyncState(status,
                    syncEnabled, activelySyncing);

            boolean syncIsPending = status != null && status.pending;
            if (syncState == AccountSyncHelper.SyncState.FAILED && !activelySyncing
                    && !syncIsPending) {
                syncIsFailing = true;
            }
        }

        return syncIsFailing;
    }
}
