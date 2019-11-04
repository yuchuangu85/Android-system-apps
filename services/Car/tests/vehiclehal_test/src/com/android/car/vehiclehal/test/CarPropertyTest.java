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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.car.Car;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.car.hardware.property.CarPropertyManager.CarPropertyEventCallback;
import android.util.ArraySet;
import android.util.Log;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import static java.lang.Integer.toHexString;

import java.io.File;
import java.time.Duration;
import java.util.List;

/**
 * The test suite will execute end-to-end Car Property API test by generating VHAL property data
 * from default VHAL and verify those data on the fly. The test data is coming from assets/ folder
 * in the test APK and will be shared with VHAL to execute the test.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class CarPropertyTest extends E2eCarTestBase {

    private static final String TAG = Utils.concatTag(CarPropertyTest.class);

    // Test should be completed within 10 minutes as it only covers a finite set of properties
    private static final Duration TEST_TIME_OUT = Duration.ofMinutes(10);

    private static final String CAR_HVAC_TEST_JSON = "car_hvac_test.json";
    private static final String CAR_INFO_TEST_JSON = "car_info_test.json";

    private class CarPropertyEventReceiver implements CarPropertyEventCallback {

        private VhalEventVerifier mVerifier;
        private Integer mNumOfEventToSkip;

        CarPropertyEventReceiver(VhalEventVerifier verifier, int numOfEventToSkip) {
            mVerifier = verifier;
            mNumOfEventToSkip = numOfEventToSkip;
        }

        @Override
        public void onChangeEvent(CarPropertyValue carPropertyValue) {
            Log.d(TAG, "Received event: " + carPropertyValue);
            synchronized (mNumOfEventToSkip) {
                if (mNumOfEventToSkip > 0) {
                    mNumOfEventToSkip--;
                    return;
                }
            }
            mVerifier.verify(carPropertyValue);
        }

        @Override
        public void onErrorEvent(final int propertyId, final int zone) {
            Assert.fail("Error: propertyId=" + toHexString(propertyId) + " zone=" + zone);
        }
    }

    private int countNumPropEventsToSkip(CarPropertyManager propMgr, ArraySet<Integer> props) {
        int numToSkip = 0;
        for (CarPropertyConfig c : propMgr.getPropertyList(props)) {
            numToSkip += c.getAreaCount();
        }
        return numToSkip;
    }

    /**
     * This test will let Default VHAL to generate HVAC data and verify on-the-fly in the test. It
     * is simulating the HVAC actions coming from hard buttons in a car.
     * @throws Exception
     */
    @Test
    public void testHvacHardButtonOperations() throws Exception {
        Log.d(TAG, "Prepare HVAC test data");
        List<CarPropertyValue> expectedEvents = getExpectedEvents(CAR_HVAC_TEST_JSON);
        VhalEventVerifier verifier = new VhalEventVerifier(expectedEvents);

        CarPropertyManager propMgr = (CarPropertyManager) mCar.getCarManager(Car.PROPERTY_SERVICE);
        assertNotNull("CarPropertyManager is null", propMgr);

        ArraySet<Integer> props = new ArraySet<>();
        for (CarPropertyValue event : expectedEvents) {
            if (!props.contains(event.getPropertyId())) {
                props.add(event.getPropertyId());
            }
        }

        int numToSkip = countNumPropEventsToSkip(propMgr, props);
        Log.d(TAG, String.format("Start listening to the HAL."
                                 + " Skipping %d events for listener registration", numToSkip));
        CarPropertyEventCallback receiver =
                new CarPropertyEventReceiver(verifier, numToSkip);
        for (Integer prop : props) {
            propMgr.registerCallback(receiver, prop, 0);
        }

        File sharedJson = makeShareable(CAR_HVAC_TEST_JSON);
        Log.d(TAG, "Send command to VHAL to start generation");
        VhalEventGenerator hvacGenerator =
                new JsonVhalEventGenerator(mVehicle).setJsonFile(sharedJson);
        hvacGenerator.start();

        Log.d(TAG, "Receiving and verifying VHAL events");
        verifier.waitForEnd(TEST_TIME_OUT.toMillis());

        Log.d(TAG, "Send command to VHAL to stop generation");
        hvacGenerator.stop();
        propMgr.unregisterCallback(receiver);

        assertTrue("Detected mismatched events: " + verifier.getResultString(),
                    verifier.getMismatchedEvents().isEmpty());
    }

    /**
     * This test will exercise on "set" calls to inject HVAC data in order to test the Car Property
     * API end-to-end functionality.
     * @throws Exception
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testHvacSetGetOperations() throws Exception {
        Log.d(TAG, "Prepare HVAC test data");
        List<CarPropertyValue> expectedEvents = getExpectedEvents(CAR_HVAC_TEST_JSON);

        CarPropertyManager propMgr = (CarPropertyManager) mCar.getCarManager(Car.PROPERTY_SERVICE);
        assertNotNull("CarPropertyManager is null", propMgr);

        final long waitForSetMillisecond = 2;
        for (CarPropertyValue expectedEvent : expectedEvents) {
            Class valueClass = expectedEvent.getValue().getClass();
            propMgr.setProperty(valueClass,
                                expectedEvent.getPropertyId(),
                                expectedEvent.getAreaId(),
                                expectedEvent.getValue());

            Thread.sleep(waitForSetMillisecond);
            CarPropertyValue receivedEvent = propMgr.getProperty(valueClass,
                    expectedEvent.getPropertyId(), expectedEvent.getAreaId());
            assertTrue("Mismatched events, expected: " + expectedEvent + ", received: "
                    + receivedEvent, Utils.areCarPropertyValuesEqual(expectedEvent, receivedEvent));
        }
    }

    /**
     * This test will load static vehicle information from test data file and verify them through
     * get calls.
     * @throws Exception
     */
    @Test
    public void testStaticInfoOperations() throws Exception {
        Log.d(TAG, "Prepare static car information");

        List<CarPropertyValue> expectedEvents = getExpectedEvents(CAR_INFO_TEST_JSON);
        CarPropertyManager propMgr = (CarPropertyManager) mCar.getCarManager(Car.PROPERTY_SERVICE);
        assertNotNull("CarPropertyManager is null", propMgr);

        File sharedJson = makeShareable(CAR_INFO_TEST_JSON);
        Log.d(TAG, "Send command to VHAL to start generation");
        VhalEventGenerator infoGenerator =
                new JsonVhalEventGenerator(mVehicle).setJsonFile(sharedJson);
        infoGenerator.start();

        // Wait for some time to ensure information is all loaded
        // It is assuming the test data is not very large
        Thread.sleep(2000);

        Log.d(TAG, "Send command to VHAL to stop generation");
        infoGenerator.stop();

        for (CarPropertyValue expectedEvent : expectedEvents) {
            CarPropertyValue actualEvent = propMgr.getProperty(
                    expectedEvent.getPropertyId(), expectedEvent.getAreaId());
            assertTrue(String.format(
                    "Mismatched car information data, actual: %s, expected: %s",
                    actualEvent, expectedEvent),
                    Utils.areCarPropertyValuesEqual(actualEvent, expectedEvent));
        }
    }
}
