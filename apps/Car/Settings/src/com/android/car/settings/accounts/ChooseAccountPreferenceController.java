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

import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.car.drivingstate.CarUxRestrictions;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.collection.ArrayMap;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.settingslib.accounts.AuthenticatorHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Controller for showing the user the list of accounts they can add.
 *
 * <p>Largely derived from {@link com.android.settings.accounts.ChooseAccountActivity}
 */
public class ChooseAccountPreferenceController extends
        PreferenceController<PreferenceGroup> implements
        AuthenticatorHelper.OnAccountsUpdateListener {
    private static final int ADD_ACCOUNT_REQUEST_CODE = 1;

    private final UserHandle mUserHandle;
    private AuthenticatorHelper mAuthenticatorHelper;
    private List<String> mAuthorities;
    private Set<String> mAccountTypesFilter;
    private Set<String> mAccountTypesExclusionFilter;
    private ArrayMap<String, AuthenticatorDescriptionPreference> mPreferences = new ArrayMap<>();

    public ChooseAccountPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mUserHandle = new CarUserManagerHelper(context).getCurrentProcessUserInfo().getUserHandle();

        HashSet<String> accountTypeExclusionFilter = new HashSet<>();

        // Hardcoding bluetooth account type
        accountTypeExclusionFilter.add("com.android.bluetooth.pbapsink");
        setAccountTypesExclusionFilter(accountTypeExclusionFilter);
    }

    /** Sets the authorities that the user has. */
    public void setAuthorities(List<String> authorities) {
        mAuthorities = authorities;
    }

    /** Sets the filter for accounts that should be shown. */
    public void setAccountTypesFilter(Set<String> accountTypesFilter) {
        mAccountTypesFilter = accountTypesFilter;
    }

    /** Sets the filter for accounts that should NOT be shown. */
    protected void setAccountTypesExclusionFilter(Set<String> accountTypesExclusionFilterFilter) {
        mAccountTypesExclusionFilter = accountTypesExclusionFilterFilter;
    }

    @Override
    protected Class<PreferenceGroup> getPreferenceType() {
        return PreferenceGroup.class;
    }

    @Override
    protected void updateState(PreferenceGroup preferenceGroup) {
        forceUpdateAccountsCategory();
    }

    /** Initializes the authenticator helper. */
    @Override
    protected void onCreateInternal() {
        mAuthenticatorHelper = new AuthenticatorHelper(getContext(), mUserHandle, this);
    }

    /**
     * Registers the account update callback.
     */
    @Override
    protected void onStartInternal() {
        mAuthenticatorHelper.listenToAccountUpdates();
    }

    /**
     * Unregisters the account update callback.
     */
    @Override
    protected void onStopInternal() {
        mAuthenticatorHelper.stopListeningToAccountUpdates();
    }

    @Override
    public void onAccountsUpdate(UserHandle userHandle) {
        // Only force a refresh if accounts have changed for the current user.
        if (userHandle.equals(mUserHandle)) {
            forceUpdateAccountsCategory();
        }
    }

    /** Forces a refresh of the authenticator description preferences. */
    private void forceUpdateAccountsCategory() {
        Set<String> preferencesToRemove = new HashSet<>(mPreferences.keySet());
        List<AuthenticatorDescriptionPreference> preferences =
                getAuthenticatorDescriptionPreferences(preferencesToRemove);
        // Add all preferences that aren't already shown
        for (int i = 0; i < preferences.size(); i++) {
            AuthenticatorDescriptionPreference preference = preferences.get(i);
            preference.setOrder(i);
            String key = preference.getKey();
            getPreference().addPreference(preference);
            mPreferences.put(key, preference);
        }

        // Remove all preferences that should no longer be shown
        for (String key : preferencesToRemove) {
            getPreference().removePreference(mPreferences.get(key));
            mPreferences.remove(key);
        }
    }

    /**
     * Returns a list of preferences corresponding to the account types the user can add.
     *
     * <p> Derived from
     * {@link com.android.settings.accounts.ChooseAccountActivity#onAuthDescriptionsUpdated}
     *
     * @param preferencesToRemove the current preferences shown; will contain the preferences that
     *                            need to be removed from the screen after method execution
     */
    private List<AuthenticatorDescriptionPreference> getAuthenticatorDescriptionPreferences(
            Set<String> preferencesToRemove) {
        AuthenticatorDescription[] authenticatorDescriptions = AccountManager.get(
                getContext()).getAuthenticatorTypesAsUser(
                mUserHandle.getIdentifier());

        ArrayList<AuthenticatorDescriptionPreference> authenticatorDescriptionPreferences =
                new ArrayList<>();
        // Create list of account providers to show on page.
        for (AuthenticatorDescription authenticatorDescription : authenticatorDescriptions) {
            String accountType = authenticatorDescription.type;
            CharSequence label = mAuthenticatorHelper.getLabelForType(getContext(), accountType);
            Drawable icon = mAuthenticatorHelper.getDrawableForType(getContext(), accountType);

            List<String> accountAuthorities =
                    mAuthenticatorHelper.getAuthoritiesForAccountType(accountType);

            // If there are specific authorities required, we need to check whether they are
            // included in the account type.
            boolean authorized =
                    (mAuthorities == null || mAuthorities.isEmpty() || accountAuthorities == null
                            || !Collections.disjoint(accountAuthorities, mAuthorities));

            // If there is an account type filter, make sure this account type is included.
            authorized = authorized && (mAccountTypesFilter == null
                    || mAccountTypesFilter.contains(accountType));

            // Check if the account type is in the exclusion list.
            authorized = authorized && (mAccountTypesExclusionFilter == null
                    || !mAccountTypesExclusionFilter.contains(accountType));

            // If authorized, add a preference for the provider to the list and remove it from
            // preferencesToRemove.
            if (authorized) {
                AuthenticatorDescriptionPreference preference = mPreferences.getOrDefault(
                        accountType,
                        new AuthenticatorDescriptionPreference(getContext(), accountType, label,
                                icon));
                preference.setOnPreferenceClickListener(
                        pref -> onAddAccount(preference.getAccountType()));
                authenticatorDescriptionPreferences.add(preference);
                preferencesToRemove.remove(accountType);
            }
        }

        Collections.sort(authenticatorDescriptionPreferences, Comparator.comparing(
                (AuthenticatorDescriptionPreference a) -> a.getTitle().toString()));

        return authenticatorDescriptionPreferences;
    }

    /** Starts the {@link AddAccountActivity} to add an account for the given account type. */
    private boolean onAddAccount(String accountType) {
        Intent intent = new Intent(getContext(), AddAccountActivity.class);
        intent.putExtra(AddAccountActivity.EXTRA_SELECTED_ACCOUNT, accountType);
        getFragmentController().startActivityForResult(intent, ADD_ACCOUNT_REQUEST_CODE,
                this::onAccountAdded);
        return true;
    }

    private void onAccountAdded(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == ADD_ACCOUNT_REQUEST_CODE) {
            getFragmentController().goBack();
        }
    }

    /** Used for testing to trigger an account update. */
    @VisibleForTesting
    AuthenticatorHelper getAuthenticatorHelper() {
        return mAuthenticatorHelper;
    }

    /** Handles adding accounts. */
    interface AddAccountListener {
        /** Handles adding an account. */
        void addAccount(String accountType);
    }

    private static class AuthenticatorDescriptionPreference extends Preference {
        private final String mType;

        AuthenticatorDescriptionPreference(Context context, String accountType, CharSequence label,
                Drawable icon) {
            super(context);
            mType = accountType;

            setKey(accountType);
            setTitle(label);
            setIcon(icon);
        }

        private String getAccountType() {
            return mType;
        }
    }
}
