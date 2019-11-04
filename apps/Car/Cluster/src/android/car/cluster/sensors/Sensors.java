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
package android.car.cluster.sensors;

import android.car.VehiclePropertyIds;
import android.car.VehiclePropertyType;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.CarSensorEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The collection of all sensors supported by this application.
 */
public class Sensors {
    /** Area identifier used for sensors corresponding to global VHAL properties */
    public static final int GLOBAL_AREA_ID = -1;

    private static Sensors sInstance;
    private static List<Sensor<?>> sSensors = new ArrayList<>();
    private Map<Integer, List<Sensor<?>>> mSensorsByPropertyId = new HashMap<>();

    /** Possible values of the {@link #SENSOR_GEAR} sensor */
    public enum Gear {
        NEUTRAL,
        REVERSE,
        DRIVE,
        PARK,
    }

    /** Fuel of the car, measured in millimeters */
    public static final Sensor<Float> SENSOR_FUEL = registerSensor(
            "Fuel", VehiclePropertyIds.FUEL_LEVEL, GLOBAL_AREA_ID, VehiclePropertyType.FLOAT,
            value -> (Float) value.getValue());
    /** Fuel capacity of the car, measured in millimeters */
    public static final Sensor<Float> SENSOR_FUEL_CAPACITY = registerSensor(
            "Fuel Capacity", VehiclePropertyIds.INFO_FUEL_CAPACITY, GLOBAL_AREA_ID,
            VehiclePropertyType.FLOAT,
            value -> (Float) value.getValue());
    /** RPMs */
    public static final Sensor<Float> SENSOR_RPM = registerSensor(
            "RPM", VehiclePropertyIds.ENGINE_RPM, GLOBAL_AREA_ID,
            VehiclePropertyType.FLOAT,
            value -> (Float) value.getValue());
    /** Fuel range in meters */
    public static final Sensor<Float> SENSOR_FUEL_RANGE = registerSensor(
            "Fuel Range", VehiclePropertyIds.RANGE_REMAINING, GLOBAL_AREA_ID,
            VehiclePropertyType.FLOAT,
            value -> (Float) value.getValue());
    /** Speed in meters per second */
    public static final Sensor<Float> SENSOR_SPEED = registerSensor(
            "Speed", VehiclePropertyIds.PERF_VEHICLE_SPEED, GLOBAL_AREA_ID,
            VehiclePropertyType.FLOAT,
            value -> (Float) value.getValue());
    /** Current gear of the car */
    public static final Sensor<Gear> SENSOR_GEAR = registerSensor(
            "Gear", VehiclePropertyIds.GEAR_SELECTION, GLOBAL_AREA_ID, VehiclePropertyType.INT32,
            value -> {
                if (value == null) {
                    return null;
                }
                Integer gear = (Integer) value.getValue();
                if ((gear & CarSensorEvent.GEAR_REVERSE) != 0) {
                    return Gear.REVERSE;
                } else if ((gear & CarSensorEvent.GEAR_NEUTRAL) != 0) {
                    return Gear.NEUTRAL;
                } else if ((gear & CarSensorEvent.GEAR_DRIVE) != 0) {
                    return Gear.DRIVE;
                } else if ((gear & CarSensorEvent.GEAR_PARK) != 0) {
                    return Gear.PARK;
                } else {
                    return null;
                }
            });

    private static <T> Sensor<T> registerSensor(String propertyName, int propertyId, int areaId,
            int expectedPropertyType, Function<CarPropertyValue<?>, T> adapter) {
        Sensor<T> sensor = new Sensor<>(propertyName, propertyId, areaId, expectedPropertyType,
                adapter);
        sSensors.add(sensor);
        return sensor;
    }

    /**
     * Obtains the singleton instance of this class
     */
    public static Sensors getInstance() {
        if (sInstance == null) {
            sInstance = new Sensors();
        }
        return sInstance;
    }

    private Sensors() {
        initializeSensorsMap();
    }

    private void initializeSensorsMap() {
        for (Sensor<?> sensorId : getSensors()) {
            mSensorsByPropertyId
                    .computeIfAbsent(sensorId.mPropertyId, (id) -> new ArrayList<>())
                    .add(sensorId);
        }
    }

    /**
     * Returns all sensors.
     */
    public List<Sensor<?>> getSensors() {
        return sSensors;
    }

    /**
     * Returns all sensors associated to the given VHAL property id.
     */
    public List<Sensor<?>> getSensorsForPropertyId(int propertyId) {
        return mSensorsByPropertyId.get(propertyId);
    }

    /**
     * Returns all property ids we care about.
     */
    public Set<Integer> getPropertyIds() {
        return mSensorsByPropertyId.keySet();
    }
}
