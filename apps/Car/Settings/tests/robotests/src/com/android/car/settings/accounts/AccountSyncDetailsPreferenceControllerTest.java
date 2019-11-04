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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.robolectric.RuntimeEnvironment.application;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncAdapterType;
import android.content.SyncInfo;
import android.content.SyncStatusInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ProviderInfo;
import android.os.Bundle;
import android.os.UserHandle;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowAccountManager;
import com.android.car.settings.testutils.ShadowApplicationPackageManager;
import com.android.car.settings.testutils.ShadowContentResolver;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Unit test for {@link AccountSyncDetailsPreferenceController}.
 */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowContentResolver.class, ShadowApplicationPackageManager.class,
        ShadowAccountManager.class})
public class AccountSyncDetailsPreferenceControllerTest {
    private static final int SYNCABLE = 1;
    private static final int NOT_SYNCABLE = 0;

    private static final int USER_ID = 3;
    private static final int NOT_USER_ID = 5;

    private static final String AUTHORITY = "authority";
    private static final String ACCOUNT_TYPE = "com.acct1";
    private static final String DIFFERENT_ACCOUNT_TYPE = "com.acct2";

    private final Account mAccount = new Account("acct1", ACCOUNT_TYPE);
    private final UserHandle mUserHandle = new UserHandle(USER_ID);
    @Mock
    ShadowContentResolver.SyncListener mMockSyncListener;
    private Context mContext;
    private AccountSyncDetailsPreferenceController mController;
    private LogicalPreferenceGroup mPreferenceGroup;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = application;
        ShadowContentResolver.setSyncListener(mMockSyncListener);

        PreferenceControllerTestHelper<AccountSyncDetailsPreferenceController> helper =
                new PreferenceControllerTestHelper<>(mContext,
                        AccountSyncDetailsPreferenceController.class);
        mController = helper.getController();
        mController.setAccount(mAccount);
        mController.setUserHandle(mUserHandle);

        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        helper.setPreference(mPreferenceGroup);

