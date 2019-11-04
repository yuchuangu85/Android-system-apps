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
import android.accounts.AccountManager;
import android.car.drivingstate.CarUxRestrictions;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.pm.UserInfo;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;

import androidx.collection.ArrayMap;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.settingslib.accounts.AuthenticatorHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Controller for listing accounts.
 *
 * <p>Largely derived from {@link com.android.settings.accounts.AccountPreferenceController}
 */
public class AccountListPreferenceController extends
        PreferenceController<PreferenceCategory> implements
        AuthenticatorHelper.OnAccountsUpdateListener,
        CarUserManagerHelper.OnUsersUpdateListener {
    private static final String NO_ACCOUNT_PREF_KEY = "no_accounts_added";

    private final UserInfo mUserInfo;
    private final CarUserManagerHelper mCarUserManagerHelper;
    private final ArrayMap<String, Preference> mPreferences = new ArrayMap<>();
    private AuthenticatorHelper mAuthenticatorHelper;
    private String[] mAuthorities;

    public AccountListPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mCarUserManagerHelper = new CarUserManagerHelper(context);
        mUserInfo = mCarUserManagerHelper.getCurrentProcessUserInfo();
        mAuthenticatorHelper = new AuthenticatorHelper(context,
                mUserInfo.getUserHandle(), /* listener= */ this);
    }

    /** Sets the account authorities that are available. */
    public void setAuthorities(String[] authorities) {
        mAuthorities = authorities;
    }

    @Override
    protected Class<PreferenceCategory> getPreferenceType() {
        return PreferenceCategory.class;
    }

    @Override
    protected void updateState(PreferenceCategory preference) {
        forceUpdateAccountsCategory();
    }

    @Override
    protected int getAvailabilityStatus() {
        return mCarUserManagerHelper.canCurrentProcessModifyAccounts() ? AVAILABLE
                : DISABLED_FOR_USER;
    }

    /**
     * Registers the account update and user update callbacks.
     */
    @Override
    protected void onStartInternal() {
        mAuthenticatorHelper.listenToAccountUpdates();
        mCarUserManagerHelper.registerOnUsersUpdateListener(this);
    }

    /**
     * Unregisters the account update and user update callbacks.
     */
    @Override
    protected void onStopInternal() {
        mAuthenticatorHelper.stopListeningToAccountUpdates();
        mCarUserManagerHelper.unregisterOnUsersUpdateListener(this);
    }

    @Override
    public void onAccountsUpdate(UserHandle userHandle) {
        if (userHandle.equals(mUserInfo.getUserHandle())) {
            forceUpdateAccountsCategory();
        }
    }

    @Override
    public void onUsersUpdate() {
        forceUpdateAccountsCategory();
    }

    private boolean onAccountPreferenceClicked(AccountPreference preference) {
        // Show the account's details when an account is clicked on.
        getFragmentController().launchFragment(AccountDetailsFragment.newInstance(
                preference.getAccount(), preference.getLabel(), mUserInfo));
        return true;
    }

    /** Forces a refresh of the account preferences. */
    private void forceUpdateAccountsCategory() {
        // Set the category title and include the user's name
        getPreference().setTitle(
                getContext().getString(R.string.account_list_title, mUserInfo.name));

        // Recreate the authentication helper to refresh the list of enabled accounts
        mAuthenticatorHelper = new AuthenticatorHelper(getContext(), mUserInfo.getUserHandle(),
                this);

        Set<String> preferencesToRemove = new HashSet<>(mPreferences.keySet());
        List<? extends Preference> preferences = getAccountPreferences(preferencesToRemove);
        // Add all preferences that aren't already shown. Manually set the order so that existing
        // preferences are reordered correctly.
        for (int i = 0; i < preferences.size(); i++) {
            Preference pref = preferences.get(i);
            pref.setOrder(i);
            mPreferences.put(pref.getKey(), pref);
            getPreference().addPreference(pref);
        }

        for (String key : preferencesToRemove) {
            getPreference().removePreference(mPreferences.get(key));
            mPreferences.remove(key);
        }
    }

    /**
     * Returns a list of preferences corresponding to the accounts for the current user.
     *
     * <p> Derived from
     * {@link com.android.settings.accounts.AccountPreferenceController#getAccountTypePreferences}
     *
     * @param preferencesToRemove the current preferences shown; only preferences to be removed will
     *                            remain after method execution
     */
    private List<? extends Preference> getAccountPreferences(
            Set<String> preferencesToRemove) {
        String[] accountTypes = mAuthenticatorHelper.getEnabledAccountTypes();
        ArrayList<AccountPreference> accountPreferences =
                new ArrayList<>(accountTypes.length);

        for (int i = 0; i < accountTypes.length; i++) {
            String accountType = accountTypes[i];
            // Skip showing any account that does not have any of the requested authorities
            if (!accountTypeHasAnyRequestedAuthorities(accountType)) {
                continue;
            }
            CharSequence label = mAuthenticatorHelper.getLabelForType(getContext(), accountType);
            if (label == null) {
                continue;
            }

            Account[] accounts = AccountManager.get(getContext())
                    .getAccountsByTypeAsUser(accountType, mUserInfo.getUserHandle());
            Drawable icon = mAuthenticatorHelper.getDrawableForType(getContext(), accountType);

            // Add a preference row for each individual account
            for (Account account : accounts) {
                String key = AccountPreference.buildKey(account);
                AccountPreference preference = (AccountPreference) mPreferences.getOrDefault(key,
                        new AccountPreference(getContext(), account, label, icon));
                preference.setOnPreferenceClickListener(
                        (Preference pref) -> onAccountPreferenceClicked((AccountPreference) pref));

                accountPreferences.add(preference);
                preferencesToRemove.remove(key);
            }
            mAuthenticatorHelper.preloadDrawableForType(getContext(), accountType);
        }

        // If there are no accounts, return the "no account added" preference.
        if (accountPreferences.isEmpty()) {
            preferencesToRemove.remove(NO_ACCOUNT_PREF_KEY);
            return Arrays.asList(mPreferences.getOrDefault(NO_ACCOUNT_PREF_KEY,
                    createNoAccountsAddedPreference()));
        }

        Collections.sort(accountPreferences, Comparator.comparing(
                (AccountPreference a) -> a.getSummary().toString())
                .thenComparing((AccountPreference a) -> a.getTitle().toString()));

        return accountPreferences;
    }

    private Preference createNoAccountsAddedPreference() {
        Preference emptyPreference = new Preference(getContext());
        emptyPreference.setTitle(R.string.no_accounts_added);
        emptyPreference.setKey(NO_ACCOUNT_PREF_KEY);
        emptyPreference.setSelectable(false);

        return emptyPreference;
    }

    /**
     * Returns whether the account type has any of the authorities requested by the caller.
     *
     * <p> Derived from {@link AccountPreferenceController#accountTypeHasAnyRequestedAuthorities}
     */
    private boolean accountTypeHasAnyRequestedAuthorities(String accountType) {
        if (mAuthorities == null || mAuthorities.length == 0) {
            // No authorities required
            return true;
        }
        ArrayList<String> authoritiesForType =
                mAuthenticatorHelper.getAuthoritiesForAccountType(accountType);
        if (authoritiesForType == null) {
            return false;
        }
        for (int j = 0; j < mAuthorities.length; j++) {
            if (authoritiesForType.contains(mAuthorities[j])) {
                return true;
            }
        }
        return false;
    }

    private static class AccountPreference extends Preference {
        /** Account that this Preference represents. */
        private final Account mAccount;
        private final CharSequence mLabel;

        private AccountPreference(Context context, Account account, CharSequence label,
                Drawable icon) {
            super(context);
            mAccount = account;
            mLabel = label;

            setKey(buildKey(account));
            setTitle(account.name);
            setSummary(label);
            setIcon(icon);
        }

        /**
         * Build a unique preference key based on the account.
         */
        public static String buildKey(Account account) {
            return String.valueOf(account.hashCode());
        }

        public Account getAccount() {
            return mAccount;
        }

        public CharSequence getLabel() {
            return mLabel;
        }
    }
}
