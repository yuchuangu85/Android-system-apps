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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Bundle;
import android.os.storage.VolumeInfo;
import android.util.SparseArray;

import androidx.loader.app.LoaderManager;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.settingslib.deviceinfo.PrivateStorageInfo;
import com.android.settingslib.deviceinfo.StorageVolumeProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;

/** Unit test for {@link StorageSettingsManager}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
public class StorageSettingsManagerTest {

    private Context mContext;
    private VolumeInfo mVolumeInfo;
    private StorageSettingsManager mStorageSettingsManager;
    @Captor
    private ArgumentCaptor<LoaderManager
            .LoaderCallbacks<SparseArray<StorageAsyncLoader.AppsStorageResult>>> mAppsStorageResult;
    @Captor
    private ArgumentCaptor<LoaderManager.LoaderCallbacks<PrivateStorageInfo>> mVolumeSizeCallback;

    @Mock
    private StorageSettingsManager.VolumeListener mVolumeListener1;

    @Mock
    private StorageSettingsManager.VolumeListener mVolumeListener2;

    @Mock
    private LoaderManager mLoaderManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        mStorageSettingsManager = new StorageSettingsManager(mContext, mVolumeInfo);
        mStorageSettingsManager.startLoading(mLoaderManager);
        verify(mLoaderManager, times(1)).restartLoader(eq(0), eq(Bundle.EMPTY),
                mAppsStorageResult.capture());
        verify(mLoaderManager, times(1)).restartLoader(eq(1), eq(Bundle.EMPTY),
                mVolumeSizeCallback.capture());
    }

    @Test
    public void volumeSizeCallback_onLoadFinished_listenerOnSizeCalculatedCalled()
            throws Exception {
        mStorageSettingsManager.registerListener(mVolumeListener1);
        mStorageSettingsManager.registerListener(mVolumeListener2);

        StorageVolumeProvider storageVolumeProvider = mock(StorageVolumeProvider.class);
        when(storageVolumeProvider.getTotalBytes(any(), any())).thenReturn(10000L);
        when(storageVolumeProvider.getFreeBytes(any(), any())).thenReturn(1000L);

        SparseArray<StorageAsyncLoader.AppsStorageResult> data = new SparseArray<>();
        mAppsStorageResult.getValue().onLoadFinished(null, data);

        PrivateStorageInfo storageInfo = new VolumeSizesLoader(mContext, storageVolumeProvider,
                null, mVolumeInfo).loadInBackground();

        mVolumeSizeCallback.getValue().onLoadFinished(null, storageInfo);

        verify(mVolumeListener1, times(1)).onDataLoaded(data, 9000L, 10000L);
        verify(mVolumeListener2, times(1)).onDataLoaded(data, 9000L, 10000L);
    }

    @Test
    public void appsStorageResult_unregisterListener_onlyOneListenerOnDataLoadedCalled()
            throws Exception {
        mStorageSettingsManager.registerListener(mVolumeListener1);
        mStorageSettingsManager.registerListener(mVolumeListener2);
        mStorageSettingsManager.unregisterlistener(mVolumeListener2);
        SparseArray<StorageAsyncLoader.AppsStorageResult> data = new SparseArray<>();
        mAppsStorageResult.getValue().onLoadFinished(null, data);

        StorageVolumeProvider storageVolumeProvider = mock(StorageVolumeProvider.class);
        when(storageVolumeProvider.getTotalBytes(any(), any())).thenReturn(10000L);
        when(storageVolumeProvider.getFreeBytes(any(), any())).thenReturn(1000L);

        PrivateStorageInfo storageInfo = new VolumeSizesLoader(mContext, storageVolumeProvider,
                null, mVolumeInfo).loadInBackground();

        mVolumeSizeCallback.getValue().onLoadFinished(null, storageInfo);

        verify(mVolumeListener1, times(1)).onDataLoaded(data, 9000L, 10000L);
        verify(mVolumeListener2, never()).onDataLoaded(data, 9000L, 10000L);
    }

    @Test
    public void onReceivedSizes_storageResultNotLoaded_noListenersCalled()
            throws Exception {
        mStorageSettingsManager.registerListener(mVolumeListener1);
        mStorageSettingsManager.registerListener(mVolumeListener2);
        SparseArray<StorageAsyncLoader.AppsStorageResult> data = new SparseArray<>();

        StorageVolumeProvider storageVolumeProvider = mock(StorageVolumeProvider.class);
        when(storageVolumeProvider.getTotalBytes(any(), any())).thenReturn(10000L);
        when(storageVolumeProvider.getFreeBytes(any(), any())).thenReturn(1000L);

        PrivateStorageInfo storageInfo = new VolumeSizesLoader(mContext, storageVolumeProvider,
                null, mVolumeInfo).loadInBackground();
        mVolumeSizeCallback.getValue().onLoadFinished(null, storageInfo);

        verify(mVolumeListener1, never()).onDataLoaded(data, 9000L, 10000L);
        verify(mVolumeListener2, never()).onDataLoaded(data, 9000L, 10000L);
    }

    @Test
    public void onReceivedSizes_volumeSizeNotLoaded_noListenersCalled()
            throws Exception {
        mStorageSettingsManager.registerListener(mVolumeListener1);
        mStorageSettingsManager.registerListener(mVolumeListener2);

        SparseArray<StorageAsyncLoader.AppsStorageResult> data = new SparseArray<>();
        mAppsStorageResult.getValue().onLoadFinished(null, data);

        StorageVolumeProvider storageVolumeProvider = mock(StorageVolumeProvider.class);
        when(storageVolumeProvider.getTotalBytes(any(), any())).thenReturn(10000L);
        when(storageVolumeProvider.getFreeBytes(any(), any())).thenReturn(1000L);

        PrivateStorageInfo storageInfo = new VolumeSizesLoader(mContext, storageVolumeProvider,
                null, mVolumeInfo).loadInBackground();

        verify(mVolumeListener1, never()).onDataLoaded(data, 9000L, 10000L);
        verify(mVolumeListener2, never()).onDataLoaded(data, 9000L, 10000L);
    }
}
