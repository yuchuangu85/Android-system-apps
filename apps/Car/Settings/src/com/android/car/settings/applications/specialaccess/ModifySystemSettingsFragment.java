/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.car.settings.applications.specialaccess;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;

import androidx.annotation.XmlRes;

import com.android.car.settings.R;
import com.android.car.settings.applications.AppListFragment;

/**
 * Displays apps which have requested to modify system settings and their current allowed status.
 */
public class ModifySystemSettingsFragment extends AppListFragment {

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.modify_system_settings_fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        use(AppOpsPreferenceController.class, R.string.pk_modify_system_settings).init(
                AppOpsManager.OP_WRITE_SETTINGS,
                Manifest.permission.WRITE_SETTINGS,
                AppOpsManager.MODE_ERRORED);
    }

    @Override
    protected void onToggleShowSystemApps(boolean showSystem) {
        use(AppOpsPreferenceController.class, R.string.pk_modify_system_settings).setShowSystem(
                showSystem);
    }
}
