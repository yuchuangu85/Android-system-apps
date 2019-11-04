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

import static android.content.pm.UserInfo.FLAG_ADMIN;
import static android.content.pm.UserInfo.FLAG_INITIALIZED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.pm.UserInfo;

import androidx.lifecycle.Lifecycle;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.common.ButtonPreference;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;
import com.android.car.settings.testutils.ShadowUserIconProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCarUserManagerHelper.class, ShadowUserIconProvider.class})
public class EditUserNameEntryPreferenceControllerTest {

    private static final String TEST_USERNAME = "Test Username";

    private Context mContext;
    private PreferenceControllerTestHelper<EditUserNameEntryPreferenceController>
            mPreferenceControllerHelper;
    private EditUserNameEntryPreferenceController mController;
    private ButtonPreference mButtonPreference;
    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowCarUserManagerHelper.setMockInstance(mCarUserManagerHelper);
        mContext = RuntimeEnvironment.application;
        mPreferenceControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                EditUserNameEntryPreferenceController.class);
        mController = mPreferenceControllerHelper.getController();
        mController.setUserInfo(new UserInfo());
        mButtonPreference = new ButtonPreference(mContext);
        mButtonPreference.setSelectable(false);
        mPreferenceControllerHelper.setPreference(mButtonPreference);
    }

    @After
    public void tearDown() {
        ShadowCarUserManagerHelper.reset();
    }

    @Test
    public void testOnButtonClick_isCurrentUser_launchesEditUsernameFragment() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_USERNAME, /* flags= */ 0);
        when(mCarUserManagerHelper.isCurrentProcessUser(userInfo)).thenReturn(true);
        mController.setUserInfo(userInfo);
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mButtonPreference.performButtonClick();
        verify(mPreferenceControllerHelper.getMockFragmentController()).launchFragment(
                any(EditUsernameFragment.class));
    }

    @Test
    public void testOnButtonClick_isNotCurrentUser_doesNothing() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_USERNAME, /* flags= */ 0);
        when(mCarUserManagerHelper.isCurrentProcessUser(userInfo)).thenReturn(false);
        mController.setUserInfo(userInfo);
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mButtonPreference.performButtonClick();
        verify(mPreferenceControllerHelper.getMockFragmentController(), never()).launchFragment(
                any(EditUsernameFragment.class));
    }

    @Test
    public void testRefreshUi_elementHasTitle() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_USERNAME, /* flags= */ 0);
        when(mCarUserManagerHelper.isCurrentProcessUser(userInfo)).thenReturn(false);
        mController.setUserInfo(userInfo);
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mController.refreshUi();
        assertThat(mButtonPreference.getTitle()).isEqualTo(TEST_USERNAME);
    }

    @Test
    public void testRefreshUi_userNotSetup_setsSummary() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_USERNAME, /* flags= */ 0);
        mController.setUserInfo(userInfo);
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mController.refreshUi();
        assertThat(mButtonPreference.getSummary()).isEqualTo(
                mContext.getString(R.string.user_summary_not_set_up));
    }

    @Test
    public void testRefreshUi_userSetup_noSummary() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_USERNAME, FLAG_INITIALIZED);
        mController.setUserInfo(userInfo);
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mController.refreshUi();
        assertThat(mButtonPreference.getSummary()).isNull();
    }

    @Test
    public void testRefreshUi_isAdmin_notCurrentUser() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_USERNAME,
                FLAG_INITIALIZED | FLAG_ADMIN);
        when(mCarUserManagerHelper.isCurrentProcessUser(userInfo)).thenReturn(false);
        mController.setUserInfo(userInfo);
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mController.refreshUi();
        assertThat(mButtonPreference.getSummary()).isEqualTo(
                mContext.getString(R.string.user_admin));
    }

    @Test
    public void testRefreshUi_isAdmin_currentUser() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_USERNAME,
                FLAG_INITIALIZED | FLAG_ADMIN);
        when(mCarUserManagerHelper.isCurrentProcessUser(userInfo)).thenReturn(true);
        mController.setUserInfo(userInfo);
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mController.refreshUi();
        assertThat(mButtonPreference.getSummary()).isEqualTo(
                mContext.getString(R.string.signed_in_admin_user));
    }
}
