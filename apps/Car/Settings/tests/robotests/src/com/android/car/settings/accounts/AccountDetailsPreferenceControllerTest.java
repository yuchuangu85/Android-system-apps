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

import static org.robolectric.RuntimeEnvironment.application;
import static org.testng.Assert.assertThrows;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
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
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

/** Unit test for {@link AccountDetailsPreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowAccountManager.class, ShadowContentResolver.class,
        ShadowApplicationPackageManager.class})
public class AccountDetailsPreferenceControllerTest {
    private static final String ACCOUNT_NAME = "Name";
    private static final String ACCOUNT_TYPE = "com.acct";
    private final Account mAccount = new Account(ACCOUNT_NAME, ACCOUNT_TYPE);
    private final UserHandle mUserHandle = new UserHandle(0);

    private AccountDetailsPreferenceController mController;
    private Preference mPreference;

    @Before
    public void setUp() {
        PreferenceControllerTestHelper<AccountDetailsPreferenceController> helper =
                new PreferenceControllerTestHelper<>(application,
                        AccountDetailsPreferenceController.class);
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
    public void checkInitialized_accountSetAndUserHandleSet_doesNothing() {
        mController = new PreferenceControllerTestHelper<>(application,
                AccountDetailsPreferenceController.class).getController();
        mController.setAccount(mAccount);
        mController.setUserHandle(mUserHandle);

        mController.checkInitialized();
    }

    @Test
    public void checkInitialized_nullAccount_throwsIllegalStateException() {
        mController = new PreferenceControllerTestHelper<>(application,
                AccountDetailsPreferenceController.class).getController();
        mController.setUserHandle(mUserHandle);

        assertThrows(IllegalStateException.class, () -> mController.checkInitialized());
    }

    @Test
    public void checkInitialized_nullUserHandle_throwsIllegalStateException() {
        mController = new PreferenceControllerTestHelper<>(application,
                AccountDetailsPreferenceController.class).getController();
        mController.setAccount(mAccount);

        assertThrows(IllegalStateException.class, () -> mController.checkInitialized());
    }

    @Test
    public void refreshUi_shouldSetTitle() {
        mController.refreshUi();

        assertThat(mPreference.getTitle().toString()).isEqualTo(ACCOUNT_NAME);
    }

    @Test
    public void refreshUi_shouldSetIcon() {
        // Add authenticator description with icon resource
        addAuthenticator(/* type= */ ACCOUNT_TYPE, /* labelRes= */
                R.string.account_type1_label, /* iconId= */ R.drawable.ic_add);

        mController.refreshUi();

        assertThat(mPreference.getIcon()).isNotNull();
        assertThat(Shadows.shadowOf(mPreference.getIcon()).getCreatedFromResId()).isEqualTo(
                R.drawable.ic_add);
    }

    private void addAuthenticator(String type, int labelRes, int iconId) {
        getShadowAccountManager().addAuthenticator(
                new AuthenticatorDescription(type, "com.android.car.settings",
                        labelRes, iconId, /* smallIconId= */ 0, /* prefId= */ 0,
                        /* customTokens= */ false));
    }

    private ShadowAccountManager getShadowAccountManager() {
        return Shadow.extract(AccountManager.get(application));
    }
}
