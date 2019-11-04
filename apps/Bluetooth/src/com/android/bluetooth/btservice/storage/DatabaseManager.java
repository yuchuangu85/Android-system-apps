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

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProtoEnums;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.util.StatsLog;

import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * The active device manager is responsible to handle a Room database
 * for Bluetooth persistent data.
 */
public class DatabaseManager {
    private static final boolean DBG = true;
    private static final boolean VERBOSE = true;
    private static final String TAG = "BluetoothDatabase";

    private AdapterService mAdapterService = null;
    private HandlerThread mHandlerThread = null;
    private Handler mHandler = null;
    private MetadataDatabase mDatabase = null;
    private boolean mMigratedFromSettingsGlobal = false;

    @VisibleForTesting
    final Map<String, Metadata> mMetadataCache = new HashMap<>();
    private final Semaphore mSemaphore = new Semaphore(1);

    private static final int LOAD_DATABASE_TIMEOUT = 500; // milliseconds
    private static final int MSG_LOAD_DATABASE = 0;
    private static final int MSG_UPDATE_DATABASE = 1;
    private static final int MSG_DELETE_DATABASE = 2;
    private static final int MSG_CLEAR_DATABASE = 100;
    private static final String LOCAL_STORAGE = "LocalStorage";

    /**
     * Constructor of the DatabaseManager
     */
    public DatabaseManager(AdapterService service) {
        mAdapterService = service;
    }

