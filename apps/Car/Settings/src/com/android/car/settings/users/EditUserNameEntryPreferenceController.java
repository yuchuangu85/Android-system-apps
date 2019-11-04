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

package com.android.car.settings.users;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.graphics.drawable.Drawable;

import com.android.car.settings.R;
import com.android.car.settings.common.ButtonPreference;
import com.android.car.settings.common.FragmentController;

/** Business logic for the preference which opens the EditUserNameFragment. */
public class EditUserNameEntryPreferenceController extends
        UserDetailsBasePreferenceController<ButtonPreference> {

    public EditUserNameEntryPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<ButtonPreference> getPreferenceType() {
        return ButtonPreference.class;
    }

    @Override
    protected void updateState(ButtonPreference preference) {
        preference.setOnButtonClickListener(pref -> {
            getFragmentController().launchFragment(EditUsernameFragment.newInstance(getUserInfo()));
        });

        Drawable icon = new UserIconProvider(getCarUserManagerHelper()).getUserIcon(getUserInfo(),
                getContext());
        preference.setIcon(icon);
        preference.setTitle(UserUtils.getUserDisplayName(getContext(), getCarUserManagerHelper(),
                getUserInfo()));

        if (!getCarUserManagerHelper().isCurrentProcessUser(getUserInfo())) {
            preference.showAction(false);
        }
        preference.setSummary(getSummary());
    }

    private CharSequence getSummary() {
        if (!getUserInfo().isInitialized()) {
            return getContext().getString(R.string.user_summary_not_set_up);
        }
        if (getUserInfo().isAdmin()) {
            return getCarUserManagerHelper().isCurrentProcessUser(getUserInfo())
                    ? getContext().getString(R.string.signed_in_admin_user)
                    : getContext().getString(R.string.user_admin);
        }
        return null;
    }
}
