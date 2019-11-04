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

package com.android.car;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.car.hardware.power.CarPowerManager;
import android.car.hardware.power.CarPowerManager.CarPowerStateListenerWithCompletion;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import java.io.PrintWriter;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * A Bluetooth Device Connection policy that is specific to the use cases of a Car. Contains policy
 * for deciding when to trigger connection and disconnection events.
 */

public class BluetoothDeviceConnectionPolicy {
    private static final String TAG = "BluetoothDeviceConnectionPolicy";
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    private final int mUserId;
    private final Context mContext;
    private final BluetoothAdapter mBluetoothAdapter;
    private final CarBluetoothService mCarBluetoothService;

    private CarPowerManager mCarPowerManager;
    private final CarPowerStateListenerWithCompletion mCarPowerStateListener =
            new CarPowerStateListenerWithCompletion() {
        @Override
        public void onStateChanged(int state, CompletableFuture<Void> future) {
            logd("Car power state has changed to " + state);

            // ON is the state when user turned on the car (it can be either ignition or
            // door unlock) the policy for ON is defined by OEMs and we can rely on that.
            if (state == CarPowerManager.CarPowerStateListener.ON) {
                logd("Car is powering on. Enable Bluetooth and auto-connect to devices");
                if (isBluetoothPersistedOn()) {
                    enableBluetooth();
                }

                // The above isBluetoothPersistedOn() call is always true when the adapter is on and
                // can be true or false if the adapter is off. If we are turned the adapter back on
                // then this connectDevices() call would fail at first here but be caught by the
                // following adapter on broadcast below. We'll only do this if the adapter is on
                if (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_ON) {
                    connectDevices();
                }
                return;
            }

            // Since we're appearing to be off after shutdown prepare, but may stay on in idle mode,
            // we'll turn off Bluetooth to disconnect devices and better the "off" illusion
            if (state == CarPowerManager.CarPowerStateListener.SHUTDOWN_PREPARE) {
                logd("Car is preparing for shutdown. Disable bluetooth adapter");
                disableBluetooth();

                // Let CPMS know we're ready to shutdown. Otherwise, CPMS will get stuck for
                // up to an hour.
                if (future != null) {
                    future.complete(null);
                }
                return;
            }
        }
    };

    /**
     * Get the policy's CarPowerStateListenerWithCompletion object
     *
     * For testing purposes only
     */
    public CarPowerStateListenerWithCompletion getCarPowerStateListener() {
        return mCarPowerStateListener;
    }

