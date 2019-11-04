/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.car.settings.system;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.pm.UserInfo;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Collections;

/** Unit test for {@link MasterClearOtherUsersPresentPreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCarUserManagerHelper.class})
public class MasterClearOtherUsersPresentPreferenceControllerTest {

    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;
    private Preference mPreference;
    private MasterClearOtherUsersPresentPreferenceController mController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowCarUserManagerHelper.setMockInstance(mCarUserManagerHelper);

        Context context = RuntimeEnvironment.application;
        mPreference = new Preference(context);
        PreferenceControllerTestHelper<MasterClearOtherUsersPresentPreferenceController>
                controllerHelper = new PreferenceControllerTestHelper<>(context,
                MasterClearOtherUsersPresentPreferenceController.class, mPreference);
        controllerHelper.markState(Lifecycle.State.STARTED);
        mController = controllerHelper.getController();
    }

    @After
    public void tearDown() {
        ShadowCarUserManagerHelper.reset();
    }

    @Test
    public void refreshUi_noSwitchableUsers_hidesPreference() {
        when(mCarUserManagerHelper.getAllSwitchableUsers()).thenReturn(Collections.emptyList());

        mController.refreshUi();

        assertThat(mPreference.isVisible()).isFalse();
    }

    @Test
    public void refreshUi_switchableUsers_showsPreference() {
        when(mCarUserManagerHelper.getAllSwitchableUsers()).thenReturn(
                Collections.singletonList(new UserInfo()));

        mController.refreshUi();

        assertThat(mPreference.isVisible()).isTrue();
    }
}
