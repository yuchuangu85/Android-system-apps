/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.car.developeroptions.widget;

import android.content.Context;
import android.os.UserManager;
import android.util.AttributeSet;

import androidx.preference.PreferenceCategory;

import com.android.car.developeroptions.SelfAvailablePreference;
import com.android.car.developeroptions.Utils;

/**
 * A PreferenceCategory that is only visible when the device has a work profile.
 */
public class WorkOnlyCategory extends PreferenceCategory implements SelfAvailablePreference {

    public WorkOnlyCategory(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean isAvailable(Context context) {
        return Utils.getManagedProfile(UserManager.get(context)) != null;
    }
}