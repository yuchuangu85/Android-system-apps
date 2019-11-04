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

package com.android.car.settings.units;

import android.car.VehiclePropertyIds;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.preference.ListPreference;

import com.android.car.settings.common.FragmentController;

/** Controls {@link Unit} used for Volume Display. */
public class UnitsVolumePreferenceController extends UnitsBasePreferenceController {

    public UnitsVolumePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<ListPreference> getPreferenceType() {
        return ListPreference.class;
    }

    @Override
    protected int getPropertyId() {
        return VehiclePropertyIds.FUEL_VOLUME_DISPLAY_UNITS;
    }

}
