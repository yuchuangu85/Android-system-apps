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

package com.android.car.settings.testutils;

import android.annotation.UserIdInt;
import android.content.pm.UserInfo;
import android.os.UserManager;
import android.util.ArrayMap;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Implements(UserManager.class)
public class ShadowUserManager extends org.robolectric.shadows.ShadowUserManager {
    private static UserManager sInstance;

    private Map<Integer, List<UserInfo>> mProfiles = new ArrayMap<>();

    public static void setInstance(UserManager manager) {
        sInstance = manager;
    }

    @Implementation
    protected UserInfo getUserInfo(@UserIdInt int userHandle) {
        return sInstance.getUserInfo(userHandle);
    }

    @Implementation
    protected int[] getProfileIdsWithDisabled(int userId) {
        if (mProfiles.containsKey(userId)) {
            return mProfiles.get(userId).stream().mapToInt(userInfo -> userInfo.id).toArray();
        }
        return new int[]{};
    }

    @Implementation
    protected List<UserInfo> getProfiles(int userHandle) {
        if (mProfiles.containsKey(userHandle)) {
            return new ArrayList<>(mProfiles.get(userHandle));
        }
        return Collections.emptyList();
    }

    /** Adds a profile to be returned by {@link #getProfiles(int)}. **/
    public void addProfile(
            int userHandle, int profileUserHandle, String profileName, int profileFlags) {
        mProfiles.putIfAbsent(userHandle, new ArrayList<>());
        mProfiles.get(userHandle).add(new UserInfo(profileUserHandle, profileName, profileFlags));
    }

    @Resetter
    public static void reset() {
        sInstance = null;
    }
}
