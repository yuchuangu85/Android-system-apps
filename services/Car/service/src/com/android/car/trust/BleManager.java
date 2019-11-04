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
package com.android.car.trust;

import static android.bluetooth.BluetoothProfile.GATT_SERVER;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.car.Utils;

import java.util.UUID;

/**
 * A generic class that manages BLE operations like start/stop advertising, notifying connects/
 * disconnects and reading/writing values to GATT characteristics.
 *
 * TODO(b/123248433) This could move to a separate comms library.
 */
public abstract class BleManager {
    private static final String TAG = BleManager.class.getSimpleName();

    private static final int BLE_RETRY_LIMIT = 5;
    private static final int BLE_RETRY_INTERVAL_MS = 1000;

    private static final int GATT_SERVER_RETRY_LIMIT = 20;
    private static final int GATT_SERVER_RETRY_DELAY_MS = 200;

    // https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth
    // .service.generic_access.xml
    private static final UUID GENERIC_ACCESS_PROFILE_UUID =
            UUID.fromString("00001800-0000-1000-8000-00805f9b34fb");
    //https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth
    // .characteristic.gap.device_name.xml
    private static final UUID DEVICE_NAME_UUID =
            UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb");

    private final Handler mHandler = new Handler();

    private final Context mContext;
    private BluetoothManager mBluetoothManager;
    private BluetoothLeAdvertiser mAdvertiser;
    private BluetoothGattServer mGattServer;
    private BluetoothGatt mBluetoothGatt;
    private int mAdvertiserStartCount;
    private int mGattServerRetryStartCount;
    private BluetoothGattService mBluetoothGattService;
    private AdvertiseCallback mAdvertiseCallback;
    private AdvertiseData mData;

    BleManager(Context context) {
        mContext = context;
    }

