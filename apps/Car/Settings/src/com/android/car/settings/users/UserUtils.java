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
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.UserManager;

import com.android.car.settings.R;

/**
 * Util class for providing basic, universally needed user-related methods.
 */
public class UserUtils {
    private UserUtils() {
    }

    /**
     * Fetches the {@link UserInfo} from UserManager system service for the user ID.
     *
     * @param userId ID that corresponds to the returned UserInfo.
     * @return {@link UserInfo} for the user ID.
     */
    public static UserInfo getUserInfo(Context context, int userId) {
        UserManager userManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        return userManager.getUserInfo(userId);
    }

    /**
     * Returns the user name that should be displayed. The caller shouldn't use userInfo.name
     * directly, because the display name is modified for the current process user.
     */
    public static String getUserDisplayName(Context context,
            CarUserManagerHelper carUserManagerHelper, UserInfo userInfo) {
        return carUserManagerHelper.isCurrentProcessUser(userInfo) ? context.getString(
                R.string.current_user_name, userInfo.name) : userInfo.name;
    }

    /**
     * Returns whether or not the current user is an admin and whether the user info they are
     * viewing is of a non-admin.
     */
    public static boolean isAdminViewingNonAdmin(CarUserManagerHelper carUserManagerHelper,
            UserInfo userInfo) {
        return carUserManagerHelper.isCurrentProcessAdminUser() && !userInfo.isAdmin();
    }

}
