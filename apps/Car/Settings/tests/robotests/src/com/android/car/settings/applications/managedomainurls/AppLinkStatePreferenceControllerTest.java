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

import static android.content.pm.PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS;
import static android.content.pm.PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS_ASK;
import static android.content.pm.PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_NEVER;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;

import androidx.lifecycle.Lifecycle;
import androidx.preference.ListPreference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
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
@Config(shadows = {ShadowCarUserManagerHelper.class, ShadowApplicationPackageManager.class})
public class AppLinkStatePreferenceControllerTest {

    private static final int USER_ID = 10;
    private static final String TEST_PACKAGE_NAME = "com.example.test";
    private static final int TEST_PACKAGE_ID = 1;
    private static final String TEST_LABEL = "Test App";
    private static final String TEST_PATH = "TEST_PATH";
    private static final String TEST_ACTIVITY = "TestActivity";

    private Context mContext;
    private ListPreference mPreference;
    private PreferenceControllerTestHelper<AppLinkStatePreferenceController> mControllerHelper;
    private AppLinkStatePreferenceController mController;
    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowCarUserManagerHelper.setMockInstance(mCarUserManagerHelper);
        when(mCarUserManagerHelper.getCurrentProcessUserId()).thenReturn(USER_ID);

        mContext = RuntimeEnvironment.application;
        mPreference = new ListPreference(mContext);
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                AppLinkStatePreferenceController.class);
        mController = mControllerHelper.getController();
    }

    @After
    public void tearDown() {
        ShadowCarUserManagerHelper.reset();
        ShadowApplicationPackageManager.reset();
    }

    @Test
    public void refreshUi_isBrowserApp_isDisabled() {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = TEST_PACKAGE_NAME;
        info.uid = TEST_PACKAGE_ID;
        info.sourceDir = TEST_PATH;
        ApplicationsState.AppEntry entry = new ApplicationsState.AppEntry(mContext, info,
                TEST_PACKAGE_ID);

        setupIsBrowserApp(true);

        mController.setAppEntry(entry);
        mControllerHelper.setPreference(mPreference);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        mController.refreshUi();

        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void refreshUi_isNotBrowserApp_noDomainUrls_isDisabled() {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = TEST_PACKAGE_NAME;
        info.uid = TEST_PACKAGE_ID;
        info.sourceDir = TEST_PATH;
        info.privateFlags = 0;
        ApplicationsState.AppEntry entry = new ApplicationsState.AppEntry(mContext, info,
                TEST_PACKAGE_ID);

        setupIsBrowserApp(false);

        mController.setAppEntry(entry);
        mControllerHelper.setPreference(mPreference);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        mController.refreshUi();

        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void refreshUi_isNotBrowserApp_hasDomainUrls_isEnabled() {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = TEST_PACKAGE_NAME;
        info.uid = TEST_PACKAGE_ID;
        info.sourceDir = TEST_PATH;
        info.privateFlags = ApplicationInfo.PRIVATE_FLAG_HAS_DOMAIN_URLS;
        ApplicationsState.AppEntry entry = new ApplicationsState.AppEntry(mContext, info,
                TEST_PACKAGE_ID);

        setupIsBrowserApp(false);

        mController.setAppEntry(entry);
        mControllerHelper.setPreference(mPreference);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        mController.refreshUi();

        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void refreshUi_isNotBrowserApp_hasDomainUrls_defaultState_entrySetToAsk() {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = TEST_PACKAGE_NAME;
        info.uid = TEST_PACKAGE_ID;
        info.sourceDir = TEST_PATH;
        info.privateFlags = ApplicationInfo.PRIVATE_FLAG_HAS_DOMAIN_URLS;
        ApplicationsState.AppEntry entry = new ApplicationsState.AppEntry(mContext, info,
                TEST_PACKAGE_ID);

        setupIsBrowserApp(false);

        mController.setAppEntry(entry);
        mControllerHelper.setPreference(mPreference);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        mController.refreshUi();

        assertThat(mPreference.getEntry()).isEqualTo(
                mContext.getString(R.string.app_link_open_ask));
    }

    @Test
    public void refreshUi_isNotBrowserApp_hasDomainUrls_askState_entrySetToAsk() {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = TEST_PACKAGE_NAME;
        info.uid = TEST_PACKAGE_ID;
        info.sourceDir = TEST_PATH;
        info.privateFlags = ApplicationInfo.PRIVATE_FLAG_HAS_DOMAIN_URLS;
        ApplicationsState.AppEntry entry = new ApplicationsState.AppEntry(mContext, info,
                TEST_PACKAGE_ID);

        setupIsBrowserApp(false);
        mContext.getPackageManager().updateIntentVerificationStatusAsUser(TEST_PACKAGE_NAME,
                INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS_ASK, USER_ID);

        mController.setAppEntry(entry);
        mControllerHelper.setPreference(mPreference);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        mController.refreshUi();

        assertThat(mPreference.getEntry()).isEqualTo(
                mContext.getString(R.string.app_link_open_ask));
    }

    @Test
    public void refreshUi_isNotBrowserApp_hasDomainUrls_alwaysState_entrySetToAlways() {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = TEST_PACKAGE_NAME;
        info.uid = TEST_PACKAGE_ID;
        info.sourceDir = TEST_PATH;
        info.privateFlags = ApplicationInfo.PRIVATE_FLAG_HAS_DOMAIN_URLS;
        ApplicationsState.AppEntry entry = new ApplicationsState.AppEntry(mContext, info,
                TEST_PACKAGE_ID);

        setupIsBrowserApp(false);
        mContext.getPackageManager().updateIntentVerificationStatusAsUser(TEST_PACKAGE_NAME,
                INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS, USER_ID);

        mController.setAppEntry(entry);
        mControllerHelper.setPreference(mPreference);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        mController.refreshUi();

        assertThat(mPreference.getEntry()).isEqualTo(
                mContext.getString(R.string.app_link_open_always));
    }

    @Test
    public void refreshUi_isNotBrowserApp_hasDomainUrls_neverState_entrySetToNever() {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = TEST_PACKAGE_NAME;
        info.uid = TEST_PACKAGE_ID;
        info.sourceDir = TEST_PATH;
        info.privateFlags = ApplicationInfo.PRIVATE_FLAG_HAS_DOMAIN_URLS;
        ApplicationsState.AppEntry entry = new ApplicationsState.AppEntry(mContext, info,
                TEST_PACKAGE_ID);

        setupIsBrowserApp(false);
        mContext.getPackageManager().updateIntentVerificationStatusAsUser(TEST_PACKAGE_NAME,
                INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_NEVER, USER_ID);

        mController.setAppEntry(entry);
        mControllerHelper.setPreference(mPreference);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        mController.refreshUi();

        assertThat(mPreference.getEntry()).isEqualTo(
                mContext.getString(R.string.app_link_open_never));
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
