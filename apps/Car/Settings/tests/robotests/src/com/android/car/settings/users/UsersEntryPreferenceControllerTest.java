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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.Intent;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Unit test for {@link UsersEntryPreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCarUserManagerHelper.class})
public class UsersEntryPreferenceControllerTest {

    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;
    private Preference mPreference;
    private PreferenceControllerTestHelper<UsersEntryPreferenceController> mControllerHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowCarUserManagerHelper.setMockInstance(mCarUserManagerHelper);

        Context context = RuntimeEnvironment.application;
        mPreference = new Preference(context);
        mControllerHelper = new PreferenceControllerTestHelper<>(context,
                UsersEntryPreferenceController.class, mPreference);
        mControllerHelper.markState(Lifecycle.State.STARTED);
    }

    @After
    public void tearDown() {
        ShadowCarUserManagerHelper.reset();
    }

    @Test
    public void preferenceClicked_adminUser_handled() {
        when(mCarUserManagerHelper.isCurrentProcessAdminUser()).thenReturn(true);

        assertThat(
                mPreference.getOnPreferenceClickListener().onPreferenceClick(mPreference)).isTrue();
    }

    @Test
    public void preferenceClicked_adminUser_launchesUsersListFragment() {
        when(mCarUserManagerHelper.isCurrentProcessAdminUser()).thenReturn(true);

        mPreference.performClick();

        verify(mControllerHelper.getMockFragmentController()).launchFragment(
                any(UsersListFragment.class));
    }

    @Test
    public void preferenceClicked_nonAdminUser_handled() {
        when(mCarUserManagerHelper.isCurrentProcessAdminUser()).thenReturn(false);

        assertThat(
                mPreference.getOnPreferenceClickListener().onPreferenceClick(mPreference)).isTrue();
    }

    @Test
    public void preferenceClicked_nonAdminUser_launchesUserDetailsFragment() {
        int userId = 1234;
        when(mCarUserManagerHelper.getCurrentProcessUserId()).thenReturn(userId);
        when(mCarUserManagerHelper.isCurrentProcessAdminUser()).thenReturn(false);

        mPreference.performClick();

        ArgumentCaptor<UserDetailsFragment> fragmentCaptor = ArgumentCaptor.forClass(
                UserDetailsFragment.class);
        verify(mControllerHelper.getMockFragmentController()).launchFragment(
                fragmentCaptor.capture());
        UserDetailsFragment launchedFragment = fragmentCaptor.getValue();
        assertThat(launchedFragment.getArguments()).isNotNull();
        assertThat(launchedFragment.getArguments().getInt(Intent.EXTRA_USER_ID)).isEqualTo(userId);
    }
}
