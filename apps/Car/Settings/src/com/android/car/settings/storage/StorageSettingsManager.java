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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.usage.StorageStatsManager;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.util.SparseArray;

import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;

import com.android.settingslib.applications.StorageStatsSource;
import com.android.settingslib.deviceinfo.PrivateStorageInfo;
import com.android.settingslib.deviceinfo.StorageManagerVolumeProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * Class to manage all the callbacks needed to calculate the total volume, storage used by each app
 * category and notifying the listeners when the data is loaded.
 */
public class StorageSettingsManager {

    /**
     * Callback that is called once the volume of data is loaded for the mounted device.
     */
    public interface VolumeListener {
        /**
         * Called when the data is successfully loaded from {@link VolumeSizeCallback} and the
         * total and used size for the mounted device is calculated from {@link AppsStorageResult}
         */
        void onDataLoaded(SparseArray<StorageAsyncLoader.AppsStorageResult> result,
                long usedSizeBytes, long totalSizeBytes);
    }

    private static final int STORAGE_JOB_ID = 0;
    private static final int VOLUME_SIZE_JOB_ID = 1;

    private final Context mContext;
    private final VolumeInfo mVolumeInfo;

    private List<VolumeListener> mVolumeListeners = new ArrayList<>();
    private PrivateStorageInfo mPrivateStorageInfo;
    private SparseArray<StorageAsyncLoader.AppsStorageResult> mAppsStorageResultSparseArray;

    StorageSettingsManager(Context context, VolumeInfo volume) {
        mContext = context;
        mVolumeInfo = volume;
    }

    /**
     * Registers a listener that will be notified once the data is loaded.
     */
    public void registerListener(VolumeListener volumeListener) {
        if (!mVolumeListeners.contains(volumeListener)) {
            mVolumeListeners.add(volumeListener);
        }
    }

    /**
     * Unregisters the listener.
     */
    public void unregisterlistener(VolumeListener volumeListener) {
        mVolumeListeners.remove(volumeListener);
    }

    /**
     * Start calculating the storage and volume.
     */
    public void startLoading(LoaderManager loaderManager) {
        loaderManager.restartLoader(STORAGE_JOB_ID, Bundle.EMPTY, new AppsStorageResult());
        loaderManager.restartLoader(VOLUME_SIZE_JOB_ID, Bundle.EMPTY, new VolumeSizeCallback());
    }

    private void onReceivedSizes() {
        if (mAppsStorageResultSparseArray != null && mPrivateStorageInfo != null) {
            long privateUsedBytes = mPrivateStorageInfo.totalBytes - mPrivateStorageInfo.freeBytes;
            for (VolumeListener listener : mVolumeListeners) {
                listener.onDataLoaded(mAppsStorageResultSparseArray, privateUsedBytes,
                        mPrivateStorageInfo.totalBytes);
            }
        }
    }

    /**
     * Callback to get the storage volume information for the device that is mounted.
     */
    private class VolumeSizeCallback
            implements LoaderManager.LoaderCallbacks<PrivateStorageInfo> {

        @Override
        public Loader<PrivateStorageInfo> onCreateLoader(int id, Bundle args) {
            StorageManager sm = mContext.getSystemService(StorageManager.class);
            StorageManagerVolumeProvider smvp = new StorageManagerVolumeProvider(sm);
            StorageStatsManager stats = mContext.getSystemService(StorageStatsManager.class);
            return new VolumeSizesLoader(mContext, smvp, stats, mVolumeInfo);
        }

        @Override
        public void onLoadFinished(
                Loader<PrivateStorageInfo> loader, PrivateStorageInfo privateStorageInfo) {
            if (privateStorageInfo == null) {
                return;
            }
            mPrivateStorageInfo = privateStorageInfo;
            onReceivedSizes();
        }

        @Override
        public void onLoaderReset(Loader<PrivateStorageInfo> loader) {
        }
    }

    /**
     * Callback to calculate how much space each category of applications is using.
     */
    private class AppsStorageResult implements
            LoaderManager.LoaderCallbacks<SparseArray<StorageAsyncLoader.AppsStorageResult>> {

        @NonNull
        @Override
        public Loader<SparseArray<StorageAsyncLoader.AppsStorageResult>> onCreateLoader(int id,
                @Nullable Bundle args) {
            return new StorageAsyncLoader(mContext, new CarUserManagerHelper(mContext),
                    new StorageStatsSource(mContext));
        }

        @Override
        public void onLoadFinished(
                @NonNull Loader<SparseArray<StorageAsyncLoader.AppsStorageResult>> loader,
                SparseArray<StorageAsyncLoader.AppsStorageResult> data) {
            mAppsStorageResultSparseArray = data;
            onReceivedSizes();
        }

        @Override
        public void onLoaderReset(
                @NonNull Loader<SparseArray<StorageAsyncLoader.AppsStorageResult>> loader) {
        }
    }
}
