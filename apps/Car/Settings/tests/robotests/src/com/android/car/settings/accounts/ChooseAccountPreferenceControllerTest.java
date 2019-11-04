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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.robolectric.RuntimeEnvironment.application;

import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.car.userlib.CarUserManagerHelper;
import android.content.Intent;
import android.content.SyncAdapterType;
import android.content.pm.UserInfo;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.common.ActivityResultCallback;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowAccountManager;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;
import com.android.car.settings.testutils.ShadowContentResolver;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Unit tests for {@link ChooseAccountPreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCarUserManagerHelper.class, ShadowContentResolver.class,
        ShadowAccountManager.class})
public class ChooseAccountPreferenceControllerTest {
    private static final int ADD_ACCOUNT_REQUEST_CODE = 1;
    private static final int USER_ID = 0;

    PreferenceControllerTestHelper<ChooseAccountPreferenceController> mHelper;
    private PreferenceGroup mPreferenceGroup;
    private ChooseAccountPreferenceController mController;
    @Mock
    private CarUserManagerHelper mMockCarUserManagerHelper;

    private AccountManager mAccountManager = AccountManager.get(application);

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        // Set up user info
        ShadowCarUserManagerHelper.setMockInstance(mMockCarUserManagerHelper);
        doReturn(new UserInfo(USER_ID, "name", 0)).when(
                mMockCarUserManagerHelper).getCurrentProcessUserInfo();

        // Add authenticated account types
        addAuthenticator(/* type= */ "com.acct1", /* label= */ R.string.account_type1_label);
        addAuthenticator(/* type= */ "com.acct2", /* label= */ R.string.account_type2_label);