    /**
     * A BroadcastReceiver that listens specifically for actions related to the profile we're
     * tracking and uses them to update the status.
     *
     * On BluetoothAdapter.ACTION_STATE_CHANGED:
     *    If the adapter is going into the ON state, then commit trigger auto connection.
     */
    private class BluetoothBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                logd("Bluetooth Adapter state changed: " + Utils.getAdapterStateName(state));
                if (state == BluetoothAdapter.STATE_ON) {
                    connectDevices();
                }
            }
        }
    }
    private BluetoothBroadcastReceiver mBluetoothBroadcastReceiver;

    /**
     * Create a new BluetoothDeviceConnectionPolicy object, responsible for encapsulating the
     * default policy for when to initiate device connections given the list of prioritized devices
     * for each profile.
     *
     * @param context - The context of the creating application
     * @param userId - The user ID we're operating as
     * @param bluetoothService - A reference to CarBluetoothService so we can connect devices
     * @return A new instance of a BluetoothProfileDeviceManager, or null on any error
     */
    public static BluetoothDeviceConnectionPolicy create(Context context, int userId,
            CarBluetoothService bluetoothService) {
        try {
            return new BluetoothDeviceConnectionPolicy(context, userId, bluetoothService);
        } catch (NullPointerException e) {
            return null;
        }
    }

    /**
     * Create a new BluetoothDeviceConnectionPolicy object, responsible for encapsulating the
     * default policy for when to initiate device connections given the list of prioritized devices
     * for each profile.
     *
     * @param context - The context of the creating application
     * @param userId - The user ID we're operating as
     * @param bluetoothService - A reference to CarBluetoothService so we can connect devices
     * @return A new instance of a BluetoothProfileDeviceManager
     */
    private BluetoothDeviceConnectionPolicy(Context context, int userId,
            CarBluetoothService bluetoothService) {
        mUserId = userId;
        mContext = Objects.requireNonNull(context);
        mCarBluetoothService = bluetoothService;
        mBluetoothAdapter = Objects.requireNonNull(BluetoothAdapter.getDefaultAdapter());
    }

    /**
     * Setup the Bluetooth profile service connections and Vehicle Event listeners.
     * and start the state machine -{@link BluetoothAutoConnectStateMachine}
     */
    public synchronized void init() {
        logd("init()");
        mBluetoothBroadcastReceiver = new BluetoothBroadcastReceiver();
        IntentFilter profileFilter = new IntentFilter();
        profileFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        mContext.registerReceiverAsUser(mBluetoothBroadcastReceiver, UserHandle.CURRENT,
                profileFilter, null, null);
        mCarPowerManager = CarLocalServices.createCarPowerManager(mContext);
        // CarLocalServices can fail to return a service.
        if (mCarPowerManager != null) {
            mCarPowerManager.setListenerWithCompletion(mCarPowerStateListener);
        } else {
            logd("Failed to get car power manager");
        }

        // Since we do this only on start up and on user switch, it's safe to kick off a connect on
        // init. If we have a connect in progress, this won't hurt anything. If we already have
        // devices connected, this will add on top of it. We _could_ enter this from a crash
        // recovery, but that would at worst cause more devices to connect and wouldn't change the
        // existing devices.
        if (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_ON) {
            // CarPowerManager doesn't provide a getState() or that would go here too.
            connectDevices();
        }
    }

    /**
     * Clean up slate. Close the Bluetooth profile service connections and quit the state machine -
     * {@link BluetoothAutoConnectStateMachine}
     */
    public synchronized void release() {
        logd("release()");
        if (mCarPowerManager != null) {
            mCarPowerManager.clearListener();
            mCarPowerManager = null;
        }
        if (mBluetoothBroadcastReceiver != null) {
            mContext.unregisterReceiver(mBluetoothBroadcastReceiver);
            mBluetoothBroadcastReceiver = null;
        }
    }

    /**
     * Tell each Profile device manager that its time to begin auto connecting devices
     */
    public void connectDevices() {
        logd("Connect devices for each profile");
        mCarBluetoothService.connectDevices();
    }

    /**
     * Get the persisted Bluetooth state from Settings
     *
     * @return True if the persisted Bluetooth state is on, false otherwise
     */
    private boolean isBluetoothPersistedOn() {
        return (Settings.Global.getInt(
                mContext.getContentResolver(), Settings.Global.BLUETOOTH_ON, -1) != 0);
    }

    /**
     * Turn on the Bluetooth Adapter.
     */
    private void enableBluetooth() {
        logd("Enable bluetooth adapter");
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Cannot enable Bluetooth adapter. The object is null.");
            return;
        }
        mBluetoothAdapter.enable();
    }

    /**
     * Turn off the Bluetooth Adapter.
     *
     * Tells BluetoothAdapter to shut down _without_ persisting the off state as the desired state
     * of the Bluetooth adapter for next start up.
     */
    private void disableBluetooth() {
        logd("Disable bluetooth, do not persist state across reboot");
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Cannot disable Bluetooth adapter. The object is null.");
            return;
        }
        mBluetoothAdapter.disable(false);
    }

    /**
     * Print the verbose status of the object
     */
    public synchronized void dump(PrintWriter writer, String indent) {
        writer.println(indent + TAG + ":");
        writer.println(indent + "\tUserId: " + mUserId);
    }

    /**
     * Print to debug if debug is enabled
     */
    private static void logd(String msg) {
        if (DBG) {
            Log.d(TAG, msg);
        }
    }
}
