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
 * limitations under the License
 */

package com.android.server.telecom.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothHearingAid;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.telecom.Log;
import android.telecom.Logging.Session;

import com.android.internal.os.SomeArgs;

import static com.android.server.telecom.bluetooth.BluetoothRouteManager.BT_AUDIO_IS_ON;
import static com.android.server.telecom.bluetooth.BluetoothRouteManager.BT_AUDIO_LOST;


public class BluetoothStateReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = BluetoothStateReceiver.class.getSimpleName();
    public static final IntentFilter INTENT_FILTER;
    static {
        INTENT_FILTER = new IntentFilter();
        INTENT_FILTER.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        INTENT_FILTER.addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED);
        INTENT_FILTER.addAction(BluetoothHeadset.ACTION_ACTIVE_DEVICE_CHANGED);
        INTENT_FILTER.addAction(BluetoothHearingAid.ACTION_CONNECTION_STATE_CHANGED);
        INTENT_FILTER.addAction(BluetoothHearingAid.ACTION_ACTIVE_DEVICE_CHANGED);
    }

    // If not in a call, BSR won't listen to the Bluetooth stack's HFP on/off messages, since
    // other apps could be turning it on and off. We don't want to interfere.
    private boolean mIsInCall = false;
    private final BluetoothRouteManager mBluetoothRouteManager;
    private final BluetoothDeviceManager mBluetoothDeviceManager;

    public void onReceive(Context context, Intent intent) {
        Log.startSession("BSR.oR");
        try {
            String action = intent.getAction();
            switch (action) {
                case BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED:
                    handleAudioStateChanged(intent);
                    break;
                case BluetoothHearingAid.ACTION_CONNECTION_STATE_CHANGED:
                case BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED:
                    handleConnectionStateChanged(intent);
                    break;
                case BluetoothHearingAid.ACTION_ACTIVE_DEVICE_CHANGED:
                case BluetoothHeadset.ACTION_ACTIVE_DEVICE_CHANGED:
                    handleActiveDeviceChanged(intent);
                    break;
            }
        } finally {
            Log.endSession();
        }
    }

    private void handleAudioStateChanged(Intent intent) {
        int bluetoothHeadsetAudioState =
                intent.getIntExtra(BluetoothHeadset.EXTRA_STATE,
                        BluetoothHeadset.STATE_AUDIO_DISCONNECTED);
        BluetoothDevice device =
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (device == null) {
            Log.w(LOG_TAG, "Got null device from broadcast. " +
                    "Ignoring.");
            return;
        }

        Log.i(LOG_TAG, "Device %s transitioned to audio state %d",
                device.getAddress(), bluetoothHeadsetAudioState);
        Session session = Log.createSubsession();
        SomeArgs args = SomeArgs.obtain();
        args.arg1 = session;
        args.arg2 = device.getAddress();
        switch (bluetoothHeadsetAudioState) {
            case BluetoothHeadset.STATE_AUDIO_CONNECTED:
                if (!mIsInCall) {
                    Log.i(LOG_TAG, "Ignoring BT audio on since we're not in a call");
                    return;
                }
                mBluetoothRouteManager.sendMessage(BT_AUDIO_IS_ON, args);
                break;
            case BluetoothHeadset.STATE_AUDIO_DISCONNECTED:
                mBluetoothRouteManager.sendMessage(BT_AUDIO_LOST, args);
                break;
        }
    }

    private void handleConnectionStateChanged(Intent intent) {
        int bluetoothHeadsetState = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE,
                BluetoothHeadset.STATE_DISCONNECTED);
        BluetoothDevice device =
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

        if (device == null) {
            Log.w(LOG_TAG, "Got null device from broadcast. " +
                    "Ignoring.");
            return;
        }

        Log.i(LOG_TAG, "Device %s changed state to %d",
                device.getAddress(), bluetoothHeadsetState);

        boolean isHearingAid = BluetoothHearingAid.ACTION_CONNECTION_STATE_CHANGED
                .equals(intent.getAction());
        if (bluetoothHeadsetState == BluetoothProfile.STATE_CONNECTED) {
            mBluetoothDeviceManager.onDeviceConnected(device, isHearingAid);
        } else if (bluetoothHeadsetState == BluetoothProfile.STATE_DISCONNECTED
                || bluetoothHeadsetState == BluetoothProfile.STATE_DISCONNECTING) {
            mBluetoothDeviceManager.onDeviceDisconnected(device, isHearingAid);
        }
    }

    private void handleActiveDeviceChanged(Intent intent) {
        BluetoothDevice device =
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        boolean isHearingAid =
                BluetoothHearingAid.ACTION_ACTIVE_DEVICE_CHANGED.equals(intent.getAction());
        Log.i(LOG_TAG, "Device %s is now the preferred BT device for %s", device,
                isHearingAid ? "hearing aid" : "HFP");

        mBluetoothRouteManager.onActiveDeviceChanged(device, isHearingAid);
        if (isHearingAid) {
            Session session = Log.createSubsession();
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = session;
            if (device == null) {
                mBluetoothRouteManager.sendMessage(BT_AUDIO_LOST, args);
            } else {
                args.arg2 = device.getAddress();
                mBluetoothRouteManager.sendMessage(BT_AUDIO_IS_ON, args);
            }
        }
    }

    public BluetoothStateReceiver(BluetoothDeviceManager deviceManager,
            BluetoothRouteManager routeManager) {
        mBluetoothDeviceManager = deviceManager;
        mBluetoothRouteManager = routeManager;
    }

    public void setIsInCall(boolean isInCall) {
        mIsInCall = isInCall;
    }
}
