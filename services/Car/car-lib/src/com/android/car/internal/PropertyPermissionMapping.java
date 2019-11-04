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

package com.android.car.internal;

import android.annotation.Nullable;
import android.car.Car;
import android.car.VehiclePropertyIds;
import android.util.SparseArray;

/** @hide */
// TODO(pavelm): use this class in Car Service instead of PropertyHalServiceIds
public final class PropertyPermissionMapping {

    private static final int VENDOR_MASK = 0x20000000;
    private static final Permission VENDOR_PERMISION =
            Permission.of(Car.PERMISSION_VENDOR_EXTENSION);

    private final SparseArray<Permission> mPermissions = new SparseArray<>();

    public PropertyPermissionMapping() {
        // Add propertyId and read/write permissions
        // Cabin Properties
        map(Car.PERMISSION_CONTROL_CAR_DOORS,
                VehiclePropertyIds.DOOR_POS,
                VehiclePropertyIds.DOOR_MOVE,
                VehiclePropertyIds.DOOR_LOCK);
        map(Car.PERMISSION_CONTROL_CAR_MIRRORS,
                VehiclePropertyIds.MIRROR_Z_POS,
                VehiclePropertyIds.MIRROR_Z_MOVE,
                VehiclePropertyIds.MIRROR_Y_POS,
                VehiclePropertyIds.MIRROR_Y_MOVE,
                VehiclePropertyIds.MIRROR_LOCK,
                VehiclePropertyIds.MIRROR_FOLD);
        map(Car.PERMISSION_CONTROL_CAR_SEATS,
                VehiclePropertyIds.SEAT_MEMORY_SELECT,
                VehiclePropertyIds.SEAT_MEMORY_SET,
                VehiclePropertyIds.SEAT_BELT_BUCKLED,
                VehiclePropertyIds.SEAT_BELT_HEIGHT_POS,
                VehiclePropertyIds.SEAT_BELT_HEIGHT_MOVE,
                VehiclePropertyIds.SEAT_FORE_AFT_POS,
                VehiclePropertyIds.SEAT_FORE_AFT_MOVE,
                VehiclePropertyIds.SEAT_BACKREST_ANGLE_1_POS,
                VehiclePropertyIds.SEAT_BACKREST_ANGLE_1_MOVE,
                VehiclePropertyIds.SEAT_BACKREST_ANGLE_2_POS,
                VehiclePropertyIds.SEAT_BACKREST_ANGLE_2_MOVE,
                VehiclePropertyIds.SEAT_HEIGHT_POS,
                VehiclePropertyIds.SEAT_HEIGHT_MOVE,
                VehiclePropertyIds.SEAT_DEPTH_POS,
                VehiclePropertyIds.SEAT_DEPTH_MOVE,
                VehiclePropertyIds.SEAT_TILT_POS,
                VehiclePropertyIds.SEAT_TILT_MOVE,
                VehiclePropertyIds.SEAT_LUMBAR_FORE_AFT_POS,
                VehiclePropertyIds.SEAT_LUMBAR_FORE_AFT_MOVE,
                VehiclePropertyIds.SEAT_LUMBAR_SIDE_SUPPORT_POS,
                VehiclePropertyIds.SEAT_LUMBAR_SIDE_SUPPORT_MOVE,
                VehiclePropertyIds.SEAT_HEADREST_HEIGHT_POS,
                VehiclePropertyIds.SEAT_HEADREST_HEIGHT_MOVE,
                VehiclePropertyIds.SEAT_HEADREST_ANGLE_POS,
                VehiclePropertyIds.SEAT_HEADREST_ANGLE_MOVE,
                VehiclePropertyIds.SEAT_HEADREST_FORE_AFT_POS,
                VehiclePropertyIds.SEAT_HEADREST_FORE_AFT_MOVE);
        map(Car.PERMISSION_CONTROL_CAR_WINDOWS,
                VehiclePropertyIds.WINDOW_POS,
                VehiclePropertyIds.WINDOW_MOVE,
                VehiclePropertyIds.WINDOW_LOCK);

        map(Car.PERMISSION_CONTROL_CAR_CLIMATE,
                VehiclePropertyIds.HVAC_FAN_SPEED,
                VehiclePropertyIds.HVAC_FAN_DIRECTION,
                VehiclePropertyIds.HVAC_TEMPERATURE_CURRENT,
                VehiclePropertyIds.HVAC_TEMPERATURE_SET,
                VehiclePropertyIds.HVAC_DEFROSTER,
                VehiclePropertyIds.HVAC_AC_ON,
                VehiclePropertyIds.HVAC_MAX_AC_ON,
                VehiclePropertyIds.HVAC_MAX_DEFROST_ON,
                VehiclePropertyIds.HVAC_RECIRC_ON,
                VehiclePropertyIds.HVAC_DUAL_ON,
                VehiclePropertyIds.HVAC_AUTO_ON,
                VehiclePropertyIds.HVAC_SEAT_TEMPERATURE,
                VehiclePropertyIds.HVAC_SIDE_MIRROR_HEAT,
                VehiclePropertyIds.HVAC_STEERING_WHEEL_HEAT,
                VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS,
                VehiclePropertyIds.HVAC_ACTUAL_FAN_SPEED_RPM,
                VehiclePropertyIds.HVAC_POWER_ON,
                VehiclePropertyIds.HVAC_FAN_DIRECTION_AVAILABLE,
                VehiclePropertyIds.HVAC_AUTO_RECIRC_ON,
                VehiclePropertyIds.HVAC_SEAT_VENTILATION);

        // Info properties
        map(Car.PERMISSION_IDENTIFICATION, VehiclePropertyIds.INFO_VIN);
        map(Car.PERMISSION_CAR_INFO,
                VehiclePropertyIds.INFO_MAKE,
                VehiclePropertyIds.INFO_MODEL,
                VehiclePropertyIds.INFO_MODEL_YEAR,
                VehiclePropertyIds.INFO_FUEL_CAPACITY,
                VehiclePropertyIds.INFO_FUEL_TYPE,
                VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY,
                VehiclePropertyIds.INFO_EV_CONNECTOR_TYPE,
                VehiclePropertyIds.INFO_FUEL_DOOR_LOCATION,
                VehiclePropertyIds.INFO_EV_PORT_LOCATION,
                VehiclePropertyIds.INFO_DRIVER_SEAT);

        // Sensors
        map(Car.PERMISSION_MILEAGE, VehiclePropertyIds.PERF_ODOMETER);
        map(Car.PERMISSION_SPEED,
                VehiclePropertyIds.PERF_VEHICLE_SPEED,
                VehiclePropertyIds.PERF_VEHICLE_SPEED_DISPLAY,
                VehiclePropertyIds.WHEEL_TICK);
        map(Car.PERMISSION_CAR_ENGINE_DETAILED,
                VehiclePropertyIds.ENGINE_COOLANT_TEMP,
                VehiclePropertyIds.ENGINE_OIL_LEVEL,
                VehiclePropertyIds.ENGINE_OIL_TEMP,
                VehiclePropertyIds.ENGINE_RPM);
        map(Car.PERMISSION_ENERGY,
                VehiclePropertyIds.FUEL_LEVEL,
                VehiclePropertyIds.EV_BATTERY_LEVEL,
                VehiclePropertyIds.EV_BATTERY_INSTANTANEOUS_CHARGE_RATE,
                VehiclePropertyIds.RANGE_REMAINING,
                VehiclePropertyIds.FUEL_LEVEL_LOW);
        map(Car.PERMISSION_ENERGY_PORTS,
                VehiclePropertyIds.FUEL_DOOR_OPEN,
                VehiclePropertyIds.EV_CHARGE_PORT_OPEN,
                VehiclePropertyIds.EV_CHARGE_PORT_CONNECTED);
        map(Car.PERMISSION_TIRES, VehiclePropertyIds.TIRE_PRESSURE);
        map(Car.PERMISSION_POWERTRAIN,
                VehiclePropertyIds.GEAR_SELECTION,
                VehiclePropertyIds.CURRENT_GEAR,
                VehiclePropertyIds.PARKING_BRAKE_ON,
                VehiclePropertyIds.PARKING_BRAKE_AUTO_APPLY,
                VehiclePropertyIds.IGNITION_STATE);

        map(Car.PERMISSION_EXTERIOR_ENVIRONMENT,
                VehiclePropertyIds.NIGHT_MODE,
                VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE);
        map(Car.PERMISSION_CAR_DYNAMICS_STATE,
                VehiclePropertyIds.ABS_ACTIVE,
                VehiclePropertyIds.TRACTION_CONTROL_ACTIVE);
        map(Car.PERMISSION_EXTERIOR_LIGHTS,
                VehiclePropertyIds.TURN_SIGNAL_STATE,
                VehiclePropertyIds.HEADLIGHTS_STATE,
                VehiclePropertyIds.HIGH_BEAM_LIGHTS_STATE,
                VehiclePropertyIds.FOG_LIGHTS_STATE,
                VehiclePropertyIds.HAZARD_LIGHTS_STATE);
        map(Car.PERMISSION_EXTERIOR_LIGHTS, Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS,
                VehiclePropertyIds.HEADLIGHTS_SWITCH,
                VehiclePropertyIds.HIGH_BEAM_LIGHTS_SWITCH,
                VehiclePropertyIds.FOG_LIGHTS_SWITCH,
                VehiclePropertyIds.HAZARD_LIGHTS_SWITCH);
    }

