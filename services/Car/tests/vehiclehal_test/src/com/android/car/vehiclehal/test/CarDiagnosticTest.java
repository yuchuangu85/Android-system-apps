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
package com.android.car.vehiclehal.test;

import static junit.framework.Assert.assertTrue;

import android.car.Car;
import android.car.diagnostic.CarDiagnosticEvent;
import android.car.diagnostic.CarDiagnosticManager;
import android.car.diagnostic.FloatSensorIndex;
import android.car.diagnostic.IntegerSensorIndex;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.CarSensorManager;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.util.Log;
import android.util.SparseIntArray;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.car.vehiclehal.VehiclePropValueBuilder;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.time.Duration;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class CarDiagnosticTest extends E2eCarTestBase {

    private static final String TAG = Utils.concatTag(CarDiagnosticTest.class);

    private static final Duration TEST_TIME_OUT = Duration.ofMinutes(5);

    private static final String CAR_DIAGNOSTIC_TEST_JSON = "car_diagnostic_test.json";

    private static final SparseIntArray DIAGNOSTIC_PROPERTY_MAP;

    static {
        DIAGNOSTIC_PROPERTY_MAP = new SparseIntArray();
        DIAGNOSTIC_PROPERTY_MAP.append(
                CarDiagnosticManager.FRAME_TYPE_LIVE, VehicleProperty.OBD2_LIVE_FRAME);
        DIAGNOSTIC_PROPERTY_MAP.append(
                CarDiagnosticManager.FRAME_TYPE_FREEZE, VehicleProperty.OBD2_FREEZE_FRAME);
    }

    private class CarDiagnosticListner implements CarDiagnosticManager.OnDiagnosticEventListener {

        private VhalEventVerifier mVerifier;

        CarDiagnosticListner(VhalEventVerifier verifier) {
            mVerifier = verifier;
        }

        @Override
        public void onDiagnosticEvent(CarDiagnosticEvent event) {
            mVerifier.verify(fromCarDiagnosticEvent(event));
        }
    }

    private static CarPropertyValue<VehiclePropValue.RawValue> fromCarDiagnosticEvent(
            final CarDiagnosticEvent event) {
        int prop = DIAGNOSTIC_PROPERTY_MAP.get(event.frameType);
        VehiclePropValueBuilder builder = VehiclePropValueBuilder.newBuilder(prop);

        for (int i = 0; i <= IntegerSensorIndex.LAST_SYSTEM; i++) {
            builder.addIntValue(event.getSystemIntegerSensor(i, 0));
        }
        for (int i = 0; i <= FloatSensorIndex.LAST_SYSTEM; i++) {
            builder.addFloatValue(event.getSystemFloatSensor(i, 0));
        }

        builder.setStringValue(event.dtc);
        return new CarPropertyValue<>(prop, 0, builder.build().value);
    }

    @Test
    public void testDiagnosticEvents() throws Exception {
        Log.d(TAG, "Prepare Diagnostic test data");
        List<CarPropertyValue> expectedEvents = getExpectedEvents(CAR_DIAGNOSTIC_TEST_JSON);
        VhalEventVerifier verifier = new VhalEventVerifier(expectedEvents);

        CarDiagnosticManager diagnosticManager = (CarDiagnosticManager) mCar.getCarManager(
                Car.DIAGNOSTIC_SERVICE);

        CarDiagnosticListner listner = new CarDiagnosticListner(verifier);

        assertTrue("Failed to register for OBD2 diagnostic live frame.",
                   diagnosticManager.registerListener(listner,
                                                      CarDiagnosticManager.FRAME_TYPE_LIVE,
                                                      CarSensorManager.SENSOR_RATE_NORMAL));
        assertTrue("Failed to register for OBD2 diagnostic freeze frame.",
                   diagnosticManager.registerListener(listner,
                                                      CarDiagnosticManager.FRAME_TYPE_FREEZE,
                                                      CarSensorManager.SENSOR_RATE_NORMAL));

        File sharedJson = makeShareable(CAR_DIAGNOSTIC_TEST_JSON);
        Log.d(TAG, "Send command to VHAL to start generation");
        VhalEventGenerator diagnosticGenerator =
                new JsonVhalEventGenerator(mVehicle).setJsonFile(sharedJson);
        diagnosticGenerator.start();

        Log.d(TAG, "Receiving and verifying VHAL events");
        verifier.waitForEnd(TEST_TIME_OUT.toMillis());

        Log.d(TAG, "Send command to VHAL to stop generation");
        diagnosticGenerator.stop();
        diagnosticManager.unregisterListener(listner);

        assertTrue("Detected mismatched events: " + verifier.getResultString(),
                    verifier.getMismatchedEvents().isEmpty());
    }
}
