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
package android.car;

import android.annotation.IntDef;
import android.annotation.SystemApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Units used for int or float type with no attached enum types.
 * @hide
 */
@SystemApi
public final class VehicleUnit {
    /**
     * List of Unit Types from VHAL
     */
    public static final int SHOULD_NOT_USE = 0x000;

    public static final int RPM = 0x02;

    public static final int HERTZ = 0x03;

    public static final int PERCENTILE = 0x10;

    public static final int MILLIMETER = 0x20;
    public static final int METER = 0x21;
    public static final int KILOMETER = 0x23;
    public static final int MILE = 0x24;

    public static final int METER_PER_SEC = 0x01;

    public static final int CELSIUS = 0x30;
    public static final int FAHRENHEIT = 0x31;
    public static final int KELVIN = 0x32;

    public static final int MILLILITER = 0x40;
    public static final int LITER = 0x41;
    public static final int US_GALLON  = 0x42;
    public static final int IMPERIAL_GALLON = 0x43;

    public static final int NANO_SECS = 0x50;
    public static final int SECS = 0x53;
    public static final int YEAR = 0x59;

    public static final int MILLIAMPERE = 0x61;
    public static final int MILLIVOLT = 0x62;
    public static final int MILLIWATTS = 0x63;
    public static final int WATT_HOUR = 0x60;
    public static final int AMPERE_HOURS = 0x64;
    public static final int KILOWATT_HOUR = 0x65;

    public static final int KILOPASCAL = 0x70;
    public static final int PSI = 0x71;
    public static final int BAR = 0x72;

    public static final int DEGREES = 0x80;

    /** @hide */
    public static final int MILES_PER_HOUR = 0x90;

    /** @hide */
    public static final int KILOMETERS_PER_HOUR = 0x91;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            SHOULD_NOT_USE,
            METER_PER_SEC,
            RPM,
            HERTZ,
            PERCENTILE,
            MILLIMETER,
            METER,
            KILOMETER,
            MILE,
            CELSIUS,
            FAHRENHEIT,
            KELVIN,
            MILLILITER,
            LITER,
            US_GALLON,
            IMPERIAL_GALLON,
            NANO_SECS,
            SECS,
            YEAR,
            KILOPASCAL,
            WATT_HOUR,
            MILLIAMPERE,
            MILLIVOLT,
            MILLIWATTS,
            AMPERE_HOURS,
            KILOWATT_HOUR,
            PSI,
            BAR,
            DEGREES,
            MILES_PER_HOUR,
            KILOMETERS_PER_HOUR
    })
    public @interface Enum {}

    private VehicleUnit() {}
}