    /**
     * Returns read permission string for given property ID.
     */
    @Nullable
    public String getReadPermission(int propId) {
        final Permission p = getPermission(propId);
        return p == null ? null : p.mReadPermission;
    }

    /**
     * Returns read permission string for given property ID.
     */
    @Nullable
    public String getWritePermission(int propId) {
        final Permission p = getPermission(propId);
        return p == null ? null : p.mWritePermission;
    }

    @Nullable
    private Permission getPermission(int propId) {
        return isVendorExtension(propId) ? VENDOR_PERMISION : mPermissions.get(propId);

    }

    private static boolean isVendorExtension(int propId) {
        return (propId & VENDOR_MASK) == VENDOR_MASK;
    }

    private void map(String readPermission, String writePermission, Integer... propIds) {
        map(Permission.of(readPermission, writePermission), propIds);
    }

    private void map(String readPermission, Integer... propIds) {
        map(Permission.of(readPermission), propIds);
    }

    private void map(Permission p, Integer[] propIds) {
        for (int propId : propIds) {
            mPermissions.put(propId, p);
        }
    }

    private static class Permission {
        private final String mReadPermission;
        private final String mWritePermission;

        private Permission(String readPermission, String writePermission) {
            this.mReadPermission = readPermission;
            this.mWritePermission = writePermission;
        }

        static Permission of(String readPermission, String writePermission) {
            return new Permission(readPermission, writePermission);
        }

        static Permission of(String readPermission) {
            return new Permission(readPermission, readPermission);
        }
    }
}
