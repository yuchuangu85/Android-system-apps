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

import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.os.storage.VolumeInfo;

import com.android.car.settingslib.loader.AsyncLoader;
import com.android.settingslib.deviceinfo.PrivateStorageInfo;
import com.android.settingslib.deviceinfo.StorageVolumeProvider;

import java.io.IOException;

/**
 * Loads the storage volume information for the device mounted.
 */
public class VolumeSizesLoader extends AsyncLoader<PrivateStorageInfo> {
    private final StorageVolumeProvider mVolumeProvider;
    private final StorageStatsManager mStats;
    private final VolumeInfo mVolume;

    public VolumeSizesLoader(Context context, StorageVolumeProvider volumeProvider,
            StorageStatsManager stats, VolumeInfo volume) {
        super(context);
        mVolumeProvider = volumeProvider;
        mStats = stats;
        mVolume = volume;
    }

    @Override
    public PrivateStorageInfo loadInBackground() {
        PrivateStorageInfo volumeSizes;
        try {
            volumeSizes = getVolumeSize();
        } catch (IOException e) {
            return null;
        }
        return volumeSizes;
    }

    private PrivateStorageInfo getVolumeSize() throws IOException {
        long privateTotalBytes = mVolumeProvider.getTotalBytes(mStats, mVolume);
        long privateFreeBytes = mVolumeProvider.getFreeBytes(mStats, mVolume);
        return new PrivateStorageInfo(privateFreeBytes, privateTotalBytes);
    }
}
