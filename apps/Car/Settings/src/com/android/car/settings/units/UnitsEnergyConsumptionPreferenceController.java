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
import android.car.VehicleUnit;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.preference.ListPreference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;

/** Controls {@link Unit} used for Energy Consumption Display. */
public class UnitsEnergyConsumptionPreferenceController extends UnitsBasePreferenceController {

    private final String mKWh;

    public UnitsEnergyConsumptionPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mKWh = getContext().getString(UnitsMap.KILOWATT_HOUR.getAbbreviationResId());
    }

    @Override
    protected Class<ListPreference> getPreferenceType() {
        return ListPreference.class;
    }

    @Override
    protected int getPropertyId() {
        return VehiclePropertyIds.EV_BATTERY_DISPLAY_UNITS;
    }

    @Override
    protected String generateSummaryFromUnit(Unit unit) {
        return getAbbreviationByUnit(unit);
    }

    @Override
    protected String generateEntryStringFromUnit(Unit unit) {
        int unitNameResId;
        switch (unit.getId()) {
            case VehicleUnit.MILE:
                unitNameResId = R.string.units_unit_name_kilowatt_per_hundred_miles;
                break;
            default:
                unitNameResId = R.string.units_unit_name_kilowatt_per_hundred_kilometers;
        }

        return getContext().getString(R.string.units_list_entry, getAbbreviationByUnit(unit),
                getContext().getString(unitNameResId));

    }

    private String getAbbreviationByUnit(Unit unit) {
        int quantity = 100;
        String denominator = getContext().getString(R.string.units_ratio_denominator, quantity,
                getContext().getString(unit.getAbbreviationResId()));

        return getContext().getString(R.string.units_ratio, mKWh, denominator);
    }
}
