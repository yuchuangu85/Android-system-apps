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

import static android.content.pm.ApplicationInfo.CATEGORY_AUDIO;
import static android.content.pm.ApplicationInfo.CATEGORY_GAME;
import static android.content.pm.ApplicationInfo.CATEGORY_IMAGE;
import static android.content.pm.ApplicationInfo.CATEGORY_VIDEO;

import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.SparseArray;

import com.android.car.settings.common.Logger;
import com.android.car.settingslib.loader.AsyncLoader;
import com.android.settingslib.applications.StorageStatsSource;

import java.io.IOException;
import java.util.List;

/**
 * {@link StorageAsyncLoader} is a Loader which loads categorized app information and external stats
 * for all users.
 *
 * <p>Class is taken from {@link com.android.settings.deviceinfo.storage.StorageAsyncLoader}
 */
public class StorageAsyncLoader
        extends AsyncLoader<SparseArray<StorageAsyncLoader.AppsStorageResult>> {
    private static final Logger LOG = new Logger(StorageAsyncLoader.class);

    private final CarUserManagerHelper mCarUserManagerHelper;
    private final StorageStatsSource mStatsManager;
    private final PackageManager mPackageManager;

    public StorageAsyncLoader(Context context, CarUserManagerHelper carUserManagerHelper,
            StorageStatsSource source) {
        super(context);
        mCarUserManagerHelper = carUserManagerHelper;
        mStatsManager = source;
        mPackageManager = context.getPackageManager();
    }

    @Override
    public SparseArray<AppsStorageResult> loadInBackground() {
        ArraySet<String> seenPackages = new ArraySet<>();
        SparseArray<AppsStorageResult> result = new SparseArray<>();
        List<UserInfo> infos = mCarUserManagerHelper.getAllUsers();
        for (int i = 0, userCount = infos.size(); i < userCount; i++) {
            UserInfo info = infos.get(i);
            result.put(info.id, getStorageResultForUser(info.id, seenPackages));
        }
        return result;
    }

    private AppsStorageResult getStorageResultForUser(int userId, ArraySet<String> seenPackages) {
        LOG.d("Loading apps");
        List<ApplicationInfo> applicationInfos =
                mPackageManager.getInstalledApplicationsAsUser(/* getAllInstalledApplications= */ 0,
                        userId);
        UserHandle myUser = UserHandle.of(userId);
        long gameAppSize = 0;
        long musicAppsSize = 0;
        long videoAppsSize = 0;
        long photosAppsSize = 0;
        long otherAppsSize = 0;
        for (int i = 0, size = applicationInfos.size(); i < size; i++) {
            ApplicationInfo app = applicationInfos.get(i);
            StorageStatsSource.AppStorageStats stats;
            try {
                stats = mStatsManager.getStatsForPackage(/* volumeUuid= */ null, app.packageName,
                        myUser);
            } catch (NameNotFoundException | IOException e) {
                // This may happen if the package was removed during our calculation.
                LOG.w("App unexpectedly not found", e);
                continue;
            }

            long dataSize = stats.getDataBytes();
            long cacheQuota = mStatsManager.getCacheQuotaBytes(/* volumeUuid= */null, app.uid);
            long cacheBytes = stats.getCacheBytes();
            long blamedSize = dataSize;
            // Technically, we could show overages as freeable on the storage settings screen.
            // If the app is using more cache than its quota, we would accidentally subtract the
            // overage from the system size (because it shows up as unused) during our attribution.
            // Thus, we cap the attribution at the quota size.
            if (cacheQuota < cacheBytes) {
                blamedSize = blamedSize - cacheBytes + cacheQuota;
            }

            // This isn't quite right because it slams the first user by user id with the whole code
            // size, but this ensures that we count all apps seen once.
            if (!seenPackages.contains(app.packageName)) {
                blamedSize += stats.getCodeBytes();
                seenPackages.add(app.packageName);
            }

            switch (app.category) {
                case CATEGORY_GAME:
                    gameAppSize += blamedSize;
                    break;
                case CATEGORY_AUDIO:
                    musicAppsSize += blamedSize;
                    break;
                case CATEGORY_VIDEO:
                    videoAppsSize += blamedSize;
                    break;
                case CATEGORY_IMAGE:
                    photosAppsSize += blamedSize;
                    break;
                default:
                    // The deprecated game flag does not set the category.
                    if ((app.flags & ApplicationInfo.FLAG_IS_GAME) != 0) {
                        gameAppSize += blamedSize;
                        break;
                    }
                    otherAppsSize += blamedSize;
                    break;
            }
        }

        AppsStorageResult result = new AppsStorageResult(gameAppSize, musicAppsSize, photosAppsSize,
                videoAppsSize, otherAppsSize);

        LOG.d("Loading external stats");
        try {
            result.mStorageStats = mStatsManager.getExternalStorageStats(null,
                    UserHandle.of(userId));
        } catch (IOException e) {
            LOG.w("External stats not loaded" + e);
        }
        LOG.d("Obtaining result completed");
        return result;
    }

    /**
     * Class to hold the result for different categories for storage.
     */
    public static class AppsStorageResult {
        private final long mGamesSize;
        private final long mMusicAppsSize;
        private final long mPhotosAppsSize;
        private final long mVideoAppsSize;
        private final long mOtherAppsSize;
        private long mCacheSize;
        private StorageStatsSource.ExternalStorageStats mStorageStats;

        AppsStorageResult(long gamesSize, long musicAppsSize, long photosAppsSize,
                long videoAppsSize, long otherAppsSize) {
            mGamesSize = gamesSize;
            mMusicAppsSize = musicAppsSize;
            mPhotosAppsSize = photosAppsSize;
            mVideoAppsSize = videoAppsSize;
            mOtherAppsSize = otherAppsSize;
        }

        /**
         * Returns the size in bytes used by the applications of category {@link CATEGORY_GAME}.
         */
        public long getGamesSize() {
            return mGamesSize;
        }

        /**
         * Returns the size in bytes used by the applications of category {@link CATEGORY_AUDIO}.
         */
        public long getMusicAppsSize() {
            return mMusicAppsSize;
        }

        /**
         * Returns the size in bytes used by the applications of category {@link CATEGORY_IMAGE}.
         */
        public long getPhotosAppsSize() {
            return mPhotosAppsSize;
        }

        /**
         * Returns the size in bytes used by the applications of category {@link CATEGORY_VIDEO}.
         */
        public long getVideoAppsSize() {
            return mVideoAppsSize;
        }

        /**
         * Returns the size in bytes used by the applications not assigned to one of the other
         * categories.
         */
        public long getOtherAppsSize() {
            return mOtherAppsSize;
        }

        /**
         * Returns the cached size in bytes.
         */
        public long getCacheSize() {
            return mCacheSize;
        }

        /**
         * Sets the storage cached size.
         */
        public void setCacheSize(long cacheSize) {
            this.mCacheSize = cacheSize;
        }

        /**
         * Returns the size in bytes for external storage of mounted device.
         */
        public StorageStatsSource.ExternalStorageStats getExternalStats() {
            return mStorageStats;
        }

        /**
         * Sets the size in bytes for the external storage.
         */
        public void setExternalStats(
                StorageStatsSource.ExternalStorageStats externalStats) {
            this.mStorageStats = externalStats;
        }
    }
}
