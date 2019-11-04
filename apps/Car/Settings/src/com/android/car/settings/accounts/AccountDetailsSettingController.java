/*
 * Copyright (C) 2019 The Android Open Source Project
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
import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.preference.Preference;

import com.android.car.settings.common.ExtraSettingsPreferenceController;
import com.android.car.settings.common.FragmentController;

import java.util.Map;

/**
 * Injects preferences from other system applications at a placeholder location into the account
 * details fragment. This class is and extension of {@link ExtraSettingsPreferenceController} which
 * is needed to check what all preferences to show in the account details page.
 */
public class AccountDetailsSettingController extends ExtraSettingsPreferenceController {

    private static final String METADATA_IA_ACCOUNT = "com.android.settings.ia.account";
    private Account mAccount;

    public AccountDetailsSettingController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions restrictionInfo) {
        super(context, preferenceKey, fragmentController, restrictionInfo);
    }

    /** Sets the account that the preferences are being shown for. */
    public void setAccount(Account account) {
        mAccount = account;
    }

    @Override
    @CallSuper
    protected void checkInitialized() {
        if (mAccount == null) {
            throw new IllegalStateException(
                    "AccountDetailsSettingController must be initialized by calling "
                            + "setAccount(Account)");
        }
    }

    @Override
    @CallSuper
    protected void addExtraSettings(Map<Preference, Bundle> preferenceBundleMap) {
        for (Preference setting : preferenceBundleMap.keySet()) {
            if (mAccount != null && !mAccount.type.equals(
                    preferenceBundleMap.get(setting).getString(METADATA_IA_ACCOUNT))) {
                continue;
            }
            getPreference().addPreference(setting);
        }
    }
}
