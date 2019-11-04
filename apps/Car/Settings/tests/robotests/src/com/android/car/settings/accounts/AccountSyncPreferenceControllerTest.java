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
import static org.mockito.Mockito.verify;
import static org.robolectric.RuntimeEnvironment.application;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.SyncAdapterType;
import android.os.UserHandle;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowContentResolver;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/**
 * Unit test for {@link AccountSyncPreferenceController}.
 *
 * <p>Largely copied from {@link com.android.settings.accounts.AccountSyncPreferenceControllerTest}.
 */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowContentResolver.class})
public class AccountSyncPreferenceControllerTest {
    private static final int SYNCABLE = 1;
    private static final int NOT_SYNCABLE = 0;

    private final Account mAccount = new Account("acct1", "type1");
    private final int mUserId = 3;
    private final UserHandle mUserHandle = new UserHandle(mUserId);

    private AccountSyncPreferenceController mController;
    private FragmentController mMockFragmentController;
    private Preference mPreference;

    @Before
    public void setUp() {
        PreferenceControllerTestHelper<AccountSyncPreferenceController> helper =
                new PreferenceControllerTestHelper<>(application,
                        AccountSyncPreferenceController.class);
        mController = helper.getController();
        mMockFragmentController = helper.getMockFragmentController();

        mController.setAccount(mAccount);
        mController.setUserHandle(mUserHandle);

        mPreference = new Preference(application);
        helper.setPreference(mPreference);
        helper.markState(Lifecycle.State.STARTED);
    }

    @After
    public void tearDown() {
        ShadowContentResolver.reset();
    }

    @Test
    public void refreshUi_notSameAccountType_shouldNotCount() {
        // Adds a sync adapter type that has a visible user, is syncable, and syncs automatically
        // but does not have the right account type.
        SyncAdapterType syncAdapterType = new SyncAdapterType("authority", /* accountType */
                "type5", /* userVisible */ true, /* supportsUploading */ true);
        SyncAdapterType[] syncAdapters = {syncAdapterType};
        ShadowContentResolver.setSyncAdapterTypes(syncAdapters);
        ContentResolver.setIsSyncable(mAccount, "authority", SYNCABLE);
        ContentResolver.setSyncAutomaticallyAsUser(mAccount,
                "authority", /* sync= */ true, /* userId= */ mUserId);

        mController.refreshUi();

        assertThat(mPreference.getSummary())
                .isEqualTo(application.getString(R.string.account_sync_summary_all_off));
    }

    @Test
    public void refreshUi_adapterInvisible_shouldNotCount() {
        // Adds a sync adapter type that has the right account type, is syncable, and syncs
        // automatically, but doesn't have a visible user
        SyncAdapterType syncAdapterType = new SyncAdapterType("authority",
                /* accountType */ "type1", /* userVisible */ false, /* supportsUploading */ true);
        SyncAdapterType[] syncAdapters = {syncAdapterType};
        ShadowContentResolver.setSyncAdapterTypes(syncAdapters);
        ContentResolver.setIsSyncable(mAccount, "authority", SYNCABLE);
        ContentResolver.setSyncAutomaticallyAsUser(mAccount, "authority", /* sync= */
                true, /* userId= */ mUserId);

        mController.refreshUi();

        assertThat(mPreference.getSummary())
                .isEqualTo(application.getString(R.string.account_sync_summary_all_off));
    }

    @Test
    public void refreshUi_notSyncable_shouldNotCount() {
        // Adds a sync adapter type that is the right account type and a visible user, but is not
        // syncable
        SyncAdapterType syncAdapterType = new SyncAdapterType("authority", /* accountType */
                "type1", /* userVisible */ true, /* supportsUploading */ true);
        SyncAdapterType[] syncAdapters = {syncAdapterType};
        ShadowContentResolver.setSyncAdapterTypes(syncAdapters);
        ContentResolver.setIsSyncable(mAccount, "authority", NOT_SYNCABLE);

        mController.refreshUi();

        assertThat(mPreference.getSummary())
                .isEqualTo(application.getString(R.string.account_sync_summary_all_off));
    }

    @Test
    public void refreshUi_masterAutomaticSyncIgnoredAndAccountSyncDisabled_shouldNotCount() {
        // Adds a sync adapter type that is the right account type, has a visible user, and is
        // syncable, but has master automatic sync ignored and account-level sync disabled
        SyncAdapterType syncAdapterType = new SyncAdapterType("authority", /* accountType */
                "type1", /* userVisible */ true, /* supportsUploading */ true);
        SyncAdapterType[] syncAdapters = {syncAdapterType};
        ShadowContentResolver.setSyncAdapterTypes(syncAdapters);
        ContentResolver.setIsSyncable(mAccount, "authority", SYNCABLE);
        ContentResolver.setMasterSyncAutomaticallyAsUser(true, mUserId);
        ContentResolver.setSyncAutomaticallyAsUser(mAccount, "authority", /* sync= */
                false, /* userId= */ mUserId);

        mController.refreshUi();

        assertThat(mPreference.getSummary())
                .isEqualTo(application.getString(R.string.account_sync_summary_all_off));
    }

