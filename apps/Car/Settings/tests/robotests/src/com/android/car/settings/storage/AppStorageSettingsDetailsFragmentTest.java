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

package com.android.car.settings.storage;

import static com.android.car.settings.storage.AppStorageSettingsDetailsFragment.EXTRA_PACKAGE_NAME;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.app.usage.StorageStats;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.testutils.FragmentController;
import com.android.car.settings.testutils.ShadowActivityManager;
import com.android.car.settings.testutils.ShadowApplicationPackageManager;
import com.android.car.settings.testutils.ShadowApplicationsState;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;
import com.android.car.settings.testutils.ShadowRestrictedLockUtilsInternal;
import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.applications.ApplicationsState;
import com.android.settingslib.applications.StorageStatsSource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Unit test for {@link AppStorageSettingsDetailsFragment}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowApplicationsState.class, ShadowCarUserManagerHelper.class,
        ShadowRestrictedLockUtilsInternal.class, ShadowApplicationPackageManager.class,
        ShadowActivityManager.class})
public class AppStorageSettingsDetailsFragmentTest {

    private static final String PACKAGE_NAME = "com.google.packageName";
    private static final String SOURCE = "source";
    private static final int UID = 12;
    private static final String LABEL = "label";
    private static final String SIZE_STR = "12.34 MB";
    private static final int TEST_USER_ID = 10;

    private Context mContext;
    private AppStorageSettingsDetailsFragment mFragment;
    private FragmentController<AppStorageSettingsDetailsFragment> mFragmentController;

    @Mock
    private ApplicationsState mApplicationsState;

    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;

    @Mock
    private RestrictedLockUtils.EnforcedAdmin mEnforcedAdmin;

