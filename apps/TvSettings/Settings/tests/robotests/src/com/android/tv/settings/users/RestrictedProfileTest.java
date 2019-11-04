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

package com.android.tv.settings.users;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.os.UserManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class RestrictedProfileTest {

    private final UserInfo mOwnerInfo = new UserInfo(123, "owner", 0);
    private final UserInfo mProfileInfo = new UserInfo(345, "profile", UserInfo.FLAG_RESTRICTED);
    private final UserInfo mSystemInfo = new UserInfo(UserHandle.USER_SYSTEM, "system", 0);

    @Mock private Context mContext;
    @Mock private ActivityManager mActivityManager;
    @Mock private UserManager mUserManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mContext.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(mActivityManager);
        when(mContext.getSystemService(Context.USER_SERVICE)).thenReturn(mUserManager);
        mProfileInfo.restrictedProfileParentId = mOwnerInfo.id;
    }

    @Test
    public void testGetUser_isSelf() {
        setCurrentUser(mProfileInfo);
        assertThat(newRpm().getUser()).isSameAs(mProfileInfo);
    }

    @Test
    public void testGetUser_isChild() {
        setCurrentUser(mOwnerInfo).addOtherUsers(mProfileInfo);
        assertThat(newRpm().getUser()).isSameAs(mProfileInfo);
    }

    @Test
    public void testGetUser_doesNotExist() {
        setCurrentUser(mOwnerInfo);
        assertThat(newRpm().getUser()).isNull();
    }

    @Test
    public void testEnterUser_isOwner_profileExists() {
        setCurrentUser(mOwnerInfo).addOtherUsers(mProfileInfo);
        assertThat(newRpm().enterUser()).isTrue();
        verify(mActivityManager, times(1)).switchUser(eq(mProfileInfo.id));
    }

    @Test
    public void testEnterUser_isOwner_profileDoesNotExist() {
        setCurrentUser(mOwnerInfo);
        assertThat(newRpm().enterUser()).isFalse();
        verify(mActivityManager, never()).switchUser(anyInt());
    }

    @Test
    public void testEnterUser_isProfile() {
        setCurrentUser(mProfileInfo).addOtherUsers(mOwnerInfo);
        assertThat(newRpm().enterUser()).isFalse();
        verify(mActivityManager, never()).switchUser(anyInt());
    }

    @Test
    public void testExitUser_isOwner() {
        setCurrentUser(mOwnerInfo).addOtherUsers(mProfileInfo);
        newRpm().exitUser();
        verify(mActivityManager, never()).switchUser(anyInt());
    }

    @Test
    public void testExitUser_isProfile() {
        setCurrentUser(mProfileInfo).addOtherUsers(mOwnerInfo, mSystemInfo);
        newRpm().exitUser();
        verify(mActivityManager, times(1)).switchUser(eq(mOwnerInfo.id));
    }

    @Test
    public void testExitUser_isProfile_legacyParent() {
        setCurrentUser(mProfileInfo).addOtherUsers(mProfileInfo, mSystemInfo);
        mProfileInfo.restrictedProfileParentId = UserInfo.NO_PROFILE_GROUP_ID;
        newRpm().exitUser();
        verify(mActivityManager, times(1)).switchUser(eq(mSystemInfo.id));
    }

    private RestrictedProfileModel newRpm() {
        return new RestrictedProfileModel(mContext, /* applyRestrictions= */ false);
    }

    private UserSetter setCurrentUser(UserInfo user) {
        when(mContext.getUserId()).thenReturn(user.id);
        return new UserSetter().addOtherUsers(user);
    }

    private class UserSetter {
        private final List<UserInfo> mUsers = new ArrayList<>();

        private UserSetter addOtherUsers(UserInfo... users) {
            for (UserInfo user : users) {
                when(mUserManager.getUserInfo(eq(user.id))).thenReturn(user);
            }
            mUsers.addAll(Arrays.asList(users));
            when(mUserManager.getUsers()).thenReturn(mUsers);
            return this;
        }
    }
}
