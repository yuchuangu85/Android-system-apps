/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.car.dialer.telecom;

import android.content.Context;

import com.android.car.dialer.livedata.BluetoothHfpStateLiveData;
import com.android.car.dialer.livedata.BluetoothPairListLiveData;
import com.android.car.dialer.livedata.BluetoothStateLiveData;

/**
 * Class that responsible for getting status of bluetooth connections.
 */
public class UiBluetoothMonitor {
    private static String TAG = "Em.BtMonitor";

    private static UiBluetoothMonitor sUiBluetoothMonitor;

    private final Context mContext;

    private BluetoothHfpStateLiveData mHfpStateLiveData;
    private BluetoothPairListLiveData mPairListLiveData;
    private BluetoothStateLiveData mBluetoothStateLiveData;

    /**
     * Initialized a globally accessible {@link UiBluetoothMonitor} which can be retrieved by
     * {@link #get}. If this function is called a second time before calling {@link #tearDown()},
     * an exception will be thrown.
     *
     * @param applicationContext Application context.
     */
    // TODO: Create a singleton abstract class for Dialer common service.
    public static UiBluetoothMonitor init(Context applicationContext) {
        if (sUiBluetoothMonitor == null) {
            sUiBluetoothMonitor = new UiBluetoothMonitor(applicationContext);
        } else {
            throw new IllegalStateException("UiBluetoothMonitor has been initialized.");
        }

        return sUiBluetoothMonitor;
    }

    public static UiBluetoothMonitor get() {
        return sUiBluetoothMonitor;
    }

    private UiBluetoothMonitor(Context applicationContext) {
        mContext = applicationContext;
    }

    /**
     * Stops the {@link UiBluetoothMonitor}. Call this function when Dialer goes to background.
     * {@link #get()} won't return a valid {@link UiBluetoothMonitor} after calling this function.
     */
    public void tearDown() {
        sUiBluetoothMonitor = null;
    }

    /**
     * Returns a LiveData which monitors the HFP profile state changes.
     */
    public BluetoothHfpStateLiveData getHfpStateLiveData() {
        if (mHfpStateLiveData == null) {
            mHfpStateLiveData = new BluetoothHfpStateLiveData(mContext);
        }
        return mHfpStateLiveData;
    }

    /**
     * Returns a LiveData which monitors the paired device list changes.
     */
    public BluetoothPairListLiveData getPairListLiveData() {
        if (mPairListLiveData == null) {
            mPairListLiveData = new BluetoothPairListLiveData(mContext);
        }
        return mPairListLiveData;
    }

    /**
     * Returns a LiveData which monitors the Bluetooth state changes.
     */
    public BluetoothStateLiveData getBluetoothStateLiveData() {
        if (mBluetoothStateLiveData == null) {
            mBluetoothStateLiveData = new BluetoothStateLiveData(mContext);
        }
        return mBluetoothStateLiveData;
    }
}
