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
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.UserHandle;

import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;

import com.android.settingslib.applications.StorageStatsSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Class to manage the callbacks needed to calculate storage stats for an application.
 */
public class AppsStorageStatsManager {

    /**
     * Callback that is called once the AppsStorageStats is loaded.
     */
    public interface Callback {
        /**
         * Called when the data is successfully loaded from {@link AppsStorageStatsResult}. The
         * result can be {@link null} if the package is removed during loading. Also notifies if
         * this callback was initiated when cache or data is cleared or not.
         */
        void onDataLoaded(StorageStatsSource.AppStorageStats data, boolean cacheCleared,
                boolean dataCleared);
    }

    private final Context mContext;
    private ApplicationInfo mInfo;
    private int mUserId;
    private boolean mCacheCleared;
    private boolean mDataCleared;
    private List<Callback> mAppsStorageStatsListeners = new ArrayList<>();

    AppsStorageStatsManager(Context context) {
        mContext = context;
    }

    /**
     * Registers a listener that will be notified once the data is loaded.
     */
    public void registerListener(Callback appsStorageStatsListener) {
        if (!mAppsStorageStatsListeners.contains(appsStorageStatsListener)) {
            mAppsStorageStatsListeners.add(appsStorageStatsListener);
        }
    }

    /**
     * Unregisters the listener.
     */
    public void unregisterListener(Callback appsStorageStatsListener) {
        mAppsStorageStatsListeners.remove(appsStorageStatsListener);
    }

    /**
     * Start calculating the storage stats.
     */
    public void startLoading(LoaderManager loaderManager, ApplicationInfo info, int userId,
            boolean cacheCleared, boolean dataCleared) {
        mInfo = info;
        mUserId = userId;
        mCacheCleared = cacheCleared;
        mDataCleared = dataCleared;
        loaderManager.restartLoader(/* id= */ 1, Bundle.EMPTY, new AppsStorageStatsResult());
    }

    private void onAppsStorageStatsLoaded(StorageStatsSource.AppStorageStats data) {
        for (Callback listener : mAppsStorageStatsListeners) {
            listener.onDataLoaded(data, mCacheCleared, mDataCleared);
        }
    }

    /**
     * Callback to calculate applications storage stats.
     */
    private class AppsStorageStatsResult implements
            LoaderManager.LoaderCallbacks<StorageStatsSource.AppStorageStats> {

        @NonNull
        @Override
        public Loader<StorageStatsSource.AppStorageStats> onCreateLoader(int id,
                @Nullable Bundle args) {
            return new FetchPackageStorageAsyncLoader(
                    mContext, new StorageStatsSource(mContext), mInfo, UserHandle.of(mUserId));
        }

        @Override
        public void onLoadFinished(
                @NonNull Loader<StorageStatsSource.AppStorageStats> loader,
                StorageStatsSource.AppStorageStats data) {
            onAppsStorageStatsLoaded(data);
        }

        @Override
        public void onLoaderReset(
                @NonNull Loader<StorageStatsSource.AppStorageStats> loader) {
        }
    }
}
