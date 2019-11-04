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
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.res.Resources;
import android.os.storage.VolumeInfo;
import android.util.SparseArray;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.car.settings.common.ProgressBarPreference;

/**
 * Controller which have the basic logic to determines the storage for different categories visible
 * in the storage preference screen.
 */
public abstract class StorageUsageBasePreferenceController extends
        PreferenceController<ProgressBarPreference> implements
        StorageSettingsManager.VolumeListener {

    private static final int PROGRESS_MAX = 100;

    private VolumeInfo mVolumeInfo;
    private CarUserManagerHelper mCarUserManagerHelper;

    public StorageUsageBasePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mCarUserManagerHelper = new CarUserManagerHelper(context);
    }

    @Override
    protected Class<ProgressBarPreference> getPreferenceType() {
        return ProgressBarPreference.class;
    }

    /**
     * Calculates the storage used by the category.
     *
     * @return usage value in bytes.
     */
    protected abstract long calculateCategoryUsage(
            SparseArray<StorageAsyncLoader.AppsStorageResult> result, long usedSizeBytes);

    @Override
    protected void onCreateInternal() {
        getPreference().setSummary(R.string.memory_calculating_size);
        getPreference().setMax(PROGRESS_MAX);
    }

    @Override
    public void onDataLoaded(SparseArray<StorageAsyncLoader.AppsStorageResult> result,
            long usedSizeBytes, long totalSizeBytes) {
        setStorageSize(calculateCategoryUsage(result, usedSizeBytes), totalSizeBytes);
    }

    CarUserManagerHelper getCarUserManagerHelper() {
        return mCarUserManagerHelper;
    }

    public VolumeInfo getVolumeInfo() {
        return mVolumeInfo;
    }

    public void setVolumeInfo(VolumeInfo volumeInfo) {
        mVolumeInfo = volumeInfo;
    }

    /**
     * Sets the storage size for this preference that will be displayed as a summary. It will also
     * update the progress bar accordingly.
     */
    private void setStorageSize(long size, long total) {
        getPreference().setSummary(
                FileSizeFormatter.formatFileSize(
                        getContext(),
                        size,
                        getGigabyteSuffix(getContext().getResources()),
                        FileSizeFormatter.GIGABYTE_IN_BYTES));
        int progressPercent;
        if (total == 0) {
            progressPercent = 0;
        } else {
            progressPercent = (int) (size * PROGRESS_MAX / total);
        }
        getPreference().setProgress(progressPercent);
    }

    private static int getGigabyteSuffix(Resources res) {
        return res.getIdentifier("gigabyteShort", "string", "android");
    }
}
