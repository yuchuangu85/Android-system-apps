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

import android.car.VehicleUnit;

import com.android.car.settings.R;

import java.util.HashMap;

/**
 * Contains {@link Unit} instances for all units defined in {@link VehicleUnit}. This mapping is
 * safe because OEMs cannot define their own VehicleUnit.
 */
public final class UnitsMap {
    protected static final Unit METER_PER_SEC = new Unit(VehicleUnit.METER_PER_SEC,
            R.string.units_unit_abbreviation_meter_per_sec, R.string.units_unit_name_meter_per_sec);
    protected static final Unit RPM = new Unit(VehicleUnit.RPM,
            R.string.units_unit_abbreviation_rpm, R.string.units_unit_name_rpm);
    protected static final Unit HERTZ = new Unit(VehicleUnit.HERTZ,
            R.string.units_unit_abbreviation_hertz, R.string.units_unit_name_hertz);
    protected static final Unit PERCENTILE = new Unit(VehicleUnit.PERCENTILE,
            R.string.units_unit_abbreviation_percentile, R.string.units_unit_name_percentile);
    protected static final Unit MILLIMETER = new Unit(VehicleUnit.MILLIMETER,
            R.string.units_unit_abbreviation_millimeter, R.string.units_unit_name_millimeter);
    protected static final Unit METER = new Unit(VehicleUnit.METER,
            R.string.units_unit_abbreviation_meter, R.string.units_unit_name_meter);
    protected static final Unit KILOMETER = new Unit(VehicleUnit.KILOMETER,
            R.string.units_unit_abbreviation_kilometer, R.string.units_unit_name_kilometer);
    protected static final Unit MILE = new Unit(VehicleUnit.MILE,
            R.string.units_unit_abbreviation_mile, R.string.units_unit_name_mile);
    protected static final Unit CELSIUS = new Unit(VehicleUnit.CELSIUS,
            R.string.units_unit_abbreviation_celsius, R.string.units_unit_name_celsius);
    protected static final Unit FAHRENHEIT = new Unit(VehicleUnit.FAHRENHEIT,
            R.string.units_unit_abbreviation_fahrenheit, R.string.units_unit_name_fahrenheit);
    protected static final Unit KELVIN = new Unit(VehicleUnit.KELVIN,
            R.string.units_unit_abbreviation_kelvin, R.string.units_unit_name_kelvin);
    protected static final Unit MILLILITER = new Unit(VehicleUnit.MILLILITER,
            R.string.units_unit_abbreviation_milliliter, R.string.units_unit_name_milliliter);
    protected static final Unit LITER = new Unit(VehicleUnit.LITER,
            R.string.units_unit_abbreviation_liter, R.string.units_unit_name_liter);
    protected static final Unit US_GALLON = new Unit(VehicleUnit.US_GALLON,
            R.string.units_unit_abbreviation_us_gallon, R.string.units_unit_name_us_gallon);
    protected static final Unit IMPERIAL_GALLON = new Unit(VehicleUnit.IMPERIAL_GALLON,
            R.string.units_unit_abbreviation_imperial_gallon,
            R.string.units_unit_name_imperial_gallon);
    protected static final Unit NANO_SECS = new Unit(VehicleUnit.NANO_SECS,
            R.string.units_unit_abbreviation_nano_secs, R.string.units_unit_name_nano_secs);
    protected static final Unit SECS = new Unit(VehicleUnit.SECS,
            R.string.units_unit_abbreviation_secs, R.string.units_unit_name_secs);
    protected static final Unit YEAR = new Unit(VehicleUnit.YEAR,
            R.string.units_unit_abbreviation_year, R.string.units_unit_name_year);
    protected static final Unit KILOPASCAL = new Unit(VehicleUnit.KILOPASCAL,
            R.string.units_unit_abbreviation_kilopascal, R.string.units_unit_name_kilopascal);
    protected static final Unit WATT_HOUR = new Unit(VehicleUnit.WATT_HOUR,
            R.string.units_unit_abbreviation_watt_hour, R.string.units_unit_name_watt_hour);
    protected static final Unit MILLIAMPERE = new Unit(VehicleUnit.MILLIAMPERE,
            R.string.units_unit_abbreviation_milliampere, R.string.units_unit_name_milliampere);
    protected static final Unit MILLIVOLT = new Unit(VehicleUnit.MILLIVOLT,
            R.string.units_unit_abbreviation_millivolt, R.string.units_unit_name_millivolt);
    protected static final Unit MILLIWATTS = new Unit(VehicleUnit.MILLIWATTS,
            R.string.units_unit_abbreviation_milliwatts, R.string.units_unit_name_milliwatts);
    protected static final Unit AMPERE_HOURS = new Unit(VehicleUnit.AMPERE_HOURS,
            R.string.units_unit_abbreviation_ampere_hour, R.string.units_unit_name_ampere_hour);
    protected static final Unit KILOWATT_HOUR = new Unit(VehicleUnit.KILOWATT_HOUR,
            R.string.units_unit_abbreviation_kilowatt_hour, R.string.units_unit_name_kilowatt_hour);
    protected static final Unit PSI = new Unit(VehicleUnit.PSI,
            R.string.units_unit_abbreviation_psi, R.string.units_unit_name_psi);
    protected static final Unit BAR = new Unit(VehicleUnit.BAR,
            R.string.units_unit_abbreviation_bar, R.string.units_unit_name_bar);
    protected static final Unit DEGREES = new Unit(VehicleUnit.DEGREES,
            R.string.units_unit_abbreviation_degrees, R.string.units_unit_name_degrees);
    protected static final Unit MILES_PER_HOUR = new Unit(VehicleUnit.MILES_PER_HOUR,
            R.string.units_unit_abbreviation_miles_per_hour,
            R.string.units_unit_name_miles_per_hour);
    protected static final Unit KILOMETERS_PER_HOUR = new Unit(VehicleUnit.KILOMETERS_PER_HOUR,
            R.string.units_unit_abbreviation_kilometers_per_hour,
            R.string.units_unit_name_kilometers_per_hour);

