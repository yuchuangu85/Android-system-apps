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

import static android.car.settings.CarSettings.Secure.KEY_BLUETOOTH_HFP_CLIENT_DEVICES;

import static org.mockito.Mockito.*;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadsetClient;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.car.ICarBluetoothUserService;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.UserHandle;
import android.provider.Settings;
import android.test.mock.MockContentResolver;
import android.test.suitebuilder.annotation.Suppress;
import android.text.TextUtils;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.util.test.BroadcastInterceptingContext;
import com.android.internal.util.test.FakeSettingsProvider;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for {@link BluetoothProfileDeviceManager}
 *
 * Run:
 * atest BluetoothProfileDeviceManagerTest
 */
@RunWith(AndroidJUnit4.class)
public class BluetoothProfileDeviceManagerTest {
    private static final int CONNECT_LATENCY_MS = 100;
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int ADAPTER_STATE_ANY = 0;
    private static final int ADAPTER_STATE_OFF = 1;
    private static final int ADAPTER_STATE_OFF_NOT_PERSISTED = 2;
    private static final int ADAPTER_STATE_ON = 3;

    private static final List<String> EMPTY_DEVICE_LIST = Arrays.asList();
    private static final List<String> SINGLE_DEVICE_LIST = Arrays.asList("DE:AD:BE:EF:00:00");
    private static final List<String> SMALL_DEVICE_LIST = Arrays.asList(
            "DE:AD:BE:EF:00:00",
            "DE:AD:BE:EF:00:01",
            "DE:AD:BE:EF:00:02");
    private static final List<String> LARGE_DEVICE_LIST = Arrays.asList(
            "DE:AD:BE:EF:00:00",
            "DE:AD:BE:EF:00:01",
            "DE:AD:BE:EF:00:02",
            "DE:AD:BE:EF:00:03",
            "DE:AD:BE:EF:00:04",
            "DE:AD:BE:EF:00:05",
            "DE:AD:BE:EF:00:06",
            "DE:AD:BE:EF:00:07");

    private static final String EMPTY_SETTINGS_STRING = "";
    private static final String SINGLE_SETTINGS_STRING = makeSettingsString(SINGLE_DEVICE_LIST);
    private static final String SMALL_SETTINGS_STRING = makeSettingsString(SMALL_DEVICE_LIST);
    private static final String LARGE_SETTINGS_STRING = makeSettingsString(LARGE_DEVICE_LIST);

    BluetoothProfileDeviceManager mProfileDeviceManager;

    private final int mUserId = 0;
    private final int mProfileId = BluetoothProfile.HEADSET_CLIENT;
    private final String mSettingsKey = KEY_BLUETOOTH_HFP_CLIENT_DEVICES;
    private final String mConnectionAction = BluetoothHeadsetClient.ACTION_CONNECTION_STATE_CHANGED;
    private ParcelUuid[] mUuids = new ParcelUuid[] {
            BluetoothUuid.Handsfree_AG,
            BluetoothUuid.HSP_AG};
    private ParcelUuid[] mBadUuids = new ParcelUuid[] {
            BluetoothUuid.PANU};
    private final int[] mProfileTriggers = new int[] {
            BluetoothProfile.MAP_CLIENT,
            BluetoothProfile.PBAP_CLIENT};

    @Mock private ICarBluetoothUserService mMockProxies;
    private Handler mHandler;
    private static final Object HANDLER_TOKEN = new Object();

    private MockContext mMockContext;

    private BluetoothAdapterHelper mBluetoothAdapterHelper;
    private BluetoothAdapter mBluetoothAdapter;

    public class MockContext extends BroadcastInterceptingContext {
        private MockContentResolver mContentResolver;
        private FakeSettingsProvider mContentProvider;

        MockContext(Context base) {
            super(base);
            FakeSettingsProvider.clearSettingsProvider();
            mContentResolver = new MockContentResolver(this);
            mContentProvider = new FakeSettingsProvider();
            mContentResolver.addProvider(Settings.AUTHORITY, mContentProvider);
        }

        public void release() {
            FakeSettingsProvider.clearSettingsProvider();
        }

        @Override
        public ContentResolver getContentResolver() {
            return mContentResolver;
        }

        @Override
        public Intent registerReceiverAsUser(BroadcastReceiver receiver, UserHandle user,
                IntentFilter filter, String broadcastPermission, Handler scheduler) {
            // We're basically ignoring the userhandle since the tests only assume one user anyway.
            // BroadcastInterceptingContext doesn't implement this hook either so this has to do.
            return super.registerReceiver(receiver, filter);
        }
    }

    //--------------------------------------------------------------------------------------------//
    // Setup/TearDown                                                                             //
    //--------------------------------------------------------------------------------------------//

    @Before
    public void setUp() {

        MockitoAnnotations.initMocks(this);

        mMockContext = new MockContext(InstrumentationRegistry.getTargetContext());
        setSettingsDeviceList("");
        assertSettingsContains("");

        mBluetoothAdapterHelper = new BluetoothAdapterHelper();
        mBluetoothAdapterHelper.init();

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Assert.assertTrue(mBluetoothAdapter != null);

        mHandler = new Handler(Looper.getMainLooper());

        mProfileDeviceManager = BluetoothProfileDeviceManager.create(mMockContext, mUserId,
                mMockProxies, mProfileId);
        Assert.assertTrue(mProfileDeviceManager != null);
    }

