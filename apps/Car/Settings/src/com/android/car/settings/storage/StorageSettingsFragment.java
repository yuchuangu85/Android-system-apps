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

import static com.android.car.settings.storage.StorageUtils.maybeInitializeVolume;

import android.annotation.XmlRes;
import android.content.Context;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;

import androidx.loader.app.LoaderManager;

import com.android.car.settings.R;
import com.android.car.settings.common.SettingsFragment;

import java.util.Arrays;
import java.util.List;

/** Fragment which shows the settings for storage. */
public class StorageSettingsFragment extends SettingsFragment {

    private StorageSettingsManager mStorageSettingsManager;

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.storage_settings_fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        StorageManager sm = context.getSystemService(StorageManager.class);
        VolumeInfo volume = maybeInitializeVolume(sm, getArguments());
        mStorageSettingsManager = new StorageSettingsManager(getContext(), volume);
        List<StorageUsageBasePreferenceController> usagePreferenceControllers =
                Arrays.asList(
                        use(StorageMediaCategoryPreferenceController.class,
                                R.string.pk_storage_music_audio),
                        use(StorageOtherCategoryPreferenceController.class,
                                R.string.pk_storage_other_apps),
                        use(StorageFileCategoryPreferenceController.class,
                                R.string.pk_storage_files),
                        use(StorageSystemCategoryPreferenceController.class,
                                R.string.pk_storage_system));

        for (StorageUsageBasePreferenceController pc : usagePreferenceControllers) {
            mStorageSettingsManager.registerListener(pc);
            pc.setVolumeInfo(volume);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LoaderManager loaderManager = LoaderManager.getInstance(this);
        mStorageSettingsManager.startLoading(loaderManager);
    }
}
