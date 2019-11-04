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

import android.app.AlertDialog;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.net.TrafficStats;
import android.os.Build;
import android.util.SparseArray;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.ProgressBarPreference;

/**
 * Controller which determines the storage for system category in the storage preference screen.
 */
public class StorageSystemCategoryPreferenceController extends
        StorageUsageBasePreferenceController {

    public StorageSystemCategoryPreferenceController(Context context,
            String preferenceKey, FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected long calculateCategoryUsage(
            SparseArray<StorageAsyncLoader.AppsStorageResult> result, long usedSizeBytes) {
        long attributedSize = 0;
        for (int i = 0; i < result.size(); i++) {
            StorageAsyncLoader.AppsStorageResult otherData = result.valueAt(i);

            attributedSize += otherData.getGamesSize()
                    + otherData.getMusicAppsSize()
                    + otherData.getVideoAppsSize()
                    + otherData.getPhotosAppsSize()
                    + otherData.getOtherAppsSize();

            attributedSize += otherData.getExternalStats().totalBytes
                    - otherData.getExternalStats().appBytes;
        }
        return Math.max(TrafficStats.GB_IN_BYTES, usedSizeBytes - attributedSize);
    }

    @Override
    protected boolean handlePreferenceClicked(ProgressBarPreference preference) {
        AlertDialog alertDialog = new AlertDialog.Builder(getContext())
                .setMessage(getContext().getString(R.string.storage_detail_dialog_system,
                        Build.VERSION.RELEASE))
                .setPositiveButton(android.R.string.ok, null)
                .create();
        alertDialog.show();
        return true;
    }
}
