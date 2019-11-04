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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.userlib.CarUserManagerHelper;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.hardware.usb.IUsbManager;
import android.os.RemoteException;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowApplicationPackageManager;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;
import com.android.car.settings.testutils.ShadowIUsbManager;
import com.android.settingslib.applications.AppUtils;
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

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCarUserManagerHelper.class, ShadowApplicationPackageManager.class,
        ShadowIUsbManager.class})
public class ClearDefaultsPreferenceControllerTest {

    private static final int USER_ID = 10;
    private static final String TEST_PACKAGE_NAME = "com.example.test";
    private static final int TEST_PACKAGE_ID = 1;
    private static final String TEST_LABEL = "Test App";
    private static final String TEST_PATH = "TEST_PATH";
    private static final String TEST_ACTIVITY = "TestActivity";

    private Context mContext;
    private Preference mPreference;
    private PreferenceControllerTestHelper<ClearDefaultsPreferenceController> mControllerHelper;
    private ClearDefaultsPreferenceController mController;
    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;
    @Mock
    private IUsbManager mIUsbManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowCarUserManagerHelper.setMockInstance(mCarUserManagerHelper);
        ShadowIUsbManager.setInstance(mIUsbManager);
        when(mCarUserManagerHelper.getCurrentProcessUserId()).thenReturn(USER_ID);

        mContext = RuntimeEnvironment.application;
        mPreference = new Preference(mContext);
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                ClearDefaultsPreferenceController.class);
        mController = mControllerHelper.getController();

        ApplicationInfo info = new ApplicationInfo();
        info.packageName = TEST_PACKAGE_NAME;
        info.uid = TEST_PACKAGE_ID;
        info.sourceDir = TEST_PATH;
        ApplicationsState.AppEntry entry = new ApplicationsState.AppEntry(mContext, info,
                TEST_PACKAGE_ID);

        mController.setAppEntry(entry);
        mControllerHelper.setPreference(mPreference);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
    }

    @After
    public void tearDown() {
        ShadowCarUserManagerHelper.reset();
        ShadowIUsbManager.reset();
        ShadowApplicationPackageManager.reset();
    }

    @Test
    public void refreshUi_hasPreferredActivities_hasSummary() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
        filter.addCategory(Intent.CATEGORY_HOME);
        ComponentName name = new ComponentName(TEST_PACKAGE_NAME, TEST_ACTIVITY);
        getShadowPackageManager().addPreferredActivity(filter, 0, null, name);

        mController.refreshUi();

        assertThat(mPreference.getSummary().toString()).isNotEmpty();
    }

    @Test
    public void refreshUi_isDefaultBrowser_hasSummary() {
        mContext.getPackageManager().setDefaultBrowserPackageNameAsUser(TEST_PACKAGE_NAME, USER_ID);
        mController.refreshUi();

        assertThat(mPreference.getSummary().toString()).isNotEmpty();
    }

    @Test
    public void refreshUi_hasUsbDefaults_hasSummary() throws RemoteException {
        when(mIUsbManager.hasDefaults(TEST_PACKAGE_NAME, USER_ID)).thenReturn(true);
        mController.refreshUi();

        assertThat(mPreference.getSummary().toString()).isNotEmpty();
    }

    @Test
    public void refreshUi_autoLaunchDisabled_hasNoSummary() {
        mController.refreshUi();

        assertThat(mPreference.getSummary()).isNull();
    }

    @Test
    public void performClick_hasUsbManager_hasPreferredActivities_clearsPreferredActivities() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
        filter.addCategory(Intent.CATEGORY_HOME);
        ComponentName name = new ComponentName(TEST_PACKAGE_NAME, TEST_ACTIVITY);
        getShadowPackageManager().addPreferredActivity(filter, 0, null, name);
        assertThat(AppUtils.hasPreferredActivities(mContext.getPackageManager(),
                TEST_PACKAGE_NAME)).isTrue();
        mController.refreshUi();
        mPreference.performClick();

        assertThat(AppUtils.hasPreferredActivities(mContext.getPackageManager(),
                TEST_PACKAGE_NAME)).isFalse();
    }

    @Test
    public void performClick_hasUsbManager_isDefaultBrowser_clearsDefaultBrowser() {
        mContext.getPackageManager().setDefaultBrowserPackageNameAsUser(TEST_PACKAGE_NAME, USER_ID);
        assertThat(mContext.getPackageManager().getDefaultBrowserPackageNameAsUser(
                USER_ID)).isNotNull();
        mController.refreshUi();
        mPreference.performClick();

        assertThat(
                mContext.getPackageManager().getDefaultBrowserPackageNameAsUser(USER_ID)).isNull();
    }

    @Test
    public void performClick_hasUsbDefaults_clearsUsbDefaults() throws RemoteException {
        when(mIUsbManager.hasDefaults(TEST_PACKAGE_NAME, USER_ID)).thenReturn(true);
        mController.refreshUi();
        mPreference.performClick();

        verify(mIUsbManager).clearDefaults(TEST_PACKAGE_NAME, USER_ID);
    }

    private ShadowApplicationPackageManager getShadowPackageManager() {
        return Shadow.extract(mContext.getPackageManager());
    }
}
