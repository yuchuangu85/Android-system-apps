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

package com.android.car.settings.security;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.userlib.CarUserManagerHelper;
import android.content.Context;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;
import com.android.car.settings.testutils.ShadowLockPatternUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCarUserManagerHelper.class, ShadowLockPatternUtils.class})
public class NoLockPreferenceControllerTest {

    private static final byte[] TEST_CURRENT_PASSWORD = "test_password".getBytes();
    private static final int TEST_USER = 10;

    private Context mContext;
    private PreferenceControllerTestHelper<NoLockPreferenceController> mPreferenceControllerHelper;
    private NoLockPreferenceController mController;
    private Preference mPreference;
    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowCarUserManagerHelper.setMockInstance(mCarUserManagerHelper);
        mContext = RuntimeEnvironment.application;
        mPreference = new Preference(mContext);
        mPreferenceControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                NoLockPreferenceController.class, mPreference);
        mController = mPreferenceControllerHelper.getController();
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
    }

    @After
    public void tearDown() {
        ShadowCarUserManagerHelper.reset();
        ShadowLockPatternUtils.reset();
    }

    @Test
    public void testHandlePreferenceClicked_returnsTrue() {
        assertThat(mController.handlePreferenceClicked(mPreference)).isTrue();
    }

    @Test
    public void testHandlePreferenceClicked_goesToNextFragment() {
        mPreference.performClick();
        verify(mPreferenceControllerHelper.getMockFragmentController()).showDialog(
                any(ConfirmRemoveScreenLockDialog.class), anyString());
    }

    @Test
    public void testConfirmRemoveScreenLockListener_removesLock() {
        when(mCarUserManagerHelper.getCurrentProcessUserId()).thenReturn(TEST_USER);
        mController.setCurrentPassword(TEST_CURRENT_PASSWORD);
        mController.mRemoveLockListener.onConfirmRemoveScreenLock();
        assertThat(ShadowLockPatternUtils.getClearLockCredential()).isEqualTo(
                TEST_CURRENT_PASSWORD);
        assertThat(ShadowLockPatternUtils.getClearLockUser()).isEqualTo(TEST_USER);
    }

    @Test
    public void testConfirmRemoveScreenLockListener_goesBack() {
        when(mCarUserManagerHelper.getCurrentProcessUserId()).thenReturn(TEST_USER);
        mController.setCurrentPassword(TEST_CURRENT_PASSWORD);
        mController.mRemoveLockListener.onConfirmRemoveScreenLock();
        verify(mPreferenceControllerHelper.getMockFragmentController()).goBack();
    }
}
