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

import android.car.VehiclePropertyType;
import android.car.hardware.CarPropertyValue;

import java.util.function.Function;

/**
 * Description of a car sensor. It can be used as identifier at
 * {@link android.car.cluster.ClusterViewModel#getSensor(Sensor)} to obtain a
 * {@link androidx.lifecycle.LiveData} to track values of this sensor. The sensor description is
 * used to process and decode the information reported by the car.
 * <p>
 * All instances of this class must be obtained from {@link Sensors}.
 *
 * @param <T> data type used by this sensor.
 */
public class Sensor<T> {
    /** Name of the sensor (for debugging) */
    public final String mName;
    /** VHAL identifier of this sensor */
    public final int mPropertyId;
    /** VHAL area associated with this sensor (each area is reported as an independent sensor) */
    public final int mAreaId;
    /**
     * Data type expected to be reported by the VHAL. If the values received don't match with the
     * expected ones, we warn about it and ignore the value.
     */
    @VehiclePropertyType.Enum
    public final int mExpectedPropertyType;
    /** VHAL Area associated with this sensor. */
    public final Function<CarPropertyValue<?>, T> mAdapter;

    /**
     * Creates a new sensor. Only {@link Sensors} should use this constructor.
     */
    Sensor(String name, int propertyId, int areaId, int expectedPropertyType,
            Function<CarPropertyValue<?>, T> adapter) {
        mName = name;
        mPropertyId = propertyId;
        mAreaId = areaId;
        mExpectedPropertyType = expectedPropertyType;
        mAdapter = adapter;
    }

    @Override
    public String toString() {
        return mName;
    }
}
