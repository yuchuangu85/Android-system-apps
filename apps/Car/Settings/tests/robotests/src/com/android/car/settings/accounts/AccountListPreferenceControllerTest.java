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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.robolectric.RuntimeEnvironment.application;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.car.userlib.CarUserManagerHelper;
import android.content.pm.UserInfo;
import android.os.UserHandle;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowAccountManager;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;
import com.android.car.settings.testutils.ShadowContentResolver;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

/** Unit tests for {@link AccountListPreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCarUserManagerHelper.class, ShadowContentResolver.class,
        ShadowAccountManager.class})
public class AccountListPreferenceControllerTest {
    private static final int USER_ID = 0;
    private static final String USER_NAME = "name";
    private static final int NOT_THIS_USER_ID = 1;
    private PreferenceControllerTestHelper<AccountListPreferenceController> mHelper;
    private AccountManager mAccountManager = AccountManager.get(application);
    private PreferenceCategory mPreferenceCategory;
    private AccountListPreferenceController mController;
    private FragmentController mFragmentController;

    @Mock
    private CarUserManagerHelper mMockCarUserManagerHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        // Set up user info
        ShadowCarUserManagerHelper.setMockInstance(mMockCarUserManagerHelper);
        doReturn(new UserInfo(USER_ID, USER_NAME, 0)).when(
                mMockCarUserManagerHelper).getCurrentProcessUserInfo();
        doReturn(true).when(
                mMockCarUserManagerHelper).canCurrentProcessModifyAccounts();

        // Add authenticated account types so that they are listed below
        addAuthenticator(/* type= */ "com.acct1", /* labelRes= */ R.string.account_type1_label);
        addAuthenticator(/* type= */ "com.acct2", /* labelRes= */ R.string.account_type2_label);

        mPreferenceCategory = new PreferenceCategory(application);
        mHelper = new PreferenceControllerTestHelper<>(application,
                AccountListPreferenceController.class, mPreferenceCategory);
        mHelper.markState(Lifecycle.State.CREATED);
        mController = mHelper.getController();
        mFragmentController = mHelper.getMockFragmentController();
    }

    @After
    public void reset() {
        removeAllAccounts();
        ShadowContentResolver.reset();
    }

    @Test
    public void onCreate_preferenceCategoryTitleShouldBeSet() {
        String expectedTitle = application.getString(R.string.account_list_title, "name");
        assertThat(mPreferenceCategory.getTitle()).isEqualTo(expectedTitle);
    }

    @Test
    public void refreshUi_hasNoAccounts_shouldDisplayNoAccountPref() {
        mController.refreshUi();

        assertThat(mPreferenceCategory.getPreferenceCount()).isEqualTo(1);
        Preference noAccountPref = mPreferenceCategory.getPreference(0);

        assertThat(noAccountPref.getTitle()).isEqualTo(
                application.getString(R.string.no_accounts_added));
    }

    @Test
    public void refreshUi_hasAccounts_shouldDisplayAccounts() {
        addAccount(/* name= */ "Account1", /* type= */ "com.acct1");
        addAccount(/* name= */ "Account2", /* type= */ "com.acct2");

        mController.refreshUi();

        assertThat(mPreferenceCategory.getPreferenceCount()).isEqualTo(2);

        Preference firstPref = mPreferenceCategory.getPreference(0);
        assertThat(firstPref.getTitle()).isEqualTo("Account1");
        assertThat(firstPref.getSummary()).isEqualTo("Type 1");

        Preference secondPref = mPreferenceCategory.getPreference(1);
        assertThat(secondPref.getTitle()).isEqualTo("Account2");
        assertThat(secondPref.getSummary()).isEqualTo("Type 2");
    }

    @Test
    public void refreshUi_hasUnauthenticatedAccount_shouldNotDisplayAccount() {
        addAccount(/* name= */ "Account1", /* type= */ "com.acct1");
        addAccount(/* name= */ "Account2", /* type= */ "com.acct2");
        // There is not authenticator for account type "com.acct3" so this account should not
        // appear in the list of displayed accounts.
        addAccount(/* name= */ "Account3", /* type= */ "com.acct3");

        mController.refreshUi();

        assertThat(mPreferenceCategory.getPreferenceCount()).isEqualTo(2);

        Preference firstPref = mPreferenceCategory.getPreference(0);
        assertThat(firstPref.getTitle()).isEqualTo("Account1");
        assertThat(firstPref.getSummary()).isEqualTo("Type 1");

        Preference secondPref = mPreferenceCategory.getPreference(1);
        assertThat(secondPref.getTitle()).isEqualTo("Account2");
        assertThat(secondPref.getSummary()).isEqualTo("Type 2");
    }

    @Test
    public void onAccountsUpdate_isThisUser_shouldForceUpdate() {
        addAccount(/* name= */ "Account1", /* type= */ "com.acct1");
        addAccount(/* name= */ "Account2", /* type= */ "com.acct2");

        mController.refreshUi();
        assertThat(mPreferenceCategory.getPreferenceCount()).isEqualTo(2);

        getShadowAccountManager().removeAllAccounts();
        addAccount(/* name= */ "Account3", /* type= */ "com.acct1");

        mController.onAccountsUpdate(new UserHandle(USER_ID));

        assertThat(mPreferenceCategory.getPreferenceCount()).isEqualTo(1);
        Preference firstPref = mPreferenceCategory.getPreference(0);
        assertThat(firstPref.getTitle()).isEqualTo("Account3");
        assertThat(firstPref.getSummary()).isEqualTo("Type 1");
    }

    @Test
    public void onAccountsUpdate_updatedUserIsNotCurrentUser_shouldNotForceUpdate() {
        addAccount(/* name= */ "Account1", /* type= */ "com.acct1");
        addAccount(/* name= */ "Account2", /* type= */ "com.acct2");

        mController.refreshUi();
        assertThat(mPreferenceCategory.getPreferenceCount()).isEqualTo(2);

        getShadowAccountManager().removeAllAccounts();
        addAccount(/* name= */ "Account3", /* type= */ "com.acct1");

        mController.onAccountsUpdate(new UserHandle(NOT_THIS_USER_ID));

        assertThat(mPreferenceCategory.getPreferenceCount()).isEqualTo(2);
    }

    @Test
    public void onUsersUpdate_shouldForceUpdate() {
        addAccount(/* name= */ "Account1", /* type= */ "com.acct1");
        addAccount(/* name= */ "Account2", /* type= */ "com.acct2");

        mController.refreshUi();
        assertThat(mPreferenceCategory.getPreferenceCount()).isEqualTo(2);

        getShadowAccountManager().removeAllAccounts();
        addAccount(/* name= */ "Account3", /* type= */ "com.acct1");

        mController.onUsersUpdate();

        assertThat(mPreferenceCategory.getPreferenceCount()).isEqualTo(1);
        Preference firstPref = mPreferenceCategory.getPreference(0);
        assertThat(firstPref.getTitle()).isEqualTo("Account3");
        assertThat(firstPref.getSummary()).isEqualTo("Type 1");
    }

    @Test
    public void onAccountPreferenceClicked_shouldLaunchAccountDetailsFragment() {
        addAccount(/* name= */ "Account1", /* type= */ "com.acct1");
        mController.refreshUi();

        Preference firstPref = mPreferenceCategory.getPreference(0);
        firstPref.performClick();

        verify(mFragmentController).launchFragment(any(AccountDetailsFragment.class));
    }

    private void addAccount(String name, String type) {
        getShadowAccountManager().addAccount(new Account(name, type));
    }

    private void removeAllAccounts() {
        getShadowAccountManager().removeAllAccounts();
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
