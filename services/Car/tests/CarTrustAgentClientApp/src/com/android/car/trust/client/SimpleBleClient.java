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
package com.android.car.trust.client;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * A simple client that supports the scanning and connecting to available BLE devices. Should be
 * used along with {@link SimpleBleServer}.
 */
public class SimpleBleClient {
    private static final String TAG = "SimpleBleClient";
    private static final long SCAN_TIME_MS = TimeUnit.SECONDS.toMillis(10);

    private final Queue<BleAction> mBleActionQueue = new ConcurrentLinkedQueue<BleAction>();
    private final List<ClientCallback> mCallbacks = new ArrayList<>();
    private final Context mContext;
    private final BluetoothLeScanner mScanner;

    private BluetoothGatt mBtGatt;
    private ParcelUuid mServiceUuid;

    public SimpleBleClient(Context context) {
        mContext = context;
        BluetoothManager btManager = (BluetoothManager) mContext.getSystemService(
                Context.BLUETOOTH_SERVICE);
        mScanner = btManager.getAdapter().getBluetoothLeScanner();
    }

    /**
     * Start scanning for a BLE devices with the specified service uuid.
     *
     * @param parcelUuid {@link ParcelUuid} used to identify the device that should be used for
     *                   this client. This uuid should be the same as the one that is set in the
     *                   {@link android.bluetooth.le.AdvertiseData.Builder} by the advertising
     *                   device.
     */
    public void start(ParcelUuid parcelUuid) {
        mServiceUuid = parcelUuid;

        // We only want to scan for devices that have the correct uuid set in its advertise data.
        List<ScanFilter> filters = new ArrayList<ScanFilter>();
        ScanFilter.Builder serviceFilter = new ScanFilter.Builder();
        serviceFilter.setServiceUuid(mServiceUuid);
        filters.add(serviceFilter.build());

        ScanSettings.Builder settings = new ScanSettings.Builder();
        settings.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Start scanning for uuid: " + mServiceUuid.getUuid());
        }