    @After
    public void tearDown() {
        if (mProfileDeviceManager != null) {
            mProfileDeviceManager.stop();
            mProfileDeviceManager = null;
        }
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(HANDLER_TOKEN);
            mHandler = null;
        }
        mBluetoothAdapter = null;
        if (mBluetoothAdapterHelper != null) {
            mBluetoothAdapterHelper.release();
            mBluetoothAdapterHelper = null;
        }
        mMockProxies = null;
        if (mMockContext != null) {
            mMockContext.release();
            mMockContext = null;
        }
    }

    //--------------------------------------------------------------------------------------------//
    // Utilities                                                                                  //
    //--------------------------------------------------------------------------------------------//

    private void setSettingsDeviceList(String devicesStr) {
        Settings.Secure.putStringForUser(mMockContext.getContentResolver(), mSettingsKey,
                devicesStr, mUserId);
    }

    private String getSettingsDeviceList() {
        String devices = Settings.Secure.getStringForUser(mMockContext.getContentResolver(),
                mSettingsKey, mUserId);
        if (devices == null) devices = "";
        return devices;
    }

    private ArrayList<BluetoothDevice> makeDeviceList(List<String> addresses) {
        ArrayList<BluetoothDevice> devices = new ArrayList<>();
        for (String address : addresses) {
            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
            if (device == null) continue;
            devices.add(device);
        }
        return devices;
    }

    private static String makeSettingsString(List<String> addresses) {
        return TextUtils.join(",", addresses);
    }

    private void setPreconditionsAndStart(int adapterState, String settings,
            List<String> devices) {
        switch (adapterState) {
            case ADAPTER_STATE_ON:
                mBluetoothAdapterHelper.forceAdapterOn();
                break;
            case ADAPTER_STATE_OFF:
                mBluetoothAdapterHelper.forceAdapterOff();
                break;
            case ADAPTER_STATE_OFF_NOT_PERSISTED:
                mBluetoothAdapterHelper.forceAdapterOffDoNotPersist();
                break;
            case ADAPTER_STATE_ANY:
                break;
            default:
                break;
        }

        setSettingsDeviceList(settings);

        mProfileDeviceManager.start();

        for (BluetoothDevice device : makeDeviceList(devices)) {
            mProfileDeviceManager.addDevice(device);
        }
    }

    private void mockDeviceAvailability(BluetoothDevice device, boolean available)
            throws Exception {
        doAnswer(new Answer<Boolean>() {
            @Override
            public Boolean answer(InvocationOnMock invocation) throws Throwable {
                Object[] arguments = invocation.getArguments();
                if (arguments != null && arguments.length == 2 && arguments[1] != null) {
                    BluetoothDevice device = (BluetoothDevice) arguments[1];
                    int state = (available
                            ? BluetoothProfile.STATE_CONNECTED
                            : BluetoothProfile.STATE_DISCONNECTED);
                    mHandler.postDelayed(() -> {
                        sendConnectionStateChanged(device, state);
                    }, HANDLER_TOKEN, CONNECT_LATENCY_MS);
                }
                return true;
            }
        }).when(mMockProxies).bluetoothConnectToProfile(mProfileId, device);
    }

    private void captureDevicePriority(BluetoothDevice device) throws Exception {
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Object[] arguments = invocation.getArguments();
                if (arguments != null && arguments.length == 3 && arguments[1] != null) {
                    BluetoothDevice device = (BluetoothDevice) arguments[1];
                    int priority = (int) arguments[2];
                    mockDevicePriority(device, priority);
                }
                return null;
            }
        }).when(mMockProxies).setProfilePriority(mProfileId, device, anyInt());
    }

    private void mockDevicePriority(BluetoothDevice device, int priority) throws Exception {
        when(mMockProxies.getProfilePriority(mProfileId, device)).thenReturn(priority);
    }

    private void sendAdapterStateChanged(int newState) {
        Assert.assertTrue(mMockContext != null);
        Intent intent = new Intent(BluetoothAdapter.ACTION_STATE_CHANGED);
        intent.putExtra(BluetoothAdapter.EXTRA_STATE, newState);
        mMockContext.sendBroadcast(intent);
    }

    private void sendBondStateChanged(BluetoothDevice device, int newState) {
        Assert.assertTrue(mMockContext != null);
        Intent intent = new Intent(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothDevice.EXTRA_BOND_STATE, newState);
        mMockContext.sendBroadcast(intent);
    }

    private void sendDeviceUuids(BluetoothDevice device, ParcelUuid[] uuids) {
        Assert.assertTrue(mMockContext != null);
        Intent intent = new Intent(BluetoothDevice.ACTION_UUID);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothDevice.EXTRA_UUID, uuids);
        mMockContext.sendBroadcast(intent);
    }

    private void sendConnectionStateChanged(BluetoothDevice device, int newState) {
        Assert.assertTrue(mMockContext != null);
        Intent intent = new Intent(mConnectionAction);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, newState);
        mMockContext.sendBroadcast(intent);
    }

    private synchronized void assertSettingsContains(String expected) {
        Assert.assertTrue(expected != null);
        String settings = getSettingsDeviceList();
        if (settings == null) settings = "";
        Assert.assertEquals(expected, settings);
    }

    private void assertDeviceList(List<String> expected) {
        ArrayList<BluetoothDevice> devices = mProfileDeviceManager.getDeviceListSnapshot();
        ArrayList<BluetoothDevice> expectedDevices = makeDeviceList(expected);
        Assert.assertEquals(expectedDevices, devices);
    }

    //--------------------------------------------------------------------------------------------//
    // Load from persistent memory tests                                                          //
    //--------------------------------------------------------------------------------------------//

    /**
     * Preconditions:
     * - Settings contains no devices
     *
     * Actions:
     * - Initialize the device manager
     *
     * Outcome:
     * - device manager should initialize
     */
    @Test
    public void testEmptySettingsString_loadNoDevices() {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, EMPTY_DEVICE_LIST);
        assertDeviceList(EMPTY_DEVICE_LIST);
    }

    /**
     * Preconditions:
     * - Settings contains a single device
     *
     * Actions:
     * - Initialize the device manager
     *
     * Outcome:
     * - The single device is now located in the device manager's device list
     */
    @Test
    public void testSingleDeviceSettingsString_loadSingleDevice() {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, SINGLE_SETTINGS_STRING, EMPTY_DEVICE_LIST);
        assertDeviceList(SINGLE_DEVICE_LIST);
    }

    /**
     * Preconditions:
     * - Settings contains several devices
     *
     * Actions:
     * - Initialize the device manager
     *
     * Outcome:
     * - All devices are now in the device manager's list, all in the proper order.
     */
    @Test
    public void testSeveralDevicesSettingsString_loadAllDevices() {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, LARGE_SETTINGS_STRING, EMPTY_DEVICE_LIST);
        assertDeviceList(LARGE_DEVICE_LIST);
    }

    //--------------------------------------------------------------------------------------------//
    // Commit to persistent memory tests                                                          //
    //--------------------------------------------------------------------------------------------//

    /**
     * Preconditions:
     * - The device manager is initialized and the list contains no devices
     *
     * Actions:
     * - An event forces the device manager to commit it's list
     *
     * Outcome:
     * - The empty list should be written to Settings.Secure as an empty string, ""
     */
    @Test
    public void testNoDevicesCommit_commitEmptyDeviceString() {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, EMPTY_DEVICE_LIST);
        sendAdapterStateChanged(BluetoothAdapter.STATE_OFF);
        assertSettingsContains(EMPTY_SETTINGS_STRING);
    }

    /**
     * Preconditions:
     * - The device manager contains several devices
     *
     * Actions:
     * - An event forces the device manager to commit it's list
     *
     * Outcome:
     * - The ordered device list should be written to Settings.Secure as a comma separated list
     */
    @Test
    public void testSeveralDevicesCommit_commitAllDeviceString() {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, LARGE_DEVICE_LIST);
        sendAdapterStateChanged(BluetoothAdapter.STATE_OFF);
        assertSettingsContains(LARGE_SETTINGS_STRING);
    }

    //--------------------------------------------------------------------------------------------//
    // Add Device tests                                                                           //
    //--------------------------------------------------------------------------------------------//

    /**
     * Preconditions:
     * - The device manager is initialized and contains no devices.
     *
     * Actions:
     * - Add a single device
     *
     * Outcome:
     * - The device manager priority list contains the single device
     */
    @Test
    public void testAddSingleDevice_devicesAppearInPriorityList() {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, SINGLE_DEVICE_LIST);
        assertDeviceList(SINGLE_DEVICE_LIST);
    }

    /**
     * Preconditions:
     * - The device manager is initialized and contains no devices.
     *
     * Actions:
     * - Add several devices
     *
     * Outcome:
     * - The device manager priority list contains all devices, ordered properly
     */
    @Test
    public void testAddMultipleDevices_devicesAppearInPriorityList() {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, LARGE_DEVICE_LIST);
        assertDeviceList(LARGE_DEVICE_LIST);
    }

    /**
     * Preconditions:
     * - The device manager is initialized and contains one device
     *
     * Actions:
     * - Add the device that is already in the list
     *
     * Outcome:
     * - The device manager's list remains unchanged with only one device in it
     */
    @Test
    public void testAddDeviceAlreadyInList_priorityListUnchanged() {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, SINGLE_DEVICE_LIST);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(SINGLE_DEVICE_LIST.get(0));
        mProfileDeviceManager.addDevice(device);
        assertDeviceList(SINGLE_DEVICE_LIST);
    }

    //--------------------------------------------------------------------------------------------//
    // Remove Device tests                                                                        //
    //--------------------------------------------------------------------------------------------//

    /**
     * Preconditions:
     * - The device manager is initialized and contains no devices.
     *
     * Actions:
     * - Remove a device from the list
     *
     * Outcome:
     * - The device manager does not error out and continues to have an empty list
     */
    @Test
    public void testRemoveDeviceFromEmptyList_priorityListUnchanged() {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, EMPTY_DEVICE_LIST);
        mProfileDeviceManager.removeDevice(mBluetoothAdapter.getRemoteDevice("DE:AD:BE:EF:00:00"));
        assertDeviceList(EMPTY_DEVICE_LIST);
    }

    /**
     * Preconditions:
     * - The device manager is initialized and contains several devices.
     *
     * Actions:
     * - Remove the device with the highest priority (front of list)
     *
     * Outcome:
     * - The device manager removes the leading device. The other devices have been shifted down.
     */
    @Test
    public void testRemoveDeviceFront_deviceNoLongerInPriorityList() {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, SMALL_DEVICE_LIST);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(SINGLE_DEVICE_LIST.get(0));
        mProfileDeviceManager.removeDevice(device);
        ArrayList<String> expected = new ArrayList(SMALL_DEVICE_LIST);
        expected.remove(0);
        assertDeviceList(expected);
    }

    /**
     * Preconditions:
     * - The device manager is initialized and contains several devices.
     *
     * Actions:
     * - Remove a device from the middle of the list
     *
     * Outcome:
     * - The device manager removes the device. The other devices with larger priorities have been
     *   shifted down.
     */
    @Test
    public void testRemoveDeviceMiddle_deviceNoLongerInPriorityList() {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, SMALL_DEVICE_LIST);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(SMALL_DEVICE_LIST.get(1));
        mProfileDeviceManager.removeDevice(device);
        ArrayList<String> expected = new ArrayList(SMALL_DEVICE_LIST);
        expected.remove(1);
        assertDeviceList(expected);
    }

    /**
     * Preconditions:
     * - The device manager is initialized and contains several devices.
     *
     * Actions:
     * - Remove the device from the end of the list
     *
     * Outcome:
     * - The device manager removes the device. The other devices remain in their places, unchanged
     */
    @Test
    public void testRemoveDeviceEnd_deviceNoLongerInPriorityList() {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, SMALL_DEVICE_LIST);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(SMALL_DEVICE_LIST.get(2));
        mProfileDeviceManager.removeDevice(device);
        ArrayList<String> expected = new ArrayList(SMALL_DEVICE_LIST);
        expected.remove(2); // 00, 01
        assertDeviceList(expected);
    }

    /**
     * Preconditions:
     * - The device manager is initialized and contains several devices.
     *
     * Actions:
     * - Remove a device thats not in the list
     *
     * Outcome:
     * - The device manager's list remains unchanged.
     */
    @Test
    public void testRemoveDeviceNotInList_priorityListUnchanged() {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, SMALL_DEVICE_LIST);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice("10:20:30:40:50:60");
        mProfileDeviceManager.removeDevice(device);
        assertDeviceList(SMALL_DEVICE_LIST);
    }

    //--------------------------------------------------------------------------------------------//
    // GetDeviceConnectionPriority() tests                                                        //
    //--------------------------------------------------------------------------------------------//

    /**
     * Preconditions:
     * - The device manager is initialized and contains several devices.
     *
     * Actions:
     * - Get the priority of each device in the list
     *
     * Outcome:
     * - The device manager returns the proper priority for each device
     */
    @Test
    public void testGetConnectionPriority_prioritiesReturned() {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, SMALL_DEVICE_LIST);
        ArrayList<BluetoothDevice> devices = makeDeviceList(SMALL_DEVICE_LIST);
        for (int i = 0; i < devices.size(); i++) {
            int priority = mProfileDeviceManager.getDeviceConnectionPriority(devices.get(i));
            Assert.assertEquals(i, priority);
        }
    }

    /**
     * Preconditions:
     * - The device manager is initialized and contains several devices.
     *
     * Actions:
     * - Get the priority of a device that is not in the list
     *
     * Outcome:
     * - The device manager returns a -1
     */
    @Test
    public void testGetConnectionPriorityDeviceNotInList_negativeOneReturned() {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, SMALL_DEVICE_LIST);
        BluetoothDevice deviceNotPresent = mBluetoothAdapter.getRemoteDevice("10:20:30:40:50:60");
        int priority = mProfileDeviceManager.getDeviceConnectionPriority(deviceNotPresent);
        Assert.assertEquals(-1, priority);
    }

    //--------------------------------------------------------------------------------------------//
    // setDeviceConnectionPriority() tests                                                        //
    //--------------------------------------------------------------------------------------------//

    /**
     * Preconditions:
     * - The device manager is initialized and contains several devices.
     *
     * Actions:
     * - Set the priority of several devices in the list, testing the following moves:
     *      Mid priority -> higher priority
     *      Mid priority -> lower priority
     *      Highest priority -> lower priority
     *      Lowest priority -> higher priority
     *      Any priority -> same priority
     *
     * Outcome:
     * - Increased prioritied shuffle devices to proper lower priorities, decreased priorities
     *   shuffle devices to proper high priorities, and a request to set the same priority yields no
     *   change.
     */
    @Test
    public void testSetConnectionPriority_listOrderedCorrectly() {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, SMALL_DEVICE_LIST);

        // move middle device to front
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(SMALL_DEVICE_LIST.get(1));
        mProfileDeviceManager.setDeviceConnectionPriority(device, 0);
        Assert.assertEquals(0, mProfileDeviceManager.getDeviceConnectionPriority(device));
        ArrayList<String> expected = new ArrayList(SMALL_DEVICE_LIST);
        Collections.swap(expected, 1, 0); // expected: 00, [01], 02 -> [01], 00, 02
        assertDeviceList(expected);

        // move front device to the end
        mProfileDeviceManager.setDeviceConnectionPriority(device, 2);
        Assert.assertEquals(2, mProfileDeviceManager.getDeviceConnectionPriority(device));
        Collections.swap(expected, 0, 2); // expected: [01], 00, 02 -> 00, 02, [01]
        Collections.swap(expected, 0, 1);
        assertDeviceList(expected);

        // move end device to middle
        mProfileDeviceManager.setDeviceConnectionPriority(device, 1);
        Assert.assertEquals(1, mProfileDeviceManager.getDeviceConnectionPriority(device));
        Collections.swap(expected, 2, 1); // expected: 00, 02, [01] -> 00, [01], 02
        assertDeviceList(expected);

        // move middle to end
        mProfileDeviceManager.setDeviceConnectionPriority(device, 2);
        Assert.assertEquals(2, mProfileDeviceManager.getDeviceConnectionPriority(device));
        Collections.swap(expected, 1, 2); // expected: 00, [01], 02 -> 00, 02, [01]
        assertDeviceList(expected);

        // move end to front
        mProfileDeviceManager.setDeviceConnectionPriority(device, 0);
        Assert.assertEquals(0, mProfileDeviceManager.getDeviceConnectionPriority(device));
        Collections.swap(expected, 2, 0); // expected: 00, 02, [01] -> [01], 00, 02
        Collections.swap(expected, 1, 2);
        assertDeviceList(expected);

        // move front to middle
        mProfileDeviceManager.setDeviceConnectionPriority(device, 1);
        Assert.assertEquals(1, mProfileDeviceManager.getDeviceConnectionPriority(device));
        Collections.swap(expected, 0, 1); // expected: [01], 00, 02 -> 00, [01], 02
        assertDeviceList(expected);

        // move middle to middle (i.e same to same)
        mProfileDeviceManager.setDeviceConnectionPriority(device, 1);
        Assert.assertEquals(1, mProfileDeviceManager.getDeviceConnectionPriority(device));
        assertDeviceList(expected); // expected: 00, [01], 02 -> 00, [01], 02
    }

    /**
     * Preconditions:
     * - The device manager is initialized and contains several devices.
     *
     * Actions:
     * - Set the priority of a device that is not currently in the list
     *
     * Outcome:
     * - Device is added to the list in the requested spot. Devices with lower priorities have had
     *   their priorities adjusted accordingly.
     */
    @Test
    public void testSetConnectionPriorityNewDevice_listOrderedCorrectly() {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, SMALL_DEVICE_LIST);

        // add new device to the middle
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice("10:20:30:40:50:60");
        mProfileDeviceManager.setDeviceConnectionPriority(device, 1);
        Assert.assertEquals(1, mProfileDeviceManager.getDeviceConnectionPriority(device));
        ArrayList<String> expected = new ArrayList(SMALL_DEVICE_LIST);
        expected.add(1, "10:20:30:40:50:60"); // 00, 60, 01, 02
        assertDeviceList(expected);

        // add new device to the front
        device = mBluetoothAdapter.getRemoteDevice("10:20:30:40:50:61");
        mProfileDeviceManager.setDeviceConnectionPriority(device, 0);
        Assert.assertEquals(0, mProfileDeviceManager.getDeviceConnectionPriority(device));
        expected.add(0, "10:20:30:40:50:61"); // 61, 00, 60, 01, 02
        assertDeviceList(expected);

        // add new device to the end
        device = mBluetoothAdapter.getRemoteDevice("10:20:30:40:50:62");
        mProfileDeviceManager.setDeviceConnectionPriority(device, 5);
        Assert.assertEquals(5, mProfileDeviceManager.getDeviceConnectionPriority(device));
        expected.add(5, "10:20:30:40:50:62"); // 61, 00, 60, 01, 02, 62
        assertDeviceList(expected);
    }

    /**
     * Preconditions:
     * - The device manager is initialized and contains several devices.
     *
     * Actions:
     * - Request to set a priority that exceeds the bounds of the list (upper)
     *
     * Outcome:
     * - No operation is taken
     */
    @Test
    public void testSetConnectionPriorityLargerThanSize_priorityListUnchanged() {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, SMALL_DEVICE_LIST);

        // Attempt to move middle device to end with huge end priority
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(SMALL_DEVICE_LIST.get(1));
        mProfileDeviceManager.setDeviceConnectionPriority(device, 100000);
        Assert.assertEquals(1, mProfileDeviceManager.getDeviceConnectionPriority(device));
        assertDeviceList(SMALL_DEVICE_LIST);
    }

    /**
     * Preconditions:
     * - The device manager is initialized and contains several devices.
     *
     * Actions:
     * - Request to set a priority that exceeds the bounds of the list (lower)
     *
     * Outcome:
     * - No operation is taken
     */
    @Test
    public void testSetConnectionPriorityNegative_priorityListUnchanged() {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, SMALL_DEVICE_LIST);

        // Attempt to move middle device to negative priority
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(SMALL_DEVICE_LIST.get(1));
        mProfileDeviceManager.setDeviceConnectionPriority(device, -1);
        assertDeviceList(SMALL_DEVICE_LIST);
    }

    /**
     * Preconditions:
     * - The device manager is initialized and contains several devices.
     *
     * Actions:
     * - Request to set a priority for a null device
     *
     * Outcome:
     * - No operation is taken
     */
    @Test
    public void testSetConnectionPriorityNullDevice_priorityListUnchanged() {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, SMALL_DEVICE_LIST);
        mProfileDeviceManager.setDeviceConnectionPriority(null, 1);
        assertDeviceList(SMALL_DEVICE_LIST);
    }

    //--------------------------------------------------------------------------------------------//
    // beginAutoConnecting() tests                                                                //
    //--------------------------------------------------------------------------------------------//

    /**
     * Preconditions:
     * - The Bluetooth adapter is ON, the device manager is initialized and no devices are in the
     *   list.
     *
     * Actions:
     * - Initiate an auto connection
     *
     * Outcome:
     * - Auto connect returns immediately with no connection attempts made.
     */
    @Test
    public void testAutoConnectNoDevices_returnsImmediately() throws Exception {
        setPreconditionsAndStart(ADAPTER_STATE_ON, EMPTY_SETTINGS_STRING, EMPTY_DEVICE_LIST);
        mProfileDeviceManager.beginAutoConnecting();
        verify(mMockProxies, times(0)).bluetoothConnectToProfile(eq(mProfileId),
                any(BluetoothDevice.class));
        Assert.assertFalse(mProfileDeviceManager.isAutoConnecting());
    }

    /**
     * Preconditions:
     * - The Bluetooth adapter is OFF, the device manager is initialized and there are several
     *    devices are in the list.
     *
     * Actions:
     * - Initiate an auto connection
     *
     * Outcome:
     * - Auto connect returns immediately with no connection attempts made.
     */
    @Test
    public void testAutoConnectAdapterOff_returnsImmediately() throws Exception {
        setPreconditionsAndStart(ADAPTER_STATE_OFF, EMPTY_SETTINGS_STRING, SMALL_DEVICE_LIST);
        mProfileDeviceManager.beginAutoConnecting();
        verify(mMockProxies, times(0)).bluetoothConnectToProfile(eq(mProfileId),
                any(BluetoothDevice.class));
        Assert.assertFalse(mProfileDeviceManager.isAutoConnecting());
    }

    /**
     * Preconditions:
     * - The Bluetooth adapter is ON, the device manager is initialized and there are several
     *    devices are in the list.
     *
     * Actions:
     * - Initiate an auto connection
     *
     * Outcome:
     * - Auto connect attempts to connect each device in the list, in order of priority.
     */
    @Test
    public void testAutoConnectSeveralDevices_attemptsToConnectEachDevice() throws Exception {
        setPreconditionsAndStart(ADAPTER_STATE_ON, EMPTY_SETTINGS_STRING, SMALL_DEVICE_LIST);
        ArrayList<BluetoothDevice> devices = makeDeviceList(SMALL_DEVICE_LIST);
        for (BluetoothDevice device : devices) {
            mockDeviceAvailability(device, true);
        }

        mProfileDeviceManager.beginAutoConnecting();

        InOrder ordered = inOrder(mMockProxies);
        for (BluetoothDevice device : devices) {
            ordered.verify(mMockProxies, timeout(CONNECT_TIMEOUT_MS).times(1))
                    .bluetoothConnectToProfile(mProfileId, device);
        }
    }

    //--------------------------------------------------------------------------------------------//
    // Bluetooth stack device connection status changed event tests                               //
    //--------------------------------------------------------------------------------------------//

    /**
     * Preconditions:
     * - The device manager is initialized, there are no devices in the list. We are not auto
     *   connecting
     *
     * Actions:
     * - A connection action comes in for the profile we're tracking and the device's priority is
     *   PRIORITY_AUTO_CONNECT.
     *
     * Outcome:
     * - The device is added to the list. Related/configured trigger profiles are connected.
     */
    @Test
    public void testReceiveDeviceConnectPriorityAutoConnect_deviceAdded() throws Exception {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, EMPTY_DEVICE_LIST);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(SINGLE_DEVICE_LIST.get(0));
        mockDevicePriority(device, BluetoothProfile.PRIORITY_AUTO_CONNECT);
        sendConnectionStateChanged(device, BluetoothProfile.STATE_CONNECTED);
        assertDeviceList(SINGLE_DEVICE_LIST);
        for (int profile : mProfileTriggers) {
            verify(mMockProxies, times(1)).bluetoothConnectToProfile(profile, device);
        }
    }

    /**
     * Preconditions:
     * - The device manager is initialized, there are no devices in the list.
     *
     * Actions:
     * - A connection action comes in for the profile we're tracking and the device's priority is
     *   PRIORITY_ON.
     *
     * Outcome:
     * - The device is added to the list. Related/configured trigger profiles are connected.
     */
    @Test
    public void testReceiveDeviceConnectPriorityOn_deviceAdded() throws Exception {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, EMPTY_DEVICE_LIST);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(SINGLE_DEVICE_LIST.get(0));
        mockDevicePriority(device, BluetoothProfile.PRIORITY_ON);
        sendConnectionStateChanged(device, BluetoothProfile.STATE_CONNECTED);
        assertDeviceList(SINGLE_DEVICE_LIST);
        for (int profile : mProfileTriggers) {
            verify(mMockProxies, times(1)).bluetoothConnectToProfile(profile, device);
        }
    }

    /**
     * Preconditions:
     * - The device manager is initialized, there are no devices in the list.
     *
     * Actions:
     * - A connection action comes in for the profile we're tracking and the device's priority is
     *   PRIORITY_OFF.
     *
     * Outcome:
     * - The device is not added to the list. Related/configured trigger profiles are connected.
     */
    @Test
    public void testReceiveDeviceConnectPriorityOff_deviceNotAdded() throws Exception {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, EMPTY_DEVICE_LIST);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(SINGLE_DEVICE_LIST.get(0));
        mockDevicePriority(device, BluetoothProfile.PRIORITY_OFF);
        sendConnectionStateChanged(device, BluetoothProfile.STATE_CONNECTED);
        assertDeviceList(EMPTY_DEVICE_LIST);
        for (int profile : mProfileTriggers) {
            verify(mMockProxies, times(1)).bluetoothConnectToProfile(profile, device);
        }
    }

    /**
     * Preconditions:
     * - The device manager is initialized, there are no devices in the list.
     *
     * Actions:
     * - A connection action comes in for the profile we're tracking and the device's priority is
     *   PRIORITY_UNDEFINED.
     *
     * Outcome:
     * - The device is not added to the list. Related/configured trigger profiles are connected.
     */
    @Test
    public void testReceiveDeviceConnectPriorityUndefined_deviceNotAdded() throws Exception {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, EMPTY_DEVICE_LIST);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(SINGLE_DEVICE_LIST.get(0));
        mockDevicePriority(device, BluetoothProfile.PRIORITY_UNDEFINED);
        sendConnectionStateChanged(device, BluetoothProfile.STATE_CONNECTED);
        assertDeviceList(EMPTY_DEVICE_LIST);
        for (int profile : mProfileTriggers) {
            verify(mMockProxies, times(1)).bluetoothConnectToProfile(profile, device);
        }
    }

    /**
     * Preconditions:
     * - The device manager is initialized, there are is one device in the list.
     *
     * Actions:
     * - A disconnection action comes in for the profile we're tracking and the device's priority is
     *   PRIORITY_AUTO_CONNECT.
     *
     * Outcome:
     * - The device list is unchanged.
     */
    @Test
    public void testReceiveDeviceDisconnectPriorityAutoConnect_listUnchanged() throws Exception {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, SINGLE_DEVICE_LIST);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(SINGLE_DEVICE_LIST.get(0));
        mockDevicePriority(device, BluetoothProfile.PRIORITY_AUTO_CONNECT);
        sendConnectionStateChanged(device, BluetoothProfile.STATE_DISCONNECTED);
        assertDeviceList(SINGLE_DEVICE_LIST);
    }

    /**
     * Preconditions:
     * - The device manager is initialized, there are is one device in the list.
     *
     * Actions:
     * - A disconnection action comes in for the profile we're tracking and the device's priority is
     *   PRIORITY_ON.
     *
     * Outcome:
     * - The device list is unchanged.
     */
    @Test
    public void testReceiveDeviceDisconnectPriorityOn_listUnchanged() throws Exception {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, SINGLE_DEVICE_LIST);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(SINGLE_DEVICE_LIST.get(0));
        mockDevicePriority(device, BluetoothProfile.PRIORITY_ON);
        sendConnectionStateChanged(device, BluetoothProfile.STATE_DISCONNECTED);
        assertDeviceList(SINGLE_DEVICE_LIST);
    }

    /**
     * Preconditions:
     * - The device manager is initialized, there are is one device in the list.
     *
     * Actions:
     * - A disconnection action comes in for the profile we're tracking and the device's priority is
     *   PRIORITY_OFF.
     *
     * Outcome:
     * - The device list is unchanged.
     */
    @Test
    public void testReceiveDeviceDisconnectPriorityOff_deviceRemoved() throws Exception {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, SINGLE_DEVICE_LIST);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(SINGLE_DEVICE_LIST.get(0));
        mockDevicePriority(device, BluetoothProfile.PRIORITY_OFF);
        sendConnectionStateChanged(device, BluetoothProfile.STATE_DISCONNECTED);
        assertDeviceList(SINGLE_DEVICE_LIST);
    }

    /**
     * Preconditions:
     * - The device manager is initialized, there are is one device in the list.
     *
     * Actions:
     * - A disconnection action comes in for the profile we're tracking and the device's priority is
     *   PRIORITY_OFF.
     *
     * Outcome:
     * - The device list is unchanged.
     */
    @Test
    public void testReceiveDeviceDisconnectPriorityUndefined_listUnchanged() throws Exception {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, SINGLE_DEVICE_LIST);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(SINGLE_DEVICE_LIST.get(0));
        mockDevicePriority(device, BluetoothProfile.PRIORITY_UNDEFINED);
        sendConnectionStateChanged(device, BluetoothProfile.STATE_DISCONNECTED);
        assertDeviceList(SINGLE_DEVICE_LIST);
    }

    //--------------------------------------------------------------------------------------------//
    // Bluetooth stack device bond status changed event tests                                     //
    //--------------------------------------------------------------------------------------------//

    /**
     * Preconditions:
     * - The device manager is initialized, there are is one device in the list.
     *
     * Actions:
     * - A device from the list has unbonded
     *
     * Outcome:
     * - The device is removed from the list.
     */
    @Test
    public void testReceiveDeviceUnbonded_deviceRemoved() {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, SINGLE_DEVICE_LIST);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(SINGLE_DEVICE_LIST.get(0));
        sendBondStateChanged(device, BluetoothDevice.BOND_NONE);
        assertDeviceList(EMPTY_DEVICE_LIST);
    }

    /**
     * Preconditions:
     * - The device manager is initialized, there are no devices in the list.
     *
     * Actions:
     * - A device has bonded and its UUID set claims it supports this profile.
     *
     * Outcome:
     * - The device is added to the list.
     *
     * NOTE: Car Service version of Mockito does not support mocking final classes and
     * BluetoothDevice is a final class. Unsuppress this when we can support it.
     */
    @Test
    @Suppress
    public void testReceiveSupportedDeviceBonded_deviceAdded() {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, EMPTY_DEVICE_LIST);
        BluetoothDevice device = mock(BluetoothDevice.class);
        doReturn(SINGLE_DEVICE_LIST.get(0)).when(device).getAddress();
        doReturn(mUuids).when(device).getUuids();
        sendBondStateChanged(device, BluetoothDevice.BOND_BONDED);
        assertDeviceList(SINGLE_DEVICE_LIST);
    }

    /**
     * Preconditions:
     * - The device manager is initialized, there are no devices in the list.
     *
     * Actions:
     * - A device has bonded and its UUID set claims it does not support this profile.
     *
     * Outcome:
     * - The device is ignored.
     */
    @Test
    public void testReceiveUnsupportedDeviceBonded_deviceNotAdded() {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, EMPTY_DEVICE_LIST);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(SINGLE_DEVICE_LIST.get(0));
        sendBondStateChanged(device, BluetoothDevice.BOND_BONDED);
        assertDeviceList(EMPTY_DEVICE_LIST);
    }

    //--------------------------------------------------------------------------------------------//
    // Bluetooth stack device UUID event tests                                                    //
    //--------------------------------------------------------------------------------------------//

    /**
     * Preconditions:
     * - The device manager is initialized, there are no devices in the list.
     *
     * Actions:
     * - A Uuid set is received for a device that has PRIORITY_AUTO_CONNECT
     *
     * Outcome:
     * - The device is ignored, no priority update is made.
     */
    @Test
    public void testReceiveUuidDevicePriorityAutoConnect_doNothing() throws Exception {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, EMPTY_DEVICE_LIST);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(SINGLE_DEVICE_LIST.get(0));
        mockDevicePriority(device, BluetoothProfile.PRIORITY_AUTO_CONNECT);
        sendDeviceUuids(device, mUuids);
        assertDeviceList(EMPTY_DEVICE_LIST);
        verify(mMockProxies, times(0)).setProfilePriority(eq(mProfileId),
                any(BluetoothDevice.class), anyInt());
    }

    /**
     * Preconditions:
     * - The device manager is initialized, there are no devices in the list.
     *
     * Actions:
     * - A Uuid set is received for a device that has PRIORITY_ON
     *
     * Outcome:
     * - The device is ignored, no priority update is made.
     */
    @Test
    public void testReceiveUuidDevicePriorityOn_doNothing() throws Exception {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, EMPTY_DEVICE_LIST);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(SINGLE_DEVICE_LIST.get(0));
        mockDevicePriority(device, BluetoothProfile.PRIORITY_ON);
        sendDeviceUuids(device, mUuids);
        assertDeviceList(EMPTY_DEVICE_LIST);
        verify(mMockProxies, times(0)).setProfilePriority(eq(mProfileId),
                any(BluetoothDevice.class), anyInt());
    }

    /**
     * Preconditions:
     * - The device manager is initialized, there are no devices in the list.
     *
     * Actions:
     * - A Uuid set is received for a device that has PRIORITY_OFF
     *
     * Outcome:
     * - The device is ignored, no priority update is made.
     */
    @Test
    public void testReceiveUuidDevicePriorityOff_doNothing() throws Exception {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, EMPTY_DEVICE_LIST);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(SINGLE_DEVICE_LIST.get(0));
        mockDevicePriority(device, BluetoothProfile.PRIORITY_OFF);
        sendDeviceUuids(device, mUuids);
        assertDeviceList(EMPTY_DEVICE_LIST);
        verify(mMockProxies, times(0)).setProfilePriority(eq(mProfileId),
                any(BluetoothDevice.class), anyInt());
    }

    /**
     * Preconditions:
     * - The device manager is initialized, there are no devices in the list.
     *
     * Actions:
     * - A Uuid set is received for a device that has PRIORITY_UNDEFINED
     *
     * Outcome:
     * - The device has its priority updated to PRIORITY_ON.
     */
    @Test
    public void testReceiveUuidDevicePriorityUndefined_setPriorityOn() throws Exception {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, EMPTY_DEVICE_LIST);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(SINGLE_DEVICE_LIST.get(0));
        mockDevicePriority(device, BluetoothProfile.PRIORITY_UNDEFINED);
        sendDeviceUuids(device, mUuids);
        assertDeviceList(EMPTY_DEVICE_LIST);
        verify(mMockProxies, times(1)).setProfilePriority(mProfileId, device,
                BluetoothProfile.PRIORITY_ON);
    }

    /**
     * Preconditions:
     * - The device manager is initialized, there are no devices in the list.
     *
     * Actions:
     * - A Uuid set is received for a device that is not supported for this profile
     *
     * Outcome:
     * - The device is ignored, no priority update is made.
     */
    @Test
    public void testReceiveUuidsDeviceUnsupported_doNothing() throws Exception {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, EMPTY_DEVICE_LIST);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(SINGLE_DEVICE_LIST.get(0));
        mockDevicePriority(device, BluetoothProfile.PRIORITY_UNDEFINED);
        sendDeviceUuids(device, mBadUuids);
        assertDeviceList(EMPTY_DEVICE_LIST);
        verify(mMockProxies, times(0)).getProfilePriority(eq(mProfileId),
                any(BluetoothDevice.class));
    }

    //--------------------------------------------------------------------------------------------//
    // Bluetooth stack adapter status changed event tests                                         //
    //--------------------------------------------------------------------------------------------//

    /**
     * Preconditions:
     * - The device manager is initialized, there are several devices in the list. The adapter is on
     *   and we are currently connecting devices.
     *
     * Actions:
     * - The adapter is turning off
     *
     * Outcome:
     * - Auto-connecting is cancelled
     */
    @Test
    public void testReceiveAdapterTurningOff_cancel() {
        setPreconditionsAndStart(ADAPTER_STATE_ON, EMPTY_SETTINGS_STRING, SMALL_DEVICE_LIST);
        mProfileDeviceManager.beginAutoConnecting();
        Assert.assertTrue(mProfileDeviceManager.isAutoConnecting());
        // We have 24 seconds of auto connecting time while we force it to quit
        sendAdapterStateChanged(BluetoothAdapter.STATE_TURNING_OFF);
        Assert.assertFalse(mProfileDeviceManager.isAutoConnecting());
    }

    /**
     * Preconditions:
     * - The device manager is initialized, there are several devices in the list. The adapter is on
     *   and we are currently connecting devices.
     *
     * Actions:
     * - The adapter becomes off
     *
     * Outcome:
     * - Auto-connecting is cancelled. The device list is committed
     */
    @Test
    public void testReceiveAdapterOff_cancelAndCommit() {
        setPreconditionsAndStart(ADAPTER_STATE_ON, EMPTY_SETTINGS_STRING, SMALL_DEVICE_LIST);
        mProfileDeviceManager.beginAutoConnecting();
        Assert.assertTrue(mProfileDeviceManager.isAutoConnecting());
        // We have 24 seconds of auto connecting time while we force it to quit
        sendAdapterStateChanged(BluetoothAdapter.STATE_OFF);
        Assert.assertFalse(mProfileDeviceManager.isAutoConnecting());
        assertSettingsContains(SMALL_SETTINGS_STRING);
    }

    /**
     * Preconditions:
     * - The device manager is initialized, there are several devices in the list. The adapter is on
     *   and we are currently connecting devices.
     *
     * Actions:
     * - The adapter sends a turning on. (This can happen in weird cases in the stack where the
     *   adapter is ON but the intent is sent away. Additionally, being ON and sending the intent is
     *   a great way to make sure we called cancel)
     *
     * Outcome:
     * - Auto-connecting is cancelled
     */
    @Test
    public void testReceiveAdapterTurningOn_cancel() {
        setPreconditionsAndStart(ADAPTER_STATE_ON, EMPTY_SETTINGS_STRING, SMALL_DEVICE_LIST);
        mProfileDeviceManager.beginAutoConnecting();
        Assert.assertTrue(mProfileDeviceManager.isAutoConnecting());
        sendAdapterStateChanged(BluetoothAdapter.STATE_TURNING_ON);
        Assert.assertFalse(mProfileDeviceManager.isAutoConnecting());
    }

    /**
     * Preconditions:
     * - The device manager is initialized, there are several devices in the list.
     *
     * Actions:
     * - The adapter becomes on
     *
     * Outcome:
     * - No actions are taken
     */
    @Test
    public void testReceiveAdapterOn_doNothing() {
        setPreconditionsAndStart(ADAPTER_STATE_ANY, EMPTY_SETTINGS_STRING, SMALL_DEVICE_LIST);
        sendAdapterStateChanged(BluetoothAdapter.STATE_ON);
        verifyNoMoreInteractions(mMockProxies);
    }
}
