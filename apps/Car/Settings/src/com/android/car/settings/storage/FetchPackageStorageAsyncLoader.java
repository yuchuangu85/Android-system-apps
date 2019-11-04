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
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.UserHandle;
import android.util.Log;

import com.android.car.settingslib.loader.AsyncLoader;
import com.android.internal.util.Preconditions;
import com.android.settingslib.applications.StorageStatsSource;
import com.android.settingslib.applications.StorageStatsSource.AppStorageStats;

import java.io.IOException;

/**
 * Fetches the storage stats using the {@link StorageStatsSource} for a given package and user
 * tuple.
 *
 * <p>Class is taken from {@link com.android.settings.applications.FetchPackageStorageAsyncLoader}
 */
public class FetchPackageStorageAsyncLoader extends AsyncLoader<AppStorageStats> {
    private static final String TAG = "FetchPackageStorage";
    private final StorageStatsSource mSource;
    private final ApplicationInfo mInfo;
    private final UserHandle mUser;

    public FetchPackageStorageAsyncLoader(Context context, @NonNull StorageStatsSource source,
            @NonNull ApplicationInfo info, @NonNull UserHandle user) {
        super(context);
        mSource = Preconditions.checkNotNull(source);
        mInfo = info;
        mUser = user;
    }

    @Override
    public AppStorageStats loadInBackground() {
        AppStorageStats result = null;
        try {
            result = mSource.getStatsForPackage(mInfo.volumeUuid, mInfo.packageName, mUser);
        } catch (NameNotFoundException | IOException e) {
            Log.w(TAG, "Package may have been removed during query, failing gracefully", e);
        }
        return result;
    }
}