        mPreferenceGroup = new LogicalPreferenceGroup(application);
        mHelper = new PreferenceControllerTestHelper<>(application,
                ChooseAccountPreferenceController.class, mPreferenceGroup);
        // Mark state as started so the AuthenticatorHelper listener is attached.
        mHelper.markState(Lifecycle.State.STARTED);
        mController = mHelper.getController();
    }

    @After
    public void tearDown() {
        ShadowCarUserManagerHelper.reset();
        ShadowContentResolver.reset();
    }

    @Test
    public void refreshUi_authenticatorPreferencesShouldBeSet() {
        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(2);

        Preference acct1Pref = mPreferenceGroup.getPreference(0);
        assertThat(acct1Pref.getTitle()).isEqualTo("Type 1");

        Preference acct2Pref = mPreferenceGroup.getPreference(1);
        assertThat(acct2Pref.getTitle()).isEqualTo("Type 2");
    }

    @Test
    public void refreshUi_hasAccountTypeFilter_shouldFilterAccounts() {
        // Add a filter that should filter out the second account type (com.acct2)
        Set<String> accountTypesFilter = new HashSet<>();
        accountTypesFilter.add("com.acct1");
        mController.setAccountTypesFilter(accountTypesFilter);

        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);

        Preference acct1Pref = mPreferenceGroup.getPreference(0);
        assertThat(acct1Pref.getTitle()).isEqualTo("Type 1");
    }

    @Test
    public void refreshUi_hasAccountExclusionFilter_shouldFilterAccounts() {
        // Add a filter that should filter out the first account type (com.acct1)
        Set<String> accountExclusionTypesFilter = new HashSet<>();
        accountExclusionTypesFilter.add("com.acct1");
        mController.setAccountTypesExclusionFilter(accountExclusionTypesFilter);

        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);

        Preference acct1Pref = mPreferenceGroup.getPreference(0);
        assertThat(acct1Pref.getTitle()).isEqualTo("Type 2");
    }

    @Test
    public void refreshUi_doesNotHaveAuthoritiesInFilter_shouldNotBeShown() {
        // Adds a sync adapter type for the com.acct1 account type that does not have the same
        // authority as the one passed to someAuthority
        SyncAdapterType syncAdapterType = new SyncAdapterType("someAuthority",
                "com.acct1", /* userVisible */ true, /* supportsUploading */ true);
        SyncAdapterType[] syncAdapters = {syncAdapterType};
        ShadowContentResolver.setSyncAdapterTypes(syncAdapters);

        mController.setAuthorities(Collections.singletonList("someOtherAuthority"));

        // Force an authenticator refresh so the authorities are refreshed
        mController.getAuthenticatorHelper().onReceive(application, null);
        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);

        Preference acct2Pref = mPreferenceGroup.getPreference(0);
        assertThat(acct2Pref.getTitle()).isEqualTo("Type 2");
    }

    @Test
    public void refreshUi_hasAuthoritiesInFilter_shouldBeShown() {
        // Adds a sync adapter type for the com.acct1 account type that has the same authority as
        // the one passed to someAuthority
        SyncAdapterType syncAdapterType = new SyncAdapterType("someAuthority",
                "com.acct1", /* userVisible */ true, /* supportsUploading */ true);
        SyncAdapterType[] syncAdapters = {syncAdapterType};
        ShadowContentResolver.setSyncAdapterTypes(syncAdapters);

        mController.setAuthorities(Collections.singletonList("someAuthority"));

        // Force an authenticator refresh so the authorities are refreshed
        mController.getAuthenticatorHelper().onReceive(application, null);
        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(2);

        Preference acct1Pref = mPreferenceGroup.getPreference(0);
        assertThat(acct1Pref.getTitle()).isEqualTo("Type 1");

        Preference acct2Pref = mPreferenceGroup.getPreference(1);
        assertThat(acct2Pref.getTitle()).isEqualTo("Type 2");
    }

    @Test
    public void onAccountsUpdate_currentUserUpdated_shouldForceUpdate() {
        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(2);

        addAuthenticator(/* type= */ "com.acct3", /* label= */ R.string.account_type3_label);

        // Trigger an account update via the authenticator helper so that the state matches what
        // it would be during actual execution.
        mController.getAuthenticatorHelper().onReceive(application, null);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(3);
        Preference acct3Pref = mPreferenceGroup.getPreference(2);
        assertThat(acct3Pref.getTitle()).isEqualTo("Type 3");
    }

    @Test
    public void onPreferenceClick_shouldStartActivity() {
        Preference acct1Pref = mPreferenceGroup.getPreference(0);
        acct1Pref.performClick();

        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);

        verify(mHelper.getMockFragmentController()).startActivityForResult(captor.capture(),
                anyInt(), any(ActivityResultCallback.class));

        Intent intent = captor.getValue();
        assertThat(intent.getComponent().getClassName()).isEqualTo(
                AddAccountActivity.class.getName());
        assertThat(intent.getStringExtra(AddAccountActivity.EXTRA_SELECTED_ACCOUNT)).isEqualTo(
                "com.acct1");
    }

    @Test
    public void onAccountAdded_shouldGoBack() {
        Preference acct1Pref = mPreferenceGroup.getPreference(0);
        acct1Pref.performClick();

        ArgumentCaptor<ActivityResultCallback> captor = ArgumentCaptor.forClass(
                ActivityResultCallback.class);

        verify(mHelper.getMockFragmentController()).startActivityForResult(any(Intent.class),
                anyInt(), captor.capture());

        ActivityResultCallback callback = captor.getValue();
        callback.processActivityResult(ADD_ACCOUNT_REQUEST_CODE, /* resultCode= */ 0, /* data= */
                null);

        verify(mHelper.getMockFragmentController()).goBack();
    }

    private void addAuthenticator(String type, int labelRes) {
        getShadowAccountManager().addAuthenticator(
                new AuthenticatorDescription(type, "com.android.car.settings",
                        labelRes, 0, 0, 0, false));
    }

    private ShadowAccountManager getShadowAccountManager() {
        return Shadow.extract(mAccountManager);
    }
}