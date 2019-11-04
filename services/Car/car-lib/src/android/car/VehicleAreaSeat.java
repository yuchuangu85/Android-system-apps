/*
 * Copyright (C) 2016 The Android Open Source Project
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
import android.car.hardware.CarPropertyValue;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * VehicleAreaSeat is an abstraction for a seat in a car. Some car APIs like
 * {@link CarPropertyValue} may provide control per seat and
 * values defined here should be used to distinguish different seats.
 */
public final class VehicleAreaSeat {
    /** List of vehicle's seats. */
    public static final int SEAT_UNKNOWN = 0;
    /** Row 1 left side seat*/
    public static final int SEAT_ROW_1_LEFT = 0x0001;
    /** Row 1 center seat*/
    public static final int SEAT_ROW_1_CENTER = 0x0002;
    /** Row 1 right side seat*/
    public static final int SEAT_ROW_1_RIGHT = 0x0004;
    /** Row 2 left side seat*/
    public static final int SEAT_ROW_2_LEFT = 0x0010;
    /** Row 2 center seat*/
    public static final int SEAT_ROW_2_CENTER = 0x0020;
    /** Row 2 right side seat*/
    public static final int SEAT_ROW_2_RIGHT = 0x0040;
    /** Row 3 left side seat*/
    public static final int SEAT_ROW_3_LEFT = 0x0100;
    /** Row 3 center seat*/
    public static final int SEAT_ROW_3_CENTER = 0x0200;
    /** Row 3 right side seat*/
    public static final int SEAT_ROW_3_RIGHT = 0x0400;

    /** @hide */
    @IntDef(prefix = {"SEAT_"}, value = {
        SEAT_UNKNOWN,
        SEAT_ROW_1_LEFT,
        SEAT_ROW_1_CENTER,
        SEAT_ROW_1_RIGHT,
        SEAT_ROW_2_LEFT,
        SEAT_ROW_2_CENTER,
        SEAT_ROW_2_RIGHT,
        SEAT_ROW_3_LEFT,
        SEAT_ROW_3_CENTER,
        SEAT_ROW_3_RIGHT
    })
    @Retention(RetentionPolicy.SOURCE)

    public @interface Enum {}
    private VehicleAreaSeat() {}

}
