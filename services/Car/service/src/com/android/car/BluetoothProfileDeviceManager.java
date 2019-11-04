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

import static android.car.settings.CarSettings.Secure.KEY_BLUETOOTH_A2DP_SINK_DEVICES;
import static android.car.settings.CarSettings.Secure.KEY_BLUETOOTH_HFP_CLIENT_DEVICES;
import static android.car.settings.CarSettings.Secure.KEY_BLUETOOTH_MAP_CLIENT_DEVICES;
import static android.car.settings.CarSettings.Secure.KEY_BLUETOOTH_PAN_DEVICES;
import static android.car.settings.CarSettings.Secure.KEY_BLUETOOTH_PBAP_CLIENT_DEVICES;

import android.bluetooth.BluetoothA2dpSink;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadsetClient;
import android.bluetooth.BluetoothMapClient;
import android.bluetooth.BluetoothPan;
import android.bluetooth.BluetoothPbapClient;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.car.ICarBluetoothUserService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseArray;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * BluetoothProfileDeviceManager - Manages a list of devices, sorted by connection attempt priority.
 * Provides a means for other applications to request connection events and adjust the device
 * connection priorities. Access to these functions is provided through CarBluetoothManager.
 */
public class BluetoothProfileDeviceManager {
    private static final String TAG = "BluetoothProfileDeviceManager";
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);
    private final Context mContext;
    private final int mUserId;

    private static final String SETTINGS_DELIMITER = ",";

    private static final int AUTO_CONNECT_TIMEOUT_MS = 8000;
    private static final Object AUTO_CONNECT_TOKEN = new Object();

    private static class BluetoothProfileInfo {
        final String mSettingsKey;
        final String mConnectionAction;
        final ParcelUuid[] mUuids;
        final int[] mProfileTriggers;

        private BluetoothProfileInfo(String action, String settingsKey, ParcelUuid[] uuids,
                int[] profileTriggers) {
            mSettingsKey = settingsKey;
            mConnectionAction = action;
            mUuids = uuids;
            mProfileTriggers = profileTriggers;
        }
    }

    private static final SparseArray<BluetoothProfileInfo> sProfileActions = new SparseArray();
    static {
        sProfileActions.put(BluetoothProfile.A2DP_SINK,
                new BluetoothProfileInfo(BluetoothA2dpSink.ACTION_CONNECTION_STATE_CHANGED,
                        KEY_BLUETOOTH_A2DP_SINK_DEVICES, new ParcelUuid[] {
                            BluetoothUuid.AudioSource
                        }, new int[] {}));
        sProfileActions.put(BluetoothProfile.HEADSET_CLIENT,
                new BluetoothProfileInfo(BluetoothHeadsetClient.ACTION_CONNECTION_STATE_CHANGED,
                        KEY_BLUETOOTH_HFP_CLIENT_DEVICES, new ParcelUuid[] {
                            BluetoothUuid.Handsfree_AG,
                            BluetoothUuid.HSP_AG
                        }, new int[] {BluetoothProfile.MAP_CLIENT, BluetoothProfile.PBAP_CLIENT}));
        sProfileActions.put(BluetoothProfile.MAP_CLIENT,
                new BluetoothProfileInfo(BluetoothMapClient.ACTION_CONNECTION_STATE_CHANGED,
                        KEY_BLUETOOTH_MAP_CLIENT_DEVICES, new ParcelUuid[] {
                            BluetoothUuid.MAS
                        }, new int[] {}));
        sProfileActions.put(BluetoothProfile.PAN,
                new BluetoothProfileInfo(BluetoothPan.ACTION_CONNECTION_STATE_CHANGED,
                        KEY_BLUETOOTH_PAN_DEVICES, new ParcelUuid[] {
                            BluetoothUuid.PANU
                        }, new int[] {}));
        sProfileActions.put(BluetoothProfile.PBAP_CLIENT,
                new BluetoothProfileInfo(BluetoothPbapClient.ACTION_CONNECTION_STATE_CHANGED,
                        KEY_BLUETOOTH_PBAP_CLIENT_DEVICES, new ParcelUuid[] {
                            BluetoothUuid.PBAP_PSE
                        }, new int[] {}));
    }

    private final int mProfileId;
    private final String mSettingsKey;
    private final String mProfileConnectionAction;
    private final ParcelUuid[] mProfileUuids;
    private final int[] mProfileTriggers;
    private ArrayList<BluetoothDevice> mPrioritizedDevices;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothBroadcastReceiver mBluetoothBroadcastReceiver;

    private ICarBluetoothUserService mBluetoothUserProxies;

    private final Object mAutoConnectLock = new Object();
    private boolean mConnecting = false;
    private int mAutoConnectPriority;
    private ArrayList<BluetoothDevice> mAutoConnectingDevices;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    /**
     * A BroadcastReceiver that listens specifically for actions related to the profile we're
     * tracking and uses them to update the status.
     */
    private class BluetoothBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (mProfileConnectionAction.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE,
                        BluetoothProfile.STATE_DISCONNECTED);
                handleDeviceConnectionStateChange(device, state);
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,
                        BluetoothDevice.ERROR);
                handleDeviceBondStateChange(device, state);
            } else if (BluetoothDevice.ACTION_UUID.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Parcelable[] uuids = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
                handleDeviceUuidEvent(device, uuids);
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                handleAdapterStateChange(state);
            }
        }
    }

    /**
     * Handles an incoming Profile-Device connection event.
     *
     * On <BluetoothProfile>.ACTION_CONNECTION_STATE_CHANGED coming from the BroadcastReceiver:
     *    On connected, if we're auto connecting and this is the current device we're managing, then
     *    see if we can move on to the next device in the list. Otherwise, If the device connected
     *    then add it to our priority list if it's not on their already.
     *
     *    On disconnected, if the device that disconnected also has had its profile priority set to
     *    PRIORITY_OFF, then remove it from our list.
     *
     * @param device - The Bluetooth device the state change is for
     * @param state - The new profile connection state of the device
     */
    private void handleDeviceConnectionStateChange(BluetoothDevice device, int state) {
        logd("Connection state changed [device: " + device + ", state: "
                        + Utils.getConnectionStateName(state) + "]");
        if (state == BluetoothProfile.STATE_CONNECTED) {
            if (isAutoConnecting() && isAutoConnectingDevice(device)) {
                continueAutoConnecting();
            } else {
                if (getProfilePriority(device) >= BluetoothProfile.PRIORITY_ON) {
                    addDevice(device); // No-op if device is in the list.
                }
                triggerConnections(device);
            }
        }
        // NOTE: We wanted check on disconnect if a device is priority off and use that as an
        // indicator to remove a device from the list, but priority reporting can be flaky and
        // was leading to us removing devices when we didn't want to.
    }

    /**
     * Handles an incoming device bond status event.
     *
     * On BluetoothDevice.ACTION_BOND_STATE_CHANGED:
     *    - If a device becomes unbonded, remove it from our list if it's there.
     *    - If it's bonded, then add it to our list if the UUID set says it supports us.
     *
     * @param device - The Bluetooth device the state change is for
     * @param state - The new bond state of the device
     */
    private void handleDeviceBondStateChange(BluetoothDevice device, int state) {
        logd("Bond state has changed [device: " + device + ", state: "
                + Utils.getBondStateName(state) + "]");
        if (state == BluetoothDevice.BOND_NONE) {
            // Note: We have seen cases of unbonding events being sent without actually
            // unbonding the device.
            removeDevice(device);
        } else if (state == BluetoothDevice.BOND_BONDED) {
            addBondedDeviceIfSupported(device);
        }
    }

    /**
     * Handles an incoming device UUID set update event.
     *
     * On BluetoothDevice.ACTION_UUID:
     *    If the UUID is one this profile cares about, set the profile priority for the device that
     *    the UUID was found on to PRIORITY_ON if its not PRIORITY_OFF already (meaning inhibited or
     *    disabled by the user through settings).
     *
     * @param device - The Bluetooth device the UUID event is for
     * @param uuids - The incoming set of supported UUIDs for the device
     */
    private void handleDeviceUuidEvent(BluetoothDevice device, Parcelable[] uuids) {
        logd("UUIDs found, device: " + device);
        if (uuids != null) {
            ParcelUuid[] uuidsToSend = new ParcelUuid[uuids.length];
            for (int i = 0; i < uuidsToSend.length; i++) {
                uuidsToSend[i] = (ParcelUuid) uuids[i];
            }
            provisionDeviceIfSupported(device, uuidsToSend);
        }
    }

    /**
     * Handle an adapter state change event.
     *
     * On BluetoothAdapter.ACTION_STATE_CHANGED:
     *    If the adapter is going into the OFF state, then cancel any auto connecting, commit our
     *    priority list and go idle.
     *
     * @param state - The new state of the Bluetooth adapter
     */
    private void handleAdapterStateChange(int state) {
        logd("Bluetooth Adapter state changed: " + Utils.getAdapterStateName(state));
        // Crashes of the BT stack mean we're not promised to see all the state changes we
        // might want to see. In order to be a bit more robust to crashes, we'll treat any
        // non-ON state as a time to cancel auto-connect. This gives us a better chance of
        // seeing a cancel state before a crash, as well as makes sure we're "cancelled"
        // before we see an ON.
        if (state != BluetoothAdapter.STATE_ON) {
            cancelAutoConnecting();
        }
        // To reduce how many times we're committing the list, we'll only write back on off
        if (state == BluetoothAdapter.STATE_OFF) {
            commit();
        }
    }

    /**
     * Creates an instance of BluetoothProfileDeviceManager that will manage devices
     * for the given profile ID.
     *
     * @param context - context of calling code
     * @param userId - ID of user we want to manage devices for
     * @param bluetoothUserProxies - Set of per-user bluetooth proxies for calling into the
     *                               bluetooth stack as the current user.
     * @param profileId - BluetoothProfile integer that represents the profile we're managing
     * @return A new instance of a BluetoothProfileDeviceManager, or null on any error
     */
    public static BluetoothProfileDeviceManager create(Context context, int userId,
            ICarBluetoothUserService bluetoothUserProxies, int profileId) {
        try {
            return new BluetoothProfileDeviceManager(context, userId, bluetoothUserProxies,
                    profileId);
        } catch (NullPointerException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Creates an instance of BluetoothProfileDeviceManager that will manage devices
     * for the given profile ID.
     *
     * @param context - context of calling code
     * @param userId - ID of user we want to manage devices for
     * @param bluetoothUserProxies - Set of per-user bluetooth proxies for calling into the
     *                               bluetooth stack as the current user.
     * @param profileId - BluetoothProfile integer that represents the profile we're managing
     * @return A new instance of a BluetoothProfileDeviceManager
     */
    private BluetoothProfileDeviceManager(Context context, int userId,
            ICarBluetoothUserService bluetoothUserProxies, int profileId) {
        mContext = Objects.requireNonNull(context);
        mUserId = userId;
        mBluetoothUserProxies = bluetoothUserProxies;

        mPrioritizedDevices = new ArrayList<>();
        BluetoothProfileInfo bpi = sProfileActions.get(profileId);
        if (bpi == null) {
            throw new IllegalArgumentException("Provided profile " + Utils.getProfileName(profileId)
                    + " is unrecognized");
        }
        mProfileId = profileId;
        mSettingsKey = bpi.mSettingsKey;
        mProfileConnectionAction = bpi.mConnectionAction;
        mProfileUuids = bpi.mUuids;
        mProfileTriggers = bpi.mProfileTriggers;

        mBluetoothAdapter = Objects.requireNonNull(BluetoothAdapter.getDefaultAdapter());
    }

    /**
     * Begin managing devices for this profile. Sets the start state from persistent memory.
     */
    public void start() {
        logd("Starting device management");
        load();
        synchronized (mAutoConnectLock) {
            mConnecting = false;
            mAutoConnectPriority = -1;
            mAutoConnectingDevices = null;
        }
        mBluetoothBroadcastReceiver = new BluetoothBroadcastReceiver();
        IntentFilter profileFilter = new IntentFilter();
        profileFilter.addAction(mProfileConnectionAction);
        profileFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        profileFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        profileFilter.addAction(BluetoothDevice.ACTION_UUID);
        mContext.registerReceiverAsUser(mBluetoothBroadcastReceiver, UserHandle.CURRENT,
                profileFilter, null, null);
    }

    /**
     * Stop managing devices for this profile. Commits the final priority list to persistent memory
     * and cleans up local resources.
     */
    public void stop() {
        logd("Stopping device management");
        if (mBluetoothBroadcastReceiver != null) {
            if (mContext != null) {
                mContext.unregisterReceiver(mBluetoothBroadcastReceiver);
            }
            mBluetoothBroadcastReceiver = null;
        }
        cancelAutoConnecting();
        commit();
        return;
    }

    /**
     * Loads the current device priority list from persistent memory in {@link Settings.Secure}.
     *
     * This will overwrite the contents of the local priority list. It does not attempt to take the
     * union of the file and existing set. As such, you likely do not want to load after starting.
     * Failed attempts to load leave the prioritized device list unchanged.
     *
     * @return true on success, false otherwise
     */
    private boolean load() {
        logd("Loading device priority list snapshot using key '" + mSettingsKey + "'");

        // Read from Settings.Secure for our profile, as the current user.
        String devicesStr = Settings.Secure.getStringForUser(mContext.getContentResolver(),
                mSettingsKey, mUserId);
        logd("Found Device String: '" + devicesStr + "'");
        if (devicesStr == null || "".equals(devicesStr)) {
            return false;
        }

        // Split string into list of device MAC addresses
        List<String> deviceList = Arrays.asList(devicesStr.split(SETTINGS_DELIMITER));
        if (deviceList == null) {
            return false;
        }

        // Turn the strings into full blown Bluetooth devices
        ArrayList<BluetoothDevice> devices = new ArrayList<>();
        for (String address : deviceList) {
            try {
                BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                devices.add(device);
            } catch (IllegalArgumentException e) {
                logw("Unable to parse address '" + address + "' to a device");
                continue;
            }
        }

        synchronized (this) {
            mPrioritizedDevices = devices;
        }

        logd("Loaded Priority list: " + devices);
        return true;
    }

    /**
     * Commits the current device priority list to persistent memory in {@link Settings.Secure}.
     *
     * @return true on success, false otherwise
     */
    private boolean commit() {
        StringBuilder sb = new StringBuilder();
        String delimiter = "";
        synchronized (this) {
            for (BluetoothDevice device : mPrioritizedDevices) {
                sb.append(delimiter);
                sb.append(device.getAddress());
                delimiter = SETTINGS_DELIMITER;
            }
        }

        String devicesStr = sb.toString();
        Settings.Secure.putStringForUser(mContext.getContentResolver(), mSettingsKey, devicesStr,
                mUserId);
        logd("Committed key: " + mSettingsKey + ", value: '" + devicesStr + "'");
        return true;
    }

    /**
     * Syncs the current priority list against the list of bonded devices from the adapter so that
     * we can make sure things haven't changed on us between the last two times we've ran.
     */
    private void sync() {
        logd("Syncing the priority list with the adapter's list of bonded devices");
        Set<BluetoothDevice> bondedDevices = mBluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : bondedDevices) {
            addDevice(device); // No-op if device is already in the priority list
        }

        synchronized (this) {
            ArrayList<BluetoothDevice> devices = getDeviceListSnapshot();
            for (BluetoothDevice device : devices) {
                if (!bondedDevices.contains(device)) {
                    removeDevice(device);
                }
            }
        }
    }

    /**
     * Makes a clone of the current prioritized device list in a synchronized fashion
     *
     * @return A clone of the most up to date prioritized device list
     */
    public ArrayList<BluetoothDevice> getDeviceListSnapshot() {
        ArrayList<BluetoothDevice> devices = new ArrayList<>();
        synchronized (this) {
            devices = (ArrayList) mPrioritizedDevices.clone();
        }
        return devices;
    }

    /**
     * Adds a device to the end of the priority list.
     *
     * @param device - The device you wish to add
     */
    public synchronized void addDevice(BluetoothDevice device) {
        if (device == null) return;
        if (mPrioritizedDevices.contains(device)) return;
        logd("Add device " + device);
        mPrioritizedDevices.add(device);
        commit();
    }

    /**
     * Removes a device from the priority list.
     *
     * @param device - The device you wish to remove
     */
    public synchronized void removeDevice(BluetoothDevice device) {
        if (device == null) return;
        if (!mPrioritizedDevices.contains(device)) return;
        logd("Remove device " + device);
        mPrioritizedDevices.remove(device);
        commit();
    }

    /**
     * Get the connection priority of a device.
     *
     * @param device - The device you want the priority of
     * @return The priority of the device, or -1 if the device is not in the list
     */
    public synchronized int getDeviceConnectionPriority(BluetoothDevice device) {
        if (device == null) return -1;
        logd("Get connection priority of " + device);
        return mPrioritizedDevices.indexOf(device);
    }

    /**
     * Set the connection priority of a device.
     *
     * If the devide does not exist, it will be added. If the priority is less than zero,
     * no priority will be set. If the priority exceeds the bounds of the list, no priority will be
     * set.
     *
     * @param device - The device you want to set the priority of
     * @param priority - The priority you want to the device to have
     */
    public synchronized void setDeviceConnectionPriority(BluetoothDevice device, int priority) {
        if (device == null || priority < 0 || priority > mPrioritizedDevices.size()
                || getDeviceConnectionPriority(device) == priority) return;
        if (mPrioritizedDevices.contains(device)) {
            mPrioritizedDevices.remove(device);
            if (priority > mPrioritizedDevices.size()) priority = mPrioritizedDevices.size();
        }
        logd("Set connection priority of " + device + " to " + priority);
        mPrioritizedDevices.add(priority, device);
        commit();
    }

    /**
     * Connect a specific device on this profile.
     *
     * @param device - The device to connect
     * @return true on success, false otherwise
     */
    private boolean connect(BluetoothDevice device) {
        logd("Connecting " + device);
        try {
            return mBluetoothUserProxies.bluetoothConnectToProfile(mProfileId, device);
        } catch (RemoteException e) {
            logw("Failed to connect " + device + ", Reason: " + e);
        }
        return false;
    }

    /**
     * Disconnect a specific device from this profile.
     *
     * @param device - The device to disconnect
     * @return true on success, false otherwise
     */
    private boolean disconnect(BluetoothDevice device) {
        logd("Disconnecting " + device);
        try {
            return mBluetoothUserProxies.bluetoothDisconnectFromProfile(mProfileId, device);
        } catch (RemoteException e) {
            logw("Failed to disconnect " + device + ", Reason: " + e);
        }
        return false;
    }

    /**
     * Gets the Bluetooth stack priority on this profile for a specific device.
     *
     * @param device - The device to get the Bluetooth stack priority of
     * @return The Bluetooth stack priority on this profile for the given device
     */
    private int getProfilePriority(BluetoothDevice device) {
        try {
            return mBluetoothUserProxies.getProfilePriority(mProfileId, device);
        } catch (RemoteException e) {
            logw("Failed to get bluetooth stack priority for " + device + ", Reason: " + e);
        }
        return BluetoothProfile.PRIORITY_UNDEFINED;
    }

    /**
     * Gets the Bluetooth stack priority on this profile for a specific device.
     *
     * @param device - The device to set the Bluetooth stack priority of
     * @return true on success, false otherwise
     */
    private boolean setProfilePriority(BluetoothDevice device, int priority) {
        logd("Set " + device + " stack priority to " + Utils.getProfilePriorityName(priority));
        try {
            mBluetoothUserProxies.setProfilePriority(mProfileId, device, priority);
        } catch (RemoteException e) {
            logw("Failed to set bluetooth stack priority for " + device + ", Reason: " + e);
            return false;
        }
        return true;
    }

    /**
     * Begins the process of connecting to devices, one by one, in the order that the priority
     * list currently specifies.
     *
     * If we are already connecting, or no devices are present, then no work is done.
     */
    public void beginAutoConnecting() {
        logd("Request to begin auto connection process");
        synchronized (mAutoConnectLock) {
            if (isAutoConnecting()) {
                logd("Auto connect requested while we are already auto connecting.");
                return;
            }
            if (mBluetoothAdapter.getState() != BluetoothAdapter.STATE_ON) {
                logd("Bluetooth Adapter is not on, cannot connect devices");
                return;
            }
            mAutoConnectingDevices = getDeviceListSnapshot();
            if (mAutoConnectingDevices.size() == 0) {
                logd("No saved devices to auto-connect to.");
                cancelAutoConnecting();
                return;
            }
            mConnecting = true;
            mAutoConnectPriority = 0;
        }
        autoConnectWithTimeout();
    }

    /**
     * Connects the current priority device and sets a timeout timer to indicate when to give up and
     * move on to the next one.
     */
    private void autoConnectWithTimeout() {
        synchronized (mAutoConnectLock) {
            if (!isAutoConnecting()) {
                logd("Autoconnect process was cancelled, skipping connecting next device.");
                return;
            }
            if (mAutoConnectPriority < 0 || mAutoConnectPriority >= mAutoConnectingDevices.size()) {
                return;
            }

            BluetoothDevice device = mAutoConnectingDevices.get(mAutoConnectPriority);
            logd("Auto connecting (" + mAutoConnectPriority + ") device: " + device);

            mHandler.post(() -> {
                boolean connectStatus = connect(device);
                if (!connectStatus) {
                    logw("Connection attempt immediately failed, moving to the next device");
                    continueAutoConnecting();
                }
            });
            mHandler.postDelayed(() -> {
                logw("Auto connect process has timed out connecting to " + device);
                continueAutoConnecting();
            }, AUTO_CONNECT_TOKEN, AUTO_CONNECT_TIMEOUT_MS);
        }
    }

    /**
     * Will forcibly move the auto connect process to the next device, or finish it if no more
     * devices are available.
     */
    private void continueAutoConnecting() {
        logd("Continue auto-connect process on next device");
        synchronized (mAutoConnectLock) {
            if (!isAutoConnecting()) {
                logd("Autoconnect process was cancelled, no need to continue.");
                return;
            }
            mHandler.removeCallbacksAndMessages(AUTO_CONNECT_TOKEN);
            mAutoConnectPriority++;
            if (mAutoConnectPriority >= mAutoConnectingDevices.size()) {
                logd("No more devices to connect to");
                cancelAutoConnecting();
                return;
            }
        }
        autoConnectWithTimeout();
    }

    /**
     * Cancels the auto-connection process. Any in-flight connection attempts will still be tried.
     *
     * Canceling is defined as deleting the snapshot of devices, resetting the device to connect
     * index, setting the connecting boolean to null, and removing any pending timeouts if they
     * exist.
     *
     * If there are no auto-connects in process this will do nothing.
     */
    private void cancelAutoConnecting() {
        logd("Cleaning up any auto-connect process");
        synchronized (mAutoConnectLock) {
            if (!isAutoConnecting()) return;
            mHandler.removeCallbacksAndMessages(AUTO_CONNECT_TOKEN);
            mConnecting = false;
            mAutoConnectPriority = -1;
            mAutoConnectingDevices = null;
        }
    }

    /**
     * Get the auto-connect status of thie profile device manager
     *
     * @return true on success, false otherwise
     */
    public boolean isAutoConnecting() {
        synchronized (mAutoConnectLock) {
            return mConnecting;
        }
    }

    /**
     * Determine if a device is the currently auto-connecting device
     *
     * @param device - A BluetoothDevice object to compare against any know auto connecting device
     * @return true if the input device is the device we're currently connecting, false otherwise
     */
    private boolean isAutoConnectingDevice(BluetoothDevice device) {
        synchronized (mAutoConnectLock) {
            if (mAutoConnectingDevices == null) return false;
            return mAutoConnectingDevices.get(mAutoConnectPriority).equals(device);
        }
    }

    /**
     * Given a device, will check the cached UUID set and see if it supports this profile. If it
     * does then we will add it to the end of our prioritized set and attempt a connection if and
     * only if the Bluetooth device priority allows a connection.
     *
     * Will do nothing if the device isn't bonded.
     */
    private void addBondedDeviceIfSupported(BluetoothDevice device) {
        logd("Add device " + device + " if it is supported");
        if (device.getBondState() != BluetoothDevice.BOND_BONDED) return;
        if (BluetoothUuid.containsAnyUuid(device.getUuids(), mProfileUuids)
                && getProfilePriority(device) >= BluetoothProfile.PRIORITY_ON) {
            addDevice(device);
        }
    }

    /**
     * Checks the reported UUIDs for a device to see if the device supports this profile. If it does
     * then it will update the underlying Bluetooth stack with PRIORITY_ON so long as the device
     * doesn't have a PRIORITY_OFF value set.
     *
     * @param device - The device that may support our profile
     * @param uuids - The set of UUIDs for the device, which may include our profile
     */
    private void provisionDeviceIfSupported(BluetoothDevice device, ParcelUuid[] uuids) {
        logd("Checking UUIDs for device: " + device);
        if (BluetoothUuid.containsAnyUuid(uuids, mProfileUuids)) {
            int devicePriority = getProfilePriority(device);
            logd("Device " + device + " supports this profile. Priority: "
                    + Utils.getProfilePriorityName(devicePriority));
            // Transition from PRIORITY_OFF to any other Bluetooth stack priority value is supposed
            // to be a user choice, enabled through the Settings applications. That's why we don't
            // do it here for them.
            if (devicePriority == BluetoothProfile.PRIORITY_UNDEFINED) {
                // As a note, UUID updates happen during pairing, as well as each time the adapter
                // turns on. Initiating connections to bonded device following UUID verification
                // would defeat the purpose of the priority list. They don't arrive in a predictable
                // order either. Since we call this function on UUID discovery, don't connect here!
                setProfilePriority(device, BluetoothProfile.PRIORITY_ON);
                return;
            }
        }
        logd("Provisioning of " + device + " has ended without priority being set");
    }

    /**
     * Trigger connections of related Bluetooth profiles on a device
     *
     * @param device - The Bluetooth device you would like to connect to
     */
    private void triggerConnections(BluetoothDevice device) {
        for (int profile : mProfileTriggers) {
            logd("Trigger connection to " + Utils.getProfileName(profile) + "on " + device);
            try {
                mBluetoothUserProxies.bluetoothConnectToProfile(profile, device);
            } catch (RemoteException e) {
                logw("Failed to connect " + device + ", Reason: " + e);
            }
        }
    }

    /**
     * Writes the verbose current state of the object to the PrintWriter
     *
     * @param writer PrintWriter object to write lines to
     */
    public void dump(PrintWriter writer, String indent) {
        writer.println(indent + "BluetoothProfileDeviceManager [" + Utils.getProfileName(mProfileId)
                + "]");
        writer.println(indent + "\tUser: " + mUserId);
        writer.println(indent + "\tSettings Location: " + mSettingsKey);
        writer.println(indent + "\tUser Proxies Exist: "
                + (mBluetoothUserProxies != null ? "Yes" : "No"));
        writer.println(indent + "\tAuto-Connecting: " + (isAutoConnecting() ? "Yes" : "No"));
        writer.println(indent + "\tPriority List:");
        ArrayList<BluetoothDevice> devices = getDeviceListSnapshot();
        for (BluetoothDevice device : devices) {
            writer.println(indent + "\t\t" + device.getAddress() + " - " + device.getName());
        }
    }

    /**
     * Log a message to DEBUG
     */
    private void logd(String msg) {
        if (DBG) {
            Log.d(TAG, "[" + Utils.getProfileName(mProfileId) + " - User: " + mUserId + "] " + msg);
        }
    }

    /**
     * Log a message to WARN
     */
    private void logw(String msg) {
        Log.w(TAG, "[" + Utils.getProfileName(mProfileId) + " - User: " + mUserId + "] " + msg);
    }
}
