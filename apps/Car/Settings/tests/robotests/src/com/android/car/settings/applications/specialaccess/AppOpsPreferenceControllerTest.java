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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Looper;
import android.os.RemoteException;

import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceGroup;
import androidx.preference.TwoStatePreference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowAppOpsManager;
import com.android.car.settings.testutils.ShadowApplicationsState;
import com.android.settingslib.applications.ApplicationsState;
import com.android.settingslib.applications.ApplicationsState.AppEntry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Unit test for {@link AppOpsPreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowAppOpsManager.class, ShadowApplicationsState.class})
public class AppOpsPreferenceControllerTest {

    private static final int APP_OP_CODE = AppOpsManager.OP_WRITE_SETTINGS;
    private static final String PERMISSION = Manifest.permission.WRITE_SETTINGS;
    private static final int NEGATIVE_MODE = AppOpsManager.MODE_ERRORED;

    @Mock
    private AppEntryListManager mAppEntryListManager;
    @Mock
    private ApplicationsState mApplicationsState;
    @Captor
    private ArgumentCaptor<AppEntryListManager.Callback> mCallbackCaptor;

    private Context mContext;
    private PreferenceGroup mPreferenceGroup;
    private PreferenceControllerTestHelper<AppOpsPreferenceController> mControllerHelper;
    private AppOpsPreferenceController mController;

