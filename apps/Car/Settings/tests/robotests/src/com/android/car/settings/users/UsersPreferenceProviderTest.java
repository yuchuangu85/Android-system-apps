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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.pm.UserInfo;

import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;
import java.util.List;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class UsersPreferenceProviderTest {

    private static final String TEST_CURRENT_USER_NAME = "Current User";
    private static final String TEST_OTHER_USER_1_NAME = "User 1";
    private static final String TEST_OTHER_USER_2_NAME = "User 2";
    private static final String TEST_GUEST_USER_1_NAME = "Guest 1";
    private static final String TEST_GUEST_USER_2_NAME = "Guest 2";

    private static final UserInfo TEST_CURRENT_USER = new UserInfo(/* id= */ 14,
            TEST_CURRENT_USER_NAME, /* flags= */ 0);
    private static final UserInfo TEST_OTHER_USER_1 = new UserInfo(/* id= */ 10,
            TEST_OTHER_USER_1_NAME, /* flags= */ 0);
    private static final UserInfo TEST_OTHER_USER_2 = new UserInfo(/* id= */ 11,
            TEST_OTHER_USER_2_NAME, /* flags= */ 0);
    private static final UserInfo TEST_GUEST_USER_1 = new UserInfo(/* id= */ 12,
            TEST_GUEST_USER_1_NAME, /* flags= */ UserInfo.FLAG_GUEST);
    private static final UserInfo TEST_GUEST_USER_2 = new UserInfo(/* id= */ 13,
            TEST_GUEST_USER_2_NAME, /* flags= */ UserInfo.FLAG_GUEST);


    private Context mContext;
    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;
    @Mock
    private UsersPreferenceProvider.UserClickListener mUserClickListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;

        List<UserInfo> users = Arrays.asList(TEST_OTHER_USER_1, TEST_GUEST_USER_1,
                TEST_GUEST_USER_2,
                TEST_OTHER_USER_2);

        doReturn(TEST_CURRENT_USER).when(mCarUserManagerHelper).getCurrentProcessUserInfo();
        doReturn(true).when(mCarUserManagerHelper).isCurrentProcessUser(TEST_CURRENT_USER);
        doReturn(users).when(mCarUserManagerHelper).getAllSwitchableUsers();
    }

    @Test
    public void testCreateUserList_firstUserIsCurrentUser() {
        UsersPreferenceProvider provider = createProvider();

        Preference first = provider.createUserList().get(0);
        assertThat(first.getTitle()).isEqualTo(
                mContext.getString(R.string.current_user_name, TEST_CURRENT_USER_NAME));
    }

    @Test
    public void testCreateUserList_repeatedGuestUserNotShown() {
        UsersPreferenceProvider provider = createProvider();

        List<Preference> userList = provider.createUserList();
        assertThat(userList.size()).isEqualTo(4); // 3 real users + guest item.
        assertThat(userList.get(0).getTitle()).isEqualTo(
                mContext.getString(R.string.current_user_name, TEST_CURRENT_USER_NAME));
        assertThat(userList.get(1).getTitle()).isEqualTo(TEST_OTHER_USER_1_NAME);
        assertThat(userList.get(2).getTitle()).isEqualTo(TEST_OTHER_USER_2_NAME);
    }

    @Test
    public void testCreateUserList_guestShownAsSeparateLastElement() {
        UsersPreferenceProvider provider = createProvider();

        List<Preference> userList = provider.createUserList();
        assertThat(userList.get(userList.size() - 1).getTitle()).isEqualTo(
                mContext.getString(R.string.user_guest));
    }

    @Test
    public void testCreateUserList_currentUserNotShown() {
        UsersPreferenceProvider provider = createProvider();
        provider.setIncludeCurrentUser(false);

        List<Preference> userList = provider.createUserList();
        assertThat(userList.size()).isEqualTo(3); // 3 real users + guest item.
        assertThat(userList.get(0).getTitle()).isEqualTo(TEST_OTHER_USER_1_NAME);
        assertThat(userList.get(1).getTitle()).isEqualTo(TEST_OTHER_USER_2_NAME);
        assertThat(userList.get(2).getTitle()).isEqualTo(
                mContext.getString(R.string.user_guest));
    }

    @Test
    public void testCreateUserList_guestNotShown() {
        UsersPreferenceProvider provider = createProvider();
        provider.setIncludeGuest(false);

        List<Preference> userList = provider.createUserList();
        assertThat(userList.size()).isEqualTo(3); // 3 real users.
        assertThat(userList.get(0).getTitle()).isEqualTo(
                mContext.getString(R.string.current_user_name, TEST_CURRENT_USER_NAME));
        assertThat(userList.get(1).getTitle()).isEqualTo(TEST_OTHER_USER_1_NAME);
        assertThat(userList.get(2).getTitle()).isEqualTo(TEST_OTHER_USER_2_NAME);
    }

    @Test
    public void testPerformClick_currentUser_invokesUserClickListener() {
        UsersPreferenceProvider provider = createProvider();

        List<Preference> userList = provider.createUserList();
        userList.get(0).performClick();
        verify(mUserClickListener).onUserClicked(TEST_CURRENT_USER);
    }

    @Test
    public void testPerformClick_otherUser_invokesUserClickListener() {
        UsersPreferenceProvider provider = createProvider();

        List<Preference> userList = provider.createUserList();
        userList.get(1).performClick();
        verify(mUserClickListener).onUserClicked(TEST_OTHER_USER_1);
    }

    @Test
    public void testPerformClick_guestUser_doesntInvokeUserClickListener() {
        UsersPreferenceProvider provider = createProvider();

        List<Preference> userList = provider.createUserList();
        userList.get(userList.size() - 1).performClick();
        verify(mUserClickListener, never()).onUserClicked(any(UserInfo.class));
    }

    private UsersPreferenceProvider createProvider() {
        return new UsersPreferenceProvider(mContext, mCarUserManagerHelper, mUserClickListener);
    }
}
