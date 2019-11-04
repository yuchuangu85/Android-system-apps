/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.bluetooth.btservice.storage;

import static org.mockito.Mockito.*;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;

import androidx.room.Room;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.AdapterService;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

@MediumTest
@RunWith(AndroidJUnit4.class)
public final class DatabaseManagerTest {

    @Mock private AdapterService mAdapterService;

    private MetadataDatabase mDatabase;
    private DatabaseManager mDatabaseManager;
    private BluetoothDevice mTestDevice;
    private BluetoothDevice mTestDevice2;

    private static final String LOCAL_STORAGE = "LocalStorage";
    private static final String TEST_BT_ADDR = "11:22:33:44:55:66";
    private static final String OTHER_BT_ADDR1 = "11:11:11:11:11:11";
    private static final String OTHER_BT_ADDR2 = "22:22:22:22:22:22";
    private static final int A2DP_SUPPORT_OP_CODEC_TEST = 0;
    private static final int A2DP_ENALBED_OP_CODEC_TEST = 1;
    private static final int MAX_META_ID = 16;
    private static final byte[] TEST_BYTE_ARRAY = "TEST_VALUE".getBytes();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        TestUtils.setAdapterService(mAdapterService);

        mTestDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(TEST_BT_ADDR);

        // Create a memory database for DatabaseManager instead of use a real database.
        mDatabase = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getTargetContext(),
                MetadataDatabase.class).build();

        when(mAdapterService.getPackageManager()).thenReturn(
                InstrumentationRegistry.getTargetContext().getPackageManager());
        mDatabaseManager = new DatabaseManager(mAdapterService);

        BluetoothDevice[] bondedDevices = {mTestDevice};
        doReturn(bondedDevices).when(mAdapterService).getBondedDevices();
        doNothing().when(mAdapterService).metadataChanged(
                anyString(), anyInt(), any(byte[].class));

        restartDatabaseManagerHelper();
    }

    @After
    public void tearDown() throws Exception {
        TestUtils.clearAdapterService(mAdapterService);
        mDatabase.deleteAll();
        mDatabaseManager.cleanup();
    }

    @Test
    public void testMetadataDefault() {
        Metadata data = new Metadata(TEST_BT_ADDR);
        mDatabase.insert(data);
        restartDatabaseManagerHelper();

        for (int id = 0; id < BluetoothProfile.MAX_PROFILE_ID; id++) {
            Assert.assertEquals(BluetoothProfile.PRIORITY_UNDEFINED,
                    mDatabaseManager.getProfilePriority(mTestDevice, id));
        }

        Assert.assertEquals(BluetoothA2dp.OPTIONAL_CODECS_SUPPORT_UNKNOWN,
                mDatabaseManager.getA2dpSupportsOptionalCodecs(mTestDevice));

        Assert.assertEquals(BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN,
                    mDatabaseManager.getA2dpOptionalCodecsEnabled(mTestDevice));

        for (int id = 0; id < MAX_META_ID; id++) {
            Assert.assertNull(mDatabaseManager.getCustomMeta(mTestDevice, id));
        }
    }

    @Test
    public void testSetGetProfilePriority() {
        int badPriority = -100;

        // Cases of device not in database
        testSetGetProfilePriorityCase(false, BluetoothProfile.PRIORITY_UNDEFINED,
                BluetoothProfile.PRIORITY_UNDEFINED, true);
        testSetGetProfilePriorityCase(false, BluetoothProfile.PRIORITY_OFF,
                BluetoothProfile.PRIORITY_OFF, true);
        testSetGetProfilePriorityCase(false, BluetoothProfile.PRIORITY_ON,
                BluetoothProfile.PRIORITY_ON, true);
        testSetGetProfilePriorityCase(false, BluetoothProfile.PRIORITY_AUTO_CONNECT,
                BluetoothProfile.PRIORITY_AUTO_CONNECT, true);
        testSetGetProfilePriorityCase(false, badPriority,
                BluetoothProfile.PRIORITY_UNDEFINED, false);

        // Cases of device already in database
        testSetGetProfilePriorityCase(true, BluetoothProfile.PRIORITY_UNDEFINED,
                BluetoothProfile.PRIORITY_UNDEFINED, true);
        testSetGetProfilePriorityCase(true, BluetoothProfile.PRIORITY_OFF,
                BluetoothProfile.PRIORITY_OFF, true);
        testSetGetProfilePriorityCase(true, BluetoothProfile.PRIORITY_ON,
                BluetoothProfile.PRIORITY_ON, true);
        testSetGetProfilePriorityCase(true, BluetoothProfile.PRIORITY_AUTO_CONNECT,
                BluetoothProfile.PRIORITY_AUTO_CONNECT, true);
        testSetGetProfilePriorityCase(true, badPriority,
                BluetoothProfile.PRIORITY_UNDEFINED, false);
    }

    @Test
    public void testSetGetA2dpSupportsOptionalCodecs() {
        int badValue = -100;

        // Cases of device not in database
        testSetGetA2dpOptionalCodecsCase(A2DP_SUPPORT_OP_CODEC_TEST, false,
                BluetoothA2dp.OPTIONAL_CODECS_SUPPORT_UNKNOWN,
                BluetoothA2dp.OPTIONAL_CODECS_SUPPORT_UNKNOWN);
        testSetGetA2dpOptionalCodecsCase(A2DP_SUPPORT_OP_CODEC_TEST, false,
                BluetoothA2dp.OPTIONAL_CODECS_NOT_SUPPORTED,
                BluetoothA2dp.OPTIONAL_CODECS_SUPPORT_UNKNOWN);
        testSetGetA2dpOptionalCodecsCase(A2DP_SUPPORT_OP_CODEC_TEST, false,
                BluetoothA2dp.OPTIONAL_CODECS_SUPPORTED,
                BluetoothA2dp.OPTIONAL_CODECS_SUPPORT_UNKNOWN);
        testSetGetA2dpOptionalCodecsCase(A2DP_SUPPORT_OP_CODEC_TEST, false,
                badValue, BluetoothA2dp.OPTIONAL_CODECS_SUPPORT_UNKNOWN);

        // Cases of device already in database
        testSetGetA2dpOptionalCodecsCase(A2DP_SUPPORT_OP_CODEC_TEST, true,
                BluetoothA2dp.OPTIONAL_CODECS_SUPPORT_UNKNOWN,
                BluetoothA2dp.OPTIONAL_CODECS_SUPPORT_UNKNOWN);
        testSetGetA2dpOptionalCodecsCase(A2DP_SUPPORT_OP_CODEC_TEST, true,
                BluetoothA2dp.OPTIONAL_CODECS_NOT_SUPPORTED,
                BluetoothA2dp.OPTIONAL_CODECS_NOT_SUPPORTED);
        testSetGetA2dpOptionalCodecsCase(A2DP_SUPPORT_OP_CODEC_TEST, true,
                BluetoothA2dp.OPTIONAL_CODECS_SUPPORTED,
                BluetoothA2dp.OPTIONAL_CODECS_SUPPORTED);
        testSetGetA2dpOptionalCodecsCase(A2DP_SUPPORT_OP_CODEC_TEST, true,
                badValue, BluetoothA2dp.OPTIONAL_CODECS_SUPPORT_UNKNOWN);
    }

    @Test
    public void testSetGetA2dpOptionalCodecsEnabled() {
        int badValue = -100;

        // Cases of device not in database
        testSetGetA2dpOptionalCodecsCase(A2DP_ENALBED_OP_CODEC_TEST, false,
                BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN,
                BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN);
        testSetGetA2dpOptionalCodecsCase(A2DP_ENALBED_OP_CODEC_TEST, false,
                BluetoothA2dp.OPTIONAL_CODECS_PREF_DISABLED,
                BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN);
        testSetGetA2dpOptionalCodecsCase(A2DP_ENALBED_OP_CODEC_TEST, false,
                BluetoothA2dp.OPTIONAL_CODECS_PREF_ENABLED,
                BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN);
        testSetGetA2dpOptionalCodecsCase(A2DP_ENALBED_OP_CODEC_TEST, false,
                badValue, BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN);

        // Cases of device already in database
        testSetGetA2dpOptionalCodecsCase(A2DP_ENALBED_OP_CODEC_TEST, true,
                BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN,
                BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN);
        testSetGetA2dpOptionalCodecsCase(A2DP_ENALBED_OP_CODEC_TEST, true,
                BluetoothA2dp.OPTIONAL_CODECS_PREF_DISABLED,
                BluetoothA2dp.OPTIONAL_CODECS_PREF_DISABLED);
        testSetGetA2dpOptionalCodecsCase(A2DP_ENALBED_OP_CODEC_TEST, true,
                BluetoothA2dp.OPTIONAL_CODECS_PREF_ENABLED,
                BluetoothA2dp.OPTIONAL_CODECS_PREF_ENABLED);
        testSetGetA2dpOptionalCodecsCase(A2DP_ENALBED_OP_CODEC_TEST, true,
                badValue, BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN);
    }

    @Test
    public void testRemoveUnusedMetadata_WithSingleBondedDevice() {
        // Insert two devices to database and cache, only mTestDevice is
        // in the bonded list
        Metadata otherData = new Metadata(OTHER_BT_ADDR1);
        // Add metadata for otherDevice
        otherData.setCustomizedMeta(0, TEST_BYTE_ARRAY);
        mDatabaseManager.mMetadataCache.put(OTHER_BT_ADDR1, otherData);
        mDatabase.insert(otherData);

        Metadata data = new Metadata(TEST_BT_ADDR);
        mDatabaseManager.mMetadataCache.put(TEST_BT_ADDR, data);
        mDatabase.insert(data);

        mDatabaseManager.removeUnusedMetadata();
        // Wait for database update
        TestUtils.waitForLooperToFinishScheduledTask(mDatabaseManager.getHandlerLooper());

        // Check removed device report metadata changed to null
        verify(mAdapterService).metadataChanged(OTHER_BT_ADDR1, 0, null);

        List<Metadata> list = mDatabase.load();

        // Check number of metadata in the database
        Assert.assertEquals(1, list.size());

        // Check whether the device is in database
        Metadata checkData = list.get(0);
        Assert.assertEquals(TEST_BT_ADDR, checkData.getAddress());

        mDatabase.deleteAll();
        // Wait for clear database
        TestUtils.waitForLooperToFinishScheduledTask(mDatabaseManager.getHandlerLooper());
        mDatabaseManager.mMetadataCache.clear();
    }

    @Test
    public void testRemoveUnusedMetadata_WithMultiBondedDevices() {
        // Insert three devices to database and cache, otherDevice1 and otherDevice2
        // are in the bonded list

        // Add metadata for TEST_BT_ADDR
        Metadata testData = new Metadata(TEST_BT_ADDR);
        testData.setCustomizedMeta(0, TEST_BYTE_ARRAY);
        mDatabaseManager.mMetadataCache.put(TEST_BT_ADDR, testData);
        mDatabase.insert(testData);

        // Add metadata for OTHER_BT_ADDR1
        Metadata otherData1 = new Metadata(OTHER_BT_ADDR1);
        otherData1.setCustomizedMeta(0, TEST_BYTE_ARRAY);
        mDatabaseManager.mMetadataCache.put(OTHER_BT_ADDR1, otherData1);
        mDatabase.insert(otherData1);

        // Add metadata for OTHER_BT_ADDR2
        Metadata otherData2 = new Metadata(OTHER_BT_ADDR2);
        otherData2.setCustomizedMeta(0, TEST_BYTE_ARRAY);
        mDatabaseManager.mMetadataCache.put(OTHER_BT_ADDR2, otherData2);
        mDatabase.insert(otherData2);

        // Add OTHER_BT_ADDR1 OTHER_BT_ADDR2 to bonded devices
        BluetoothDevice otherDevice1 = BluetoothAdapter.getDefaultAdapter()
                .getRemoteDevice(OTHER_BT_ADDR1);
        BluetoothDevice otherDevice2 = BluetoothAdapter.getDefaultAdapter()
                .getRemoteDevice(OTHER_BT_ADDR2);
        BluetoothDevice[] bondedDevices = {otherDevice1, otherDevice2};
        doReturn(bondedDevices).when(mAdapterService).getBondedDevices();

        mDatabaseManager.removeUnusedMetadata();
        TestUtils.waitForLooperToFinishScheduledTask(mDatabaseManager.getHandlerLooper());

        // Check TEST_BT_ADDR report metadata changed to null
        verify(mAdapterService).metadataChanged(TEST_BT_ADDR, 0, null);

        // Check number of metadata in the database
        List<Metadata> list = mDatabase.load();
        // OTHER_BT_ADDR1 and OTHER_BT_ADDR2 should still in database
        Assert.assertEquals(2, list.size());

        // Check whether the devices are in the database
        Metadata checkData1 = list.get(0);
        Assert.assertEquals(OTHER_BT_ADDR1, checkData1.getAddress());
        Metadata checkData2 = list.get(1);
        Assert.assertEquals(OTHER_BT_ADDR2, checkData2.getAddress());

        mDatabase.deleteAll();
        // Wait for clear database
        TestUtils.waitForLooperToFinishScheduledTask(mDatabaseManager.getHandlerLooper());
        mDatabaseManager.mMetadataCache.clear();

    }

    @Test
    public void testSetGetCustomMeta() {
        int badKey = 100;
        byte[] value = "input value".getBytes();

        // Device is not in database
        testSetGetCustomMetaCase(false, BluetoothDevice.METADATA_MANUFACTURER_NAME,
                value, true);
        testSetGetCustomMetaCase(false, BluetoothDevice.METADATA_MODEL_NAME,
                value, true);
        testSetGetCustomMetaCase(false, BluetoothDevice.METADATA_SOFTWARE_VERSION,
                value, true);
        testSetGetCustomMetaCase(false, BluetoothDevice.METADATA_HARDWARE_VERSION,
                value, true);
        testSetGetCustomMetaCase(false, BluetoothDevice.METADATA_COMPANION_APP,
                value, true);
        testSetGetCustomMetaCase(false, BluetoothDevice.METADATA_MAIN_ICON,
                value, true);
        testSetGetCustomMetaCase(false, BluetoothDevice.METADATA_IS_UNTETHERED_HEADSET,
                value, true);
        testSetGetCustomMetaCase(false, BluetoothDevice.METADATA_UNTETHERED_LEFT_ICON,
                value, true);
        testSetGetCustomMetaCase(false, BluetoothDevice.METADATA_UNTETHERED_RIGHT_ICON,
                value, true);
        testSetGetCustomMetaCase(false, BluetoothDevice.METADATA_UNTETHERED_CASE_ICON,
                value, true);
        testSetGetCustomMetaCase(false, BluetoothDevice.METADATA_UNTETHERED_LEFT_BATTERY,
                value, true);
        testSetGetCustomMetaCase(false, BluetoothDevice.METADATA_UNTETHERED_RIGHT_BATTERY,
                value, true);
        testSetGetCustomMetaCase(false, BluetoothDevice.METADATA_UNTETHERED_CASE_BATTERY,
                value, true);
        testSetGetCustomMetaCase(false, BluetoothDevice.METADATA_UNTETHERED_LEFT_CHARGING,
                value, true);
        testSetGetCustomMetaCase(false, BluetoothDevice.METADATA_UNTETHERED_RIGHT_CHARGING,
                value, true);
        testSetGetCustomMetaCase(false, BluetoothDevice.METADATA_UNTETHERED_CASE_CHARGING,
                value, true);
        testSetGetCustomMetaCase(false, BluetoothDevice.METADATA_ENHANCED_SETTINGS_UI_URI,
                value, true);
        testSetGetCustomMetaCase(false, badKey, value, false);

        // Device is in database
        testSetGetCustomMetaCase(true, BluetoothDevice.METADATA_MANUFACTURER_NAME,
                value, true);
        testSetGetCustomMetaCase(true, BluetoothDevice.METADATA_MODEL_NAME,
                value, true);
        testSetGetCustomMetaCase(true, BluetoothDevice.METADATA_SOFTWARE_VERSION,
                value, true);
        testSetGetCustomMetaCase(true, BluetoothDevice.METADATA_HARDWARE_VERSION,
                value, true);
        testSetGetCustomMetaCase(true, BluetoothDevice.METADATA_COMPANION_APP,
                value, true);
        testSetGetCustomMetaCase(true, BluetoothDevice.METADATA_MAIN_ICON,
                value, true);
        testSetGetCustomMetaCase(true, BluetoothDevice.METADATA_IS_UNTETHERED_HEADSET,
                value, true);
        testSetGetCustomMetaCase(true, BluetoothDevice.METADATA_UNTETHERED_LEFT_ICON,
                value, true);
        testSetGetCustomMetaCase(true, BluetoothDevice.METADATA_UNTETHERED_RIGHT_ICON,
                value, true);
        testSetGetCustomMetaCase(true, BluetoothDevice.METADATA_UNTETHERED_CASE_ICON,
                value, true);
        testSetGetCustomMetaCase(true, BluetoothDevice.METADATA_UNTETHERED_LEFT_BATTERY,
                value, true);
        testSetGetCustomMetaCase(true, BluetoothDevice.METADATA_UNTETHERED_RIGHT_BATTERY,
                value, true);
        testSetGetCustomMetaCase(true, BluetoothDevice.METADATA_UNTETHERED_CASE_BATTERY,
                value, true);
        testSetGetCustomMetaCase(true, BluetoothDevice.METADATA_UNTETHERED_LEFT_CHARGING,
                value, true);
        testSetGetCustomMetaCase(true, BluetoothDevice.METADATA_UNTETHERED_RIGHT_CHARGING,
                value, true);
        testSetGetCustomMetaCase(true, BluetoothDevice.METADATA_UNTETHERED_CASE_CHARGING,
                value, true);
        testSetGetCustomMetaCase(true, BluetoothDevice.METADATA_ENHANCED_SETTINGS_UI_URI,
                value, true);
    }

    void restartDatabaseManagerHelper() {
        Metadata data = new Metadata(LOCAL_STORAGE);
        data.migrated = true;
        mDatabase.insert(data);

        mDatabaseManager.cleanup();
        mDatabaseManager.start(mDatabase);
        // Wait for handler thread finish its task.
        TestUtils.waitForLooperToFinishScheduledTask(mDatabaseManager.getHandlerLooper());

        // Remove local storage
        mDatabaseManager.mMetadataCache.remove(LOCAL_STORAGE);
        mDatabase.delete(LOCAL_STORAGE);
    }

    void testSetGetProfilePriorityCase(boolean stored, int priority, int expectedPriority,
            boolean expectedSetResult) {
        if (stored) {
            Metadata data = new Metadata(TEST_BT_ADDR);
            mDatabaseManager.mMetadataCache.put(TEST_BT_ADDR, data);
            mDatabase.insert(data);
        }
        Assert.assertEquals(expectedSetResult,
                mDatabaseManager.setProfilePriority(mTestDevice,
                BluetoothProfile.HEADSET, priority));
        Assert.assertEquals(expectedPriority,
                mDatabaseManager.getProfilePriority(mTestDevice, BluetoothProfile.HEADSET));
        // Wait for database update
        TestUtils.waitForLooperToFinishScheduledTask(mDatabaseManager.getHandlerLooper());

        List<Metadata> list = mDatabase.load();

        // Check number of metadata in the database
        if (!stored) {
            if (priority != BluetoothProfile.PRIORITY_OFF
                    && priority != BluetoothProfile.PRIORITY_ON
                    && priority != BluetoothProfile.PRIORITY_AUTO_CONNECT) {
                // Database won't be updated
                Assert.assertEquals(0, list.size());
                return;
            }
        }
        Assert.assertEquals(1, list.size());

        // Check whether the device is in database
        restartDatabaseManagerHelper();
        Assert.assertEquals(expectedPriority,
                mDatabaseManager.getProfilePriority(mTestDevice, BluetoothProfile.HEADSET));

        mDatabase.deleteAll();
        // Wait for clear database
        TestUtils.waitForLooperToFinishScheduledTask(mDatabaseManager.getHandlerLooper());
        mDatabaseManager.mMetadataCache.clear();
    }

    void testSetGetA2dpOptionalCodecsCase(int test, boolean stored, int value, int expectedValue) {
        if (stored) {
            Metadata data = new Metadata(TEST_BT_ADDR);
            mDatabaseManager.mMetadataCache.put(TEST_BT_ADDR, data);
            mDatabase.insert(data);
        }
        if (test == A2DP_SUPPORT_OP_CODEC_TEST) {
            mDatabaseManager.setA2dpSupportsOptionalCodecs(mTestDevice, value);
            Assert.assertEquals(expectedValue,
                    mDatabaseManager.getA2dpSupportsOptionalCodecs(mTestDevice));
        } else {
            mDatabaseManager.setA2dpOptionalCodecsEnabled(mTestDevice, value);
            Assert.assertEquals(expectedValue,
                    mDatabaseManager.getA2dpOptionalCodecsEnabled(mTestDevice));
        }
        // Wait for database update
        TestUtils.waitForLooperToFinishScheduledTask(mDatabaseManager.getHandlerLooper());

        List<Metadata> list = mDatabase.load();

        // Check number of metadata in the database
        if (!stored) {
            // Database won't be updated
            Assert.assertEquals(0, list.size());
            return;
        }
        Assert.assertEquals(1, list.size());

        // Check whether the device is in database
        restartDatabaseManagerHelper();
        if (test == A2DP_SUPPORT_OP_CODEC_TEST) {
            Assert.assertEquals(expectedValue,
                    mDatabaseManager.getA2dpSupportsOptionalCodecs(mTestDevice));
        } else {
            Assert.assertEquals(expectedValue,
                    mDatabaseManager.getA2dpOptionalCodecsEnabled(mTestDevice));
        }

        mDatabase.deleteAll();
        // Wait for clear database
        TestUtils.waitForLooperToFinishScheduledTask(mDatabaseManager.getHandlerLooper());
        mDatabaseManager.mMetadataCache.clear();
    }

    void testSetGetCustomMetaCase(boolean stored, int key, byte[] value, boolean expectedResult) {
        byte[] testValue = "test value".getBytes();
        int verifyTime = 1;
        if (stored) {
            Metadata data = new Metadata(TEST_BT_ADDR);
            mDatabaseManager.mMetadataCache.put(TEST_BT_ADDR, data);
            mDatabase.insert(data);
            Assert.assertEquals(expectedResult,
                    mDatabaseManager.setCustomMeta(mTestDevice, key, testValue));
            verify(mAdapterService).metadataChanged(TEST_BT_ADDR, key, testValue);
            verifyTime++;
        }
        Assert.assertEquals(expectedResult,
                mDatabaseManager.setCustomMeta(mTestDevice, key, value));
        if (expectedResult) {
            // Check for callback and get value
            verify(mAdapterService, times(verifyTime)).metadataChanged(TEST_BT_ADDR, key, value);
            Assert.assertEquals(value,
                    mDatabaseManager.getCustomMeta(mTestDevice, key));
        } else {
            Assert.assertNull(mDatabaseManager.getCustomMeta(mTestDevice, key));
            return;
        }
        // Wait for database update
        TestUtils.waitForLooperToFinishScheduledTask(mDatabaseManager.getHandlerLooper());

        // Check whether the value is saved in database
        restartDatabaseManagerHelper();
        Assert.assertArrayEquals(value,
                mDatabaseManager.getCustomMeta(mTestDevice, key));

        mDatabase.deleteAll();
        // Wait for clear database
        TestUtils.waitForLooperToFinishScheduledTask(mDatabaseManager.getHandlerLooper());
        mDatabaseManager.mMetadataCache.clear();
    }
}
