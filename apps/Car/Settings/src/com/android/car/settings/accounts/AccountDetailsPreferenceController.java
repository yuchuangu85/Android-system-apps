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

import android.accounts.Account;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.os.UserHandle;

import androidx.annotation.CallSuper;
import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;
import com.android.settingslib.accounts.AuthenticatorHelper;

/** Controller for the preference that shows the details of an account. */
public class AccountDetailsPreferenceController extends PreferenceController<Preference> {
    private static final Logger LOG = new Logger(AccountDetailsPreferenceController.class);

    private Account mAccount;
    private UserHandle mUserHandle;

    public AccountDetailsPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    /** Sets the account that the details are shown for. */
    public void setAccount(Account account) {
        mAccount = account;
    }

    /** Returns the account the details are shown for. */
    public Account getAccount() {
        return mAccount;
    }

    /** Sets the UserHandle used by the controller. */
    public void setUserHandle(UserHandle userHandle) {
        mUserHandle = userHandle;
    }

    /** Returns the UserHandle used by the controller. */
    public UserHandle getUserHandle() {
        return mUserHandle;
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    /**
     * Verifies that the controller was properly initialized with
     * {@link #setAccount(Account)} and {@link #setUserHandle(UserHandle)}.
     *
     * @throws IllegalStateException if the account or user handle are {@code null}
     */
    @Override
    @CallSuper
    protected void checkInitialized() {
        LOG.v("checkInitialized");
        if (mAccount == null) {
            throw new IllegalStateException(
                    "AccountDetailsPreferenceController must be initialized by calling "
                            + "setAccount(Account)");
        }
        if (mUserHandle == null) {
            throw new IllegalStateException(
                    "AccountDetailsPreferenceController must be initialized by calling "
                            + "setUserHandle(UserHandle)");
        }
    }

    @Override
    @CallSuper
    protected void updateState(Preference preference) {
        preference.setTitle(mAccount.name);
        // Get the icon corresponding to the account's type and set it.
        AuthenticatorHelper helper = new AuthenticatorHelper(getContext(), mUserHandle, null);
        preference.setIcon(helper.getDrawableForType(getContext(), mAccount.type));
    }
}