    @Before
    public void setUp() throws RemoteException {
        MockitoAnnotations.initMocks(this);
        ShadowApplicationsState.setInstance(mApplicationsState);
        when(mApplicationsState.getBackgroundLooper()).thenReturn(Looper.getMainLooper());

        mContext = RuntimeEnvironment.application;
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                AppOpsPreferenceController.class);
        mController = mControllerHelper.getController();
        mController.init(APP_OP_CODE, PERMISSION, NEGATIVE_MODE);
        mController.mAppEntryListManager = mAppEntryListManager;
        mControllerHelper.setPreference(mPreferenceGroup);
        mControllerHelper.markState(Lifecycle.State.CREATED);
        verify(mAppEntryListManager).init(any(AppStateAppOpsBridge.class), any(),
                mCallbackCaptor.capture());
    }

    @After
    public void tearDown() {
        ShadowApplicationsState.reset();
    }

    @Test
    public void checkInitialized_noOpCode_throwsIllegalStateException() {
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                AppOpsPreferenceController.class);
        mController = mControllerHelper.getController();

        mController.init(AppOpsManager.OP_NONE, PERMISSION, NEGATIVE_MODE);

        assertThrows(IllegalStateException.class,
                () -> mControllerHelper.setPreference(mPreferenceGroup));
    }

    @Test
    public void checkInitialized_noPermission_throwsIllegalStateException() {
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                AppOpsPreferenceController.class);
        mController = mControllerHelper.getController();

        mController.init(APP_OP_CODE, /* permission= */ null, NEGATIVE_MODE);

        assertThrows(IllegalStateException.class,
                () -> mControllerHelper.setPreference(mPreferenceGroup));
    }

    @Test
    public void checkInitialized_noNegativeOpMode_throwsIllegalStateException() {
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                AppOpsPreferenceController.class);
        mController = mControllerHelper.getController();

        mController.init(APP_OP_CODE, PERMISSION, AppOpsManager.MODE_DEFAULT);

        assertThrows(IllegalStateException.class,
                () -> mControllerHelper.setPreference(mPreferenceGroup));
    }

    @Test
    public void onStart_startsListManager() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        verify(mAppEntryListManager).start();
    }

    @Test
    public void onStop_stopsListManager() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_STOP);

        verify(mAppEntryListManager).stop();
    }

    @Test
    public void onDestroy_destroysListManager() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_DESTROY);

        verify(mAppEntryListManager).destroy();
    }

    @Test
    public void onAppEntryListChanged_addsPreferencesForEntries() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        List<AppEntry> entries = Arrays.asList(
                createAppEntry("test.package", /* uid= */ 1, /* isOpPermissible= */ true),
                createAppEntry("another.test.package", /* uid= */ 2, /* isOpPermissible= */ false));

        mCallbackCaptor.getValue().onAppEntryListChanged(entries);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(2);
        assertThat(((TwoStatePreference) mPreferenceGroup.getPreference(0)).isChecked()).isTrue();
        assertThat(((TwoStatePreference) mPreferenceGroup.getPreference(1)).isChecked()).isFalse();
    }

    @Test
    public void onPreferenceChange_checkedState_setsAppOpModeAllowed() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        String packageName = "test.package";
        int uid = 1;
        List<AppEntry> entries = Collections.singletonList(
                createAppEntry(packageName, uid, /* isOpPermissible= */ false));
        mCallbackCaptor.getValue().onAppEntryListChanged(entries);
        TwoStatePreference appPref = (TwoStatePreference) mPreferenceGroup.getPreference(0);

        appPref.performClick();

        assertThat(getShadowAppOpsManager().getMode(APP_OP_CODE, uid, packageName)).isEqualTo(
                AppOpsManager.MODE_ALLOWED);
    }

    @Test
    public void onPreferenceChange_uncheckedState_setsNegativeAppOpMode() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        String packageName = "test.package";
        int uid = 1;
        List<AppEntry> entries = Collections.singletonList(
                createAppEntry(packageName, uid, /* isOpPermissible= */ true));
        mCallbackCaptor.getValue().onAppEntryListChanged(entries);
        TwoStatePreference appPref = (TwoStatePreference) mPreferenceGroup.getPreference(0);

        appPref.performClick();

        assertThat(getShadowAppOpsManager().getMode(APP_OP_CODE, uid, packageName)).isEqualTo(
                NEGATIVE_MODE);
    }

    @Test
    public void onPreferenceChange_updatesEntry() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        List<AppEntry> entries = Collections.singletonList(
                createAppEntry("test.package", /* uid= */ 1, /* isOpPermissible= */ false));
        mCallbackCaptor.getValue().onAppEntryListChanged(entries);
        TwoStatePreference appPref = (TwoStatePreference) mPreferenceGroup.getPreference(0);

        appPref.performClick();

        verify(mAppEntryListManager).forceUpdate(entries.get(0));
    }

    @Test
    public void showSystem_updatesEntries() {
        mControllerHelper.markState(Lifecycle.State.STARTED);

        mController.setShowSystem(true);

        verify(mAppEntryListManager).forceUpdate();
    }

    @Test
    public void appFilter_showingSystemApps_keepsSystemEntries() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        mController.setShowSystem(true);
        ArgumentCaptor<AppEntryListManager.AppFilterProvider> filterCaptor =
                ArgumentCaptor.forClass(AppEntryListManager.AppFilterProvider.class);
        verify(mAppEntryListManager).init(any(), filterCaptor.capture(), any());
        ApplicationsState.AppFilter filter = filterCaptor.getValue().getAppFilter();

        AppEntry systemApp = createAppEntry("test.package", /* uid= */ 1, /* isOpPermissible= */
                false);
        systemApp.info.flags |= ApplicationInfo.FLAG_SYSTEM;

        assertThat(filter.filterApp(systemApp)).isTrue();
    }

    @Test
    public void appFilter_notShowingSystemApps_removesSystemEntries() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        // Not showing system by default.
        ArgumentCaptor<AppEntryListManager.AppFilterProvider> filterCaptor =
                ArgumentCaptor.forClass(AppEntryListManager.AppFilterProvider.class);
        verify(mAppEntryListManager).init(any(), filterCaptor.capture(), any());
        ApplicationsState.AppFilter filter = filterCaptor.getValue().getAppFilter();

        AppEntry systemApp = createAppEntry("test.package", /* uid= */ 1, /* isOpPermissible= */
                false);
        systemApp.info.flags |= ApplicationInfo.FLAG_SYSTEM;

        assertThat(filter.filterApp(systemApp)).isFalse();
    }

    @Test
    public void appFilter_removesNullExtraInfoEntries() {
        mControllerHelper.markState(Lifecycle.State.STARTED);
        ArgumentCaptor<AppEntryListManager.AppFilterProvider> filterCaptor =
                ArgumentCaptor.forClass(AppEntryListManager.AppFilterProvider.class);
        verify(mAppEntryListManager).init(any(), filterCaptor.capture(), any());
        ApplicationsState.AppFilter filter = filterCaptor.getValue().getAppFilter();

        AppEntry appEntry = createAppEntry("test.package", /* uid= */ 1, /* isOpPermissible= */
                false);
        appEntry.extraInfo = null;

        assertThat(filter.filterApp(appEntry)).isFalse();
    }

    private AppEntry createAppEntry(String packageName, int uid, boolean isOpPermissible) {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = packageName;
        info.uid = uid;

        AppStateAppOpsBridge.PermissionState extraInfo = mock(
                AppStateAppOpsBridge.PermissionState.class);
        when(extraInfo.isPermissible()).thenReturn(isOpPermissible);

        AppEntry appEntry = mock(AppEntry.class);
        appEntry.info = info;
        appEntry.label = packageName;
        appEntry.extraInfo = extraInfo;

        return appEntry;
    }

    private ShadowAppOpsManager getShadowAppOpsManager() {
        return Shadow.extract(mContext.getSystemService(Context.APP_OPS_SERVICE));
    }
}
