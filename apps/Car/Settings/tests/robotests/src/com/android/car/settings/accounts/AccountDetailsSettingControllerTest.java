/*
 * Copyright 2019 The Android Open Source Project
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
import static org.mockito.Mockito.when;
import static org.robolectric.RuntimeEnvironment.application;
import static org.testng.Assert.assertThrows;

import android.accounts.Account;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.ExtraSettingsLoader;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowAccountManager;
import com.android.car.settings.testutils.ShadowApplicationPackageManager;
import com.android.car.settings.testutils.ShadowContentResolver;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Unit test for {@link AccountDetailsSettingController}.
 */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowAccountManager.class, ShadowContentResolver.class,
        ShadowApplicationPackageManager.class})
public class AccountDetailsSettingControllerTest {

    private static final String ACCOUNT_NAME = "account_name";
    private static final String MATCHING_ACCOUNT_TYPE = "account_type";
    private static final String ACCOUNT_ACCESS_ID = "account_access_id";
    private static final String METADATA_IA_ACCOUNT = "com.android.settings.ia.account";
    private static final String NOT_MATCHING_ACCOUNT_TYPE = "com.android.settings.ia.account";

    private Context mContext;
    private PreferenceGroup mPreference;
    @Mock
    private ExtraSettingsLoader mExtraSettingsLoader;
    private AccountDetailsSettingController mAccountDetailsSettingController;
    private Account mAccount = new Account(ACCOUNT_NAME, MATCHING_ACCOUNT_TYPE, ACCOUNT_ACCESS_ID);
    private List<Preference> mPreferenceList;
    private HashMap<Preference, Bundle> mPreferenceBundleMap;
    PreferenceControllerTestHelper<AccountDetailsSettingController> mHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;

        mHelper = new PreferenceControllerTestHelper<>(application,
                AccountDetailsSettingController.class);

        mAccountDetailsSettingController = mHelper.getController();
        mAccountDetailsSettingController.setAccount(mAccount);

        mPreferenceList = new ArrayList<>();
        mPreferenceBundleMap = new HashMap<>();
        mPreference = new LogicalPreferenceGroup(application);
        mPreference.setIntent(new Intent());
        mHelper.setPreference(mPreference);
    }

    @Test
    public void checkInitialized_accountSetAndUserHandleSet_doesNothing() {
        mAccountDetailsSettingController = new PreferenceControllerTestHelper<>(application,
                AccountDetailsSettingController.class).getController();
        mAccountDetailsSettingController.setAccount(mAccount);

        mAccountDetailsSettingController.checkInitialized();
    }

    @Test
    public void checkInitialized_nullAccount_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class,
                () -> new PreferenceControllerTestHelper<>(mContext,
                        AccountDetailsSettingController.class,
                        new LogicalPreferenceGroup(mContext)));
    }

    @Test
    public void addExtraSettings_preferenceEmpty_shouldNotAddAnyPreferences() {
        mHelper.markState(Lifecycle.State.STARTED);

        setupMockSettingLoaderAndRefreshUI();

        assertThat(mPreference.getPreferenceCount()).isEqualTo(0);
    }

    @Test
    public void addExtraSettings_preferenceEmpty_isNotVisible() {
        mHelper.markState(Lifecycle.State.STARTED);

        setupMockSettingLoaderAndRefreshUI();

        assertThat(mPreference.isVisible()).isFalse();
    }

    @Test
    public void addExtraSettings_accountTypeNotEqual_shouldNotAddAnyPreferences() {
        mHelper.markState(Lifecycle.State.STARTED);
        Preference preference = new Preference(mContext);
        mPreferenceList.add(preference);
        Bundle bundle = new Bundle();
        bundle.putString(METADATA_IA_ACCOUNT, NOT_MATCHING_ACCOUNT_TYPE);
        mPreferenceBundleMap.put(preference, bundle);

        setupMockSettingLoaderAndRefreshUI();

        assertThat(mPreference.getPreferenceCount()).isEqualTo(0);
    }

    @Test
    public void addExtraSettings_accountTypeNotEqual_isNotVisible() {
        mHelper.markState(Lifecycle.State.STARTED);
        Preference preference = new Preference(mContext);
        mPreferenceList.add(preference);
        Bundle bundle = new Bundle();
        bundle.putString(METADATA_IA_ACCOUNT, NOT_MATCHING_ACCOUNT_TYPE);
        mPreferenceBundleMap.put(preference, bundle);

        setupMockSettingLoaderAndRefreshUI();

        assertThat(mPreference.isVisible()).isFalse();
    }

    @Test
    public void addExtraSettings_accountTypeEqual_shouldAddPreferences() {
        Preference preference = new Preference(mContext);
        mPreferenceList.add(preference);
        Bundle bundle = new Bundle();
        bundle.putString(METADATA_IA_ACCOUNT, MATCHING_ACCOUNT_TYPE);
        mPreferenceBundleMap.put(preference, bundle);

        setupMockSettingLoaderAndRefreshUI();
        mHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        assertThat(mPreference.getPreferenceCount()).isEqualTo(1);
    }

    @Test
    public void addExtraSettings_accountTypeEqual_isVisible() {
        Preference preference = new Preference(mContext);
        mPreferenceList.add(preference);
        Bundle bundle = new Bundle();
        bundle.putString(METADATA_IA_ACCOUNT, MATCHING_ACCOUNT_TYPE);
        mPreferenceBundleMap.put(preference, bundle);

        setupMockSettingLoaderAndRefreshUI();
        mHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        assertThat(mPreference.isVisible()).isTrue();
    }

    private void setupMockSettingLoaderAndRefreshUI() {
        when(mExtraSettingsLoader.loadPreferences(any())).thenReturn(mPreferenceBundleMap);

        mAccountDetailsSettingController.setExtraSettingsLoader(mExtraSettingsLoader);
        mAccountDetailsSettingController.refreshUi();
    }
}
