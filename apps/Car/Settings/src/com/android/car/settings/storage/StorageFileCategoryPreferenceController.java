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
import android.content.Intent;
import android.os.UserHandle;
import android.os.storage.StorageManager;
import android.util.SparseArray;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.ProgressBarPreference;
import com.android.settingslib.deviceinfo.StorageManagerVolumeProvider;
import com.android.settingslib.deviceinfo.StorageVolumeProvider;

/**
 * Controller which determines the storage for file category in the storage preference screen.
 */
public class StorageFileCategoryPreferenceController extends StorageUsageBasePreferenceController {

    private StorageVolumeProvider mStorageVolumeProvider;

    public StorageFileCategoryPreferenceController(Context context,
            String preferenceKey, FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        StorageManager sm = context.getSystemService(StorageManager.class);
        mStorageVolumeProvider = new StorageManagerVolumeProvider(sm);
    }

    @Override
    protected long calculateCategoryUsage(SparseArray<StorageAsyncLoader.AppsStorageResult> result,
            long usedSizeBytes) {
        StorageAsyncLoader.AppsStorageResult data = result.get(
                getCarUserManagerHelper().getCurrentProcessUserId());
        return data.getExternalStats().totalBytes - data.getExternalStats().audioBytes
                - data.getExternalStats().videoBytes - data.getExternalStats().imageBytes
                - data.getExternalStats().appBytes;
    }

    @Override
    protected boolean handlePreferenceClicked(ProgressBarPreference preference) {
        Intent intent = getFilesIntent();
        intent.putExtra(Intent.EXTRA_USER_ID, getCarUserManagerHelper().getCurrentProcessUserId());
        getContext().startActivityAsUser(intent,
                new UserHandle(getCarUserManagerHelper().getCurrentProcessUserId()));
        return true;
    }

    private Intent getFilesIntent() {
        return mStorageVolumeProvider.findEmulatedForPrivate(getVolumeInfo()).buildBrowseIntent();
    }
}