    @Mock
    private PackageManager mPackageManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        mFragment = new AppStorageSettingsDetailsFragment();
        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_PACKAGE_NAME, PACKAGE_NAME);
        mFragment.setArguments(bundle);
        mFragmentController = FragmentController.of(mFragment);

        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.uid = UID;
        appInfo.sourceDir = SOURCE;

        ApplicationsState.AppEntry appEntry = new ApplicationsState.AppEntry(mContext, appInfo,
                1234L);
        appEntry.label = LABEL;
        appEntry.sizeStr = SIZE_STR;
        appEntry.icon = mContext.getDrawable(R.drawable.test_icon);
        appEntry.info.packageName = PACKAGE_NAME;
        when(mApplicationsState.getEntry(eq(PACKAGE_NAME), anyInt())).thenReturn(appEntry);
        // Set user.
        when(mCarUserManagerHelper.getCurrentProcessUserId()).thenReturn(TEST_USER_ID);
        ShadowCarUserManagerHelper.setMockInstance(mCarUserManagerHelper);
        ShadowApplicationsState.setInstance(mApplicationsState);
        mFragmentController.create();
    }

    @After
    public void tearDown() {
        ShadowApplicationsState.reset();
        ShadowCarUserManagerHelper.reset();
        ShadowRestrictedLockUtilsInternal.reset();
        ShadowApplicationPackageManager.reset();
        ShadowActivityManager.reset();
    }

    @Test
    public void onActivityCreated_defaultStatus_shouldShowCacheButtons() {
        mFragment.onActivityCreated(null);

        assertThat(findClearCacheButton(mFragment.requireActivity())).isNotNull();
        assertThat(findClearCacheButton(mFragment.requireActivity()).getVisibility()).isEqualTo(
                View.VISIBLE);
        assertThat(findClearCacheButton(mFragment.requireActivity()).getText()).isEqualTo(
                mContext.getString(R.string.storage_clear_cache_btn_text));

        assertThat(findClearStorageButton(mFragment.requireActivity())).isNotNull();
        assertThat(findClearStorageButton(mFragment.requireActivity()).getVisibility()).isEqualTo(
                View.VISIBLE);
        assertThat(findClearStorageButton(mFragment.requireActivity()).getText()).isEqualTo(
                mContext.getString(R.string.storage_clear_user_data_text));
    }

    @Test
    public void onActivityCreated_defaultStatus_shouldShowClearStorageButtons() {
        mFragment.onActivityCreated(null);

        assertThat(findClearStorageButton(mFragment.requireActivity())).isNotNull();
        assertThat(findClearStorageButton(mFragment.requireActivity()).getVisibility()).isEqualTo(
                View.VISIBLE);
        assertThat(findClearStorageButton(mFragment.requireActivity()).getText()).isEqualTo(
                mContext.getString(R.string.storage_clear_user_data_text));
    }

    @Test
    public void handleClearCacheClick_disallowedBySystem_shouldNotDeleteApplicationCache() {
        ShadowRestrictedLockUtilsInternal.setEnforcedAdmin(mEnforcedAdmin);
        ShadowApplicationPackageManager.setPackageManager(mPackageManager);

        doNothing().when(mPackageManager).deleteApplicationCacheFiles(anyString(), any());

        mFragmentController.resume();
        StorageStats stats = new StorageStats();
        stats.codeBytes = 100;
        stats.dataBytes = 50;
        stats.cacheBytes = 0;
        StorageStatsSource.AppStorageStats storageStats =
                new StorageStatsSource.AppStorageStatsImpl(stats);

        mFragment.onActivityCreated(null);
        mFragment.onDataLoaded(storageStats, false, false);
        findClearCacheButton(mFragment.requireActivity()).performClick();

        verify(mPackageManager, never()).deleteApplicationCacheFiles(anyString(), any());
    }

    @Test
    public void handleClearCacheClick_allowedBySystem_shouldNotDeleteApplicationCache() {
        ShadowRestrictedLockUtilsInternal.setEnforcedAdmin(mEnforcedAdmin);
        ShadowRestrictedLockUtilsInternal.setHasBaseUserRestriction(true);
        ShadowApplicationPackageManager.setPackageManager(mPackageManager);

        doNothing().when(mPackageManager).deleteApplicationCacheFiles(anyString(), any());

        mFragmentController.resume();
        StorageStats stats = new StorageStats();
        stats.codeBytes = 100;
        stats.dataBytes = 50;
        stats.cacheBytes = 10;
        StorageStatsSource.AppStorageStats storageStats =
                new StorageStatsSource.AppStorageStatsImpl(stats);

        mFragment.onActivityCreated(null);
        mFragment.onDataLoaded(storageStats, false, false);
        findClearCacheButton(mFragment.requireActivity()).performClick();

        verify(mPackageManager).deleteApplicationCacheFiles(anyString(), any());
    }

    @Test
    public void handleClearDataClick_disallowedBySystem_shouldNotShowDialogToClear() {
        ShadowRestrictedLockUtilsInternal.setEnforcedAdmin(mEnforcedAdmin);
        ShadowApplicationPackageManager.setPackageManager(mPackageManager);

        StorageStats stats = new StorageStats();
        stats.codeBytes = 100;
        stats.dataBytes = 10;
        stats.cacheBytes = 10;
        StorageStatsSource.AppStorageStats storageStats =
                new StorageStatsSource.AppStorageStatsImpl(stats);

        mFragment.onActivityCreated(null);
        mFragment.onDataLoaded(storageStats, false, false);
        findClearStorageButton(mFragment.requireActivity()).performClick();

        assertThat(mFragment.getFragmentManager().findFragmentByTag(
                AppStorageSettingsDetailsFragment.CONFIRM_CLEAR_STORAGE_DIALOG_TAG)).isNull();
    }

    @Test
    public void handleClearDataClick_allowedBySystem_shouldShowDialogToClear() {
        ShadowRestrictedLockUtilsInternal.setEnforcedAdmin(mEnforcedAdmin);
        ShadowRestrictedLockUtilsInternal.setHasBaseUserRestriction(true);
        ShadowApplicationPackageManager.setPackageManager(mPackageManager);

        mFragmentController.resume();
        StorageStats stats = new StorageStats();
        stats.codeBytes = 100;
        stats.dataBytes = 50;
        stats.cacheBytes = 10;
        StorageStatsSource.AppStorageStats storageStats =
                new StorageStatsSource.AppStorageStatsImpl(stats);

        mFragment.onActivityCreated(null);
        mFragment.onDataLoaded(storageStats, false, false);
        findClearStorageButton(mFragment.requireActivity()).performClick();

        assertThat(mFragment.getFragmentManager()).isNotNull();
    }

    @Test
    public void onDataLoaded_noResult_buttonsShouldBeDisabled() {
        mFragment.onActivityCreated(null);

        mFragment.onDataLoaded(null, false, false);

        assertThat(findClearCacheButton(mFragment.requireActivity()).isEnabled()).isFalse();
        assertThat(findClearStorageButton(mFragment.requireActivity()).isEnabled()).isFalse();
    }

    @Test
    public void onDataLoaded_resultLoaded_cacheButtonsShouldBeEnabled() {
        StorageStats stats = new StorageStats();
        stats.codeBytes = 100;
        stats.dataBytes = 50;
        stats.cacheBytes = 10;
        StorageStatsSource.AppStorageStats storageStats =
                new StorageStatsSource.AppStorageStatsImpl(stats);

        mFragment.onActivityCreated(null);
        mFragment.onDataLoaded(storageStats, false, false);

        assertThat(findClearCacheButton(mFragment.requireActivity()).isEnabled()).isTrue();
    }

    @Test
    public void onDataLoaded_resultLoaded_dataButtonsShouldBeEnabled() {
        StorageStats stats = new StorageStats();
        stats.codeBytes = 100;
        stats.dataBytes = 50;
        stats.cacheBytes = 10;
        StorageStatsSource.AppStorageStats storageStats =
                new StorageStatsSource.AppStorageStatsImpl(stats);

        mFragment.onActivityCreated(null);
        mFragment.onDataLoaded(storageStats, false, false);

        assertThat(findClearStorageButton(mFragment.requireActivity()).isEnabled()).isTrue();
    }

    @Test
    public void updateUiWithSize_resultLoaded_cacheButtonDisabledAndDataButtonsEnabled() {
        StorageStats stats = new StorageStats();
        stats.codeBytes = 100;
        stats.dataBytes = 50;
        stats.cacheBytes = 0;
        StorageStatsSource.AppStorageStats storageStats =
                new StorageStatsSource.AppStorageStatsImpl(stats);

        mFragment.onActivityCreated(null);
        mFragment.onDataLoaded(storageStats, false, false);

        assertThat(findClearCacheButton(mFragment.requireActivity()).isEnabled()).isFalse();
        assertThat(findClearStorageButton(mFragment.requireActivity()).isEnabled()).isTrue();
    }

    @Test
    public void onDataLoaded_resultLoaded_cacheButtonEnabledAndDataButtonDisabled() {
        StorageStats stats = new StorageStats();
        stats.codeBytes = 100;
        stats.dataBytes = 10;
        stats.cacheBytes = 10;
        StorageStatsSource.AppStorageStats storageStats =
                new StorageStatsSource.AppStorageStatsImpl(stats);

        mFragment.onActivityCreated(null);
        mFragment.onDataLoaded(storageStats, false, false);

        assertThat(findClearCacheButton(mFragment.requireActivity()).isEnabled()).isTrue();
        assertThat(findClearStorageButton(mFragment.requireActivity()).isEnabled()).isFalse();
    }

    private Button findClearCacheButton(Activity activity) {
        return activity.findViewById(R.id.action_button2);
    }

    private Button findClearStorageButton(Activity activity) {
        return activity.findViewById(R.id.action_button1);
    }
}
