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
import android.os.Bundle;
import android.util.SparseArray;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.ProgressBarPreference;

/**
 * Controller which determines the storage for media category in the storage preference screen.
 */
public class StorageMediaCategoryPreferenceController extends StorageUsageBasePreferenceController {

    public static final String EXTRA_AUDIO_BYTES = "extra_audio_bytes";

    private long mExternalAudioBytes;

    public StorageMediaCategoryPreferenceController(Context context,
            String preferenceKey, FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected long calculateCategoryUsage(SparseArray<StorageAsyncLoader.AppsStorageResult> result,
            long usedSizeBytes) {
        StorageAsyncLoader.AppsStorageResult data = result.get(
                getCarUserManagerHelper().getCurrentProcessUserId());
        mExternalAudioBytes = data.getExternalStats().audioBytes;
        return data.getMusicAppsSize() + mExternalAudioBytes;
    }

    @Override
    protected boolean handlePreferenceClicked(ProgressBarPreference preference) {
        Bundle bundle = new Bundle();
        bundle.putLong(EXTRA_AUDIO_BYTES, mExternalAudioBytes);
        StorageMediaCategoryDetailFragment storageMediaCategoryDetailFragment =
                StorageMediaCategoryDetailFragment.getInstance();
        storageMediaCategoryDetailFragment.setArguments(bundle);
        getFragmentController().launchFragment(storageMediaCategoryDetailFragment);
        return true;
    }
}
