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

import static org.junit.Assert.assertTrue;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Settings;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Contains the utilities to aid in car Bluetooth testing
 */
public class BluetoothAdapterHelper {
    private static final String TAG = "BluetoothAdapterHelper";
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);
    private Context mContext;

    private BluetoothAdapterReceiver mBluetoothAdapterReceiver;
    private BluetoothAdapter mBluetoothAdapter;

    // Timeout for waiting for an adapter state change
    private static final int BT_ADAPTER_TIMEOUT_MS = 8000; // ms

    // Objects to block until the adapter has reached a desired state
    private ReentrantLock mBluetoothAdapterLock;
    private Condition mConditionAdapterStateReached;
    private int mDesiredState;

    /**
     * Handles BluetoothAdapter state changes and signals when we've reached a desired state
     */
    private class BluetoothAdapterReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {

            // Decode the intent
            String action = intent.getAction();

            // Watch for BluetoothAdapter intents only
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int newState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                logd("Bluetooth adapter state changed: " + newState);

                // Signal if the state is set to the one we're waiting on. If its not and we got a
                // STATE_OFF event then handle the unexpected off event. Note that we could
                // proactively turn the adapter back on to continue testing. For now we'll just
                // log it
                mBluetoothAdapterLock.lock();
                try {
                    if (mDesiredState == newState) {
                        mConditionAdapterStateReached.signal();
                    } else if (newState == BluetoothAdapter.STATE_OFF) {
                        logw("Bluetooth turned off unexpectedly while test was running.");
                    }
                } finally {
                    mBluetoothAdapterLock.unlock();
                }
            }
        }
    }

    /**
     * Create a BluetoothAdapterHelper and tell it how to log
     *
     * @param tag - The logging tag you wish it to have
     */
    public BluetoothAdapterHelper() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        mDesiredState = -1; // Set and checked by waitForAdapterState()
        mBluetoothAdapterLock = new ReentrantLock();
        mConditionAdapterStateReached = mBluetoothAdapterLock.newCondition();
        mBluetoothAdapterReceiver = new BluetoothAdapterReceiver();
    }

    /**
     * Setup the helper and begin receiving BluetoothAdapter events.
     *
     * Must be called before functions will work.
     */
    public void init() {
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        mContext.registerReceiver(mBluetoothAdapterReceiver, filter);
    }

    /**
     * Release resource in preparation to destroy the object
     */
    public void release() {
        mContext.unregisterReceiver(mBluetoothAdapterReceiver);
    }

    /**
     * Get the state of the Bluetooth Adapter
     */
    public int getAdapterState() {
        return mBluetoothAdapter.getState();
    }

    /**
     * Get the persisted Bluetooth state from Settings
     *
     * @return True if the persisted Bluetooth state is on, false otherwise
     */
    public boolean isAdapterPersistedOn() {
        return (Settings.Global.getInt(
                mContext.getContentResolver(), Settings.Global.BLUETOOTH_ON, -1) != 0);
    }

    /**
     * Wait until the adapter is ON. If the adapter is already on it will return immediately.
     */
    public synchronized void waitForAdapterOn() {
        waitForAdapterState(BluetoothAdapter.STATE_ON);
    }

    /**
     * Wait until the adapter is OFF. If the adapter is already off it will return immediately.
     */
    public synchronized void waitForAdapterOff() {
        waitForAdapterState(BluetoothAdapter.STATE_OFF);
    }

    /**
     * Wait for the bluetooth adapter to be in a given state
     */
    private synchronized void waitForAdapterState(int desiredState) {
        logd("Waiting for adapter state " + Utils.getAdapterStateName(desiredState));
        mBluetoothAdapterLock.lock();
        try {
            // Update the desired state so that we'll signal when we get there
            mDesiredState = desiredState;

            // Wait until we're reached that desired state
            while (desiredState != mBluetoothAdapter.getState()) {
                if (!mConditionAdapterStateReached.await(
                        BT_ADAPTER_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    loge("Timeout while waiting for Bluetooth adapter state "
                            + Utils.getAdapterStateName(desiredState));
                    break;
                }
            }
        } catch (InterruptedException e) {
            logw("waitForAdapterState(" + Utils.getAdapterStateName(desiredState)
                    + "): interrupted, Reason: " + e);
        } finally {
            mBluetoothAdapterLock.unlock();
        }
        assertTrue(mBluetoothAdapter.getState() == desiredState);
    }

    /**
     * Request that the adapter be turned on.
     */
    public synchronized void forceAdapterOn() {
        forceAdapterState(BluetoothAdapter.STATE_ON, true);
    }

    /**
     * Request that the adapter be turned off.
     */
    public synchronized void forceAdapterOff() {
        forceAdapterState(BluetoothAdapter.STATE_OFF, true);
    }

    /**
     * Request that the adapter be turned off. Do not persist the off state across a reboot
     */
    public synchronized void forceAdapterOffDoNotPersist() {
        forceAdapterState(BluetoothAdapter.STATE_OFF, false);
    }

    /**
     * Request that the adapter be turned eother on or off.
     */
    private void forceAdapterState(int desiredState, boolean persistAcrossReboot) {
        logd("Forcing adapter to be " + Utils.getAdapterStateName(desiredState));
        // If the current state matches the requested state, these calls return immediately and
        // our loop below will simply read the proper state
        if (desiredState == BluetoothAdapter.STATE_ON) {
            mBluetoothAdapter.enable();
        } else {
            mBluetoothAdapter.disable(persistAcrossReboot);
        }
        waitForAdapterState(desiredState);
    }

    /**
     * Log a message to DEBUG if debug is enabled
     */
    private void logd(String msg) {
        if (DBG) {
            Log.d(TAG, msg);
        }
    }

    /**
     * Log a message to WARN
     */
    private void logw(String msg) {
        Log.w(TAG, msg);
    }

    /**
     * Log a message to ERROR
     */
    private void loge(String msg) {
        Log.e(TAG, msg);
    }
}
