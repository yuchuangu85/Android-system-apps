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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.pm.UserInfo;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;
import com.android.car.settings.testutils.ShadowUserIconProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCarUserManagerHelper.class, ShadowUserIconProvider.class})
public class UsersBasePreferenceControllerTest {

    private static class TestUsersBasePreferenceController extends UsersBasePreferenceController {

        TestUsersBasePreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        protected void userClicked(UserInfo userInfo) {
        }
    }

    private static final UserInfo TEST_CURRENT_USER = new UserInfo(/* id= */ 10,
            "TEST_USER_NAME", /* flags= */ 0);
    private static final UserInfo TEST_OTHER_USER = new UserInfo(/* id= */ 11,
            "TEST_OTHER_NAME", /* flags= */ 0);
    private PreferenceControllerTestHelper<TestUsersBasePreferenceController> mControllerHelper;
    private TestUsersBasePreferenceController mController;
    private PreferenceGroup mPreferenceGroup;
    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.application;
        MockitoAnnotations.initMocks(this);
        ShadowCarUserManagerHelper.setMockInstance(mCarUserManagerHelper);
        mPreferenceGroup = new LogicalPreferenceGroup(context);
        mControllerHelper = new PreferenceControllerTestHelper<>(context,
                TestUsersBasePreferenceController.class, mPreferenceGroup);
        mController = mControllerHelper.getController();
        when(mCarUserManagerHelper.getCurrentProcessUserInfo()).thenReturn(TEST_CURRENT_USER);
        when(mCarUserManagerHelper.isCurrentProcessUser(TEST_CURRENT_USER)).thenReturn(true);
        when(mCarUserManagerHelper.getAllSwitchableUsers()).thenReturn(
                Collections.singletonList(TEST_OTHER_USER));
    }

    @After
    public void tearDown() {
        ShadowCarUserManagerHelper.reset();
    }

    @Test
    public void onCreate_registersOnUsersUpdateListener() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        verify(mCarUserManagerHelper).registerOnUsersUpdateListener(
                any(CarUserManagerHelper.OnUsersUpdateListener.class));
    }

    @Test
    public void onCreate_populatesUsers() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        // Three users. Current user, other user, guest user.
        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(3);
    }

    @Test
    public void onDestroy_unregistersOnUsersUpdateListener() {
        mControllerHelper.markState(Lifecycle.State.STARTED);

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_DESTROY);

        verify(mCarUserManagerHelper).unregisterOnUsersUpdateListener(
                any(CarUserManagerHelper.OnUsersUpdateListener.class));
    }

    @Test
    public void refreshUi_userChange_updatesGroup() {
        mControllerHelper.markState(Lifecycle.State.STARTED);

        // Store the list of previous Preferences.
        List<Preference> currentPreferences = new ArrayList<>();
        for (int i = 0; i < mPreferenceGroup.getPreferenceCount(); i++) {
            currentPreferences.add(mPreferenceGroup.getPreference(i));
        }

        // Mock a change so that other user becomes an admin.
        UserInfo adminOtherUser = new UserInfo(/* id= */ 11, "TEST_OTHER_NAME", FLAG_ADMIN);
        when(mCarUserManagerHelper.getAllSwitchableUsers()).thenReturn(
                Collections.singletonList(adminOtherUser));

        mController.refreshUi();

        List<Preference> newPreferences = new ArrayList<>();
        for (int i = 0; i < mPreferenceGroup.getPreferenceCount(); i++) {
            newPreferences.add(mPreferenceGroup.getPreference(i));
        }

        assertThat(newPreferences).containsNoneIn(currentPreferences);
    }

    @Test
    public void refreshUi_noChange_doesNotUpdateGroup() {
        mControllerHelper.markState(Lifecycle.State.STARTED);

        // Store the list of previous Preferences.
        List<Preference> currentPreferences = new ArrayList<>();
        for (int i = 0; i < mPreferenceGroup.getPreferenceCount(); i++) {
            currentPreferences.add(mPreferenceGroup.getPreference(i));
        }

        mController.refreshUi();

        List<Preference> newPreferences = new ArrayList<>();
        for (int i = 0; i < mPreferenceGroup.getPreferenceCount(); i++) {
            newPreferences.add(mPreferenceGroup.getPreference(i));
        }

        assertThat(newPreferences).containsExactlyElementsIn(currentPreferences);
    }

    @Test
    public void onUsersUpdated_updatesGroup() {
        ArgumentCaptor<CarUserManagerHelper.OnUsersUpdateListener> listenerCaptor =
                ArgumentCaptor.forClass(CarUserManagerHelper.OnUsersUpdateListener.class);
        mControllerHelper.markState(Lifecycle.State.STARTED);
        verify(mCarUserManagerHelper).registerOnUsersUpdateListener(listenerCaptor.capture());

        // Store the list of previous Preferences.
        List<Preference> currentPreferences = new ArrayList<>();
        for (int i = 0; i < mPreferenceGroup.getPreferenceCount(); i++) {
            currentPreferences.add(mPreferenceGroup.getPreference(i));
        }

        // Mock a change so that other user becomes an admin.
        UserInfo adminOtherUser = new UserInfo(/* id= */ 11, "TEST_OTHER_NAME", FLAG_ADMIN);
        when(mCarUserManagerHelper.getAllSwitchableUsers()).thenReturn(
                Collections.singletonList(adminOtherUser));

        listenerCaptor.getValue().onUsersUpdate();

        List<Preference> newPreferences = new ArrayList<>();
        for (int i = 0; i < mPreferenceGroup.getPreferenceCount(); i++) {
            newPreferences.add(mPreferenceGroup.getPreference(i));
        }

        assertThat(newPreferences).containsNoneIn(currentPreferences);
    }
}
