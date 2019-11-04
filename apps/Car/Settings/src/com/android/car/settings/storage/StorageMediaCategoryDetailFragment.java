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


import static com.android.car.settings.storage.StorageMediaCategoryPreferenceController.EXTRA_AUDIO_BYTES;
import static com.android.car.settings.storage.StorageUtils.maybeInitializeVolume;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;

import com.android.car.settings.R;
import com.android.car.settings.applications.AppListFragment;
import com.android.car.settings.applications.ApplicationListItemManager;
import com.android.settingslib.applications.ApplicationsState;

/**
 * Lists all installed applications with category audio and their summary.
 */
public class StorageMediaCategoryDetailFragment extends AppListFragment {

    private ApplicationListItemManager mAppListItemManager;

    /**
     * Gets the instance of this class.
     */
    public static StorageMediaCategoryDetailFragment getInstance() {
        StorageMediaCategoryDetailFragment storageMedia = new StorageMediaCategoryDetailFragment();
        return storageMedia;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.storage_media_category_detail_fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        Bundle bundle = getArguments();
        long externalAudioBytes = bundle.getLong(EXTRA_AUDIO_BYTES);
        StorageManager sm = context.getSystemService(StorageManager.class);
        VolumeInfo volume = maybeInitializeVolume(sm, getArguments());
        Application application = requireActivity().getApplication();
        mAppListItemManager = new ApplicationListItemManager(volume, getLifecycle(),
                ApplicationsState.getInstance(application));
        StorageMediaCategoryDetailPreferenceController pc = use(
                StorageMediaCategoryDetailPreferenceController.class,
                R.string.pk_storage_music_audio_details);
        mAppListItemManager.registerListener(pc);
        pc.setExternalAudioBytes(externalAudioBytes);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAppListItemManager.startLoading(getAppFilter(), ApplicationsState.SIZE_COMPARATOR);
    }

    @Override
    public void onStart() {
        super.onStart();
        mAppListItemManager.onFragmentStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        mAppListItemManager.onFragmentStop();
    }

    @Override
    protected void onToggleShowSystemApps(boolean showSystem) {
        mAppListItemManager.rebuildWithFilter(getAppFilter());
    }

    private ApplicationsState.AppFilter getAppFilter() {
        ApplicationsState.AppFilter filter = ApplicationsState.FILTER_AUDIO;
        if (!shouldShowSystemApps()) {
            filter = new ApplicationsState.CompoundFilter(filter,
                    ApplicationsState.FILTER_DOWNLOADED_AND_LAUNCHER_AND_INSTANT);
        }
        return filter;
    }
}
