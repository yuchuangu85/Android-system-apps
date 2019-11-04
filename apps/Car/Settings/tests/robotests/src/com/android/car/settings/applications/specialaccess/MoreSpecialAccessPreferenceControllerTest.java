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

package com.android.car.settings.applications.specialaccess;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.UNSUPPORTED_ON_DEVICE;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowApplicationPackageManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowApplication;

import java.util.Collections;

/** Unit test for {@link MoreSpecialAccessPreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowApplicationPackageManager.class})
public class MoreSpecialAccessPreferenceControllerTest {

    private static final String PACKAGE = "test.package";

    private Context mContext;
    private Preference mPreference;
    private Intent mIntent;
    private ResolveInfo mResolveInfo;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mPreference = new Preference(mContext);
        mIntent = new Intent(Intent.ACTION_MANAGE_SPECIAL_APP_ACCESSES);
        mIntent.setPackage(PACKAGE);

        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.packageName = PACKAGE;
        applicationInfo.name = "TestClass";
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.applicationInfo = applicationInfo;

        mResolveInfo = new ResolveInfo();
        mResolveInfo.activityInfo = activityInfo;
    }

    @After
    public void tearDown() {
        ShadowApplicationPackageManager.reset();
    }

    @Test
    public void getAvailabilityStatus_noPermissionController_returnsUnsupportedOnDevice() {
        PreferenceControllerTestHelper<MoreSpecialAccessPreferenceController> controllerHelper =
                new PreferenceControllerTestHelper<>(mContext,
                        MoreSpecialAccessPreferenceController.class, mPreference);

        assertThat(controllerHelper.getController().getAvailabilityStatus()).isEqualTo(
                UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_noResolvedActivity_returnsUnsupportedOnDevice() {
        getShadowApplicationPackageManager().setPermissionControllerPackageName(PACKAGE);

        PreferenceControllerTestHelper<MoreSpecialAccessPreferenceController> controllerHelper =
                new PreferenceControllerTestHelper<>(mContext,
                        MoreSpecialAccessPreferenceController.class, mPreference);

        assertThat(controllerHelper.getController().getAvailabilityStatus()).isEqualTo(
                UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_resolvedActivity_returnsAvailable() {
        getShadowApplicationPackageManager().setPermissionControllerPackageName(PACKAGE);
        getShadowApplicationPackageManager().setResolveInfosForIntent(mIntent,
                Collections.singletonList(mResolveInfo));

        PreferenceControllerTestHelper<MoreSpecialAccessPreferenceController> controllerHelper =
                new PreferenceControllerTestHelper<>(mContext,
                        MoreSpecialAccessPreferenceController.class, mPreference);

        assertThat(controllerHelper.getController().getAvailabilityStatus()).isEqualTo(
                AVAILABLE);
    }

    @Test
    public void preferenceClicked_startsResolvedActivity() {
        getShadowApplicationPackageManager().setPermissionControllerPackageName(PACKAGE);
        getShadowApplicationPackageManager().setResolveInfosForIntent(mIntent,
                Collections.singletonList(mResolveInfo));

        PreferenceControllerTestHelper<MoreSpecialAccessPreferenceController> controllerHelper =
                new PreferenceControllerTestHelper<>(mContext,
                        MoreSpecialAccessPreferenceController.class, mPreference);
        controllerHelper.markState(Lifecycle.State.STARTED);

        mPreference.performClick();

        Intent started = ShadowApplication.getInstance().getNextStartedActivity();
        assertThat(started.getAction()).isEqualTo(Intent.ACTION_MANAGE_SPECIAL_APP_ACCESSES);
        assertThat(started.getPackage()).isEqualTo(PACKAGE);
    }

    private ShadowApplicationPackageManager getShadowApplicationPackageManager() {
        return Shadow.extract(mContext.getPackageManager());
    }
}
