/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.car.vehiclehal.test;

import static android.os.SystemClock.elapsedRealtime;

import android.annotation.Nullable;
import android.car.hardware.CarPropertyValue;
import android.hardware.automotive.vehicle.V2_0.IVehicle;
import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.os.RemoteException;
import android.util.Log;

import com.android.car.vehiclehal.VehiclePropValueBuilder;

import java.util.NoSuchElementException;
import java.util.Objects;

final class Utils {
    private Utils() {}

    private static final String TAG = concatTag(Utils.class);

    static String concatTag(Class clazz) {
        return "VehicleHalTest." + clazz.getSimpleName();
    }

    static boolean isVhalPropertyAvailable(IVehicle vehicle, int prop) throws RemoteException {
        return vehicle.getAllPropConfigs()
                .stream()
                .anyMatch((VehiclePropConfig config) -> config.prop == prop);
    }

    static VehiclePropValue readVhalProperty(
        IVehicle vehicle,
        VehiclePropValue request,
        java.util.function.BiFunction<Integer, VehiclePropValue, Boolean> f) {
        Objects.requireNonNull(vehicle);
        Objects.requireNonNull(request);
        VehiclePropValue vpv[] = new VehiclePropValue[] {null};
        try {
            vehicle.get(
                request,
                (int status, VehiclePropValue propValue) -> {
                    if (f.apply(status, propValue)) {
                        vpv[0] = propValue;
                    }
                });
        } catch (RemoteException e) {
            Log.w(TAG, "attempt to read VHAL property " + request + " caused RemoteException: ", e);
        }
        return vpv[0];
    }

    static VehiclePropValue readVhalProperty(
        IVehicle vehicle,
        int propertyId,
        java.util.function.BiFunction<Integer, VehiclePropValue, Boolean> f) {
        return readVhalProperty(vehicle, propertyId, 0, f);
    }

    static VehiclePropValue readVhalProperty(
            IVehicle vehicle,
            int propertyId,
            int areaId,
            java.util.function.BiFunction<Integer, VehiclePropValue, Boolean> f) {
        VehiclePropValue request =
            VehiclePropValueBuilder.newBuilder(propertyId).setAreaId(areaId).build();
        return readVhalProperty(vehicle, request, f);
    }

    @Nullable
    private static IVehicle getVehicle() {
        try {
            return IVehicle.getService();
        } catch (NoSuchElementException ex) {
            Log.e(TAG, "IVehicle service not registered yet", ex);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get IVehicle Service ", e);
        }
        Log.d(TAG, "Failed to connect to IVehicle service");
        return null;
    }

    static IVehicle getVehicleWithTimeout(long waitMilliseconds) {
        IVehicle vehicle = getVehicle();
        long endTime = elapsedRealtime() + waitMilliseconds;
        while (vehicle == null && endTime > elapsedRealtime()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException("Sleep was interrupted", e);
            }
            vehicle = getVehicle();
        }

        return vehicle;
    }

    /**
     * Check the equality of two VehiclePropValue object ignoring timestamp and status.
     *
     * @param value1
     * @param value2
     * @return true if equal
     */
    static boolean areCarPropertyValuesEqual(final CarPropertyValue value1,
            final CarPropertyValue value2) {
        return value1 == value2
            || value1 != null
            && value2 != null
            && value1.getPropertyId() == value2.getPropertyId()
            && value1.getAreaId() == value2.getAreaId()
            && value1.getValue().equals(value2.getValue());
    }
}
