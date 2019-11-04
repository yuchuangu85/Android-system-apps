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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.app.usage.StorageStats;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;

import androidx.loader.app.LoaderManager;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.settingslib.applications.StorageStatsSource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;

/** Unit test for {@link AppsStorageStatsManager}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
public class AppsStorageStatsManagerTest {

    private static final int USER_ID = 10;

    private Context mContext;
    private AppsStorageStatsManager mAppsStorageStatsManager;

    @Captor
    private ArgumentCaptor<LoaderManager.LoaderCallbacks<StorageStatsSource.AppStorageStats>>
            mCallbacksArgumentCaptor;

    @Mock
    private AppsStorageStatsManager.Callback mCallback1;

    @Mock
    private AppsStorageStatsManager.Callback mCallback2;

    @Mock
    private LoaderManager mLoaderManager;

    @Mock
    private ApplicationInfo mApplicationInfo;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        mAppsStorageStatsManager = new AppsStorageStatsManager(mContext);
        mAppsStorageStatsManager.startLoading(mLoaderManager, mApplicationInfo, USER_ID, false,
                false);
        verify(mLoaderManager).restartLoader(eq(1), eq(Bundle.EMPTY),
                mCallbacksArgumentCaptor.capture());
    }

    @Test
    public void callback_onLoadFinished_listenerOnDataLoadedCalled() throws Exception {
        mAppsStorageStatsManager.registerListener(mCallback1);
        mAppsStorageStatsManager.registerListener(mCallback2);

        StorageStats stats = new StorageStats();
        StorageStatsSource.AppStorageStats storageStats =
                new StorageStatsSource.AppStorageStatsImpl(stats);

        mCallbacksArgumentCaptor.getValue().onLoadFinished(null, storageStats);

        verify(mCallback1).onDataLoaded(storageStats, false, false);
        verify(mCallback2).onDataLoaded(storageStats, false, false);
    }

    @Test
    public void callback_unregisterListener_onlyOneListenerOnDataLoadedCalled() throws Exception {
        mAppsStorageStatsManager.registerListener(mCallback1);
        mAppsStorageStatsManager.registerListener(mCallback2);
        mAppsStorageStatsManager.unregisterListener(mCallback2);
        StorageStats stats = new StorageStats();
        StorageStatsSource.AppStorageStats storageStats =
                new StorageStatsSource.AppStorageStatsImpl(stats);

        mCallbacksArgumentCaptor.getValue().onLoadFinished(null, storageStats);

        verify(mCallback1).onDataLoaded(storageStats, false, false);
        verify(mCallback2, never()).onDataLoaded(storageStats, false, false);
    }

    @Test
    public void callback_notLoaded_listenerOnDataLoadedCalled() throws Exception {
        mAppsStorageStatsManager.registerListener(mCallback1);
        mAppsStorageStatsManager.registerListener(mCallback2);

        StorageStats stats = new StorageStats();
        StorageStatsSource.AppStorageStats storageStats =
                new StorageStatsSource.AppStorageStatsImpl(stats);

        verify(mCallback1, never()).onDataLoaded(storageStats, false, false);
        verify(mCallback2, never()).onDataLoaded(storageStats, false, false);
    }

    @Test
    public void callback_cachedCleared_listenerOnDataLoadedCalled() throws Exception {
        mAppsStorageStatsManager = new AppsStorageStatsManager(mContext);
        mAppsStorageStatsManager.startLoading(mLoaderManager, mApplicationInfo, USER_ID, true,
                false);

        mAppsStorageStatsManager.registerListener(mCallback1);
        mAppsStorageStatsManager.registerListener(mCallback2);

        StorageStats stats = new StorageStats();
        StorageStatsSource.AppStorageStats storageStats =
                new StorageStatsSource.AppStorageStatsImpl(stats);

        verify(mCallback1, never()).onDataLoaded(storageStats, true, false);
        verify(mCallback2, never()).onDataLoaded(storageStats, true, false);
    }

    @Test
    public void callback_userDataCleared_listenerOnDataLoadedCalled() throws Exception {
        mAppsStorageStatsManager = new AppsStorageStatsManager(mContext);
        mAppsStorageStatsManager.startLoading(mLoaderManager, mApplicationInfo, USER_ID, false,
                true);

        mAppsStorageStatsManager.registerListener(mCallback1);
        mAppsStorageStatsManager.registerListener(mCallback2);

        StorageStats stats = new StorageStats();
        StorageStatsSource.AppStorageStats storageStats =
                new StorageStatsSource.AppStorageStatsImpl(stats);

        verify(mCallback1, never()).onDataLoaded(storageStats, false, true);
        verify(mCallback2, never()).onDataLoaded(storageStats, false, true);
    }
}
