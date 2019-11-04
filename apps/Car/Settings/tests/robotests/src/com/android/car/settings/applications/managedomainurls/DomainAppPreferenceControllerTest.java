/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car.settings.applications.managedomainurls;

import static android.content.pm.UserInfo.FLAG_ADMIN;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.UserManager;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowApplicationsState;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;
import com.android.car.settings.testutils.ShadowIconDrawableFactory;
import com.android.car.settings.testutils.ShadowUserManager;
import com.android.settingslib.applications.ApplicationsState;
import com.android.settingslib.core.lifecycle.Lifecycle;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

import java.util.ArrayList;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowUserManager.class, ShadowCarUserManagerHelper.class,
        ShadowIconDrawableFactory.class, ShadowApplicationsState.class})
public class DomainAppPreferenceControllerTest {

    private static final int USER_ID = 10;
    private static final String TEST_PACKAGE_NAME = "com.android.test.package";
    private static final int TEST_PACKAGE_ID = 1;
    private static final String TEST_LABEL = "Test App";
    private static final String TEST_PATH = "TEST_PATH";

    private Context mContext;
    private PreferenceGroup mPreferenceGroup;
    private PreferenceControllerTestHelper<DomainAppPreferenceController> mControllerHelper;
    private DomainAppPreferenceController mController;
    private Lifecycle mLifecycle;
    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;
    @Mock
    private ApplicationsState mApplicationsState;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowApplicationsState.setInstance(mApplicationsState);
        ShadowCarUserManagerHelper.setMockInstance(mCarUserManagerHelper);
        when(mCarUserManagerHelper.getCurrentProcessUserId()).thenReturn(USER_ID);

        mContext = RuntimeEnvironment.application;
        getShadowUserManager().addProfile(USER_ID, USER_ID, "Test Name", /* profileFlags= */
                FLAG_ADMIN);

        when(mApplicationsState.newSession(any(), any())).thenReturn(
                mock(ApplicationsState.Session.class));

        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                DomainAppPreferenceController.class);
        mController = mControllerHelper.getController();

        LifecycleOwner lifecycleOwner = () -> mLifecycle;
        mLifecycle = new Lifecycle(lifecycleOwner);
        mController.setLifecycle(mLifecycle);

        mControllerHelper.setPreference(mPreferenceGroup);
    }

    @After
    public void tearDown() {
        ShadowApplicationsState.reset();
        ShadowCarUserManagerHelper.reset();
        ShadowUserManager.reset();
    }

    @Test
    public void checkInitialized_noLifecycle_throwsError() {
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                DomainAppPreferenceController.class);

        assertThrows(IllegalStateException.class,
                () -> mControllerHelper.setPreference(mPreferenceGroup));
    }

    @Test
    public void onRebuildComplete_sessionLoadsValues_preferenceGroupHasValues() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        ArrayList<ApplicationsState.AppEntry> apps = new ArrayList<>();
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = TEST_PACKAGE_NAME;
        info.uid = TEST_PACKAGE_ID;
        info.sourceDir = TEST_PATH;
        ApplicationsState.AppEntry entry = new ApplicationsState.AppEntry(mContext, info,
                TEST_PACKAGE_ID);
        entry.label = TEST_LABEL;
        apps.add(entry);
        mController.mApplicationStateCallbacks.onRebuildComplete(apps);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
    }

    @Test
    public void performClick_startsApplicationLaunchSettingsFragmentWithPackageName() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        ArrayList<ApplicationsState.AppEntry> apps = new ArrayList<>();
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = TEST_PACKAGE_NAME;
        info.uid = TEST_PACKAGE_ID;
        info.sourceDir = TEST_PATH;
        ApplicationsState.AppEntry entry = new ApplicationsState.AppEntry(mContext, info,
                TEST_PACKAGE_ID);
        entry.label = TEST_LABEL;
        apps.add(entry);
        mController.mApplicationStateCallbacks.onRebuildComplete(apps);

        Preference preference = mPreferenceGroup.getPreference(0);
        preference.performClick();

        ArgumentCaptor<ApplicationLaunchSettingsFragment> captor = ArgumentCaptor.forClass(
                ApplicationLaunchSettingsFragment.class);
        verify(mControllerHelper.getMockFragmentController()).launchFragment(captor.capture());

        String pkgName = captor.getValue().getArguments().getString(
                ApplicationLaunchSettingsFragment.ARG_PACKAGE_NAME);
        assertThat(pkgName).isEqualTo(TEST_PACKAGE_NAME);
    }

    private ShadowUserManager getShadowUserManager() {
        return Shadow.extract(UserManager.get(mContext));
    }
}
