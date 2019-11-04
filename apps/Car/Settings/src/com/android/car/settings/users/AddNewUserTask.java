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

import android.car.userlib.CarUserManagerHelper;
import android.content.pm.UserInfo;
import android.os.AsyncTask;

/**
 * Task to add a new user to the device
 */
public class AddNewUserTask extends AsyncTask<String, Void, UserInfo> {
    private final CarUserManagerHelper mCarUserManagerHelper;
    private final AddNewUserListener mAddNewUserListener;

    public AddNewUserTask(CarUserManagerHelper helper, AddNewUserListener addNewUserListener) {
        mCarUserManagerHelper = helper;
        mAddNewUserListener = addNewUserListener;
    }

    @Override
    protected UserInfo doInBackground(String... userNames) {
        return mCarUserManagerHelper.createNewNonAdminUser(userNames[0]);
    }

    @Override
    protected void onPreExecute() { }

    @Override
    protected void onPostExecute(UserInfo user) {
        if (user != null) {
            mAddNewUserListener.onUserAddedSuccess();
            mCarUserManagerHelper.switchToUser(user);
        } else {
            mAddNewUserListener.onUserAddedFailure();
        }
    }

    /**
     * Interface for getting notified when AddNewUserTask has been completed.
     */
    public interface AddNewUserListener {
        /**
         * Invoked in AddNewUserTask.onPostExecute after the user has been created successfully.
         */
        void onUserAddedSuccess();

        /**
         * Invoked in AddNewUserTask.onPostExecute if new user creation failed.
         */
        void onUserAddedFailure();
    }
}
