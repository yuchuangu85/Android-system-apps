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

package android.car.testapi;

import android.annotation.Nullable;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;

import java.util.List;

/**
 * Controller to modify car properties. Get instance of this interface via
 * {@link FakeCar#getCarPropertyController()}
 */
public interface CarPropertyController {
    /**
     * Adds property to {@link FakeCar}.
     *
     * <p>See {@link android.car.VehiclePropertyIds} for a
     * defined list of property IDs. Custom properties are also allowed, however they need
     * to follow the same bit format as vehicle HAL (encoding data type, area type, etc in the)
     * property id itself.
     */
    CarPropertyController addProperty(Integer propId, @Nullable Object value);

    /** Adds configured property to {@link FakeCar} */
    CarPropertyController addProperty(CarPropertyConfig<?> config,
            @Nullable CarPropertyValue<?> value);

    /**
     * Updates values of supported properties and optionally sends change events to
     * listeners
     */
    void updateValues(boolean triggerListeners, CarPropertyValue<?>... propValues);

    /** Returns the list of values that have been set from {@link CarPropertyManager} */
    List<CarPropertyValue<?>> getSetValues();
}
