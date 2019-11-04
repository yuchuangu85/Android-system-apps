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

package com.android.car.settings.system;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.car.drivingstate.CarUxRestrictions;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Displays the currently signed in accounts on the vehicle to inform the user that they will be
 * removed during a factory reset.
 */
public class MasterClearAccountsPreferenceController extends PreferenceController<PreferenceGroup> {

    private static final Logger LOG = new Logger(MasterClearAccountsPreferenceController.class);

    private final CarUserManagerHelper mCarUserManagerHelper;
    private final Map<Account, Preference> mAccountPreferenceMap = new ArrayMap<>();

    public MasterClearAccountsPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mCarUserManagerHelper = new CarUserManagerHelper(context);
    }

    @Override
    protected Class<PreferenceGroup> getPreferenceType() {
        return PreferenceGroup.class;
    }

    @Override
    protected void onCreateInternal() {
        getPreference().addPreference(
                createPreference(getContext().getString(R.string.master_clear_accounts), /* icon= */
                        null));
    }

    @Override
    protected void updateState(PreferenceGroup preferenceGroup) {
        // Refresh the accounts in the off chance an account was added or removed while stopped.
        Set<Account> accountsToRemove = new HashSet<>(mAccountPreferenceMap.keySet());
        List<UserInfo> profiles = UserManager.get(getContext()).getProfiles(
                mCarUserManagerHelper.getCurrentProcessUserId());
        for (UserInfo profile : profiles) {
            UserHandle userHandle = new UserHandle(profile.id);
            AuthenticatorDescription[] descriptions = AccountManager.get(
                    getContext()).getAuthenticatorTypesAsUser(profile.id);
            Account[] accounts = AccountManager.get(getContext()).getAccountsAsUser(profile.id);
            for (Account account : accounts) {
                AuthenticatorDescription description = null;
                for (AuthenticatorDescription desc : descriptions) {
                    if (account.type.equals(desc.type)) {
                        description = desc;
                        break;
                    }
                }
                if (description == null) {
                    LOG.w("No descriptor for account name=" + account.name + " type="
                            + account.type);
                    continue;
                }

                accountsToRemove.remove(account);
                if (!mAccountPreferenceMap.containsKey(account)) {
                    Preference accountPref = createPreference(account.name,
                            getAccountIcon(description, userHandle));
                    mAccountPreferenceMap.put(account, accountPref);
                    preferenceGroup.addPreference(accountPref);
                }
            }
        }

        for (Account accountToRemove : accountsToRemove) {
            preferenceGroup.removePreference(mAccountPreferenceMap.get(accountToRemove));
        }

        // If the only preference is the title, hide the group.
        preferenceGroup.setVisible(preferenceGroup.getPreferenceCount() > 1);
    }

    private Drawable getAccountIcon(AuthenticatorDescription description, UserHandle userHandle) {
        Drawable icon = null;
        try {
            if (description.iconId != 0) {
                Context authContext = getContext().createPackageContextAsUser(
                        description.packageName, /* flags= */ 0, userHandle);
                icon = getContext().getPackageManager().getUserBadgedIcon(
                        authContext.getDrawable(description.iconId), userHandle);
            }
        } catch (PackageManager.NameNotFoundException e) {
            LOG.w("Bad package name for account type " + description.type, e);
        } catch (Resources.NotFoundException e) {
            LOG.w("Invalid icon id for account type " + description.type, e);
        }
        if (icon == null) {
            icon = getContext().getPackageManager().getDefaultActivityIcon();
        }
        return icon;
    }

    private Preference createPreference(String title, @Nullable Drawable icon) {
        Preference preference = new Preference(getContext());
        preference.setTitle(title);
        preference.setIcon(icon);
        preference.setSelectable(false);
        return preference;
    }
}
