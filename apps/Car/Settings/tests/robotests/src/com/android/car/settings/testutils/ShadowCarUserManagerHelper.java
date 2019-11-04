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

import android.car.userlib.CarUserManagerHelper;
import android.car.userlib.CarUserManagerHelper.OnUsersUpdateListener;
import android.content.pm.UserInfo;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shadow for {@link CarUserManagerHelper}
 */
@Implements(CarUserManagerHelper.class)
public class ShadowCarUserManagerHelper {
    // First Map keys from user id to map of restrictions. Second Map keys from restriction id to
    // bool.
    private static Map<Integer, Map<String, Boolean>> sUserRestrictionMap = new HashMap<>();
    private static CarUserManagerHelper sMockInstance;

    public static void setMockInstance(CarUserManagerHelper userManagerHelper) {
        sMockInstance = userManagerHelper;
    }

    @Resetter
    public static void reset() {
        sMockInstance = null;
        sUserRestrictionMap.clear();
    }

    @Implementation
    protected void setUserName(UserInfo user, String name) {
        sMockInstance.setUserName(user, name);
    }

    @Implementation
    protected UserInfo getCurrentProcessUserInfo() {
        return sMockInstance.getCurrentProcessUserInfo();
    }

    @Implementation
    protected UserInfo getCurrentForegroundUserInfo() {
        return sMockInstance.getCurrentForegroundUserInfo();
    }

    @Implementation
    protected int getCurrentProcessUserId() {
        return sMockInstance.getCurrentProcessUserId();
    }

    @Implementation
    protected boolean isCurrentProcessUser(UserInfo userInfo) {
        return sMockInstance.isCurrentProcessUser(userInfo);
    }

    @Implementation
    protected List<UserInfo> getAllSwitchableUsers() {
        return sMockInstance.getAllSwitchableUsers();
    }

    @Implementation
    protected List<UserInfo> getAllUsers() {
        return sMockInstance.getAllUsers();
    }

    @Implementation
    protected List<UserInfo> getAllPersistentUsers() {
        return sMockInstance.getAllPersistentUsers();
    }

    @Implementation
    protected UserInfo createNewNonAdminUser(String userName) {
        return sMockInstance.createNewNonAdminUser(userName);
    }

    @Implementation
    protected void registerOnUsersUpdateListener(OnUsersUpdateListener listener) {
        sMockInstance.registerOnUsersUpdateListener(listener);
    }

    @Implementation
    protected void unregisterOnUsersUpdateListener(OnUsersUpdateListener listener) {
        sMockInstance.unregisterOnUsersUpdateListener(listener);
    }

    @Implementation
    protected boolean isUserLimitReached() {
        return sMockInstance.isUserLimitReached();
    }

    @Implementation
    protected boolean canCurrentProcessModifyAccounts() {
        return sMockInstance.canCurrentProcessModifyAccounts();
    }

    @Implementation
    protected boolean canCurrentProcessAddUsers() {
        return sMockInstance.canCurrentProcessAddUsers();
    }

    @Implementation
    protected int getMaxSupportedRealUsers() {
        return sMockInstance.getMaxSupportedRealUsers();
    }

    @Implementation
    protected boolean canCurrentProcessRemoveUsers() {
        return sMockInstance.canCurrentProcessRemoveUsers();
    }

    @Implementation
    protected boolean canUserBeRemoved(UserInfo userInfo) {
        return sMockInstance.canUserBeRemoved(userInfo);
    }

    @Implementation
    protected void grantAdminPermissions(UserInfo user) {
        sMockInstance.grantAdminPermissions(user);
    }

    @Implementation
    protected boolean isCurrentProcessDemoUser() {
        return sMockInstance.isCurrentProcessDemoUser();
    }

    @Implementation
    protected boolean isCurrentProcessAdminUser() {
        return sMockInstance.isCurrentProcessAdminUser();
    }

    @Implementation
    protected boolean isCurrentProcessGuestUser() {
        return sMockInstance.isCurrentProcessGuestUser();
    }

    @Implementation
    protected boolean isCurrentProcessUserHasRestriction(String restriction) {
        return sMockInstance.isCurrentProcessUserHasRestriction(restriction);
    }

    @Implementation
    protected boolean removeUser(UserInfo userInfo, String guestUserName) {
        return sMockInstance.removeUser(userInfo, guestUserName);
    }

    @Implementation
    protected void setUserRestriction(UserInfo userInfo, String restriction, boolean enable) {
        Map<String, Boolean> permissionsMap = sUserRestrictionMap.getOrDefault(userInfo.id,
                new HashMap<>());
        permissionsMap.put(restriction, enable);
        sUserRestrictionMap.put(userInfo.id, permissionsMap);
    }

    @Implementation
    protected boolean hasUserRestriction(String restriction, UserInfo userInfo) {
        // False by default, if nothing was added to our map,
        if (!sUserRestrictionMap.containsKey(userInfo.id)) {
            return false;
        }
        return sUserRestrictionMap.get(userInfo.id).getOrDefault(restriction, false);
    }
}
