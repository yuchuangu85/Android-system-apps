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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowApplicationPackageManager;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;
import com.android.settingslib.applications.ApplicationsState;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

import java.util.Arrays;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowApplicationPackageManager.class, ShadowCarUserManagerHelper.class})
public class DomainUrlsPreferenceControllerTest {

    private static final int USER_ID = 10;
    private static final String TEST_PACKAGE_NAME = "com.example.test";
    private static final int TEST_PACKAGE_ID = 1;
    private static final String TEST_LABEL = "Test App";
    private static final String TEST_PATH = "TEST_PATH";
    private static final String TEST_ACTIVITY = "TestActivity";

    private Context mContext;
    private Preference mPreference;
    private PreferenceControllerTestHelper<DomainUrlsPreferenceController> mControllerHelper;
    private DomainUrlsPreferenceController mController;
    private ApplicationsState.AppEntry mAppEntry;
    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowCarUserManagerHelper.setMockInstance(mCarUserManagerHelper);
        when(mCarUserManagerHelper.getCurrentProcessUserId()).thenReturn(USER_ID);

        mContext = RuntimeEnvironment.application;
        mPreference = new Preference(mContext);
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                DomainUrlsPreferenceController.class);
        mController = mControllerHelper.getController();

        ApplicationInfo info = new ApplicationInfo();
        info.packageName = TEST_PACKAGE_NAME;
        info.uid = TEST_PACKAGE_ID;
        info.sourceDir = TEST_PATH;
        mAppEntry = new ApplicationsState.AppEntry(mContext, info, TEST_PACKAGE_ID);
    }

    @After
    public void tearDown() {
        ShadowCarUserManagerHelper.reset();
        ShadowApplicationPackageManager.reset();
    }

    @Test
    public void refreshUi_isBrowserApp_isDisabled() {
        setupIsBrowserApp(true);

        mController.setAppEntry(mAppEntry);
        mControllerHelper.setPreference(mPreference);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        mController.refreshUi();

        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void refreshUi_isNotBrowserApp_isEnabled() {
        setupIsBrowserApp(false);

        mController.setAppEntry(mAppEntry);
        mControllerHelper.setPreference(mPreference);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        mController.refreshUi();

        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void refreshUi_isBrowserApp_summarySet() {
        setupIsBrowserApp(true);

        mController.setAppEntry(mAppEntry);
        mControllerHelper.setPreference(mPreference);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        mController.refreshUi();

        assertThat(mPreference.getSummary()).isNotNull();
    }

    @Test
    public void refreshUi_isNotBrowserApp_summarySet() {
        setupIsBrowserApp(false);

        mController.setAppEntry(mAppEntry);
        mControllerHelper.setPreference(mPreference);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        mController.refreshUi();

        assertThat(mPreference.getSummary()).isNotNull();
    }

    @Test
    public void performClick_isNotBrowserApp_opensDialog() {
        setupIsBrowserApp(false);

        mController.setAppEntry(mAppEntry);
        mControllerHelper.setPreference(mPreference);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        mPreference.performClick();

        verify(mControllerHelper.getMockFragmentController()).showDialog(
                any(ConfirmationDialogFragment.class), eq(ConfirmationDialogFragment.TAG));
    }

    private void setupIsBrowserApp(boolean isBrowserApp) {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = new ActivityInfo();
        resolveInfo.handleAllWebDataURI = isBrowserApp;
        AppLaunchSettingsBasePreferenceController.sBrowserIntent.setPackage(TEST_PACKAGE_NAME);
        getShadowPackageManager().addResolveInfoForIntent(
                AppLaunchSettingsBasePreferenceController.sBrowserIntent,
                Arrays.asList(resolveInfo));
    }

    private ShadowApplicationPackageManager getShadowPackageManager() {
        return Shadow.extract(mContext.getPackageManager());
    }
}
