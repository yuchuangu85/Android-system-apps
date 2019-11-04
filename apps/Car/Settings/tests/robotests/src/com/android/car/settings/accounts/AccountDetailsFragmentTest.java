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

import static org.mockito.Mockito.doReturn;
import static org.robolectric.RuntimeEnvironment.application;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.pm.UserInfo;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.testutils.BaseTestActivity;
import com.android.car.settings.testutils.ShadowAccountManager;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;
import com.android.car.settings.testutils.ShadowContentResolver;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

/**
 * Tests for the {@link AccountDetailsFragment}.
 */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCarUserManagerHelper.class, ShadowAccountManager.class,
        ShadowContentResolver.class})
public class AccountDetailsFragmentTest {
    private static final String DIALOG_TAG = "confirmRemoveAccount";
    private final Account mAccount = new Account("Name", "com.acct");
    private final UserInfo mUserInfo = new UserInfo(/* id= */ 0, /* name= */ "name", /* flags= */
            0);
    private final CharSequence mAccountLabel = "Type 1";

    private Context mContext;
    private BaseTestActivity mActivity;
    private AccountDetailsFragment mFragment;
    @Mock
    private CarUserManagerHelper mMockCarUserManagerHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowCarUserManagerHelper.setMockInstance(mMockCarUserManagerHelper);

        mContext = application;
        // Add the account to the official list of accounts
        getShadowAccountManager().addAccount(mAccount);

        mActivity = Robolectric.setupActivity(BaseTestActivity.class);
    }

    @After
    public void tearDown() {
        ShadowCarUserManagerHelper.reset();
        ShadowContentResolver.reset();
        mActivity.clearOnBackPressedFlag();
    }

    @Test
    public void onActivityCreated_titleShouldBeSet() {
        initFragment();

        TextView title = mFragment.requireActivity().findViewById(R.id.title);
        assertThat(title.getText()).isEqualTo(mAccountLabel);
    }

    @Test
    public void cannotModifyUsers_removeAccountButtonShouldNotBeVisible() {
        doReturn(false).when(mMockCarUserManagerHelper).canCurrentProcessModifyAccounts();
        initFragment();

        Button removeAccountButton = mFragment.requireActivity().findViewById(R.id.action_button1);
        assertThat(removeAccountButton.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void canModifyUsers_removeAccountButtonShouldBeVisible() {
        doReturn(true).when(mMockCarUserManagerHelper).canCurrentProcessModifyAccounts();
        initFragment();

        Button removeAccountButton = mFragment.requireActivity().findViewById(R.id.action_button1);
        assertThat(removeAccountButton.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void onRemoveAccountButtonClicked_canModifyUsers_shouldShowConfirmRemoveAccountDialog() {
        doReturn(true).when(mMockCarUserManagerHelper).canCurrentProcessModifyAccounts();
        initFragment();

        Button removeAccountButton = mFragment.requireActivity().findViewById(R.id.action_button1);
        removeAccountButton.performClick();

        Fragment dialogFragment =
                mFragment.requireActivity().getSupportFragmentManager().findFragmentByTag(
                        DIALOG_TAG);

        assertThat(dialogFragment).isNotNull();
        assertThat(dialogFragment).isInstanceOf(
                AccountDetailsFragment.ConfirmRemoveAccountDialogFragment.class);
    }

    @Test
    public void accountExists_accountStillExists_shouldBeTrue() {
        initFragment();

        // Nothing has happened to the account so this should return true;
        assertThat(mFragment.accountExists()).isTrue();
    }

    @Test
    public void accountExists_accountWasRemoved_shouldBeFalse() {
        initFragment();

        // Clear accounts so that the account being displayed appears to have been removed
        getShadowAccountManager().removeAllAccounts();
        assertThat(mFragment.accountExists()).isFalse();
    }

    @Test
    public void onAccountsUpdate_accountDoesNotExist_shouldGoBack() {
        initFragment();

        // Clear accounts so that the account being displayed appears to have been removed
        getShadowAccountManager().removeAllAccounts();
        mFragment.onAccountsUpdate(null);

        assertThat(mActivity.getOnBackPressedFlag()).isTrue();
    }

    private void initFragment() {
        mFragment = AccountDetailsFragment.newInstance(mAccount, mAccountLabel, mUserInfo);
        mActivity.launchFragment(mFragment);
    }

    private ShadowAccountManager getShadowAccountManager() {
        return Shadow.extract(AccountManager.get(mContext));
    }
}
