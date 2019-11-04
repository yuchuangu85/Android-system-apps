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

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.text.format.Formatter;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.settingslib.applications.StorageStatsSource;

/**
 * Controller which have the basic logic to determines the storage size details for a particular
 * application.
 */
public abstract class StorageSizeBasePreferenceController extends
        PreferenceController<StorageAppDetailPreference> implements
        AppsStorageStatsManager.Callback {

    private StorageStatsSource.AppStorageStats mAppStorageStats;
    private AppsStorageStatsManager mAppsStorageStatsManager;
    private Context mContext;
    private boolean mDataCleared = false;
    private boolean mCachedCleared = false;

    public StorageSizeBasePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mContext = context;
    }

    @Override
    protected Class<StorageAppDetailPreference> getPreferenceType() {
        return StorageAppDetailPreference.class;
    }

    /**
     * Calculates the storage size for a application.
     *
     * @return size value in bytes.
     */
    protected abstract long getSize();

    @Override
    protected void onCreateInternal() {
        if (mAppsStorageStatsManager == null) {
            return;
        }
        mAppsStorageStatsManager.registerListener(this);
    }

    /**
     * Sets the {@link AppsStorageStatsManager} which will be used to register the controller to the
     * Listener {@link AppsStorageStatsManager.Callback}.
     */
    public void setAppsStorageStatsManager(AppsStorageStatsManager appsStorageStatsManager) {
        mAppsStorageStatsManager = appsStorageStatsManager;
    }

    @Override
    protected void updateState(StorageAppDetailPreference preference) {
        if (mAppStorageStats == null) {
            return;
        }
        preference.setDetailText(getSizeStr(getSize()));
    }

    /**
     * Sets the {@link StorageStatsSource.AppStorageStats} for a particular application.
     */
    public void setAppStorageStats(StorageStatsSource.AppStorageStats appStorageStats) {
        mAppStorageStats = appStorageStats;
    }

    /**
     * Gets the {@link StorageStatsSource.AppStorageStats} for a particular application.
     */
    public StorageStatsSource.AppStorageStats getAppStorageStats() {
        return mAppStorageStats;
    }

    boolean isCachedCleared() {
        return mCachedCleared;
    }

    boolean isDataCleared() {
        return mDataCleared;
    }

    private String getSizeStr(long size) {
        return Formatter.formatFileSize(mContext, size);
    }

    @Override
    public void onDataLoaded(StorageStatsSource.AppStorageStats data, boolean cacheCleared,
            boolean dataCleared) {
        //  Sets if user have cleared the cache and should zero the cache bytes.
        //  When the cache is cleared, the cache directories are recreated. These directories have
        //  some size, but are empty. We zero this out to best match user expectations.
        mCachedCleared = cacheCleared;

        //  Sets if user have cleared data and should zero the data bytes.
        //  When the data is cleared, the directory are recreated. Directories have some size,
        //  but are empty. We zero this out to best match user expectations.
        mDataCleared = dataCleared;
        refreshUi();
    }
}
