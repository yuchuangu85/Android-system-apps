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
 * Displays apps which have requested to access usage data and their current allowed status.
 */
public class UsageAccessFragment extends AppListFragment {

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.usage_access_fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        use(AppOpsPreferenceController.class, R.string.pk_usage_access).init(
                AppOpsManager.OP_GET_USAGE_STATS,
                Manifest.permission.PACKAGE_USAGE_STATS,
                AppOpsManager.MODE_IGNORED);
    }

    @Override
    protected void onToggleShowSystemApps(boolean showSystem) {
        use(AppOpsPreferenceController.class, R.string.pk_usage_access).setShowSystem(showSystem);
    }
}