    @Test
    public void refreshUi_masterAutomaticSyncUsed_shouldCount() {
        // Adds a sync adapter type that is the right account type, has a visible user, is
        // syncable, and has master-level automatic syncing enabled
        SyncAdapterType syncAdapterType = new SyncAdapterType("authority", /* accountType */
                "type1", /* userVisible */ true, /* supportsUploading */ true);
        SyncAdapterType[] syncAdapters = {syncAdapterType};
        ShadowContentResolver.setSyncAdapterTypes(syncAdapters);
        ContentResolver.setIsSyncable(mAccount, "authority", SYNCABLE);
        ContentResolver.setMasterSyncAutomaticallyAsUser(false, mUserId);

        mController.refreshUi();

        assertThat(mPreference.getSummary())
                .isEqualTo(application.getString(R.string.account_sync_summary_all_on));
    }

    @Test
    public void refreshUi_automaticSyncEnabled_shouldCount() {
        // Adds a sync adapter type that is the right account type, has a visible user, is
        // syncable, and has account-level automatic syncing enabled
        SyncAdapterType syncAdapterType = new SyncAdapterType("authority", /* accountType */
                "type1", /* userVisible */ true, /* supportsUploading */ true);
        SyncAdapterType[] syncAdapters = {syncAdapterType};
        ShadowContentResolver.setSyncAdapterTypes(syncAdapters);
        ContentResolver.setIsSyncable(mAccount, "authority", SYNCABLE);
        ContentResolver.setSyncAutomaticallyAsUser(mAccount, "authority", /* sync= */
                true, /* userId= */ mUserId);

        mController.refreshUi();

        assertThat(mPreference.getSummary())
                .isEqualTo(application.getString(R.string.account_sync_summary_all_on));
    }

    @Test
    public void refreshUi_someEnabled_shouldSetSummary() {
        SyncAdapterType syncAdapterType1 = new SyncAdapterType("authority1", /* accountType */
                "type1", /* userVisible */ true, /* supportsUploading */ true);
        SyncAdapterType syncAdapterType2 = new SyncAdapterType("authority2", /* accountType */
                "type1", /* userVisible */ true, /* supportsUploading */ true);
        SyncAdapterType syncAdapterType3 = new SyncAdapterType("authority3", /* accountType */
                "type1", /* userVisible */ true, /* supportsUploading */ true);
        SyncAdapterType syncAdapterType4 = new SyncAdapterType("authority4", /* accountType */
                "type1", /* userVisible */ true, /* supportsUploading */ true);
        SyncAdapterType[] syncAdapters =
                {syncAdapterType1, syncAdapterType2, syncAdapterType3, syncAdapterType4};
        ShadowContentResolver.setSyncAdapterTypes(syncAdapters);

        // Enable sync for the first three authorities and disable it for the fourth one
        ContentResolver.setIsSyncable(mAccount, "authority1", SYNCABLE);
        ContentResolver.setSyncAutomaticallyAsUser(mAccount, "authority1", /* sync= */
                true, /* userId= */ mUserId);

        ContentResolver.setIsSyncable(mAccount, "authority2", SYNCABLE);
        ContentResolver.setSyncAutomaticallyAsUser(mAccount, "authority2", /* sync= */
                true, /* userId= */ mUserId);

        ContentResolver.setIsSyncable(mAccount, "authority3", SYNCABLE);
        ContentResolver.setSyncAutomaticallyAsUser(mAccount, "authority3", /* sync= */
                true, /* userId= */ mUserId);

        ContentResolver.setSyncAutomaticallyAsUser(mAccount, "authority4", /* sync= */
                false, /* userId= */ mUserId);

        mController.refreshUi();

        assertThat(mPreference.getSummary())
                .isEqualTo(application.getString(R.string.account_sync_summary_some_on, 3, 4));
    }

    @Test
    public void handlePreferenceClicked_shouldLaunchAccountSyncDetailsFragment() {
        mController.refreshUi();
        mController.handlePreferenceClicked(mPreference);

        verify(mMockFragmentController).launchFragment(any(AccountSyncDetailsFragment.class));
    }
}
