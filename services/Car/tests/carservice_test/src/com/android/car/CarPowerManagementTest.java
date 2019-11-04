/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.car;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.hardware.automotive.vehicle.V2_0.VehicleApPowerStateConfigFlag;
import android.hardware.automotive.vehicle.V2_0.VehicleApPowerStateReport;
import android.hardware.automotive.vehicle.V2_0.VehicleApPowerStateReq;
import android.hardware.automotive.vehicle.V2_0.VehicleApPowerStateReqIndex;
import android.hardware.automotive.vehicle.V2_0.VehicleApPowerStateShutdownParam;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.hardware.automotive.vehicle.V2_0.VehiclePropertyAccess;
import android.hardware.automotive.vehicle.V2_0.VehiclePropertyChangeMode;
import android.os.SystemClock;

import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.car.systeminterface.DisplayInterface;
import com.android.car.systeminterface.SystemInterface;
import com.android.car.vehiclehal.VehiclePropValueBuilder;
import com.android.car.vehiclehal.test.MockedVehicleHal.VehicleHalPropertyHandler;

import com.google.android.collect.Lists;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class CarPowerManagementTest extends MockedCarTestBase {

    private final PowerStatePropertyHandler mPowerStateHandler = new PowerStatePropertyHandler();
    private final MockDisplayInterface mMockDisplayInterface = new MockDisplayInterface();

    @Override
    protected synchronized SystemInterface.Builder getSystemInterfaceBuilder() {
        SystemInterface.Builder builder = super.getSystemInterfaceBuilder();
        return builder.withDisplayInterface(mMockDisplayInterface);
    }

    protected synchronized void configureMockedHal() {
        addProperty(VehicleProperty.AP_POWER_STATE_REQ, mPowerStateHandler)
                .setConfigArray(Lists.newArrayList(
                    VehicleApPowerStateConfigFlag.ENABLE_DEEP_SLEEP_FLAG))
                .setChangeMode(VehiclePropertyChangeMode.ON_CHANGE).build();
        addProperty(VehicleProperty.AP_POWER_STATE_REPORT, mPowerStateHandler)
                .setAccess(VehiclePropertyAccess.WRITE)
                .setChangeMode(VehiclePropertyChangeMode.ON_CHANGE).build();
    }

    /**********************************************************************************************
     * Test immediate shutdown
     **********************************************************************************************/
    @Test
    @UiThreadTest
    public void testImmediateShutdownFromWaitForVhal() throws Exception {
        assertWaitForVhal();
        mPowerStateHandler.sendStateAndCheckResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY,
                VehicleApPowerStateReport.SHUTDOWN_START);
    }

    @Test
    @UiThreadTest
    public void testImmediateShutdownFromOn() throws Exception {
        assertWaitForVhal();
        // Transition to ON state first
        mPowerStateHandler.sendStateAndCheckResponse(
                VehicleApPowerStateReq.ON,
                0,
                VehicleApPowerStateReport.ON);
        // Send immediate shutdown from ON state
        mPowerStateHandler.sendStateAndCheckResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY,
                VehicleApPowerStateReport.SHUTDOWN_START);
    }

    @Test
    @UiThreadTest
    public void testImmediateShutdownFromShutdownPrepare() throws Exception {
        assertWaitForVhal();
        // Put device into SHUTDOWN_PREPARE
        mPowerStateHandler.sendStateAndCheckResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP,
                VehicleApPowerStateReport.SHUTDOWN_PREPARE);
        // Initiate shutdown immediately while in SHUTDOWN_PREPARE
        mPowerStateHandler.sendStateAndCheckResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY,
                VehicleApPowerStateReport.SHUTDOWN_START);
    }

    /**********************************************************************************************
     * Test cancelling of shutdown.
     **********************************************************************************************/
    @Test
    @UiThreadTest
    public void testCancelShutdownFromShutdownPrepare() throws Exception {
        assertWaitForVhal();
        mPowerStateHandler.sendStateAndCheckResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP,
                VehicleApPowerStateReport.SHUTDOWN_PREPARE);
        // Shutdown may only be cancelled from SHUTDOWN_PREPARE
        mPowerStateHandler.sendStateAndCheckResponse(
                VehicleApPowerStateReq.CANCEL_SHUTDOWN,
                0,
                VehicleApPowerStateReport.SHUTDOWN_CANCELLED);
    }

    /**********************************************************************************************
     * Test for invalid state transtions
     **********************************************************************************************/
    @Test
    @UiThreadTest
    public void testInivalidTransitionsFromWaitForVhal() throws Exception {
        assertWaitForVhal();
        mPowerStateHandler.sendStateAndExpectNoResponse(VehicleApPowerStateReq.CANCEL_SHUTDOWN, 0);
        mPowerStateHandler.sendStateAndExpectNoResponse(VehicleApPowerStateReq.FINISHED, 0);
    }

    @Test
    @UiThreadTest
    public void testInvalidTransitionsFromOn() throws Exception {
        assertWaitForVhal();
        // Transition to ON state first
        mPowerStateHandler.sendStateAndCheckResponse(
                VehicleApPowerStateReq.ON,
                0,
                VehicleApPowerStateReport.ON);
        mPowerStateHandler.sendStateAndExpectNoResponse(VehicleApPowerStateReq.CANCEL_SHUTDOWN, 0);
        mPowerStateHandler.sendStateAndExpectNoResponse(VehicleApPowerStateReq.FINISHED, 0);
    }

    @Test
    @UiThreadTest
    public void testInvalidTransitionsFromPrepareShutdown() throws Exception {
        assertWaitForVhal();
        // Increase the timeout to handle all the test cases here
        CarPowerManagementService.setShutdownPrepareTimeout(15 * 1000);
        // Transition to SHUTDOWN_PREPARE first
        mPowerStateHandler.sendStateAndCheckResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP,
                VehicleApPowerStateReport.SHUTDOWN_PREPARE);
        // Cannot go back to ON state from here
        mPowerStateHandler.sendStateAndExpectNoResponse(VehicleApPowerStateReq.ON, 0);
        // PREPARE_SHUTDOWN should not generate state transitions unless it's an IMMEDIATE_SHUTDOWN
        mPowerStateHandler.sendStateAndExpectNoResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP);
        mPowerStateHandler.sendStateAndExpectNoResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY);
        // Test the FINISH message last, in case SHUTDOWN_PREPARE finishes early and this test
        //  should be failing.
        mPowerStateHandler.sendStateAndExpectNoResponse(VehicleApPowerStateReq.FINISHED, 0);
    }

    @Test
    @UiThreadTest
    public void testInvalidTransitionsFromWaitForFinish() throws Exception {
        assertWaitForVhal();
        mPowerStateHandler.sendStateAndCheckResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP,
                VehicleApPowerStateReport.DEEP_SLEEP_ENTRY);
        // Once the device has entered WAIT_FOR_FINISH, shutdown cannot be cancelled.
        mPowerStateHandler.sendStateAndExpectNoResponse(VehicleApPowerStateReq.CANCEL_SHUTDOWN, 0);
        mPowerStateHandler.sendStateAndExpectNoResponse(VehicleApPowerStateReq.ON, 0);
        mPowerStateHandler.sendStateAndExpectNoResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP);
        mPowerStateHandler.sendStateAndExpectNoResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY);
        // TODO:  This state may be allowed in the future, if we decide it's necessary
        mPowerStateHandler.sendStateAndExpectNoResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY);
    }

    @Test
    @UiThreadTest
    public void testInvalidTransitionsFromWaitForFinish2() throws Exception {
        assertWaitForVhal();
        mPowerStateHandler.sendStateAndCheckResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY,
                VehicleApPowerStateReport.SHUTDOWN_START);
        // Once the device has entered WAIT_FOR_FINISH, shutdown cannot be cancelled.
        mPowerStateHandler.sendStateAndExpectNoResponse(VehicleApPowerStateReq.CANCEL_SHUTDOWN, 0);
        mPowerStateHandler.sendStateAndExpectNoResponse(VehicleApPowerStateReq.ON, 0);
        mPowerStateHandler.sendStateAndExpectNoResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP);
        mPowerStateHandler.sendStateAndExpectNoResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY);
        // TODO:  This state may be allowed in the future, if we decide it's necessary
        mPowerStateHandler.sendStateAndExpectNoResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY);
    }

    /**********************************************************************************************
     * Test sleep entry
     **********************************************************************************************/
    // This test also verifies the display state as the device goes in and out of suspend.
    @Test
    @UiThreadTest
    public void testSleepEntry() throws Exception {
        assertWaitForVhal();
        mMockDisplayInterface.waitForDisplayState(false);
        mPowerStateHandler.sendStateAndCheckResponse(
                VehicleApPowerStateReq.ON,
                0,
                VehicleApPowerStateReport.ON);
        mMockDisplayInterface.waitForDisplayState(true);
        mPowerStateHandler.sendPowerState(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP);
        assertResponse(VehicleApPowerStateReport.SHUTDOWN_PREPARE, 0, true);
        mMockDisplayInterface.waitForDisplayState(false);
        assertResponse(VehicleApPowerStateReport.DEEP_SLEEP_ENTRY, 0, false);
        mMockDisplayInterface.waitForDisplayState(false);
        mPowerStateHandler.sendPowerState(VehicleApPowerStateReq.FINISHED, 0);
        assertResponse(VehicleApPowerStateReport.DEEP_SLEEP_EXIT, 0, true);
        mMockDisplayInterface.waitForDisplayState(false);
    }

    private void assertResponse(int expectedState, int expectedParam, boolean checkParam)
            throws Exception {
        LinkedList<int[]> setEvents = mPowerStateHandler.waitForStateSetAndGetAll(
                DEFAULT_WAIT_TIMEOUT_MS, expectedState);
        int[] last = setEvents.getLast();
        assertEquals(expectedState, last[0]);
        if (checkParam) {
            assertEquals(expectedParam, last[1]);
        }
    }

    private void assertWaitForVhal() throws Exception {
        mPowerStateHandler.waitForSubscription(DEFAULT_WAIT_TIMEOUT_MS);
        LinkedList<int[]> setEvents = mPowerStateHandler.waitForStateSetAndGetAll(
                DEFAULT_WAIT_TIMEOUT_MS, VehicleApPowerStateReport.WAIT_FOR_VHAL);
        int[] first = setEvents.getFirst();
        assertEquals(VehicleApPowerStateReport.WAIT_FOR_VHAL, first[0]);
        assertEquals(0, first[1]);
        CarPowerManagementService.setShutdownPrepareTimeout(0);
    }

    private final class MockDisplayInterface implements DisplayInterface {
        private boolean mDisplayOn = true;
        private final Semaphore mDisplayStateWait = new Semaphore(0);

        @Override
        public void setDisplayBrightness(int brightness) {}

        @Override
        public synchronized void setDisplayState(boolean on) {
            mDisplayOn = on;
            mDisplayStateWait.release();
        }

        boolean waitForDisplayState(boolean expectedState)
            throws Exception {
            if (expectedState == mDisplayOn) {
                return true;
            }
            mDisplayStateWait.tryAcquire(MockedCarTestBase.SHORT_WAIT_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS);
            return expectedState == mDisplayOn;
        }

        @Override
        public void startDisplayStateMonitoring(CarPowerManagementService service) {}

        @Override
        public void stopDisplayStateMonitoring() {}

        @Override
        public void refreshDisplayBrightness() {}

        @Override
        public void reconfigureSecondaryDisplays() {}
    }

    private class PowerStatePropertyHandler implements VehicleHalPropertyHandler {

        private int mPowerState = VehicleApPowerStateReq.ON;
        private int mPowerParam = 0;

        private final Semaphore mSubscriptionWaitSemaphore = new Semaphore(0);
        private final Semaphore mSetWaitSemaphore = new Semaphore(0);
        private LinkedList<int[]> mSetStates = new LinkedList<>();

        @Override
        public void onPropertySet(VehiclePropValue value) {
            ArrayList<Integer> v = value.value.int32Values;
            synchronized (this) {
                mSetStates.add(new int[] {
                        v.get(VehicleApPowerStateReqIndex.STATE),
                        v.get(VehicleApPowerStateReqIndex.ADDITIONAL)
                });
            }
            mSetWaitSemaphore.release();
        }

        @Override
        public synchronized VehiclePropValue onPropertyGet(VehiclePropValue value) {
            return VehiclePropValueBuilder.newBuilder(VehicleProperty.AP_POWER_STATE_REQ)
                    .setTimestamp(SystemClock.elapsedRealtimeNanos())
                    .addIntValue(mPowerState, mPowerParam)
                    .build();
        }

        @Override
        public void onPropertySubscribe(int property, float sampleRate) {
            mSubscriptionWaitSemaphore.release();
        }

        @Override
        public void onPropertyUnsubscribe(int property) {
            //ignore
        }

        private synchronized void setCurrentState(int state, int param) {
            mPowerState = state;
            mPowerParam = param;
        }

        private void waitForSubscription(long timeoutMs) throws Exception {
            if (!mSubscriptionWaitSemaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                fail("waitForSubscription timeout");
            }
        }

        private LinkedList<int[]> waitForStateSetAndGetAll(long timeoutMs, int expectedSet)
                throws Exception {
            while (true) {
                if (!mSetWaitSemaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                    fail("waitForStateSetAndGetAll timeout");
                }
                synchronized (this) {
                    boolean found = false;
                    for (int[] state : mSetStates) {
                        if (state[0] == expectedSet) {
                            found = true;
                        }
                    }
                    if (found) {
                        LinkedList<int[]> res = mSetStates;
                        mSetStates = new LinkedList<>();
                        return res;
                    }
                }
            }
        }

        private void sendStateAndCheckResponse(int state, int param, int expectedSet)
                throws Exception {
            sendPowerState(state, param);
            waitForStateSetAndGetAll(DEFAULT_WAIT_TIMEOUT_MS, expectedSet);
        }

        /**
         * Checks that a power state transition does NOT occur.  If any state does occur during
         * the timeout period, then the test fails.
         */
        private void sendStateAndExpectNoResponse(int state, int param) throws Exception {
            sendPowerState(state, param);
            // Wait to see if a state transition occurs
            if (!mSetWaitSemaphore.tryAcquire(DEFAULT_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                // No state transition, this is a success!
                return;
            } else {
                synchronized (this) {
                    int[] newState = mSetStates.pop();
                    if (newState[0] != VehicleApPowerStateReport.SHUTDOWN_POSTPONE) {
                        fail("Unexpected state change occured, state=" + newState[0]);
                    }
                    // Reset the collected states
                    mSetStates = new LinkedList<>();
                }
            }
        }

        private void sendPowerState(int state, int param) {
            getMockedVehicleHal().injectEvent(
                    VehiclePropValueBuilder.newBuilder(VehicleProperty.AP_POWER_STATE_REQ)
                            .setTimestamp(SystemClock.elapsedRealtimeNanos())
                            .addIntValue(state, param)
                            .build());
        }
    }
}
