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
import android.util.SparseArray;

import com.android.car.settings.common.FragmentController;

/**
 * Controller which determines the storage for the applications not assigned to one of the other
 * categories in the storage preference screen.
 */
public class StorageOtherCategoryPreferenceController extends StorageUsageBasePreferenceController {

    public StorageOtherCategoryPreferenceController(Context context,
            String preferenceKey, FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected long calculateCategoryUsage(SparseArray<StorageAsyncLoader.AppsStorageResult> result,
            long usedSizeBytes) {
        StorageAsyncLoader.AppsStorageResult data = result.get(
                getCarUserManagerHelper().getCurrentProcessUserId());
        return data.getOtherAppsSize();
    }
}
