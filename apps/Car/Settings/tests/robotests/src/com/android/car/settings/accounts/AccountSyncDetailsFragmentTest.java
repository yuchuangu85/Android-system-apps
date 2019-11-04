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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.robolectric.RuntimeEnvironment.application;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.SyncAdapterType;
import android.content.SyncInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.widget.Button;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.testutils.BaseTestActivity;
import com.android.car.settings.testutils.ShadowAccountManager;
import com.android.car.settings.testutils.ShadowContentResolver;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for the {@link AccountSyncDetailsFragment}.
 */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowContentResolver.class, ShadowAccountManager.class})
public class AccountSyncDetailsFragmentTest {
    private static final int USER_ID = 3;
    private static final String ACCOUNT_TYPE = "com.acct1";
    private static final String AUTHORITY = "authority";
    private static final String AUTHORITY_2 = "authority2";

    private final Account mAccount = new Account("Name", ACCOUNT_TYPE);
    private final UserHandle mUserHandle = new UserHandle(USER_ID);

    private BaseTestActivity mActivity;
    private AccountSyncDetailsFragment mFragment;
    @Mock
    ShadowContentResolver.SyncListener mMockSyncListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mActivity = Robolectric.setupActivity(BaseTestActivity.class);
        ShadowContentResolver.setSyncListener(mMockSyncListener);
    }

    @After
    public void tearDown() {
        ShadowContentResolver.reset();
    }

    @Test
    public void onInit_doesNotHaveActiveSyncs_actionButtonShouldSaySyncNow() {
        initFragment();

        Button syncButton = mFragment.requireActivity().findViewById(R.id.action_button1);
        assertThat(syncButton.getText()).isEqualTo(
                application.getString(R.string.sync_button_sync_now));
    }

    @Test
    public void onInit_hasActiveSyncs_actionButtonShouldSayCancelSync() {
        SyncInfo syncInfo = mock(SyncInfo.class);
        List<SyncInfo> syncs = new ArrayList<>();
        syncs.add(syncInfo);
        ShadowContentResolver.setCurrentSyncs(syncs);

        initFragment();

        Button syncButton = mFragment.requireActivity().findViewById(R.id.action_button1);
        assertThat(syncButton.getText()).isEqualTo(
                application.getString(R.string.sync_button_sync_cancel));
    }

    @Test
    public void onButtonClicked_doesNotHaveActiveSyncs_shouldSyncSyncableAdapters() {
        setUpSyncAdapters(AUTHORITY, AUTHORITY_2);
        initFragment();

        Button syncButton = mFragment.requireActivity().findViewById(R.id.action_button1);
        syncButton.performClick();

        ArgumentCaptor<String> argument = ArgumentCaptor.forClass(String.class);
        verify(mMockSyncListener, times(2)).onSyncRequested(eq(mAccount), argument.capture(),
                eq(USER_ID), any(Bundle.class));

        List<String> values = argument.getAllValues();

        assertThat(values).contains(AUTHORITY);
        assertThat(values).contains(AUTHORITY_2);
    }

    @Test
    public void onButtonClicked_doesNotHaveActiveSyncs_oneTimeSyncIsOff_shouldNotSyncOffAdapters() {
        setUpSyncAdapters(AUTHORITY, AUTHORITY_2);
        // Turns off one time sync and automatic sync for the adapter
        ContentResolver.setMasterSyncAutomaticallyAsUser(/* sync= */ true, USER_ID);
        ContentResolver.setSyncAutomaticallyAsUser(mAccount, AUTHORITY_2, /* sync= */ false,
                USER_ID);
        initFragment();

        Button syncButton = mFragment.requireActivity().findViewById(R.id.action_button1);
        syncButton.performClick();

        ArgumentCaptor<String> argument = ArgumentCaptor.forClass(String.class);
        verify(mMockSyncListener, times(1)).onSyncRequested(eq(mAccount), argument.capture(),
                eq(USER_ID), any(Bundle.class));

        List<String> values = argument.getAllValues();

        assertThat(values).contains(AUTHORITY);
        assertThat(values).doesNotContain(AUTHORITY_2);
    }

    @Test
    public void onButtonClicked_doesNotHaveActiveSyncs_oneTimeSyncIsOn_shouldSyncOffAdapters() {
        setUpSyncAdapters(AUTHORITY, AUTHORITY_2);
        // Turns on one time sync and turns off automatic sync for the adapter
        ContentResolver.setMasterSyncAutomaticallyAsUser(/* sync= */ false, USER_ID);
        ContentResolver.setSyncAutomaticallyAsUser(mAccount, AUTHORITY_2, /* sync= */ false,
                USER_ID);
        initFragment();

        Button syncButton = mFragment.requireActivity().findViewById(R.id.action_button1);
        syncButton.performClick();

        ArgumentCaptor<String> argument = ArgumentCaptor.forClass(String.class);
        verify(mMockSyncListener, times(2)).onSyncRequested(eq(mAccount), argument.capture(),
                eq(USER_ID), any(Bundle.class));

        List<String> values = argument.getAllValues();

        assertThat(values).contains(AUTHORITY);
        assertThat(values).contains(AUTHORITY_2);
    }

    @Test
    public void onButtonClicked_doesHaveActiveSyncs_shouldCancelSyncForSyncableAdapters() {
        // Add active syncs
        SyncInfo syncInfo = mock(SyncInfo.class);
        List<SyncInfo> syncs = new ArrayList<>();
        syncs.add(syncInfo);
        ShadowContentResolver.setCurrentSyncs(syncs);

        setUpSyncAdapters(AUTHORITY, AUTHORITY_2);
        initFragment();

        Button syncButton = mFragment.requireActivity().findViewById(R.id.action_button1);
        syncButton.performClick();

        ArgumentCaptor<String> argument = ArgumentCaptor.forClass(String.class);
        verify(mMockSyncListener, times(2)).onSyncCanceled(eq(mAccount), argument.capture(),
                eq(USER_ID));

        List<String> values = argument.getAllValues();

        assertThat(values).contains(AUTHORITY);
        assertThat(values).contains(AUTHORITY_2);
    }

    private void initFragment() {
        mFragment = AccountSyncDetailsFragment.newInstance(mAccount, mUserHandle);
        mActivity.launchFragment(mFragment);
    }

    private void setUpSyncAdapters(String... authorities) {
        SyncAdapterType[] syncAdapters = new SyncAdapterType[authorities.length];
        for (int i = 0; i < authorities.length; i++) {
            String authority = authorities[i];
            // Adds a sync adapter type that has the right account type and is visible.
            SyncAdapterType syncAdapterType = new SyncAdapterType(authority,
                    ACCOUNT_TYPE, /* userVisible */ true, /* supportsUploading */ true);
            syncAdapters[i] = syncAdapterType;

            // Sets that the sync adapter is syncable.
            ShadowContentResolver.setIsSyncable(mAccount, authority, /* syncable= */ 1);
        }
        ShadowContentResolver.setSyncAdapterTypes(syncAdapters);
    }
}