        mScanner.startScan(filters, settings.build(), mScanCallback);

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Stopping Scanner");
                }
                mScanner.stopScan(mScanCallback);
            }
        }, SCAN_TIME_MS);
    }

    private boolean hasServiceUuid(ScanResult result) {
        if (result.getScanRecord() == null
                || result.getScanRecord().getServiceUuids() == null
                || result.getScanRecord().getServiceUuids().size() == 0) {
            return false;
        }
        return true;
    }

    /**
     * Writes to a {@link BluetoothGattCharacteristic} if possible, or queues the action until
     * other actions are complete.
     *
     * @param characteristic {@link BluetoothGattCharacteristic} to be written
     */
    public void writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        processAction(new BleAction(characteristic, BleAction.ACTION_WRITE));
    }

    /**
     * Reads a {@link BluetoothGattCharacteristic} if possible, or queues the read action until
     * other actions are complete.
     *
     * @param characteristic {@link BluetoothGattCharacteristic} to be read.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        processAction(new BleAction(characteristic, BleAction.ACTION_READ));
    }

    /**
     * Enable or disable notification for specified {@link BluetoothGattCharacteristic}.
     *
     * @param characteristic The {@link BluetoothGattCharacteristic} for which to enable
     *                       notifications.
     * @param enabled        True if notifications should be enabled, false otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
            boolean enabled) {
        mBtGatt.setCharacteristicNotification(characteristic, enabled);
    }

    /**
     * Add a {@link ClientCallback} to listen for updates from BLE components
     */
    public void addCallback(ClientCallback callback) {
        mCallbacks.add(callback);
    }

    public void removeCallback(ClientCallback callback) {
        mCallbacks.remove(callback);
    }

    private void processAction(BleAction action) {
        // Only execute actions if the queue is empty.
        if (mBleActionQueue.size() > 0) {
            mBleActionQueue.add(action);
            return;
        }

        mBleActionQueue.add(action);
        executeAction(mBleActionQueue.peek());
    }

    private void processNextAction() {
        mBleActionQueue.poll();
        executeAction(mBleActionQueue.peek());
    }

    private void executeAction(@Nullable BleAction action) {
        if (action == null) {
            return;
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Executing BLE Action type: " + action.getAction());
        }

        switch (action.getAction()) {
            case BleAction.ACTION_WRITE:
                mBtGatt.writeCharacteristic(action.getCharacteristic());
                break;
            case BleAction.ACTION_READ:
                mBtGatt.readCharacteristic(action.getCharacteristic());
                break;
            default:
                Log.e(TAG, "Encountered unknown BlueAction: " + action.getAction());
        }
    }

    private String getStatus(int status) {
        switch (status) {
            case BluetoothGatt.GATT_FAILURE:
                return "Failure";
            case BluetoothGatt.GATT_SUCCESS:
                return "GATT_SUCCESS";
            case BluetoothGatt.GATT_READ_NOT_PERMITTED:
                return "GATT_READ_NOT_PERMITTED";
            case BluetoothGatt.GATT_WRITE_NOT_PERMITTED:
                return "GATT_WRITE_NOT_PERMITTED";
            case BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION:
                return "GATT_INSUFFICIENT_AUTHENTICATION";
            case BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED:
                return "GATT_REQUEST_NOT_SUPPORTED";
            case BluetoothGatt.GATT_INVALID_OFFSET:
                return "GATT_INVALID_OFFSET";
            case BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH:
                return "GATT_INVALID_ATTRIBUTE_LENGTH";
            case BluetoothGatt.GATT_CONNECTION_CONGESTED:
                return "GATT_CONNECTION_CONGESTED";
            default:
                return "unknown";
        }
    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Scan result found: " + result.getScanRecord().getServiceUuids());
            }

            if (!hasServiceUuid(result)) {
                return;
            }

            for (ParcelUuid uuid : result.getScanRecord().getServiceUuids()) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Scan result UUID: " + uuid);
                }

                if (uuid.equals(mServiceUuid)) {
                    // This client only supports connecting to one service.
                    // Once we find one, stop scanning and open a GATT connection to the device.
                    mScanner.stopScan(mScanCallback);
                    mBtGatt = device.connectGatt(mContext, /* autoConnect= */ false, mGattCallback);
                    return;
                }
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                for (ScanResult r : results) {
                    Log.d(TAG, "Batch scanResult: " + r.getDevice().getName()
                            + " " + r.getDevice().getAddress());
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "Scan failed: " + errorCode);
        }
    };

    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Gatt connection status: " + getStatus(status)
                        + " newState: " + newState);
            }

            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    mBtGatt.discoverServices();
                    for (ClientCallback callback : mCallbacks) {
                        callback.onDeviceConnected(gatt.getDevice());
                    }
                    break;

                case BluetoothProfile.STATE_DISCONNECTED:
                    for (ClientCallback callback : mCallbacks) {
                        callback.onDeviceDisconnected();
                    }
                    break;

                default:
                    // Do nothing.
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onServicesDiscovered: " + status);
            }

            List<BluetoothGattService> services = gatt.getServices();
            if (services == null || services.size() <= 0) {
                return;
            }

            // Notify clients of newly discovered services.
            for (BluetoothGattService service : mBtGatt.getServices()) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Found service: " + service.getUuid() + " notifying clients");
                }

                for (ClientCallback callback : mCallbacks) {
                    callback.onServiceDiscovered(service);
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic, int status) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onCharacteristicWrite: " + status);
            }

            processNextAction();
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic, int status) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onCharacteristicRead:" + new String(characteristic.getValue()));
            }

            processNextAction();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic) {
            for (ClientCallback callback : mCallbacks) {
                callback.onCharacteristicChanged(gatt, characteristic);
            }
            processNextAction();
        }
    };

    /**
     * Wrapper class to allow queuing of BLE actions. The BLE stack allows only one action to be
     * executed at a time.
     */
    private static class BleAction {
        public static final int ACTION_WRITE = 0;
        public static final int ACTION_READ = 1;

        @IntDef({ ACTION_WRITE, ACTION_READ })
        public @interface ActionType {}

        private final int mAction;
        private final BluetoothGattCharacteristic mCharacteristic;

        BleAction(BluetoothGattCharacteristic characteristic, @ActionType int action) {
            mAction = action;
            mCharacteristic = characteristic;
        }

        @ActionType
        public int getAction() {
            return mAction;
        }

        public BluetoothGattCharacteristic getCharacteristic() {
            return mCharacteristic;
        }
    }

    /**
     * Callback for classes that wish to be notified of BLE updates.
     */
    public interface ClientCallback {
        /**
         * Called when a device that has a matching service UUID is found.
         **/
        void onDeviceConnected(BluetoothDevice device);

        /** Called when the currently connected device has been disconnected. */
        void onDeviceDisconnected();

        /**
         * Called when a characteristic has been changed.
         *
         * @param gatt The GATT client the characteristic is associated with.
         * @param characteristic The characteristic that has been changed.
         */
        void onCharacteristicChanged(BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic);

        /**
         * Called for each {@link BluetoothGattService} that is discovered on the
         * {@link BluetoothDevice} after a matching scan result and connection.
         *
         * @param service {@link BluetoothGattService} that has been discovered.
         */
        void onServiceDiscovered(BluetoothGattService service);
    }
}
