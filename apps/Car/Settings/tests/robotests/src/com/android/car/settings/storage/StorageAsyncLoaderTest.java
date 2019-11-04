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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import android.app.usage.StorageStats;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.UserInfo;
import android.net.TrafficStats;
import android.os.UserHandle;
import android.util.SparseArray;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.testutils.ShadowApplicationPackageManager;
import com.android.settingslib.applications.StorageStatsSource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

import java.util.ArrayList;
import java.util.List;

/** Unit test for {@link StorageAsyncLoader}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowApplicationPackageManager.class})
public class StorageAsyncLoaderTest {
    private static final int PRIMARY_USER_ID = 0;
    private static final int SECONDARY_USER_ID = 10;
    private static final String PACKAGE_NAME_1 = "com.blah.test";
    private static final String PACKAGE_NAME_2 = "com.blah.test2";
    private static final String DEFAULT_PACKAGE_NAME = "com.android.car.settings";
    private static final long DEFAULT_QUOTA = 64 * TrafficStats.MB_IN_BYTES;

    @Mock
    private StorageStatsSource mSource;

    private Context mContext;
    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;
    private List<ApplicationInfo> mInfo = new ArrayList<>();
    private List<UserInfo> mUsers;

    private StorageAsyncLoader mLoader;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        mInfo = new ArrayList<>();
        mLoader = new StorageAsyncLoader(mContext, mCarUserManagerHelper, mSource);
        UserInfo info = new UserInfo();
        mUsers = new ArrayList<>();
        mUsers.add(info);
        when(mCarUserManagerHelper.getAllUsers()).thenReturn(mUsers);
        when(mSource.getCacheQuotaBytes(any(), anyInt())).thenReturn(DEFAULT_QUOTA);
        // there is always a "com.android.car.settings" package added by default with category
        // otherAppsSize lets remove it first for testing.
        getShadowApplicationManager().removePackage(DEFAULT_PACKAGE_NAME);
    }

    @After
    public void tearDown() {
        ShadowApplicationPackageManager.reset();
    }

    @Test
    public void testLoadingApps() throws Exception {
        addPackage(PACKAGE_NAME_1, 0, 1, 10, ApplicationInfo.CATEGORY_UNDEFINED);
        addPackage(PACKAGE_NAME_2, 0, 100, 1000, ApplicationInfo.CATEGORY_UNDEFINED);

        SparseArray<StorageAsyncLoader.AppsStorageResult> result = mLoader.loadInBackground();

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(PRIMARY_USER_ID).getGamesSize()).isEqualTo(0L);
        assertThat(result.get(PRIMARY_USER_ID).getOtherAppsSize()).isEqualTo(2200L);
    }

    @Test
    public void testGamesAreFiltered() throws Exception {
        addPackage(PACKAGE_NAME_1, 0, 1, 10, ApplicationInfo.CATEGORY_GAME);

        SparseArray<StorageAsyncLoader.AppsStorageResult> result = mLoader.loadInBackground();

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(PRIMARY_USER_ID).getGamesSize()).isEqualTo(11L);
        assertThat(result.get(PRIMARY_USER_ID).getOtherAppsSize()).isEqualTo(0L);
    }

    @Test
    public void testLegacyGamesAreFiltered() throws Exception {
        ApplicationInfo info =
                addPackage(PACKAGE_NAME_1, 0, 1, 10, ApplicationInfo.CATEGORY_UNDEFINED);
        info.flags = ApplicationInfo.FLAG_IS_GAME;

        SparseArray<StorageAsyncLoader.AppsStorageResult> result = mLoader.loadInBackground();

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(PRIMARY_USER_ID).getGamesSize()).isEqualTo(11L);
        assertThat(result.get(PRIMARY_USER_ID).getOtherAppsSize()).isEqualTo(0L);
    }

    @Test
    public void testCacheIsNotIgnored() throws Exception {
        addPackage(PACKAGE_NAME_1, 100, 1, 10, ApplicationInfo.CATEGORY_UNDEFINED);

        SparseArray<StorageAsyncLoader.AppsStorageResult> result = mLoader.loadInBackground();

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(PRIMARY_USER_ID).getOtherAppsSize()).isEqualTo(111L);
    }

    @Test
    public void testMultipleUsers() throws Exception {
        UserInfo info = new UserInfo();
        info.id = SECONDARY_USER_ID;
        mUsers.add(info);
        when(mSource.getExternalStorageStats(any(), eq(UserHandle.SYSTEM)))
                .thenReturn(new StorageStatsSource.ExternalStorageStats(9, 2, 3, 4, 0));
        when(mSource.getExternalStorageStats(any(), eq(new UserHandle(SECONDARY_USER_ID))))
                .thenReturn(new StorageStatsSource.ExternalStorageStats(10, 3, 3, 4, 0));
        SparseArray<StorageAsyncLoader.AppsStorageResult> result = mLoader.loadInBackground();

        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get(PRIMARY_USER_ID).getExternalStats().totalBytes).isEqualTo(9L);
        assertThat(result.get(SECONDARY_USER_ID).getExternalStats().totalBytes).isEqualTo(10L);
    }

    @Test
    public void testUpdatedSystemAppCodeSizeIsCounted() throws Exception {
        ApplicationInfo systemApp =
                addPackage(PACKAGE_NAME_1, 100, 1, 10, ApplicationInfo.CATEGORY_UNDEFINED);
        systemApp.flags = ApplicationInfo.FLAG_SYSTEM & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;

        SparseArray<StorageAsyncLoader.AppsStorageResult> result = mLoader.loadInBackground();

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(PRIMARY_USER_ID).getOtherAppsSize()).isEqualTo(111L);
    }

    @Test
    public void testVideoAppsAreFiltered() throws Exception {
        addPackage(PACKAGE_NAME_1, 0, 1, 10, ApplicationInfo.CATEGORY_VIDEO);

        SparseArray<StorageAsyncLoader.AppsStorageResult> result = mLoader.loadInBackground();

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(PRIMARY_USER_ID).getVideoAppsSize()).isEqualTo(11L);
        assertThat(result.get(PRIMARY_USER_ID).getOtherAppsSize()).isEqualTo(0L);
    }

    @Test
    public void testRemovedPackageDoesNotCrash() throws Exception {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = PACKAGE_NAME_1;
        info.category = ApplicationInfo.CATEGORY_UNDEFINED;
        mInfo.add(info);
        when(mSource.getStatsForPackage(any(), anyString(), any(UserHandle.class)))
                .thenThrow(new NameNotFoundException());

        SparseArray<StorageAsyncLoader.AppsStorageResult> result = mLoader.loadInBackground();

        // Should not crash.
    }

    @Test
    public void testPackageIsNotDoubleCounted() throws Exception {
        UserInfo info = new UserInfo();
        info.id = SECONDARY_USER_ID;
        mUsers.add(info);
        when(mSource.getExternalStorageStats(anyString(), eq(UserHandle.SYSTEM)))
                .thenReturn(new StorageStatsSource.ExternalStorageStats(9, 2, 3, 4, 0));
        when(mSource.getExternalStorageStats(anyString(), eq(new UserHandle(SECONDARY_USER_ID))))
                .thenReturn(new StorageStatsSource.ExternalStorageStats(10, 3, 3, 4, 0));
        addPackage(PACKAGE_NAME_1, 0, 1, 10, ApplicationInfo.CATEGORY_VIDEO);
        ArrayList<ApplicationInfo> secondaryUserApps = new ArrayList<>();
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = PACKAGE_NAME_1;
        appInfo.category = ApplicationInfo.CATEGORY_VIDEO;
        secondaryUserApps.add(appInfo);

        SparseArray<StorageAsyncLoader.AppsStorageResult> result = mLoader.loadInBackground();

        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get(PRIMARY_USER_ID).getVideoAppsSize()).isEqualTo(11L);
        // No code size for the second user.
        assertThat(result.get(SECONDARY_USER_ID).getVideoAppsSize()).isEqualTo(10L);
    }

    @Test
    public void testCacheOveragesAreCountedAsFree() throws Exception {
        addPackage(PACKAGE_NAME_1, DEFAULT_QUOTA + 100, 1, 10, ApplicationInfo.CATEGORY_UNDEFINED);

        SparseArray<StorageAsyncLoader.AppsStorageResult> result = mLoader.loadInBackground();

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(PRIMARY_USER_ID).getOtherAppsSize()).isEqualTo(DEFAULT_QUOTA + 11);
    }

    private ApplicationInfo addPackage(String packageName, long cacheSize, long codeSize,
            long dataSize, int category) throws Exception {
        StorageStats stats = new StorageStats();
        stats.codeBytes = codeSize;
        stats.dataBytes = dataSize + cacheSize;
        stats.cacheBytes = cacheSize;
        StorageStatsSource.AppStorageStats storageStats =
                new StorageStatsSource.AppStorageStatsImpl(stats);

        when(mSource.getStatsForPackage(any(), anyString(), any(UserHandle.class)))
                .thenReturn(storageStats);

        ApplicationInfo info = new ApplicationInfo();
        info.packageName = packageName;
        info.category = category;
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.applicationInfo = info;
        packageInfo.packageName = packageName;
        getShadowApplicationManager().addPackage(packageInfo);
        return info;
    }

    private ShadowApplicationPackageManager getShadowApplicationManager() {
        return Shadow.extract(mContext.getPackageManager());
    }
}