    public static final HashMap<Integer, Unit> MAP = createMap();

    private static HashMap<Integer, Unit> createMap() {
        HashMap<Integer, Unit> map = new HashMap();
        map.put(VehicleUnit.METER_PER_SEC, METER_PER_SEC);
        map.put(VehicleUnit.RPM, RPM);
        map.put(VehicleUnit.HERTZ, HERTZ);
        map.put(VehicleUnit.PERCENTILE, PERCENTILE);
        map.put(VehicleUnit.MILLIMETER, MILLIMETER);
        map.put(VehicleUnit.METER, METER);
        map.put(VehicleUnit.KILOMETER, KILOMETER);
        map.put(VehicleUnit.MILE, MILE);
        map.put(VehicleUnit.CELSIUS, CELSIUS);
        map.put(VehicleUnit.FAHRENHEIT, FAHRENHEIT);
        map.put(VehicleUnit.KELVIN, KELVIN);
        map.put(VehicleUnit.MILLILITER, MILLILITER);
        map.put(VehicleUnit.LITER, LITER);
        map.put(VehicleUnit.US_GALLON, US_GALLON);
        map.put(VehicleUnit.IMPERIAL_GALLON, IMPERIAL_GALLON);
        map.put(VehicleUnit.NANO_SECS, NANO_SECS);
        map.put(VehicleUnit.SECS, SECS);
        map.put(VehicleUnit.YEAR, YEAR);
        map.put(VehicleUnit.KILOPASCAL, KILOPASCAL);
        map.put(VehicleUnit.WATT_HOUR, WATT_HOUR);
        map.put(VehicleUnit.MILLIAMPERE, MILLIAMPERE);
        map.put(VehicleUnit.MILLIVOLT, MILLIVOLT);
        map.put(VehicleUnit.MILLIWATTS, MILLIWATTS);
        map.put(VehicleUnit.AMPERE_HOURS, AMPERE_HOURS);
        map.put(VehicleUnit.KILOWATT_HOUR, KILOWATT_HOUR);
        map.put(VehicleUnit.PSI, PSI);
        map.put(VehicleUnit.BAR, BAR);
        map.put(VehicleUnit.DEGREES, DEGREES);
        map.put(VehicleUnit.MILES_PER_HOUR, MILES_PER_HOUR);
        map.put(VehicleUnit.KILOMETERS_PER_HOUR, KILOMETERS_PER_HOUR);

        return map;
    }

    private UnitsMap() {
    }
}
