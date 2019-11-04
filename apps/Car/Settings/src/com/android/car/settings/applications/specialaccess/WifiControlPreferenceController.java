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
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import com.android.car.settings.applications.specialaccess.AppStateAppOpsBridge.PermissionState;
import com.android.car.settings.common.FragmentController;
import com.android.internal.util.ArrayUtils;
import com.android.settingslib.applications.ApplicationsState;
import com.android.settingslib.applications.ApplicationsState.AppFilter;
import com.android.settingslib.applications.ApplicationsState.CompoundFilter;

/**
 * Manages the list of apps requesting to control Wi-Fi settings. Apps that also request {@link
 * Manifest.permission#NETWORK_SETTINGS} are excluded from the list as this permission overrules
 * {@link Manifest.permission#CHANGE_WIFI_STATE}.
 */
public class WifiControlPreferenceController extends AppOpsPreferenceController {

    private static final AppFilter FILTER_CHANGE_WIFI_STATE = new AppFilter() {
        @Override
        public void init() {
            // No op.
        }

        @Override
        public boolean filterApp(ApplicationsState.AppEntry info) {
            return !ArrayUtils.contains(
                    ((PermissionState) info.extraInfo).getRequestedPermissions(),
                    Manifest.permission.NETWORK_SETTINGS);
        }
    };

    public WifiControlPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        init(AppOpsManager.OP_CHANGE_WIFI_STATE, Manifest.permission.CHANGE_WIFI_STATE,
                AppOpsManager.MODE_IGNORED);
    }

    @Override
    protected AppFilter getAppFilter() {
        AppFilter filter = super.getAppFilter();
        return new CompoundFilter(filter, FILTER_CHANGE_WIFI_STATE);
    }
}
