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

/** Controls {@link Unit} used for Fuel Consumption Display. */
public class UnitsFuelConsumptionPreferenceController extends UnitsBasePreferenceController {

    public UnitsFuelConsumptionPreferenceController(Context context, String preferenceKey,
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

    @Override
    protected String generateSummaryFromUnit(Unit unit) {
        return getAbbreviationByVolumeUnit(unit);
    }

    @Override
    protected String generateEntryStringFromUnit(Unit unit) {
        return getContext().getString(R.string.units_list_entry, getAbbreviationByVolumeUnit(unit),
                getPronouncedNameByVolumeUnit(unit));
    }

    private String getAbbreviationByVolumeUnit(Unit unit) {
        String abbreviation;
        switch (unit.getId()) {
            case VehicleUnit.US_GALLON:
                abbreviation = getContext().getString(
                        R.string.units_unit_abbreviation_miles_per_gallon_us);
                break;
            case VehicleUnit.IMPERIAL_GALLON:
                abbreviation = getContext().getString(
                        R.string.units_unit_abbreviation_miles_per_gallon_uk);
                break;
            default:
                if (getCarUnitsManager().isDistanceOverVolume()) {
                    abbreviation = getContext().getString(
                            R.string.units_unit_abbreviation_kilometers_per_liter);
                } else {
                    abbreviation = getContext().getString(
                            R.string.units_unit_abbreviation_liters_per_hundred_kilometers);
                }
        }
        return abbreviation;
    }

    private String getPronouncedNameByVolumeUnit(Unit unit) {
        String pronounced;
        switch (unit.getId()) {
            case VehicleUnit.US_GALLON:
                pronounced = getContext().getString(R.string.units_unit_name_miles_per_gallon_us);
                break;
            case VehicleUnit.IMPERIAL_GALLON:
                pronounced = getContext().getString(R.string.units_unit_name_miles_per_gallon_uk);
                break;
            default:
                if (getCarUnitsManager().isDistanceOverVolume()) {
                    pronounced = getContext().getString(
                            R.string.units_unit_name_kilometers_per_liter);
                } else {
                    pronounced = getContext().getString(
                            R.string.units_unit_name_liter_per_hundred_kilometers);
                }
        }
        return pronounced;
    }
}