    class DatabaseHandler extends Handler {
        DatabaseHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_LOAD_DATABASE: {
                    synchronized (mDatabase) {
                        List<Metadata> list;
                        try {
                            list = mDatabase.load();
                        } catch (IllegalStateException e) {
                            Log.e(TAG, "Unable to open database: " + e);
                            mDatabase = MetadataDatabase
                                    .createDatabaseWithoutMigration(mAdapterService);
                            list = mDatabase.load();
                        }
                        cacheMetadata(list);
                    }
                    break;
                }
                case MSG_UPDATE_DATABASE: {
                    Metadata data = (Metadata) msg.obj;
                    synchronized (mDatabase) {
                        mDatabase.insert(data);
                    }
                    break;
                }
                case MSG_DELETE_DATABASE: {
                    String address = (String) msg.obj;
                    synchronized (mDatabase) {
                        mDatabase.delete(address);
                    }
                    break;
                }
                case MSG_CLEAR_DATABASE: {
                    synchronized (mDatabase) {
                        mDatabase.deleteAll();
                    }
                    break;
                }
            }
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) {
                Log.e(TAG, "Received intent with null action");
                return;
            }
            switch (action) {
                case BluetoothDevice.ACTION_BOND_STATE_CHANGED: {
                    int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,
                            BluetoothDevice.ERROR);
                    BluetoothDevice device =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    Objects.requireNonNull(device,
                            "ACTION_BOND_STATE_CHANGED with no EXTRA_DEVICE");
                    bondStateChanged(device, state);
                    break;
                }
                case BluetoothAdapter.ACTION_STATE_CHANGED: {
                    int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                            BluetoothAdapter.STATE_OFF);
                    if (!mMigratedFromSettingsGlobal
                            && state == BluetoothAdapter.STATE_TURNING_ON) {
                        migrateSettingsGlobal();
                    }
                    break;
                }
            }
        }
    };

    void bondStateChanged(BluetoothDevice device, int state) {
        synchronized (mMetadataCache) {
            String address = device.getAddress();
            if (state != BluetoothDevice.BOND_NONE) {
                if (mMetadataCache.containsKey(address)) {
                    return;
                }
                createMetadata(address);
            } else {
                Metadata metadata = mMetadataCache.get(address);
                if (metadata != null) {
                    mMetadataCache.remove(address);
                    deleteDatabase(metadata);
                }
            }
        }
    }

    boolean isValidMetaKey(int key) {
        switch (key) {
            case BluetoothDevice.METADATA_MANUFACTURER_NAME:
            case BluetoothDevice.METADATA_MODEL_NAME:
            case BluetoothDevice.METADATA_SOFTWARE_VERSION:
            case BluetoothDevice.METADATA_HARDWARE_VERSION:
            case BluetoothDevice.METADATA_COMPANION_APP:
            case BluetoothDevice.METADATA_MAIN_ICON:
            case BluetoothDevice.METADATA_IS_UNTETHERED_HEADSET:
            case BluetoothDevice.METADATA_UNTETHERED_LEFT_ICON:
            case BluetoothDevice.METADATA_UNTETHERED_RIGHT_ICON:
            case BluetoothDevice.METADATA_UNTETHERED_CASE_ICON:
            case BluetoothDevice.METADATA_UNTETHERED_LEFT_BATTERY:
            case BluetoothDevice.METADATA_UNTETHERED_RIGHT_BATTERY:
            case BluetoothDevice.METADATA_UNTETHERED_CASE_BATTERY:
            case BluetoothDevice.METADATA_UNTETHERED_LEFT_CHARGING:
            case BluetoothDevice.METADATA_UNTETHERED_RIGHT_CHARGING:
            case BluetoothDevice.METADATA_UNTETHERED_CASE_CHARGING:
            case BluetoothDevice.METADATA_ENHANCED_SETTINGS_UI_URI:
                return true;
        }
        Log.w(TAG, "Invalid metadata key " + key);
        return false;
    }

    /**
     * Set customized metadata to database with requested key
     */
    @VisibleForTesting
    public boolean setCustomMeta(BluetoothDevice device, int key, byte[] newValue) {
        synchronized (mMetadataCache) {
            if (device == null) {
                Log.e(TAG, "setCustomMeta: device is null");
                return false;
            }
            if (!isValidMetaKey(key)) {
                Log.e(TAG, "setCustomMeta: meta key invalid " + key);
                return false;
            }

            String address = device.getAddress();
            if (VERBOSE) {
                Log.d(TAG, "setCustomMeta: " + address + ", key=" + key);
            }
            if (!mMetadataCache.containsKey(address)) {
                createMetadata(address);
            }
            Metadata data = mMetadataCache.get(address);
            byte[] oldValue = data.getCustomizedMeta(key);
            if (oldValue != null && Arrays.equals(oldValue, newValue)) {
                if (VERBOSE) {
                    Log.d(TAG, "setCustomMeta: metadata not changed.");
                }
                return true;
            }
            logManufacturerInfo(device, key, newValue);
            data.setCustomizedMeta(key, newValue);

            updateDatabase(data);
            mAdapterService.metadataChanged(address, key, newValue);
            return true;
        }
    }

    /**
     * Get customized metadata from database with requested key
     */
    @VisibleForTesting
    public byte[] getCustomMeta(BluetoothDevice device, int key) {
        synchronized (mMetadataCache) {
            if (device == null) {
                Log.e(TAG, "getCustomMeta: device is null");
                return null;
            }
            if (!isValidMetaKey(key)) {
                Log.e(TAG, "getCustomMeta: meta key invalid " + key);
                return null;
            }

            String address = device.getAddress();

            if (!mMetadataCache.containsKey(address)) {
                Log.e(TAG, "getCustomMeta: device " + address + " is not in cache");
                return null;
            }

            Metadata data = mMetadataCache.get(address);
            return data.getCustomizedMeta(key);
        }
    }

    /**
     * Set the device profile prioirty
     *
     * @param device {@link BluetoothDevice} wish to set
     * @param profile The Bluetooth profile; one of {@link BluetoothProfile#HEADSET},
     * {@link BluetoothProfile#HEADSET_CLIENT}, {@link BluetoothProfile#A2DP},
     * {@link BluetoothProfile#A2DP_SINK}, {@link BluetoothProfile#HID_HOST},
     * {@link BluetoothProfile#PAN}, {@link BluetoothProfile#PBAP},
     * {@link BluetoothProfile#PBAP_CLIENT}, {@link BluetoothProfile#MAP},
     * {@link BluetoothProfile#MAP_CLIENT}, {@link BluetoothProfile#SAP},
     * {@link BluetoothProfile#HEARING_AID}
     * @param newPriority the priority to set; one of
     * {@link BluetoothProfile#PRIORITY_UNDEFINED},
     * {@link BluetoothProfile#PRIORITY_OFF},
     * {@link BluetoothProfile#PRIORITY_ON},
     * {@link BluetoothProfile#PRIORITY_AUTO_CONNECT}
     */
    @VisibleForTesting
    public boolean setProfilePriority(BluetoothDevice device, int profile, int newPriority) {
        synchronized (mMetadataCache) {
            if (device == null) {
                Log.e(TAG, "setProfilePriority: device is null");
                return false;
            }

            if (newPriority != BluetoothProfile.PRIORITY_UNDEFINED
                    && newPriority != BluetoothProfile.PRIORITY_OFF
                    && newPriority != BluetoothProfile.PRIORITY_ON
                    && newPriority != BluetoothProfile.PRIORITY_AUTO_CONNECT) {
                Log.e(TAG, "setProfilePriority: invalid priority " + newPriority);
                return false;
            }

            String address = device.getAddress();
            if (VERBOSE) {
                Log.v(TAG, "setProfilePriority: " + address + ", profile=" + profile
                        + ", priority = " + newPriority);
            }
            if (!mMetadataCache.containsKey(address)) {
                if (newPriority == BluetoothProfile.PRIORITY_UNDEFINED) {
                    return true;
                }
                createMetadata(address);
            }
            Metadata data = mMetadataCache.get(address);
            int oldPriority = data.getProfilePriority(profile);
            if (oldPriority == newPriority) {
                if (VERBOSE) {
                    Log.v(TAG, "setProfilePriority priority not changed.");
                }
                return true;
            }

            data.setProfilePriority(profile, newPriority);
            updateDatabase(data);
            return true;
        }
    }

    /**
     * Get the device profile prioirty
     *
     * @param device {@link BluetoothDevice} wish to get
     * @param profile The Bluetooth profile; one of {@link BluetoothProfile#HEADSET},
     * {@link BluetoothProfile#HEADSET_CLIENT}, {@link BluetoothProfile#A2DP},
     * {@link BluetoothProfile#A2DP_SINK}, {@link BluetoothProfile#HID_HOST},
     * {@link BluetoothProfile#PAN}, {@link BluetoothProfile#PBAP},
     * {@link BluetoothProfile#PBAP_CLIENT}, {@link BluetoothProfile#MAP},
     * {@link BluetoothProfile#MAP_CLIENT}, {@link BluetoothProfile#SAP},
     * {@link BluetoothProfile#HEARING_AID}
     * @return the profile priority of the device; one of
     * {@link BluetoothProfile#PRIORITY_UNDEFINED},
     * {@link BluetoothProfile#PRIORITY_OFF},
     * {@link BluetoothProfile#PRIORITY_ON},
     * {@link BluetoothProfile#PRIORITY_AUTO_CONNECT}
     */
    @VisibleForTesting
    public int getProfilePriority(BluetoothDevice device, int profile) {
        synchronized (mMetadataCache) {
            if (device == null) {
                Log.e(TAG, "getProfilePriority: device is null");
                return BluetoothProfile.PRIORITY_UNDEFINED;
            }

            String address = device.getAddress();

            if (!mMetadataCache.containsKey(address)) {
                Log.e(TAG, "getProfilePriority: device " + address + " is not in cache");
                return BluetoothProfile.PRIORITY_UNDEFINED;
            }

            Metadata data = mMetadataCache.get(address);
            int priority = data.getProfilePriority(profile);
            if (VERBOSE) {
                Log.v(TAG, "getProfilePriority: " + address + ", profile=" + profile
                        + ", priority = " + priority);
            }
            return priority;
        }
    }

    /**
     * Set the A2DP optional coedc support value
     *
     * @param device {@link BluetoothDevice} wish to set
     * @param newValue the new A2DP optional coedc support value, one of
     * {@link BluetoothA2dp#OPTIONAL_CODECS_SUPPORT_UNKNOWN},
     * {@link BluetoothA2dp#OPTIONAL_CODECS_NOT_SUPPORTED},
     * {@link BluetoothA2dp#OPTIONAL_CODECS_SUPPORTED}
     */
    @VisibleForTesting
    public void setA2dpSupportsOptionalCodecs(BluetoothDevice device, int newValue) {
        synchronized (mMetadataCache) {
            if (device == null) {
                Log.e(TAG, "setA2dpOptionalCodec: device is null");
                return;
            }
            if (newValue != BluetoothA2dp.OPTIONAL_CODECS_SUPPORT_UNKNOWN
                    && newValue != BluetoothA2dp.OPTIONAL_CODECS_NOT_SUPPORTED
                    && newValue != BluetoothA2dp.OPTIONAL_CODECS_SUPPORTED) {
                Log.e(TAG, "setA2dpSupportsOptionalCodecs: invalid value " + newValue);
                return;
            }

            String address = device.getAddress();

            if (!mMetadataCache.containsKey(address)) {
                return;
            }
            Metadata data = mMetadataCache.get(address);
            int oldValue = data.a2dpSupportsOptionalCodecs;
            if (oldValue == newValue) {
                return;
            }

            data.a2dpSupportsOptionalCodecs = newValue;
            updateDatabase(data);
        }
    }

    /**
     * Get the A2DP optional coedc support value
     *
     * @param device {@link BluetoothDevice} wish to get
     * @return the A2DP optional coedc support value, one of
     * {@link BluetoothA2dp#OPTIONAL_CODECS_SUPPORT_UNKNOWN},
     * {@link BluetoothA2dp#OPTIONAL_CODECS_NOT_SUPPORTED},
     * {@link BluetoothA2dp#OPTIONAL_CODECS_SUPPORTED},
     */
    @VisibleForTesting
    public int getA2dpSupportsOptionalCodecs(BluetoothDevice device) {
        synchronized (mMetadataCache) {
            if (device == null) {
                Log.e(TAG, "setA2dpOptionalCodec: device is null");
                return BluetoothA2dp.OPTIONAL_CODECS_SUPPORT_UNKNOWN;
            }

            String address = device.getAddress();

            if (!mMetadataCache.containsKey(address)) {
                Log.e(TAG, "getA2dpOptionalCodec: device " + address + " is not in cache");
                return BluetoothA2dp.OPTIONAL_CODECS_SUPPORT_UNKNOWN;
            }

            Metadata data = mMetadataCache.get(address);
            return data.a2dpSupportsOptionalCodecs;
        }
    }

    /**
     * Set the A2DP optional coedc enabled value
     *
     * @param device {@link BluetoothDevice} wish to set
     * @param newValue the new A2DP optional coedc enabled value, one of
     * {@link BluetoothA2dp#OPTIONAL_CODECS_PREF_UNKNOWN},
     * {@link BluetoothA2dp#OPTIONAL_CODECS_PREF_DISABLED},
     * {@link BluetoothA2dp#OPTIONAL_CODECS_PREF_ENABLED}
     */
    @VisibleForTesting
    public void setA2dpOptionalCodecsEnabled(BluetoothDevice device, int newValue) {
        synchronized (mMetadataCache) {
            if (device == null) {
                Log.e(TAG, "setA2dpOptionalCodecEnabled: device is null");
                return;
            }
            if (newValue != BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN
                    && newValue != BluetoothA2dp.OPTIONAL_CODECS_PREF_DISABLED
                    && newValue != BluetoothA2dp.OPTIONAL_CODECS_PREF_ENABLED) {
                Log.e(TAG, "setA2dpOptionalCodecsEnabled: invalid value " + newValue);
                return;
            }

            String address = device.getAddress();

            if (!mMetadataCache.containsKey(address)) {
                return;
            }
            Metadata data = mMetadataCache.get(address);
            int oldValue = data.a2dpOptionalCodecsEnabled;
            if (oldValue == newValue) {
                return;
            }

            data.a2dpOptionalCodecsEnabled = newValue;
            updateDatabase(data);
        }
    }

    /**
     * Get the A2DP optional coedc enabled value
     *
     * @param device {@link BluetoothDevice} wish to get
     * @return the A2DP optional coedc enabled value, one of
     * {@link BluetoothA2dp#OPTIONAL_CODECS_PREF_UNKNOWN},
     * {@link BluetoothA2dp#OPTIONAL_CODECS_PREF_DISABLED},
     * {@link BluetoothA2dp#OPTIONAL_CODECS_PREF_ENABLED}
     */
    @VisibleForTesting
    public int getA2dpOptionalCodecsEnabled(BluetoothDevice device) {
        synchronized (mMetadataCache) {
            if (device == null) {
                Log.e(TAG, "getA2dpOptionalCodecEnabled: device is null");
                return BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN;
            }

            String address = device.getAddress();

            if (!mMetadataCache.containsKey(address)) {
                Log.e(TAG, "getA2dpOptionalCodecEnabled: device " + address + " is not in cache");
                return BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN;
            }

            Metadata data = mMetadataCache.get(address);
            return data.a2dpOptionalCodecsEnabled;
        }
    }

    /**
     * Get the {@link Looper} for the handler thread. This is used in testing and helper
     * objects
     *
     * @return {@link Looper} for the handler thread
     */
    @VisibleForTesting
    public Looper getHandlerLooper() {
        if (mHandlerThread == null) {
            return null;
        }
        return mHandlerThread.getLooper();
    }

    /**
     * Start and initialize the DatabaseManager
     *
     * @param database the Bluetooth storage {@link MetadataDatabase}
     */
    public void start(MetadataDatabase database) {
        if (DBG) {
            Log.d(TAG, "start()");
        }

        if (mAdapterService == null) {
            Log.e(TAG, "stat failed, mAdapterService is null.");
            return;
        }

        if (database == null) {
            Log.e(TAG, "stat failed, database is null.");
            return;
        }

        mDatabase = database;

        mHandlerThread = new HandlerThread("BluetoothDatabaseManager");
        mHandlerThread.start();
        mHandler = new DatabaseHandler(mHandlerThread.getLooper());

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        mAdapterService.registerReceiver(mReceiver, filter);

        loadDatabase();
    }

    String getDatabaseAbsolutePath() {
        //TODO backup database when Bluetooth turn off and FOTA?
        return mAdapterService.getDatabasePath(MetadataDatabase.DATABASE_NAME)
                .getAbsolutePath();
    }

    /**
     * Clear all persistence data in database
     */
    public void factoryReset() {
        Log.w(TAG, "factoryReset");
        Message message = mHandler.obtainMessage(MSG_CLEAR_DATABASE);
        mHandler.sendMessage(message);
    }

    /**
     * Close and de-init the DatabaseManager
     */
    public void cleanup() {
        removeUnusedMetadata();
        mAdapterService.unregisterReceiver(mReceiver);
        if (mHandlerThread != null) {
            mHandlerThread.quit();
            mHandlerThread = null;
        }
        mMetadataCache.clear();
    }

    void createMetadata(String address) {
        if (VERBOSE) {
            Log.v(TAG, "createMetadata " + address);
        }
        Metadata data = new Metadata(address);
        mMetadataCache.put(address, data);
        updateDatabase(data);
    }

    @VisibleForTesting
    void removeUnusedMetadata() {
        BluetoothDevice[] bondedDevices = mAdapterService.getBondedDevices();
        synchronized (mMetadataCache) {
            mMetadataCache.forEach((address, metadata) -> {
                if (!address.equals(LOCAL_STORAGE)
                        && !Arrays.asList(bondedDevices).stream().anyMatch(device ->
                        address.equals(device.getAddress()))) {
                    List<Integer> list = metadata.getChangedCustomizedMeta();
                    for (int key : list) {
                        mAdapterService.metadataChanged(address, key, null);
                    }
                    Log.i(TAG, "remove unpaired device from database " + address);
                    deleteDatabase(mMetadataCache.get(address));
                }
            });
        }
    }

    void cacheMetadata(List<Metadata> list) {
        synchronized (mMetadataCache) {
            Log.i(TAG, "cacheMetadata");
            // Unlock the main thread.
            mSemaphore.release();

            if (!isMigrated(list)) {
                // Wait for data migrate from Settings Global
                mMigratedFromSettingsGlobal = false;
                return;
            }
            mMigratedFromSettingsGlobal = true;
            for (Metadata data : list) {
                String address = data.getAddress();
                if (VERBOSE) {
                    Log.v(TAG, "cacheMetadata: found device " + address);
                }
                mMetadataCache.put(address, data);
            }
            if (VERBOSE) {
                Log.v(TAG, "cacheMetadata: Database is ready");
            }
        }
    }

    boolean isMigrated(List<Metadata> list) {
        for (Metadata data : list) {
            String address = data.getAddress();
            if (address.equals(LOCAL_STORAGE) && data.migrated) {
                return true;
            }
        }
        return false;
    }

    void migrateSettingsGlobal() {
        Log.i(TAG, "migrateSettingGlobal");

        BluetoothDevice[] bondedDevices = mAdapterService.getBondedDevices();
        ContentResolver contentResolver = mAdapterService.getContentResolver();

        for (BluetoothDevice device : bondedDevices) {
            int a2dpPriority = Settings.Global.getInt(contentResolver,
                    Settings.Global.getBluetoothA2dpSinkPriorityKey(device.getAddress()),
                    BluetoothProfile.PRIORITY_UNDEFINED);
            int a2dpSinkPriority = Settings.Global.getInt(contentResolver,
                    Settings.Global.getBluetoothA2dpSrcPriorityKey(device.getAddress()),
                    BluetoothProfile.PRIORITY_UNDEFINED);
            int hearingaidPriority = Settings.Global.getInt(contentResolver,
                    Settings.Global.getBluetoothHearingAidPriorityKey(device.getAddress()),
                    BluetoothProfile.PRIORITY_UNDEFINED);
            int headsetPriority = Settings.Global.getInt(contentResolver,
                    Settings.Global.getBluetoothHeadsetPriorityKey(device.getAddress()),
                    BluetoothProfile.PRIORITY_UNDEFINED);
            int headsetClientPriority = Settings.Global.getInt(contentResolver,
                    Settings.Global.getBluetoothHeadsetPriorityKey(device.getAddress()),
                    BluetoothProfile.PRIORITY_UNDEFINED);
            int hidHostPriority = Settings.Global.getInt(contentResolver,
                    Settings.Global.getBluetoothHidHostPriorityKey(device.getAddress()),
                    BluetoothProfile.PRIORITY_UNDEFINED);
            int mapPriority = Settings.Global.getInt(contentResolver,
                    Settings.Global.getBluetoothMapPriorityKey(device.getAddress()),
                    BluetoothProfile.PRIORITY_UNDEFINED);
            int mapClientPriority = Settings.Global.getInt(contentResolver,
                    Settings.Global.getBluetoothMapClientPriorityKey(device.getAddress()),
                    BluetoothProfile.PRIORITY_UNDEFINED);
            int panPriority = Settings.Global.getInt(contentResolver,
                    Settings.Global.getBluetoothPanPriorityKey(device.getAddress()),
                    BluetoothProfile.PRIORITY_UNDEFINED);
            int pbapPriority = Settings.Global.getInt(contentResolver,
                    Settings.Global.getBluetoothPbapClientPriorityKey(device.getAddress()),
                    BluetoothProfile.PRIORITY_UNDEFINED);
            int pbapClientPriority = Settings.Global.getInt(contentResolver,
                    Settings.Global.getBluetoothPbapClientPriorityKey(device.getAddress()),
                    BluetoothProfile.PRIORITY_UNDEFINED);
            int sapPriority = Settings.Global.getInt(contentResolver,
                    Settings.Global.getBluetoothSapPriorityKey(device.getAddress()),
                    BluetoothProfile.PRIORITY_UNDEFINED);
            int a2dpSupportsOptionalCodec = Settings.Global.getInt(contentResolver,
                    Settings.Global.getBluetoothA2dpSupportsOptionalCodecsKey(device.getAddress()),
                    BluetoothA2dp.OPTIONAL_CODECS_SUPPORT_UNKNOWN);
            int a2dpOptionalCodecEnabled = Settings.Global.getInt(contentResolver,
                    Settings.Global.getBluetoothA2dpOptionalCodecsEnabledKey(device.getAddress()),
                    BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN);

            String address = device.getAddress();
            Metadata data = new Metadata(address);
            data.setProfilePriority(BluetoothProfile.A2DP, a2dpPriority);
            data.setProfilePriority(BluetoothProfile.A2DP_SINK, a2dpSinkPriority);
            data.setProfilePriority(BluetoothProfile.HEADSET, headsetPriority);
            data.setProfilePriority(BluetoothProfile.HEADSET_CLIENT, headsetClientPriority);
            data.setProfilePriority(BluetoothProfile.HID_HOST, hidHostPriority);
            data.setProfilePriority(BluetoothProfile.PAN, panPriority);
            data.setProfilePriority(BluetoothProfile.PBAP, pbapPriority);
            data.setProfilePriority(BluetoothProfile.PBAP_CLIENT, pbapClientPriority);
            data.setProfilePriority(BluetoothProfile.MAP, mapPriority);
            data.setProfilePriority(BluetoothProfile.MAP_CLIENT, mapClientPriority);
            data.setProfilePriority(BluetoothProfile.SAP, sapPriority);
            data.setProfilePriority(BluetoothProfile.HEARING_AID, hearingaidPriority);
            data.a2dpSupportsOptionalCodecs = a2dpSupportsOptionalCodec;
            data.a2dpOptionalCodecsEnabled = a2dpOptionalCodecEnabled;
            mMetadataCache.put(address, data);
            updateDatabase(data);
        }

        // Mark database migrated from Settings Global
        Metadata localData = new Metadata(LOCAL_STORAGE);
        localData.migrated = true;
        mMetadataCache.put(LOCAL_STORAGE, localData);
        updateDatabase(localData);

        // Reload database after migration is completed
        loadDatabase();

    }

    private void loadDatabase() {
        if (DBG) {
            Log.d(TAG, "Load Database");
        }
        Message message = mHandler.obtainMessage(MSG_LOAD_DATABASE);
        mHandler.sendMessage(message);
        try {
            // Lock the thread until handler thread finish loading database.
            mSemaphore.tryAcquire(LOAD_DATABASE_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "loadDatabase: semaphore acquire failed");
        }
    }

    private void updateDatabase(Metadata data) {
        if (data.getAddress() == null) {
            Log.e(TAG, "updateDatabase: address is null");
            return;
        }
        if (DBG) {
            Log.d(TAG, "updateDatabase " + data.getAddress());
        }
        Message message = mHandler.obtainMessage(MSG_UPDATE_DATABASE);
        message.obj = data;
        mHandler.sendMessage(message);
    }

    private void deleteDatabase(Metadata data) {
        if (data.getAddress() == null) {
            Log.e(TAG, "deleteDatabase: address is null");
            return;
        }
        Log.d(TAG, "deleteDatabase: " + data.getAddress());
        Message message = mHandler.obtainMessage(MSG_DELETE_DATABASE);
        message.obj = data.getAddress();
        mHandler.sendMessage(message);
    }

    private void logManufacturerInfo(BluetoothDevice device, int key, byte[] bytesValue) {
        String callingApp = mAdapterService.getPackageManager().getNameForUid(
                Binder.getCallingUid());
        String manufacturerName = "";
        String modelName = "";
        String hardwareVersion = "";
        String softwareVersion = "";
        String value = Utils.byteArrayToUtf8String(bytesValue);
        switch (key) {
            case BluetoothDevice.METADATA_MANUFACTURER_NAME:
                manufacturerName = value;
                break;
            case BluetoothDevice.METADATA_MODEL_NAME:
                modelName = value;
                break;
            case BluetoothDevice.METADATA_HARDWARE_VERSION:
                hardwareVersion = value;
                break;
            case BluetoothDevice.METADATA_SOFTWARE_VERSION:
                softwareVersion = value;
                break;
            default:
                // Do not log anything if metadata doesn't fall into above categories
                return;
        }
        StatsLog.write(StatsLog.BLUETOOTH_DEVICE_INFO_REPORTED,
                mAdapterService.obfuscateAddress(device),
                BluetoothProtoEnums.DEVICE_INFO_EXTERNAL, callingApp, manufacturerName, modelName,
                hardwareVersion, softwareVersion);
    }
}
