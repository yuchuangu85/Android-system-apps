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

import static org.mockito.Mockito.when;

import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.UserManager;
import android.widget.TextView;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.testutils.BaseTestActivity;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;
import com.android.car.settings.testutils.ShadowUserIconProvider;
import com.android.car.settings.testutils.ShadowUserManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowUserManager.class, ShadowCarUserManagerHelper.class,
        ShadowUserIconProvider.class})
public class UserDetailsFragmentTest {

    private static final String TEST_NAME = "test_name";
    private static final String TEST_UPDATED_NAME = "test_updated_name";
    private static final int TEST_USER_ID = 10;

    private Context mContext;
    private BaseTestActivity mTestActivity;
    private UserDetailsFragment mUserDetailsFragment;
    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;
    @Mock
    private UserManager mUserManager;

    private TextView mTitle;

    @Before
    public void setUpTestActivity() {
        MockitoAnnotations.initMocks(this);
        ShadowCarUserManagerHelper.setMockInstance(mCarUserManagerHelper);
        ShadowUserManager.setInstance(mUserManager);

        mContext = RuntimeEnvironment.application;
        mTestActivity = Robolectric.setupActivity(BaseTestActivity.class);
    }

    @After
    public void tearDown() {
        ShadowCarUserManagerHelper.reset();
        ShadowUserManager.reset();
    }

    @Test
    public void testCarUserManagerHelperUpdateListener_showsCorrectText() {
        UserInfo testUser = new UserInfo(TEST_USER_ID, TEST_NAME, /* flags= */ 0);
        when(mUserManager.getUserInfo(TEST_USER_ID)).thenReturn(testUser);
        createUserDetailsFragment();
        mUserDetailsFragment.mOnUsersUpdateListener.onUsersUpdate();
        assertThat(mTitle.getText()).isEqualTo(
                UserUtils.getUserDisplayName(mContext, mCarUserManagerHelper, testUser));
    }

    @Test
    public void testCarUserManagerHelperUpdateListener_textChangesWithUserUpdate() {
        UserInfo testUser = new UserInfo(TEST_USER_ID, TEST_NAME, /* flags= */ 0);
        when(mUserManager.getUserInfo(TEST_USER_ID)).thenReturn(testUser);

        createUserDetailsFragment();
        mUserDetailsFragment.mOnUsersUpdateListener.onUsersUpdate();
        assertThat(mTitle.getText()).isEqualTo(
                UserUtils.getUserDisplayName(mContext, mCarUserManagerHelper, testUser));

        UserInfo testUserUpdated = new UserInfo(TEST_USER_ID, TEST_UPDATED_NAME, /* flags= */ 0);
        when(mUserManager.getUserInfo(TEST_USER_ID)).thenReturn(testUserUpdated);

        mUserDetailsFragment.mOnUsersUpdateListener.onUsersUpdate();
        assertThat(mTitle.getText()).isEqualTo(
                UserUtils.getUserDisplayName(mContext, mCarUserManagerHelper, testUserUpdated));
    }

    private void createUserDetailsFragment() {
        mUserDetailsFragment = UserDetailsFragment.newInstance(TEST_USER_ID);
        mTestActivity.launchFragment(mUserDetailsFragment);
        mTitle = mTestActivity.findViewById(R.id.title);
    }
}