    /**
     * Starts the GATT server with the given {@link BluetoothGattService} and begins
     * advertising.
     *
     * <p>It is possible that BLE service is still in TURNING_ON state when this method is invoked.
     * Therefore, several retries will be made to ensure advertising is started.
     *
     * @param service           {@link BluetoothGattService} that will be discovered by clients
     * @param data              {@link AdvertiseData} data to advertise
     * @param advertiseCallback {@link AdvertiseCallback} callback for advertiser
     */
    protected void startAdvertising(BluetoothGattService service, AdvertiseData data,
            AdvertiseCallback advertiseCallback) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "startAdvertising: " + service.getUuid().toString());
        }
        if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.e(TAG, "System does not support BLE");
            return;
        }

        mBluetoothGattService = service;
        mAdvertiseCallback = advertiseCallback;
        mData = data;
        mGattServerRetryStartCount = 0;
        mBluetoothManager = (BluetoothManager) mContext.getSystemService(
            Context.BLUETOOTH_SERVICE);
        openGattServer();
    }

    private void openGattServer() {
        // Only open one Gatt server.
        if (mGattServer != null) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Gatt Server created, retry count: " + mGattServerRetryStartCount);
            }
            mGattServer.clearServices();
            mGattServer.addService(mBluetoothGattService);
            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build();
            mAdvertiserStartCount = 0;
            startAdvertisingInternally(settings, mData, mAdvertiseCallback);
            mGattServerRetryStartCount = 0;
        } else if (mGattServerRetryStartCount < GATT_SERVER_RETRY_LIMIT) {
            mGattServer = mBluetoothManager.openGattServer(mContext, mGattServerCallback);
            mGattServerRetryStartCount++;
            mHandler.postDelayed(() -> openGattServer(), GATT_SERVER_RETRY_DELAY_MS);
        } else {
            Log.e(TAG, "Gatt server not created - exceeded retry limit.");
        }
    }

    private void startAdvertisingInternally(AdvertiseSettings settings, AdvertiseData data,
            AdvertiseCallback advertiseCallback) {
        if (BluetoothAdapter.getDefaultAdapter() != null) {
            mAdvertiser = BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser();
        }

        if (mAdvertiser != null) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Advertiser created, retry count: " + mAdvertiserStartCount);
            }
            mAdvertiser.startAdvertising(settings, data, advertiseCallback);
            mAdvertiserStartCount = 0;
        } else if (mAdvertiserStartCount < BLE_RETRY_LIMIT) {
            mHandler.postDelayed(
                    () -> startAdvertisingInternally(settings, data, advertiseCallback),
                    BLE_RETRY_INTERVAL_MS);
            mAdvertiserStartCount += 1;
        } else {
            Log.e(TAG, "Cannot start BLE Advertisement.  BT Adapter: "
                    + BluetoothAdapter.getDefaultAdapter() + " Advertise Retry count: "
                    + mAdvertiserStartCount);
        }
    }

    protected void stopAdvertising(AdvertiseCallback advertiseCallback) {
        if (mAdvertiser != null) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "stopAdvertising: ");
            }
            mAdvertiser.stopAdvertising(advertiseCallback);
        }
    }

    /**
     * Notifies the characteristic change via {@link BluetoothGattServer}
     */
    protected void notifyCharacteristicChanged(BluetoothDevice device,
            BluetoothGattCharacteristic characteristic, boolean confirm) {
        if (mGattServer == null) {
            return;
        }

        boolean result = mGattServer.notifyCharacteristicChanged(device, characteristic, confirm);

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "notifyCharacteristicChanged succeeded: " + result);
        }
    }

    /**
     * Connect the Gatt server of the remote device to retrieve device name.
     */
    protected final void retrieveDeviceName(BluetoothDevice device) {
        mBluetoothGatt = device.connectGatt(getContext(), false, mGattCallback);
    }

    protected Context getContext() {
        return mContext;
    }

    /**
     * Cleans up the BLE GATT server state.
     */
    void cleanup() {
        // Stops the advertiser and GATT server. This needs to be done to avoid leaks
        if (mAdvertiser != null) {
            mAdvertiser.cleanup();
        }

        if (mGattServer != null) {
            mGattServer.clearServices();
            try {
                for (BluetoothDevice d : mBluetoothManager.getConnectedDevices(GATT_SERVER)) {
                    mGattServer.cancelConnection(d);
                }
            } catch (UnsupportedOperationException e) {
                Log.e(TAG, "Error getting connected devices", e);
            } finally {
                stopGattServer();
            }
        }
    }

    /**
     * Close the GATT Server
     */
    void stopGattServer() {
        if (mGattServer == null) {
            return;
        }
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "stopGattServer");
        }
        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
        }
        mGattServer.close();
        mGattServer = null;
    }

    /**
     * Triggered when the name of the remote device is retrieved.
     *
     * @param deviceName Name of the remote device.
     */
    protected void onDeviceNameRetrieved(@Nullable String deviceName) {
    }

    /**
     * Triggered if a remote client has requested to change the MTU for a given connection.
     *
     * @param size The new MTU size.
     */
    protected void onMtuSizeChanged(int size) {
    }

    /**
     * Triggered when a device (GATT client) connected.
     *
     * @param device Remote device that connected on BLE.
     */
    protected void onRemoteDeviceConnected(BluetoothDevice device) {
    }

    /**
     * Triggered when a device (GATT client) disconnected.
     *
     * @param device Remote device that disconnected on BLE.
     */
    protected void onRemoteDeviceDisconnected(BluetoothDevice device) {
    }

    /**
     * Triggered when this BleManager receives a write request from a remote
     * device. Sub-classes should implement how to handle requests.
     * <p>
     *
     * @see BluetoothGattServerCallback#onCharacteristicWriteRequest(BluetoothDevice, int,
     * BluetoothGattCharacteristic, boolean, boolean, int, byte[])
     */
    protected abstract void onCharacteristicWrite(BluetoothDevice device, int requestId,
            BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean
            responseNeeded, int offset, byte[] value);

    /**
     * Triggered when this BleManager receives a read request from a remote device.
     * <p>
     *
     * @see BluetoothGattServerCallback#onCharacteristicReadRequest(BluetoothDevice, int, int,
     * BluetoothGattCharacteristic)
     */
    protected abstract void onCharacteristicRead(BluetoothDevice device,
            int requestId, int offset, BluetoothGattCharacteristic characteristic);

    private final BluetoothGattServerCallback mGattServerCallback =
            new BluetoothGattServerCallback() {
                @Override
                public void onConnectionStateChange(BluetoothDevice device, int status,
                        int newState) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "BLE Connection State Change: " + newState);
                    }
                    switch (newState) {
                        case BluetoothProfile.STATE_CONNECTED:
                            onRemoteDeviceConnected(device);
                            break;
                        case BluetoothProfile.STATE_DISCONNECTED:
                            onRemoteDeviceDisconnected(device);
                            break;
                        default:
                            Log.w(TAG,
                                    "Connection state not connecting or disconnecting; ignoring: "
                                            + newState);
                    }
                }

                @Override
                public void onServiceAdded(int status, BluetoothGattService service) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG,
                                "Service added status: " + status + " uuid: " + service.getUuid());
                    }
                }

                @Override
                public void onCharacteristicReadRequest(BluetoothDevice device, int requestId,
                        int offset, BluetoothGattCharacteristic characteristic) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "Read request for characteristic: " + characteristic.getUuid());
                    }

                    mGattServer.sendResponse(device, requestId,
                            BluetoothGatt.GATT_SUCCESS, offset, characteristic.getValue());
                    onCharacteristicRead(device, requestId, offset, characteristic);
                }

                @Override
                public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                        BluetoothGattCharacteristic characteristic, boolean preparedWrite,
                        boolean responseNeeded, int offset, byte[] value) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "Write request for characteristic: " + characteristic.getUuid()
                                + "value: " + Utils.byteArrayToHexString(value));
                    }

                    mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
                            offset, value);
                    onCharacteristicWrite(device, requestId, characteristic,
                            preparedWrite, responseNeeded, offset, value);
                }

                @Override
                public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                        BluetoothGattDescriptor descriptor, boolean preparedWrite,
                        boolean responseNeeded, int offset, byte[] value) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "Write request for descriptor: " + descriptor.getUuid()
                                + "; value: " + Utils.byteArrayToHexString(value));
                    }

                    mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
                            offset, value);
                }

                @Override
                public void onMtuChanged(BluetoothDevice device, int mtu) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "onMtuChanged: " + mtu + " for device " + device.getAddress());
                    }
                    onMtuSizeChanged(mtu);
                }

            };

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Gatt Connection State Change: " + newState);
            }
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "Gatt connected");
                    }
                    mBluetoothGatt.discoverServices();
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "Gatt Disconnected");
                    }
                    break;
                default:
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG,
                                "Connection state not connecting or disconnecting; ignoring: "
                                        + newState);
                    }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Gatt Services Discovered");
            }
            BluetoothGattService gapService = mBluetoothGatt.getService(
                    GENERIC_ACCESS_PROFILE_UUID);
            if (gapService == null) {
                Log.e(TAG, "Generic Access Service is Null");
                return;
            }
            BluetoothGattCharacteristic deviceNameCharacteristic = gapService.getCharacteristic(
                    DEVICE_NAME_UUID);
            if (deviceNameCharacteristic == null) {
                Log.e(TAG, "Device Name Characteristic is Null");
                return;
            }
            mBluetoothGatt.readCharacteristic(deviceNameCharacteristic);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                String deviceName = characteristic.getStringValue(0);
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "BLE Device Name: " + deviceName);
                }
                onDeviceNameRetrieved(deviceName);
            } else {
                Log.e(TAG, "Reading GAP Failed: " + status);
            }
        }
    };
}
