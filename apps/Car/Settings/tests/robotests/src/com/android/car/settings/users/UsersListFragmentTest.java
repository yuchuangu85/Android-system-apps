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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.UserManager;
import android.widget.Button;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.testutils.BaseTestActivity;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;
import com.android.car.settings.testutils.ShadowUserIconProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;

/**
 * Tests for UserDetailsFragment.
 */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCarUserManagerHelper.class, ShadowUserIconProvider.class})
public class UsersListFragmentTest {

    private Context mContext;
    private BaseTestActivity mTestActivity;
    private UsersListFragment mFragment;
    private Button mActionButton;
    private ConfirmationDialogFragment mDialog;

    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;
    @Mock
    private UserManager mUserManager;


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowCarUserManagerHelper.setMockInstance(mCarUserManagerHelper);
        mContext = RuntimeEnvironment.application;
        mTestActivity = Robolectric.setupActivity(BaseTestActivity.class);
    }

    @After
    public void tearDown() {
        ShadowCarUserManagerHelper.reset();
    }

    /* Test that onCreateNewUserConfirmed invokes a creation of a new non-admin. */
    @Test
    public void testOnCreateNewUserConfirmedInvokesCreateNewNonAdminUser() {
        createUsersListFragment();
        mFragment.mConfirmListener.onConfirm(/* arguments= */ null);
        Robolectric.flushBackgroundThreadScheduler();
        verify(mCarUserManagerHelper)
                .createNewNonAdminUser(mContext.getString(R.string.user_new_user_name));
    }

    /* Test that if we're in demo user, click on the button starts exit out of the retail mode. */
    @Test
    public void testCallOnClick_demoUser_exitRetailMode() {
        doReturn(true).when(mCarUserManagerHelper).isCurrentProcessDemoUser();
        createUsersListFragment();
        mActionButton.callOnClick();
        assertThat(isDialogShown(ConfirmExitRetailModeDialog.DIALOG_TAG)).isTrue();
    }

    /* Test that if the max num of users is reached, click on the button informs user of that. */
    @Test
    public void testCallOnClick_userLimitReached_showErrorDialog() {
        doReturn(5).when(mCarUserManagerHelper).getMaxSupportedRealUsers();
        doReturn(true).when(mCarUserManagerHelper).isUserLimitReached();
        createUsersListFragment();

        mActionButton.callOnClick();
        assertThat(isDialogShown(MaxUsersLimitReachedDialog.DIALOG_TAG)).isTrue();
    }

    /* Test that if user can add other users, click on the button creates a dialog to confirm. */
    @Test
    public void testCallOnClick_showAddUserDialog() {
        doReturn(true).when(mCarUserManagerHelper).canCurrentProcessAddUsers();
        createUsersListFragment();

        mActionButton.callOnClick();
        assertThat(isDialogShown(ConfirmationDialogFragment.TAG)).isTrue();
    }

    private void createUsersListFragment() {
        UserInfo testUser = new UserInfo();
        mFragment = new UsersListFragment();
        doReturn(testUser).when(mCarUserManagerHelper).getCurrentProcessUserInfo();
        doReturn(testUser).when(mUserManager).getUserInfo(anyInt());
        doReturn(new ArrayList<UserInfo>()).when(mCarUserManagerHelper).getAllSwitchableUsers();
        doReturn(null).when(mCarUserManagerHelper).createNewNonAdminUser(any());
        mTestActivity.launchFragment(mFragment);
        refreshButtons();
    }

    private void refreshButtons() {
        mActionButton = (Button) mTestActivity.findViewById(R.id.action_button1);
    }

    private boolean isDialogShown(String tag) {
        return mTestActivity.getSupportFragmentManager().findFragmentByTag(tag) != null;
    }
}
