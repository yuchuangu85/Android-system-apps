/*
 * Copyright 2018 The Android Open Source Project
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
package com.android.bluetooth.hid;

import static org.mockito.Mockito.*;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ServiceTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.R;
import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.storage.DatabaseManager;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class HidHostServiceTest {
    private HidHostService mService = null;
    private BluetoothAdapter mAdapter = null;
    private BluetoothDevice mTestDevice;
    private Context mTargetContext;

    @Rule public final ServiceTestRule mServiceRule = new ServiceTestRule();

    @Mock private AdapterService mAdapterService;
    @Mock private DatabaseManager mDatabaseManager;

    @Before
    public void setUp() throws Exception {
        mTargetContext = InstrumentationRegistry.getTargetContext();
        Assume.assumeTrue("Ignore test when HidHostService is not enabled",
                mTargetContext.getResources().getBoolean(R.bool.profile_supported_hid_host));
        MockitoAnnotations.initMocks(this);
        TestUtils.setAdapterService(mAdapterService);
        TestUtils.startService(mServiceRule, HidHostService.class);
        mService = HidHostService.getHidHostService();
        Assert.assertNotNull(mService);
        // Try getting the Bluetooth adapter
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        Assert.assertNotNull(mAdapter);

        // Get a device for testing
        mTestDevice = TestUtils.getTestDevice(mAdapter, 0);
    }

    @After
    public void tearDown() throws Exception {
        if (!mTargetContext.getResources().getBoolean(R.bool.profile_supported_hid_host)) {
            return;
        }
        TestUtils.stopService(mServiceRule, HidHostService.class);
        mService = HidHostService.getHidHostService();
        Assert.assertNull(mService);
        TestUtils.clearAdapterService(mAdapterService);
    }

    @Test
    public void testInitialize() {
        Assert.assertNotNull(HidHostService.getHidHostService());
    }

    /**
     *  Test okToConnect method using various test cases
     */
    @Test
    public void testOkToConnect() {
        int badPriorityValue = 1024;
        int badBondState = 42;
        testOkToConnectCase(mTestDevice,
                BluetoothDevice.BOND_NONE, BluetoothProfile.PRIORITY_UNDEFINED, false);
        testOkToConnectCase(mTestDevice,
                BluetoothDevice.BOND_NONE, BluetoothProfile.PRIORITY_OFF, false);
        testOkToConnectCase(mTestDevice,
                BluetoothDevice.BOND_NONE, BluetoothProfile.PRIORITY_ON, false);
        testOkToConnectCase(mTestDevice,
                BluetoothDevice.BOND_NONE, BluetoothProfile.PRIORITY_AUTO_CONNECT, false);
        testOkToConnectCase(mTestDevice,
                BluetoothDevice.BOND_NONE, badPriorityValue, false);
        testOkToConnectCase(mTestDevice,
                BluetoothDevice.BOND_BONDING, BluetoothProfile.PRIORITY_UNDEFINED, false);
        testOkToConnectCase(mTestDevice,
                BluetoothDevice.BOND_BONDING, BluetoothProfile.PRIORITY_OFF, false);
        testOkToConnectCase(mTestDevice,
                BluetoothDevice.BOND_BONDING, BluetoothProfile.PRIORITY_ON, false);
        testOkToConnectCase(mTestDevice,
                BluetoothDevice.BOND_BONDING, BluetoothProfile.PRIORITY_AUTO_CONNECT, false);
        testOkToConnectCase(mTestDevice,
                BluetoothDevice.BOND_BONDING, badPriorityValue, false);
        testOkToConnectCase(mTestDevice,
                BluetoothDevice.BOND_BONDED, BluetoothProfile.PRIORITY_UNDEFINED, true);
        testOkToConnectCase(mTestDevice,
                BluetoothDevice.BOND_BONDED, BluetoothProfile.PRIORITY_OFF, false);
        testOkToConnectCase(mTestDevice,
                BluetoothDevice.BOND_BONDED, BluetoothProfile.PRIORITY_ON, true);
        testOkToConnectCase(mTestDevice,
                BluetoothDevice.BOND_BONDED, BluetoothProfile.PRIORITY_AUTO_CONNECT, true);
        testOkToConnectCase(mTestDevice,
                BluetoothDevice.BOND_BONDED, badPriorityValue, false);
        testOkToConnectCase(mTestDevice,
                badBondState, BluetoothProfile.PRIORITY_UNDEFINED, false);
        testOkToConnectCase(mTestDevice,
                badBondState, BluetoothProfile.PRIORITY_OFF, false);
        testOkToConnectCase(mTestDevice,
                badBondState, BluetoothProfile.PRIORITY_ON, false);
        testOkToConnectCase(mTestDevice,
                badBondState, BluetoothProfile.PRIORITY_AUTO_CONNECT, false);
        testOkToConnectCase(mTestDevice,
                badBondState, badPriorityValue, false);
        // Restore prirority to undefined for this test device
        Assert.assertTrue(mService.setPriority(
                mTestDevice, BluetoothProfile.PRIORITY_UNDEFINED));
    }

    /**
     * Helper function to test okToConnect() method.
     *
     * @param device test device
     * @param bondState bond state value, could be invalid
     * @param priority value, could be invalid, could be invalid
     * @param expected expected result from okToConnect()
     */
    private void testOkToConnectCase(BluetoothDevice device, int bondState, int priority,
            boolean expected) {
        doReturn(bondState).when(mAdapterService).getBondState(device);
        when(mAdapterService.getDatabase()).thenReturn(mDatabaseManager);
        when(mDatabaseManager.getProfilePriority(device, BluetoothProfile.HID_HOST))
                .thenReturn(priority);

        // Test when the AdapterService is in non-quiet mode.
        doReturn(false).when(mAdapterService).isQuietModeEnabled();
        Assert.assertEquals(expected, mService.okToConnect(device));

        // Test when the AdapterService is in quiet mode.
        doReturn(true).when(mAdapterService).isQuietModeEnabled();
        Assert.assertEquals(false, mService.okToConnect(device));
    }

}
