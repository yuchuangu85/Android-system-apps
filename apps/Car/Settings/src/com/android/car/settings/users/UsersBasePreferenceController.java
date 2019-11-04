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
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.pm.UserInfo;

import androidx.annotation.CallSuper;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Shared business logic between {@link UsersListFragment} and {@link ChooseNewAdminFragment}. */
public abstract class UsersBasePreferenceController extends PreferenceController<PreferenceGroup> {

    /** Update screen when users list is updated. */
    private final CarUserManagerHelper.OnUsersUpdateListener mOnUsersUpdateListener =
            this::refreshUi;

    private UsersPreferenceProvider mPreferenceProvider;
    private CarUserManagerHelper mCarUserManagerHelper;
    private List<Preference> mUsersToDisplay = new ArrayList<>();

    public UsersBasePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mCarUserManagerHelper = new CarUserManagerHelper(context);
        UsersPreferenceProvider.UserClickListener userClickListener = this::userClicked;
        mPreferenceProvider = new UsersPreferenceProvider(context, mCarUserManagerHelper,
                userClickListener);
    }

    @Override
    protected Class<PreferenceGroup> getPreferenceType() {
        return PreferenceGroup.class;
    }

    /**
     * Ensure that helper is set by the time onCreate is called. Register a listener to refresh
     * screen on updates.
     */
    @Override
    @CallSuper
    protected void onCreateInternal() {
        mCarUserManagerHelper.registerOnUsersUpdateListener(mOnUsersUpdateListener);
    }

    /** Unregister listener to refresh screen on updates. */
    @Override
    @CallSuper
    protected void onDestroyInternal() {
        mCarUserManagerHelper.unregisterOnUsersUpdateListener(mOnUsersUpdateListener);
    }

    @Override
    protected void updateState(PreferenceGroup preferenceGroup) {
        List<Preference> newUsers = mPreferenceProvider.createUserList();
        if (userListsAreDifferent(mUsersToDisplay, newUsers)) {
            mUsersToDisplay = newUsers;
            preferenceGroup.removeAll();

            for (Preference preference : mUsersToDisplay) {
                preferenceGroup.addPreference(preference);
            }
        }
    }

    /** Gets the car user manager helper. */
    protected CarUserManagerHelper getCarUserManagerHelper() {
        return mCarUserManagerHelper;
    }

    /** Handles the user click on a preference for a certain user. */
    protected abstract void userClicked(UserInfo userInfo);


    /** Gets the preference provider to set additional arguments if necessary. */
    protected UsersPreferenceProvider getPreferenceProvider() {
        return mPreferenceProvider;
    }

    private boolean userListsAreDifferent(List<Preference> currentList,
            List<Preference> newList) {
        if (currentList.size() != newList.size()) {
            return true;
        }

        for (int i = 0; i < currentList.size(); i++) {
            // Cannot use "compareTo" on preference, since it uses the order attribute to compare.
            if (preferencesAreDifferent(currentList.get(i), newList.get(i))) {
                return true;
            }
        }

        return false;
    }

    private boolean preferencesAreDifferent(Preference lhs, Preference rhs) {
        return !Objects.equals(lhs.getTitle(), rhs.getTitle())
                || !Objects.equals(lhs.getSummary(), rhs.getSummary());
    }
}
