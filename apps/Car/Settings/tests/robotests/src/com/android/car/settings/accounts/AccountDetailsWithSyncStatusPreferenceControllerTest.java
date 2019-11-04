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

import static com.google.common.truth.Truth.assertThat;

import static org.robolectric.RuntimeEnvironment.application;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncAdapterType;
import android.content.SyncStatusInfo;
import android.content.SyncStatusObserver;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ProviderInfo;
import android.os.UserHandle;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowAccountManager;
import com.android.car.settings.testutils.ShadowApplicationPackageManager;
import com.android.car.settings.testutils.ShadowContentResolver;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

/** Unit test for {@link AccountDetailsWithSyncStatusPreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowAccountManager.class, ShadowContentResolver.class,
        ShadowApplicationPackageManager.class})
public class AccountDetailsWithSyncStatusPreferenceControllerTest {
    private static final int SYNCABLE = 1;
    private static final String AUTHORITY = "authority";
    private static final String ACCOUNT_NAME = "Name";
    private static final String ACCOUNT_TYPE = "com.acct";
    private final Account mAccount = new Account(ACCOUNT_NAME, ACCOUNT_TYPE);
    private final UserHandle mUserHandle = new UserHandle(0);

    private Context mContext;
    private AccountDetailsWithSyncStatusPreferenceController mController;
    private Preference mPreference;

    @Before
    public void setUp() {
        mContext = application;
        PreferenceControllerTestHelper<AccountDetailsWithSyncStatusPreferenceController> helper =
                new PreferenceControllerTestHelper<>(mContext,
                        AccountDetailsWithSyncStatusPreferenceController.class);
        mController = helper.getController();
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
    public void refreshUi_syncIsNotFailing_summaryShouldBeBlank() {
        setUpVisibleSyncAdapters(AUTHORITY);

        mController.refreshUi();

        assertThat(mPreference.getSummary()).isEqualTo("");
    }

    @Test
    public void refreshUi_syncIsFailing_summaryShouldBeSet() {
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

        assertThat(mPreference.getSummary()).isEqualTo(
                mContext.getString(R.string.sync_is_failing));
    }

    @Test
    public void onSyncStatusChanged_summaryShouldUpdated() {
        setUpVisibleSyncAdapters(AUTHORITY);

        // Make sure the summary is blank first
        mController.refreshUi();
        assertThat(mPreference.getSummary()).isEqualTo("");

        // Turns on automatic sync for the the sync adapter.
        ContentResolver.setSyncAutomaticallyAsUser(mAccount, AUTHORITY, /* sync= */ true,
                mUserHandle.getIdentifier());
        // Sets the sync adapter's last failure time and message so it appears to have failed
        // previously.
        SyncStatusInfo status = new SyncStatusInfo(0);
        status.lastFailureTime = 10;
        status.lastFailureMesg = "too-many-deletions";
        ShadowContentResolver.setSyncStatus(mAccount, AUTHORITY, status);

        SyncStatusObserver listener = ShadowContentResolver.getStatusChangeListener();
        listener.onStatusChanged(/* which= */ 0);

        assertThat(mPreference.getSummary()).isEqualTo(
                mContext.getString(R.string.sync_is_failing));
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
            info.name = authority;
            // Set an application info to avoid an NPE
            info.applicationInfo = new ApplicationInfo();
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
