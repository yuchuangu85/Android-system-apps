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
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.Bundle;

import androidx.lifecycle.Lifecycle;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.ButtonPreference;
import com.android.car.settings.common.ConfirmationDialogFragment;
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
public class MakeAdminPreferenceControllerTest {

    private static final UserInfo TEST_USER = new UserInfo(/* id= */ 10,
            "Test Username", /* flags= */0);

    private PreferenceControllerTestHelper<MakeAdminPreferenceController>
            mPreferenceControllerHelper;
    private MakeAdminPreferenceController mController;
    private ButtonPreference mButtonPreference;
    private ConfirmationDialogFragment mDialog;

    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowCarUserManagerHelper.setMockInstance(mCarUserManagerHelper);
        Context context = RuntimeEnvironment.application;
        mPreferenceControllerHelper = new PreferenceControllerTestHelper<>(context,
                MakeAdminPreferenceController.class);
        mController = mPreferenceControllerHelper.getController();
        mController.setUserInfo(TEST_USER);
        mButtonPreference = new ButtonPreference(context);
        mButtonPreference.setSelectable(false);
        mPreferenceControllerHelper.setPreference(mButtonPreference);
        mDialog = new ConfirmationDialogFragment.Builder(context).build();
    }

    @After
    public void tearDown() {
        ShadowCarUserManagerHelper.reset();
    }

    @Test
    public void testOnCreate_hasPreviousDialog_dialogListenerSet() {
        when(mPreferenceControllerHelper.getMockFragmentController().findDialogByTag(
                ConfirmationDialogFragment.TAG)).thenReturn(mDialog);
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        assertThat(mDialog.getConfirmListener()).isNotNull();
    }

    @Test
    public void testOnButtonClick_showsDialog() {
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mButtonPreference.performButtonClick();
        verify(mPreferenceControllerHelper.getMockFragmentController()).showDialog(
                any(ConfirmationDialogFragment.class),
                matches(ConfirmationDialogFragment.TAG));
    }

    @Test
    public void testListener_makeUserAdmin() {
        Bundle arguments = new Bundle();
        arguments.putParcelable(UsersDialogProvider.KEY_USER_TO_MAKE_ADMIN, TEST_USER);
        mController.mConfirmListener.onConfirm(arguments);
        verify(mCarUserManagerHelper).grantAdminPermissions(TEST_USER);
    }

    @Test
    public void testListener_goBack() {
        Bundle arguments = new Bundle();
        arguments.putParcelable(UsersDialogProvider.KEY_USER_TO_MAKE_ADMIN, TEST_USER);
        mController.mConfirmListener.onConfirm(arguments);
        verify(mPreferenceControllerHelper.getMockFragmentController()).goBack();
    }
}