        helper.markState(Lifecycle.State.STARTED);
    }

    @After
    public void tearDown() {
        ShadowContentResolver.reset();
    }

    @Test
    public void refreshUi_syncAdapterDoesNotHaveSameAccountType_shouldNotBeShown() {
        // Adds a sync adapter type that is visible but does not have the right account type.
        SyncAdapterType syncAdapterType = new SyncAdapterType(AUTHORITY,
                DIFFERENT_ACCOUNT_TYPE, /* userVisible */ true, /* supportsUploading */ true);
        SyncAdapterType[] syncAdapters = {syncAdapterType};
        ShadowContentResolver.setSyncAdapterTypes(syncAdapters);

        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(0);
    }

    @Test
    public void refreshUi_syncAdapterIsNotVisible_shouldNotBeShown() {
        // Adds a sync adapter type that has the right account type but is not visible.
        SyncAdapterType syncAdapterType = new SyncAdapterType(AUTHORITY,
                ACCOUNT_TYPE, /* userVisible */ false, /* supportsUploading */ true);
        SyncAdapterType[] syncAdapters = {syncAdapterType};
        ShadowContentResolver.setSyncAdapterTypes(syncAdapters);

        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(0);
    }

    @Test
    public void refreshUi_syncAdapterIsNotSyncable_shouldNotBeShown() {
        // Adds a sync adapter type that has the right account type and is visible.
        SyncAdapterType syncAdapterType = new SyncAdapterType(AUTHORITY,
                ACCOUNT_TYPE, /* userVisible */ true, /* supportsUploading */ true);
        SyncAdapterType[] syncAdapters = {syncAdapterType};
        ShadowContentResolver.setSyncAdapterTypes(syncAdapters);
        // Sets that the sync adapter to not syncable.
        ShadowContentResolver.setIsSyncable(mAccount, AUTHORITY, /* syncable= */ NOT_SYNCABLE);

        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(0);
    }

    @Test
    public void refreshUi_syncAdapterDoesNotHaveProviderInfo_shouldNotBeShown() {
        // Adds a sync adapter type that has the right account type and is visible.
        SyncAdapterType syncAdapterType = new SyncAdapterType(AUTHORITY,
                ACCOUNT_TYPE, /* userVisible */ true, /* supportsUploading */ true);
        SyncAdapterType[] syncAdapters = {syncAdapterType};
        ShadowContentResolver.setSyncAdapterTypes(syncAdapters);
        // Sets that the sync adapter to syncable.
        ShadowContentResolver.setIsSyncable(mAccount, AUTHORITY, /* syncable= */ SYNCABLE);

        // However, no provider info is set for the sync adapter, so it shouldn't be visible.

        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(0);
    }

    @Test
    public void refreshUi_providerInfoDoesNotHaveLabel_shouldNotBeShown() {
        // Adds a sync adapter type that has the right account type and is visible.
        SyncAdapterType syncAdapterType = new SyncAdapterType(AUTHORITY,
                ACCOUNT_TYPE, /* userVisible */ true, /* supportsUploading */ true);
        SyncAdapterType[] syncAdapters = {syncAdapterType};
        ShadowContentResolver.setSyncAdapterTypes(syncAdapters);
        // Sets that the sync adapter to syncable.
        ShadowContentResolver.setIsSyncable(mAccount, AUTHORITY, /* syncable= */ SYNCABLE);
        // Sets provider info for the sync adapter but it does not have a label.
        ProviderInfo info = new ProviderInfo();
        info.authority = AUTHORITY;
        info.name = "";

        ProviderInfo[] providers = {info};
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = AUTHORITY;
        packageInfo.providers = providers;
        getShadowApplicationManager().addPackage(packageInfo);

        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(0);
    }

    @Test
    public void refreshUi_providerLabelShouldBeSet() {
        // Adds a sync adapter type that has the right account type and is visible.
        SyncAdapterType syncAdapterType = new SyncAdapterType(AUTHORITY,
                ACCOUNT_TYPE, /* userVisible */ true, /* supportsUploading */ true);
        SyncAdapterType[] syncAdapters = {syncAdapterType};
        ShadowContentResolver.setSyncAdapterTypes(syncAdapters);
        // Sets that the sync adapter to syncable.
        ShadowContentResolver.setIsSyncable(mAccount, AUTHORITY, /* syncable= */ SYNCABLE);
        // Sets provider info for the sync adapter with a label.
        ProviderInfo info = new ProviderInfo();
        info.authority = AUTHORITY;
        info.name = "label";

        ProviderInfo[] providers = {info};
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = AUTHORITY;
        packageInfo.providers = providers;
        getShadowApplicationManager().addPackage(packageInfo);

        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        Preference pref = mPreferenceGroup.getPreference(0);
        assertThat(pref.getTitle()).isEqualTo("label");
    }

    @Test
    public void refreshUi_masterSyncOff_syncDisabled_shouldNotBeChecked() {
        setUpVisibleSyncAdapters(AUTHORITY);
        // Turns off master sync and automatic sync for the adapter.
        ContentResolver.setMasterSyncAutomaticallyAsUser(/* sync= */ true, USER_ID);
        ContentResolver.setSyncAutomaticallyAsUser(mAccount, AUTHORITY, /* sync= */ false,
                USER_ID);

        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        SyncPreference pref = (SyncPreference) mPreferenceGroup.getPreference(0);
        assertThat(pref.isChecked()).isFalse();
    }

    @Test
    public void refreshUi_masterSyncOn_syncDisabled_shouldBeChecked() {
        setUpVisibleSyncAdapters(AUTHORITY);
        // Turns on master sync and turns off automatic sync for the adapter.
        ContentResolver.setMasterSyncAutomaticallyAsUser(/* sync= */ false, USER_ID);
        ContentResolver.setSyncAutomaticallyAsUser(mAccount, AUTHORITY, /* sync= */ false,
                USER_ID);

        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        SyncPreference pref = (SyncPreference) mPreferenceGroup.getPreference(0);
        assertThat(pref.isChecked()).isTrue();
    }

    @Test
    public void refreshUi_masterSyncOff_syncEnabled_shouldBeChecked() {
        setUpVisibleSyncAdapters(AUTHORITY);
        // Turns off master sync and turns on automatic sync for the adapter.
        ContentResolver.setMasterSyncAutomaticallyAsUser(/* sync= */ true, USER_ID);
        ContentResolver.setSyncAutomaticallyAsUser(mAccount, AUTHORITY, /* sync= */ true,
                USER_ID);

        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        SyncPreference pref = (SyncPreference) mPreferenceGroup.getPreference(0);
        assertThat(pref.isChecked()).isTrue();
    }

    @Test
    public void refreshUi_syncDisabled_summaryShouldBeSet() {
        setUpVisibleSyncAdapters(AUTHORITY);
        // Turns off automatic sync for the the sync adapter.
        ContentResolver.setSyncAutomaticallyAsUser(mAccount, AUTHORITY, /* sync= */ false,
                mUserHandle.getIdentifier());

        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        Preference pref = mPreferenceGroup.getPreference(0);
        assertThat(pref.getSummary()).isEqualTo(mContext.getString(R.string.sync_disabled));
    }

    @Test
    public void refreshUi_syncEnabled_activelySyncing_summaryShouldBeSet() {
        setUpVisibleSyncAdapters(AUTHORITY);
        // Turns on automatic sync for the the sync adapter.
        ContentResolver.setSyncAutomaticallyAsUser(mAccount, AUTHORITY, /* sync= */ true,
                mUserHandle.getIdentifier());
        // Adds the sync adapter to the list of currently syncing adapters.
        SyncInfo syncInfo = new SyncInfo(/* authorityId= */ 0, mAccount, AUTHORITY, /* startTime= */
                0);
        List<SyncInfo> syncs = new ArrayList<>();
        syncs.add(syncInfo);
        ShadowContentResolver.setCurrentSyncs(syncs);

        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        Preference pref = mPreferenceGroup.getPreference(0);
        assertThat(pref.getSummary()).isEqualTo(mContext.getString(R.string.sync_in_progress));
    }

    @Test
    public void refreshUi_syncEnabled_syncHasHappened_summaryShouldBeSet() {
        setUpVisibleSyncAdapters(AUTHORITY);
        // Turns on automatic sync for the the sync adapter.
        ContentResolver.setSyncAutomaticallyAsUser(mAccount, AUTHORITY, /* sync= */ true,
                mUserHandle.getIdentifier());
        // Sets the sync adapter's last successful sync time.
        SyncStatusInfo status = new SyncStatusInfo(0);
        status.setLastSuccess(0, 83091);
        ShadowContentResolver.setSyncStatus(mAccount, AUTHORITY, status);

        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        Preference pref = mPreferenceGroup.getPreference(0);

        String expectedTimeString = mController.formatSyncDate(new Date(83091));
        assertThat(pref.getSummary()).isEqualTo(
                mContext.getString(R.string.last_synced, expectedTimeString));
    }

    @Test
    public void refreshUi_activelySyncing_notInitialSync_shouldHaveActiveSyncIcon() {
        setUpVisibleSyncAdapters(AUTHORITY);
        // Adds the sync adapter to the list of currently syncing adapters.
        SyncInfo syncInfo = new SyncInfo(/* authorityId= */ 0, mAccount, AUTHORITY, /* startTime= */
                0);
        List<SyncInfo> syncs = new ArrayList<>();
        syncs.add(syncInfo);
        ShadowContentResolver.setCurrentSyncs(syncs);
        // Sets the sync adapter's initializing state to false (i.e. it's not performing an
        // initial sync).
        SyncStatusInfo status = new SyncStatusInfo(0);
        status.initialize = false;
        ShadowContentResolver.setSyncStatus(mAccount, AUTHORITY, status);

        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        Preference pref = mPreferenceGroup.getPreference(0);

        assertThat(Shadows.shadowOf(pref.getIcon()).getCreatedFromResId()).isEqualTo(
                R.drawable.ic_sync_anim);
    }

    @Test
    public void refreshUi_syncPending_notInitialSync_shouldHaveActiveSyncIcon() {
        setUpVisibleSyncAdapters(AUTHORITY);
        // Sets the sync adapter's initializing state to false (i.e. it's not performing an
        // initial sync).
        // Also sets the the sync status to pending
        SyncStatusInfo status = new SyncStatusInfo(0);
        status.initialize = false;
        status.pending = true;
        ShadowContentResolver.setSyncStatus(mAccount, AUTHORITY, status);

        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        Preference pref = mPreferenceGroup.getPreference(0);

        assertThat(Shadows.shadowOf(pref.getIcon()).getCreatedFromResId()).isEqualTo(
                R.drawable.ic_sync);
    }

    @Test
    public void refreshUi_syncFailed_shouldHaveProblemSyncIcon() {
        setUpVisibleSyncAdapters(AUTHORITY);
        // Turns on automatic sync for the the sync adapter.
        ContentResolver.setSyncAutomaticallyAsUser(mAccount, AUTHORITY, /* sync= */ true,
                mUserHandle.getIdentifier());
        // Sets the sync adapter's last failure time and message so it appears to have failed
        // previously.
        SyncStatusInfo status = new SyncStatusInfo(0);
        status.lastFailureTime = 10;
        status.lastFailureMesg = "too-many-deletions";
        ShadowContentResolver.setSyncStatus(mAccount, AUTHORITY, status);

        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        Preference pref = mPreferenceGroup.getPreference(0);

        assertThat(Shadows.shadowOf(pref.getIcon()).getCreatedFromResId()).isEqualTo(
                R.drawable.ic_sync_problem);
    }

    @Test
    public void refreshUi_noSyncStatus_shouldHaveNoIcon() {
        setUpVisibleSyncAdapters(AUTHORITY);

        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        Preference pref = mPreferenceGroup.getPreference(0);

        assertThat(pref.getIcon()).isNull();
        assertThat(pref.isIconSpaceReserved()).isTrue();
    }

    @Test
    public void onAccountsUpdate_correctUserId_shouldForceUpdatePreferences() {
        setUpVisibleSyncAdapters(AUTHORITY);

        mController.refreshUi();
        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);

        ShadowContentResolver.reset();
        mController.onAccountsUpdate(mUserHandle);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(0);
    }

    @Test
    public void onAccountsUpdate_incorrectUserId_shouldNotForceUpdatePreferences() {
        setUpVisibleSyncAdapters(AUTHORITY);

        mController.refreshUi();
        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);

        ShadowContentResolver.reset();
        mController.onAccountsUpdate(new UserHandle(NOT_USER_ID));

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
    }

    @Test
    public void onSyncPreferenceClicked_preferenceUnchecked_shouldSetSyncAutomaticallyOff() {
        setUpVisibleSyncAdapters(AUTHORITY);

        // Turns off one time sync and turns on automatic sync for the adapter so the preference is
        // checked.
        ContentResolver.setMasterSyncAutomaticallyAsUser(/* sync= */ true, USER_ID);
        ContentResolver.setSyncAutomaticallyAsUser(mAccount, AUTHORITY, /* sync= */ true,
                USER_ID);

        mController.refreshUi();
        SyncPreference pref = (SyncPreference) mPreferenceGroup.getPreference(0);
        pref.performClick();

        assertThat(ContentResolver.getSyncAutomaticallyAsUser(mAccount, AUTHORITY,
                USER_ID)).isFalse();
    }

    @Test
    public void onSyncPreferenceClicked_preferenceUnchecked_shouldCancelSync() {
        setUpVisibleSyncAdapters(AUTHORITY);

        // Turns off one time sync and turns on automatic sync for the adapter so the preference is
        // checked.
        ContentResolver.setMasterSyncAutomaticallyAsUser(/* sync= */ true, USER_ID);
        ContentResolver.setSyncAutomaticallyAsUser(mAccount, AUTHORITY, /* sync= */ true,
                USER_ID);

        mController.refreshUi();
        SyncPreference pref = (SyncPreference) mPreferenceGroup.getPreference(0);
        pref.performClick();

        verify(mMockSyncListener).onSyncCanceled(eq(mAccount), eq(AUTHORITY), eq(USER_ID));
    }

    @Test
    public void onSyncPreferenceClicked_preferenceChecked_shouldSetSyncAutomaticallyOn() {
        setUpVisibleSyncAdapters(AUTHORITY);

        // Turns off one time sync and automatic sync for the adapter so the preference is
        // unchecked.
        ContentResolver.setMasterSyncAutomaticallyAsUser(/* sync= */ true, USER_ID);
        ContentResolver.setSyncAutomaticallyAsUser(mAccount, AUTHORITY, /* sync= */ false,
                USER_ID);

        mController.refreshUi();
        SyncPreference pref = (SyncPreference) mPreferenceGroup.getPreference(0);
        pref.performClick();

        assertThat(ContentResolver.getSyncAutomaticallyAsUser(mAccount, AUTHORITY,
                USER_ID)).isTrue();
    }

    @Test
    public void onSyncPreferenceClicked_preferenceChecked_masterSyncOff_shouldRequestSync() {
        setUpVisibleSyncAdapters(AUTHORITY);

        // Turns off master sync and automatic sync for the adapter so the preference is unchecked.
        ContentResolver.setMasterSyncAutomaticallyAsUser(/* sync= */ false, USER_ID);
        ContentResolver.setSyncAutomaticallyAsUser(mAccount, AUTHORITY, /* sync= */ false,
                USER_ID);

        mController.refreshUi();
        SyncPreference pref = (SyncPreference) mPreferenceGroup.getPreference(0);

        // Sets master sync off
        ContentResolver.setMasterSyncAutomaticallyAsUser(/* sync= */ false, USER_ID);
        pref.performClick();

        verify(mMockSyncListener).onSyncRequested(eq(mAccount), eq(AUTHORITY), eq(USER_ID),
                any(Bundle.class));
    }

    @Test
    public void onSyncPreferenceClicked_oneTimeSyncOn_shouldRequestSync() {
        setUpVisibleSyncAdapters(AUTHORITY);

        // Turns on one time sync mode
        ContentResolver.setMasterSyncAutomaticallyAsUser(/* sync= */ false, USER_ID);

        mController.refreshUi();
        SyncPreference pref = (SyncPreference) mPreferenceGroup.getPreference(0);
        pref.performClick();

        verify(mMockSyncListener).onSyncRequested(eq(mAccount), eq(AUTHORITY), eq(USER_ID),
                any(Bundle.class));
    }

    private void setUpVisibleSyncAdapters(String... authorities) {
        SyncAdapterType[] syncAdapters = new SyncAdapterType[authorities.length];
        for (int i = 0; i < authorities.length; i++) {
            String authority = authorities[i];
            // Adds a sync adapter type that has the right account type and is visible.
            SyncAdapterType syncAdapterType = new SyncAdapterType(authority,
                    ACCOUNT_TYPE, /* userVisible */ true, /* supportsUploading */ true);
            syncAdapters[i] = syncAdapterType;

            // Sets that the sync adapter is syncable.
            ShadowContentResolver.setIsSyncable(mAccount, authority, /* syncable= */ SYNCABLE);

            // Sets provider info with a label for the sync adapter.
            ProviderInfo info = new ProviderInfo();
            info.authority = authority;
            info.name = "label";
            ProviderInfo[] providers = {info};

            PackageInfo packageInfo = new PackageInfo();
            packageInfo.packageName = authority;
            packageInfo.providers = providers;
            getShadowApplicationManager().addPackage(packageInfo);
        }
        ShadowContentResolver.setSyncAdapterTypes(syncAdapters);
    }

    private ShadowApplicationPackageManager getShadowApplicationManager() {
        return Shadow.extract(mContext.getPackageManager());
    }
}
