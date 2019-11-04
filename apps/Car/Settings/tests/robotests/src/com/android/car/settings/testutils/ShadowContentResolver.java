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

package com.android.car.settings.testutils;

import android.accounts.Account;
import android.annotation.UserIdInt;
import android.content.ContentResolver;
import android.content.SyncAdapterType;
import android.content.SyncInfo;
import android.content.SyncStatusInfo;
import android.content.SyncStatusObserver;
import android.os.Bundle;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Derived from {@link com.android.settings.testutils.shadow.ShadowContentResolver}
 *
 * <p>Needed for many account-related tests because the default ShadowContentResolver does not
 * include an implementation of getSyncAdapterTypesAsUser, which is used by {@link
 * com.android.settingslib.accounts.AuthenticatorHelper#buildAccountTypeToAuthoritiesMap}.
 */
@Implements(ContentResolver.class)
public class ShadowContentResolver extends org.robolectric.shadows.ShadowContentResolver {
    private static final int SYNCABLE = 1;

    private static SyncAdapterType[] sSyncAdapterTypes = new SyncAdapterType[0];
    private static Map<String, Integer> sSyncable = new HashMap<>();
    private static Map<String, Boolean> sSyncAutomatically = new HashMap<>();
    private static Map<Integer, Boolean> sMasterSyncAutomatically = new HashMap<>();
    private static Map<String, SyncStatusInfo> sSyncStatus = new HashMap<>();
    private static List<SyncInfo> sSyncs = new ArrayList<>();
    private static SyncListener sSyncListener;
    private static SyncStatusObserver sStatusObserver;

    @Implementation
    protected static SyncAdapterType[] getSyncAdapterTypesAsUser(int userId) {
        return sSyncAdapterTypes;
    }

    @Implementation
    protected static int getIsSyncableAsUser(Account account, String authority, int userId) {
        return sSyncable.getOrDefault(authority, SYNCABLE);
    }

    @Implementation
    protected static boolean getSyncAutomaticallyAsUser(Account account, String authority,
            int userId) {
        return sSyncAutomatically.getOrDefault(authority, true);
    }

    @Implementation
    protected static boolean getMasterSyncAutomaticallyAsUser(int userId) {
        return sMasterSyncAutomatically.getOrDefault(userId, true);
    }

    @Implementation
    protected static List<SyncInfo> getCurrentSyncsAsUser(@UserIdInt int userId) {
        return sSyncs;
    }

    @Implementation
    protected static SyncStatusInfo getSyncStatusAsUser(Account account, String authority,
            @UserIdInt int userId) {
        return sSyncStatus.get(authority);
    }

    public static void setSyncAdapterTypes(SyncAdapterType[] syncAdapterTypes) {
        sSyncAdapterTypes = syncAdapterTypes;
    }

    @Implementation
    public static void setIsSyncable(Account account, String authority, int syncable) {
        sSyncable.put(authority, syncable);
    }

    @Implementation
    protected static void setSyncAutomaticallyAsUser(Account account, String authority,
            boolean sync, @UserIdInt int userId) {
        sSyncAutomatically.put(authority, sync);
    }

    @Implementation
    protected static void setMasterSyncAutomaticallyAsUser(boolean sync, @UserIdInt int userId) {
        sMasterSyncAutomatically.put(userId, sync);
    }

    public static void setCurrentSyncs(List<SyncInfo> syncs) {
        sSyncs = syncs;
    }

    public static void setSyncStatus(Account account, String authority, SyncStatusInfo status) {
        sSyncStatus.put(authority, status);
    }

    @Implementation
    public static void cancelSyncAsUser(Account account, String authority, @UserIdInt int userId) {
        if (sSyncListener != null) {
            sSyncListener.onSyncCanceled(account, authority, userId);
        }
    }

    @Implementation
    public static void requestSyncAsUser(Account account, String authority, @UserIdInt int userId,
            Bundle extras) {
        if (sSyncListener != null) {
            sSyncListener.onSyncRequested(account, authority, userId, extras);
        }
    }

    public static void setSyncListener(SyncListener syncListener) {
        sSyncListener = syncListener;
    }

    @Implementation
    protected static Object addStatusChangeListener(int mask, SyncStatusObserver callback) {
        sStatusObserver = callback;
        return null;
    }

    @Implementation
    protected static void removeStatusChangeListener(Object handle) {
        sStatusObserver = null;
    }

    public static SyncStatusObserver getStatusChangeListener() {
        return sStatusObserver;
    }

    @Resetter
    public static void reset() {
        org.robolectric.shadows.ShadowContentResolver.reset();
        sSyncable.clear();
        sSyncAutomatically.clear();
        sMasterSyncAutomatically.clear();
        sSyncAdapterTypes = new SyncAdapterType[0];
        sSyncStatus.clear();
        sSyncs = new ArrayList<>();
        sSyncListener = null;
        sStatusObserver = null;
    }

    /**
     * A listener interface that can be used to verify calls to {@link #cancelSyncAsUser} and {@link
     * #requestSyncAsUser}
     */
    public interface SyncListener {
        void onSyncCanceled(Account account, String authority, @UserIdInt int userId);

        void onSyncRequested(Account account, String authority, @UserIdInt int userId,
                Bundle extras);
    }
}
