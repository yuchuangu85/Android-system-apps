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
package android.car;

import android.Manifest;
import android.annotation.RequiresPermission;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;

/**
 * CarBluetoothManager - Provides an API to interact with Car specific Bluetooth Device Management
 *
 * @hide
 */
public final class CarBluetoothManager implements CarManagerBase {
    private static final String TAG = "CarBluetoothManager";
    private final Context mContext;
    private final ICarBluetooth mService;

    /**
     * Initiate automatated connecting of devices based on the prioritized device lists for each
     * profile.
     *
     * The device lists for each profile are maintained by CarBluetoothService. Devices are added to
     * the end of a profile's list when the device bonds, if the device supports the given profile.
     * Devices are removed when unbond. These lists are specific to the current foreground user.
     *
     * If you are calling this function, you may want to disable CarBluetoothService's default
     * device connection policy by setting the "useDefaultBluetoothConnectionPolicy" resource
     * overlay flag to false.
     *
     * @hide
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
    public void connectDevices() {
        try {
            mService.connectDevices();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Create an instance of CarBluetoothManager
     *
     * @hide
     */
    public CarBluetoothManager(IBinder service, Context context) {
        mContext = context;
        mService = ICarBluetooth.Stub.asInterface(service);
    }

    @Override
    public void onCarDisconnected() {
    }
}
