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

package com.android.car;

import static org.mockito.Mockito.*;

import android.bluetooth.BluetoothAdapter;
import android.car.hardware.power.CarPowerManager;
import android.car.hardware.power.CarPowerManager.CarPowerStateListenerWithCompletion;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.provider.Settings;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Unit tests for {@link BluetoothDeviceConnectionPolicy}
 *
 * Run:
 * atest BluetoothDeviceConnectionPolicyTest
 */
@RunWith(AndroidJUnit4.class)
public class BluetoothDeviceConnectionPolicyTest {
    private BluetoothDeviceConnectionPolicy mPolicy;

    @Mock private Context mMockContext;
    @Mock private Resources mMockResources;
    private MockContentResolver mMockContentResolver;
    private MockContentProvider mMockContentProvider;
    @Mock private PackageManager mMockPackageManager;
    private final int mUserId = 10;
    @Mock private CarBluetoothService mMockBluetoothService;

    private BluetoothAdapterHelper mBluetoothAdapterHelper;
    private BroadcastReceiver mReceiver;
    private CarPowerStateListenerWithCompletion mPowerStateListener;

    //--------------------------------------------------------------------------------------------//
    // Setup/TearDown                                                                             //
    //--------------------------------------------------------------------------------------------//

    @Before
    public void setUp() {
        mMockContentResolver = new MockContentResolver(mMockContext);
        mMockContentProvider = new MockContentProvider() {
            @Override
            public Bundle call(String method, String request, Bundle args) {
                return new Bundle();
            }
        };
        mMockContentResolver.addProvider(Settings.AUTHORITY, mMockContentProvider);

        MockitoAnnotations.initMocks(this);
        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mMockContext.getContentResolver()).thenReturn(mMockContentResolver);
        when(mMockContext.getApplicationContext()).thenReturn(mMockContext);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);

        // Make sure we grab and store the bluetooth broadcast receiver object
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Object[] arguments = invocation.getArguments();
                if (arguments != null && arguments.length == 5 && arguments[0] != null) {
                    mReceiver = (BroadcastReceiver) arguments[0];
                }
                return null;
            }
        }).when(mMockContext).registerReceiverAsUser(any(BroadcastReceiver.class), any(), any(),
                any(), any());

        mBluetoothAdapterHelper = new BluetoothAdapterHelper();
        mBluetoothAdapterHelper.init();

        mPolicy = BluetoothDeviceConnectionPolicy.create(mMockContext, mUserId,
                mMockBluetoothService);
        Assert.assertTrue(mPolicy != null);
    }

    @After
    public void tearDown() {
        mPowerStateListener = null;
        mPolicy.release();
        mPolicy = null;
        mBluetoothAdapterHelper.release();
        mBluetoothAdapterHelper = null;
        mReceiver = null;
        mMockBluetoothService = null;
        mMockResources = null;
        mMockContext = null;
        mMockContentProvider = null;
        mMockContentResolver = null;
    }

    //--------------------------------------------------------------------------------------------//
    // Utilities                                                                                  //
    //--------------------------------------------------------------------------------------------//

    private void sendAdapterStateChanged(int newState) {
        if (mReceiver != null) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_STATE_CHANGED);
            intent.putExtra(BluetoothAdapter.EXTRA_STATE, newState);
            mReceiver.onReceive(null, intent);
        }
    }

    private void sendPowerStateChanged(int newState) {
        if (mPowerStateListener != null) {
            mPowerStateListener.onStateChanged(newState, null);
        }
    }

    //--------------------------------------------------------------------------------------------//
    // Policy Init tests                                                                          //
    //--------------------------------------------------------------------------------------------//

    /**
     * Preconditions:
     * - Adapter is on
     *
     * Action:
     * - Initialize the policy
     *
     * Outcome:
     * - Because the Adapter is ON at init time, we should attempt to connect devices
     */
    @Test
    public void testInitWithAdapterOn_connectDevices() {
        mBluetoothAdapterHelper.forceAdapterOn();
        mPolicy.init();
        verify(mMockBluetoothService, times(1)).connectDevices();
    }

    /**
     * Preconditions:
     * - Adapter is off
     *
     * Action:
     * - Initialize the policy
     *
     * Outcome:
     * - Because the Adapter is OFF at init time, we should not attempt to connect devices
     */
    @Test
    public void testInitWithAdapterOff_doNothing() {
        mBluetoothAdapterHelper.forceAdapterOff();
        mPolicy.init();
        verify(mMockBluetoothService, times(0)).connectDevices();
    }

    //--------------------------------------------------------------------------------------------//
    // Car Power Manager state changed event tests                                                //
    //--------------------------------------------------------------------------------------------//

    /**
     * Preconditions:
     * - Adapter is on
     *
     * Action:
     * - Receive a SHUTDOWN_PREPARE call
     *
     * Outcome:
     * - Adapter is turned off but the ON state is persisted
     */
    @Test
    public void testReceivePowerShutdownPrepare_disableBluetooth() {
        mBluetoothAdapterHelper.forceAdapterOn();
        mPolicy.init();
        mPowerStateListener = mPolicy.getCarPowerStateListener();
        reset(mMockBluetoothService);

        sendPowerStateChanged(CarPowerManager.CarPowerStateListener.SHUTDOWN_PREPARE);
        mBluetoothAdapterHelper.waitForAdapterOff();
        Assert.assertTrue(mBluetoothAdapterHelper.isAdapterPersistedOn());
    }

    /**
     * Preconditions:
     * - Adapter is off and is persisted off
     * - Policy is initialized
     *
     * Action:
     * - Power state ON is received
     *
     * Outcome:
     * - Because the Adapter is persisted off, we should do nothing. The adapter should remain off
     */
    @Test
    public void testReceivePowerOnBluetoothPersistedOff_doNothing() {
        mBluetoothAdapterHelper.forceAdapterOff();
        mPolicy.init();
        mPowerStateListener = mPolicy.getCarPowerStateListener();
        reset(mMockBluetoothService);

        sendPowerStateChanged(CarPowerManager.CarPowerStateListener.ON);
        mBluetoothAdapterHelper.waitForAdapterOff();
    }

     /**
     * Preconditions:
     * - Adapter is off and is not persisted off
     * - Policy is initialized
     *
     * Action:
     * - Power state ON is received
     *
     * Outcome:
     * - Because the Adapter is not persisted off, we should turn it back on. No attemp to connect
     *   devices is made because we're yielding to the adapter ON event.
     */
    @Test
    public void testReceivePowerOnBluetoothOffNotPersisted_BluetoothOnConnectDevices() {
        mBluetoothAdapterHelper.forceAdapterOffDoNotPersist();
        mPolicy.init();
        mPowerStateListener = mPolicy.getCarPowerStateListener();
        reset(mMockBluetoothService);

        sendPowerStateChanged(CarPowerManager.CarPowerStateListener.ON);
        verify(mMockBluetoothService, times(0)).connectDevices();
        mBluetoothAdapterHelper.waitForAdapterOn();
    }

    /**
     * Preconditions:
     * - Adapter is on
     * - Policy is initialized
     *
     * Action:
     * - Power state ON is received
     *
     * Outcome:
     * - Because the Adapter on, we should attempt to connect devices
     */
    @Test
    public void testReceivePowerOnBluetoothOn_connectDevices() {
        mBluetoothAdapterHelper.forceAdapterOn();
        mPolicy.init();
        mPowerStateListener = mPolicy.getCarPowerStateListener();
        reset(mMockBluetoothService);

        sendPowerStateChanged(CarPowerManager.CarPowerStateListener.ON);
        verify(mMockBluetoothService, times(1)).connectDevices();
    }

    //--------------------------------------------------------------------------------------------//
    // Bluetooth stack adapter status changed event tests                                         //
    //--------------------------------------------------------------------------------------------//

    /**
     * Preconditions:
     * - Policy is initialized
     *
     * Action:
     * - Adapter state TURNING_OFF is received
     *
     * Outcome:
     * - No nothing
     */
    @Test
    public void testReceiveAdapterTurningOff_doNothing() {
        mPolicy.init();
        mPowerStateListener = mPolicy.getCarPowerStateListener();
        reset(mMockBluetoothService);

        sendAdapterStateChanged(BluetoothAdapter.STATE_TURNING_OFF);
        verify(mMockBluetoothService, times(0)).connectDevices();
    }

    /**
     * Preconditions:
     * - Policy is initialized
     *
     * Action:
     * - Adapter state OFF is received
     *
     * Outcome:
     * - No nothing
     */
    @Test
    public void testReceiveAdapterOff_doNothing() {
        mPolicy.init();
        mPowerStateListener = mPolicy.getCarPowerStateListener();
        reset(mMockBluetoothService);

        sendAdapterStateChanged(BluetoothAdapter.STATE_OFF);
        verify(mMockBluetoothService, times(0)).connectDevices();
    }

    /**
     * Preconditions:
     * - Policy is initialized
     *
     * Action:
     * - Adapter state TURNING_ON is received
     *
     * Outcome:
     * - No nothing
     */
    @Test
    public void testReceiveAdapterTurningOn_doNothing() {
        mPolicy.init();
        mPowerStateListener = mPolicy.getCarPowerStateListener();
        reset(mMockBluetoothService);

        sendAdapterStateChanged(BluetoothAdapter.STATE_TURNING_ON);
        verify(mMockBluetoothService, times(0)).connectDevices();
    }

    /**
     * Preconditions:
     * - Policy is initialized
     *
     * Action:
     * - Adapter state ON is received
     *
     * Outcome:
     * - Attempt to connect devices
     */
    @Test
    public void testReceiveAdapterOn_connectDevices() {
        mPolicy.init();
        mPowerStateListener = mPolicy.getCarPowerStateListener();
        reset(mMockBluetoothService);

        sendAdapterStateChanged(BluetoothAdapter.STATE_ON);
        verify(mMockBluetoothService, times(1)).connectDevices();
    }
}
