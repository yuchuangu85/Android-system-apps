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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Used by INFO_FUEL_DOOR_LOCATION/INFO_CHARGE_PORT_LOCATION to enumerate fuel door or
 * ev port location.
 * Use getProperty and setProperty in {@link android.car.hardware.property.CarPropertyManager} to
 * set and get this VHAL property.
 */
public final class PortLocationType {
    /**
     * List of port location types
     */
    public static final int UNKNOWN = 0;
    /** Port is on front left side of vehicle. */
    public static final int FRONT_LEFT = 1;
    /** Port is on front right side of vehicle. */
    public static final int FRONT_RIGHT = 2;
    /** Port is on rear right side of vehicle. */
    public static final int REAR_RIGHT = 3;
    /** Port is on rear left side of vehicle. */
    public static final int REAR_LEFT = 4;
    /** Port is on front of vehicle. */
    public static final int FRONT = 5;
    /** Port is on rear of vehicle. */
    public static final int REAR = 6;

    /** @hide */
    @IntDef({
        UNKNOWN,
        FRONT_LEFT,
        FRONT_RIGHT,
        REAR_LEFT,
        REAR_RIGHT,
        FRONT,
        REAR
    })
    @Retention(RetentionPolicy.SOURCE)

    public @interface Enum {}
    private PortLocationType() {}
}
