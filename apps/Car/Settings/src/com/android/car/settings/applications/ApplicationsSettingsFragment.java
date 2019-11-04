/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.settings.applications;

import static com.android.car.settings.storage.StorageUtils.maybeInitializeVolume;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;

import com.android.car.settings.R;
import com.android.settingslib.applications.ApplicationsState;

/**
 * Lists all installed applications and their summary.
 */
public class ApplicationsSettingsFragment extends AppListFragment {

    private ApplicationListItemManager mAppListItemManager;

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.applications_settings_fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        Application application = requireActivity().getApplication();
        StorageManager sm = context.getSystemService(StorageManager.class);
        VolumeInfo volume = maybeInitializeVolume(sm, getArguments());
        mAppListItemManager = new ApplicationListItemManager(volume, getLifecycle(),
                ApplicationsState.getInstance(application));
        mAppListItemManager.registerListener(
                use(ApplicationsSettingsPreferenceController.class,
                        R.string.pk_all_applications_settings_list));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAppListItemManager.startLoading(getAppFilter(), ApplicationsState.ALPHA_COMPARATOR);
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
        return shouldShowSystemApps() ? null
                : ApplicationsState.FILTER_DOWNLOADED_AND_LAUNCHER_AND_INSTANT;
    }
}
