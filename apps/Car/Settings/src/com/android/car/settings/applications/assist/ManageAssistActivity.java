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

package com.android.car.settings.applications.assist;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.car.settings.common.BaseCarSettingsActivity;

/**
 * Starts {@link ManageAssistFragment} in a separate activity to help with the back navigation
 * flow. This setting differs from the other settings in that the user arrives here from the
 * PermissionController rather than from within the Settings app itself.
 */
public class ManageAssistActivity extends BaseCarSettingsActivity {

    @Nullable
    @Override
    protected Fragment getInitialFragment() {
        return new ManageAssistFragment();
    }
}
