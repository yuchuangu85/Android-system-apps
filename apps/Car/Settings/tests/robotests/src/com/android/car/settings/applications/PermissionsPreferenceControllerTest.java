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

package com.android.car.settings.applications;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.content.Context;
import android.content.Intent;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowPermissionControllerManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowPermissionControllerManager.class})
public class PermissionsPreferenceControllerTest {

    private static final String PACKAGE_NAME = "Test Package Name";

    private Context mContext;
    private Preference mPreference;
    private PreferenceControllerTestHelper<PermissionsPreferenceController>
        mPreferenceControllerHelper;
    private PermissionsPreferenceController mController;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;

        mPreferenceControllerHelper = new PreferenceControllerTestHelper<>(mContext,
            PermissionsPreferenceController.class);
        mController = mPreferenceControllerHelper.getController();
        mPreference = new Preference(mContext);
    }

    @Test
    public void testCheckInitialized_noResolveInfo_throwException() {
        assertThrows(IllegalStateException.class,
            () -> mPreferenceControllerHelper.setPreference(mPreference));
    }

    @Test
    public void testHandlePreferenceClicked_navigateToNextActivity() {
        // Setup so the controller knows about the preference.
        mController.setPackageName(PACKAGE_NAME);
        mPreferenceControllerHelper.setPreference(mPreference);
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        assertThat(mController.handlePreferenceClicked(mPreference)).isTrue();

        Intent actual = ShadowApplication.getInstance().getNextStartedActivity();
        assertThat(actual.getAction()).isEqualTo(Intent.ACTION_MANAGE_APP_PERMISSIONS);
        assertThat(actual.getStringExtra(Intent.EXTRA_PACKAGE_NAME)).isEqualTo(PACKAGE_NAME);
    }
}
