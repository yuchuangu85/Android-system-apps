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
 * limitations under the License
 */

package com.android.server.telecom.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothHearingAid;
import android.content.Context;
import android.os.Message;
import android.telecom.Log;
import android.telecom.Logging.Session;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.SomeArgs;
import com.android.internal.util.IState;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.server.telecom.BluetoothHeadsetProxy;
import com.android.server.telecom.TelecomSystem;
import com.android.server.telecom.Timeouts;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class BluetoothRouteManager extends StateMachine {
    private static final String LOG_TAG = BluetoothRouteManager.class.getSimpleName();

    private static final SparseArray<String> MESSAGE_CODE_TO_NAME = new SparseArray<String>() {{
         put(NEW_DEVICE_CONNECTED, "NEW_DEVICE_CONNECTED");
         put(LOST_DEVICE, "LOST_DEVICE");
         put(CONNECT_HFP, "CONNECT_HFP");
         put(DISCONNECT_HFP, "DISCONNECT_HFP");
         put(RETRY_HFP_CONNECTION, "RETRY_HFP_CONNECTION");
         put(BT_AUDIO_IS_ON, "BT_AUDIO_IS_ON");
         put(BT_AUDIO_LOST, "BT_AUDIO_LOST");
         put(CONNECTION_TIMEOUT, "CONNECTION_TIMEOUT");
         put(GET_CURRENT_STATE, "GET_CURRENT_STATE");
         put(RUN_RUNNABLE, "RUN_RUNNABLE");
    }};

    public static final String AUDIO_OFF_STATE_NAME = "AudioOff";
    public static final String AUDIO_CONNECTING_STATE_NAME_PREFIX = "Connecting";
    public static final String AUDIO_CONNECTED_STATE_NAME_PREFIX = "Connected";

    // Timeout for querying the current state from the state machine handler.
    private static final int GET_STATE_TIMEOUT = 1000;

    public interface BluetoothStateListener {
        void onBluetoothDeviceListChanged();
        void onBluetoothActiveDevicePresent();
        void onBluetoothActiveDeviceGone();
        void onBluetoothAudioConnected();
        void onBluetoothAudioDisconnected();
        /**
         * This gets called when we get an unexpected state change from Bluetooth. Their stack does
         * weird things sometimes, so this is really a signal for the listener to refresh their
         * internal state and make sure it matches up with what the BT stack is doing.
         */
        void onUnexpectedBluetoothStateChange();
    }

    /**
     * Constants representing messages sent to the state machine.
     * Messages are expected to be sent with {@link SomeArgs} as the obj.
     * In all cases, arg1 will be the log session.
     */
    // arg2: Address of the new device
    public static final int NEW_DEVICE_CONNECTED = 1;
    // arg2: Address of the lost device
    public static final int LOST_DEVICE = 2;

    // arg2 (optional): the address of the specific device to connect to.
    public static final int CONNECT_HFP = 100;
    // No args.
    public static final int DISCONNECT_HFP = 101;
    // arg2: the address of the device to connect to.
    public static final int RETRY_HFP_CONNECTION = 102;

    // arg2: the address of the device that is on
    public static final int BT_AUDIO_IS_ON = 200;
    // arg2: the address of the device that lost BT audio
    public static final int BT_AUDIO_LOST = 201;

    // No args; only used internally
    public static final int CONNECTION_TIMEOUT = 300;

    // Get the current state and send it through the BlockingQueue<IState> provided as the object
    // arg.
    public static final int GET_CURRENT_STATE = 400;

    // arg2: Runnable
    public static final int RUN_RUNNABLE = 9001;

    private static final int MAX_CONNECTION_RETRIES = 2;

    // States
    private final class AudioOffState extends State {
        @Override
        public String getName() {
            return AUDIO_OFF_STATE_NAME;
        }

        @Override
        public void enter() {
            BluetoothDevice erroneouslyConnectedDevice = getBluetoothAudioConnectedDevice();
            if (erroneouslyConnectedDevice != null) {
                Log.w(LOG_TAG, "Entering AudioOff state but device %s appears to be connected. " +
                        "Switching to audio-on state for that device.", erroneouslyConnectedDevice);
                // change this to just transition to the new audio on state
                transitionToActualState();
            }
            cleanupStatesForDisconnectedDevices();
            if (mListener != null) {
                mListener.onBluetoothAudioDisconnected();
            }
        }

        @Override
        public boolean processMessage(Message msg) {
            if (msg.what == RUN_RUNNABLE) {
                ((Runnable) msg.obj).run();
                return HANDLED;
            }

            SomeArgs args = (SomeArgs) msg.obj;
            try {
                switch (msg.what) {
                    case NEW_DEVICE_CONNECTED:
                        addDevice((String) args.arg2);
                        break;
                    case LOST_DEVICE:
                        removeDevice((String) args.arg2);
                        break;
                    case CONNECT_HFP:
                        String actualAddress = connectBtAudio((String) args.arg2);

                        if (actualAddress != null) {
                            transitionTo(getConnectingStateForAddress(actualAddress,
                                    "AudioOff/CONNECT_HFP"));
                        } else {
                            Log.w(LOG_TAG, "Tried to connect to %s but failed to connect to" +
                                    " any HFP device.", (String) args.arg2);
                        }
                        break;
                    case DISCONNECT_HFP:
                        // Ignore.
                        break;
                    case RETRY_HFP_CONNECTION:
                        Log.i(LOG_TAG, "Retrying HFP connection to %s", (String) args.arg2);
                        String retryAddress = connectBtAudio((String) args.arg2, args.argi1);

                        if (retryAddress != null) {
                            transitionTo(getConnectingStateForAddress(retryAddress,
                                    "AudioOff/RETRY_HFP_CONNECTION"));
                        } else {
                            Log.i(LOG_TAG, "Retry failed.");
                        }
                        break;
                    case CONNECTION_TIMEOUT:
                        // Ignore.
                        break;
                    case BT_AUDIO_IS_ON:
                        String address = (String) args.arg2;
                        Log.w(LOG_TAG, "HFP audio unexpectedly turned on from device %s", address);
                        transitionTo(getConnectedStateForAddress(address,
                                "AudioOff/BT_AUDIO_IS_ON"));
                        break;
                    case BT_AUDIO_LOST:
                        Log.i(LOG_TAG, "Received HFP off for device %s while HFP off.",
                                (String) args.arg2);
                        mListener.onUnexpectedBluetoothStateChange();
                        break;
                    case GET_CURRENT_STATE:
                        BlockingQueue<IState> sink = (BlockingQueue<IState>) args.arg3;
                        sink.offer(this);
                        break;
                }
            } finally {
                args.recycle();
            }
            return HANDLED;
        }
    }

    private final class AudioConnectingState extends State {
        private final String mDeviceAddress;

        AudioConnectingState(String address) {
            mDeviceAddress = address;
        }

        @Override
        public String getName() {
            return AUDIO_CONNECTING_STATE_NAME_PREFIX + ":" + mDeviceAddress;
        }

        @Override
        public void enter() {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = Log.createSubsession();
            sendMessageDelayed(CONNECTION_TIMEOUT, args,
                    mTimeoutsAdapter.getBluetoothPendingTimeoutMillis(
                            mContext.getContentResolver()));
            // Pretend like audio is connected when communicating w/ CARSM.
            mListener.onBluetoothAudioConnected();
        }

        @Override
        public void exit() {
            removeMessages(CONNECTION_TIMEOUT);
        }

        @Override
        public boolean processMessage(Message msg) {
            if (msg.what == RUN_RUNNABLE) {
                ((Runnable) msg.obj).run();
                return HANDLED;
            }

            SomeArgs args = (SomeArgs) msg.obj;
            String address = (String) args.arg2;
            try {
                switch (msg.what) {
                    case NEW_DEVICE_CONNECTED:
                        // If the device isn't new, don't bother passing it up.
                        addDevice(address);
                        break;
                    case LOST_DEVICE:
                        removeDevice((String) args.arg2);
                        if (Objects.equals(address, mDeviceAddress)) {
                            transitionToActualState();
                        }
                        break;
                    case CONNECT_HFP:
                        if (Objects.equals(mDeviceAddress, address)) {
                            // Ignore repeated connection attempts to the same device
                            break;
                        }
                        String actualAddress = connectBtAudio(address);

                        if (actualAddress != null) {
                            transitionTo(getConnectingStateForAddress(actualAddress,
                                    "AudioConnecting/CONNECT_HFP"));
                        } else {
                            Log.w(LOG_TAG, "Tried to connect to %s but failed" +
                                    " to connect to any HFP device.", (String) args.arg2);
                        }
                        break;
                    case DISCONNECT_HFP:
                        mDeviceManager.disconnectAudio();
                        break;
                    case RETRY_HFP_CONNECTION:
                        if (Objects.equals(address, mDeviceAddress)) {
                            Log.d(LOG_TAG, "Retry message came through while connecting.");
                        } else {
                            String retryAddress = connectBtAudio(address, args.argi1);
                            if (retryAddress != null) {
                                transitionTo(getConnectingStateForAddress(retryAddress,
                                        "AudioConnecting/RETRY_HFP_CONNECTION"));
                            } else {
                                Log.i(LOG_TAG, "Retry failed.");
                            }
                        }
                        break;
                    case CONNECTION_TIMEOUT:
                        Log.i(LOG_TAG, "Connection with device %s timed out.",
                                mDeviceAddress);
                        transitionToActualState();
                        break;
                    case BT_AUDIO_IS_ON:
                        if (Objects.equals(mDeviceAddress, address)) {
                            Log.i(LOG_TAG, "HFP connection success for device %s.", mDeviceAddress);
                            transitionTo(mAudioConnectedStates.get(mDeviceAddress));
                        } else {
                            Log.w(LOG_TAG, "In connecting state for device %s but %s" +
                                    " is now connected", mDeviceAddress, address);
                            transitionTo(getConnectedStateForAddress(address,
                                    "AudioConnecting/BT_AUDIO_IS_ON"));
                        }
                        break;
                    case BT_AUDIO_LOST:
                        if (Objects.equals(mDeviceAddress, address) || address == null) {
                            Log.i(LOG_TAG, "Connection with device %s failed.",
                                    mDeviceAddress);
                            transitionToActualState();
                        } else {
                            Log.w(LOG_TAG, "Got HFP lost message for device %s while" +
                                    " connecting to %s.", address, mDeviceAddress);
                            mListener.onUnexpectedBluetoothStateChange();
                        }
                        break;
                    case GET_CURRENT_STATE:
                        BlockingQueue<IState> sink = (BlockingQueue<IState>) args.arg3;
                        sink.offer(this);
                        break;
                }
            } finally {
                args.recycle();
            }
            return HANDLED;
        }
    }

    private final class AudioConnectedState extends State {
        private final String mDeviceAddress;

        AudioConnectedState(String address) {
            mDeviceAddress = address;
        }

        @Override
        public String getName() {
            return AUDIO_CONNECTED_STATE_NAME_PREFIX + ":" + mDeviceAddress;
        }

        @Override
        public void enter() {
            // Remove any of the retries that are still in the queue once any device becomes
            // connected.
            removeMessages(RETRY_HFP_CONNECTION);
            // Remove and add to ensure that the device is at the top.
            mMostRecentlyUsedDevices.remove(mDeviceAddress);
            mMostRecentlyUsedDevices.add(mDeviceAddress);
            mListener.onBluetoothAudioConnected();
        }

        @Override
        public boolean processMessage(Message msg) {
            if (msg.what == RUN_RUNNABLE) {
                ((Runnable) msg.obj).run();
                return HANDLED;
            }

            SomeArgs args = (SomeArgs) msg.obj;
            String address = (String) args.arg2;
            try {
                switch (msg.what) {
                    case NEW_DEVICE_CONNECTED:
                        addDevice(address);
                        break;
                    case LOST_DEVICE:
                        removeDevice((String) args.arg2);
                        if (Objects.equals(address, mDeviceAddress)) {
                            transitionToActualState();
                        }
                        break;
                    case CONNECT_HFP:
                        if (Objects.equals(mDeviceAddress, address)) {
                            // Ignore connection to already connected device.
                            break;
                        }
                        String actualAddress = connectBtAudio(address);

                        if (actualAddress != null) {
                            transitionTo(getConnectingStateForAddress(address,
                                    "AudioConnected/CONNECT_HFP"));
                        } else {
                            Log.w(LOG_TAG, "Tried to connect to %s but failed" +
                                    " to connect to any HFP device.", (String) args.arg2);
                        }
                        break;
                    case DISCONNECT_HFP:
                        mDeviceManager.disconnectAudio();
                        break;
                    case RETRY_HFP_CONNECTION:
                        if (Objects.equals(address, mDeviceAddress)) {
                            Log.d(LOG_TAG, "Retry message came through while connected.");
                        } else {
                            String retryAddress = connectBtAudio(address, args.argi1);
                            if (retryAddress != null) {
                                transitionTo(getConnectingStateForAddress(retryAddress,
                                        "AudioConnected/RETRY_HFP_CONNECTION"));
                            } else {
                                Log.i(LOG_TAG, "Retry failed.");
                            }
                        }
                        break;
                    case CONNECTION_TIMEOUT:
                        Log.w(LOG_TAG, "Received CONNECTION_TIMEOUT while connected.");
                        break;
                    case BT_AUDIO_IS_ON:
                        if (Objects.equals(mDeviceAddress, address)) {
                            Log.i(LOG_TAG,
                                    "Received redundant BT_AUDIO_IS_ON for %s", mDeviceAddress);
                        } else {
                            Log.w(LOG_TAG, "In connected state for device %s but %s" +
                                    " is now connected", mDeviceAddress, address);
                            transitionTo(getConnectedStateForAddress(address,
                                    "AudioConnected/BT_AUDIO_IS_ON"));
                        }
                        break;
                    case BT_AUDIO_LOST:
                        if (Objects.equals(mDeviceAddress, address) || address == null) {
                            Log.i(LOG_TAG, "HFP connection with device %s lost.", mDeviceAddress);
                            transitionToActualState();
                        } else {
                            Log.w(LOG_TAG, "Got HFP lost message for device %s while" +
                                    " connected to %s.", address, mDeviceAddress);
                            mListener.onUnexpectedBluetoothStateChange();
                        }
                        break;
                    case GET_CURRENT_STATE:
                        BlockingQueue<IState> sink = (BlockingQueue<IState>) args.arg3;
                        sink.offer(this);
                        break;
                }
            } finally {
                args.recycle();
            }
            return HANDLED;
        }
    }

    private final State mAudioOffState;
    private final Map<String, AudioConnectingState> mAudioConnectingStates = new HashMap<>();
    private final Map<String, AudioConnectedState> mAudioConnectedStates = new HashMap<>();
    private final Set<State> statesToCleanUp = new HashSet<>();
    private final LinkedHashSet<String> mMostRecentlyUsedDevices = new LinkedHashSet<>();

    private final TelecomSystem.SyncRoot mLock;
    private final Context mContext;
    private final Timeouts.Adapter mTimeoutsAdapter;

    private BluetoothStateListener mListener;
    private BluetoothDeviceManager mDeviceManager;
    // Tracks the active devices in the BT stack (HFP or hearing aid).
    private BluetoothDevice mHfpActiveDeviceCache = null;
    private BluetoothDevice mHearingAidActiveDeviceCache = null;
    private BluetoothDevice mMostRecentlyReportedActiveDevice = null;

    public BluetoothRouteManager(Context context, TelecomSystem.SyncRoot lock,
            BluetoothDeviceManager deviceManager, Timeouts.Adapter timeoutsAdapter) {
        super(BluetoothRouteManager.class.getSimpleName());
        mContext = context;
        mLock = lock;
        mDeviceManager = deviceManager;
        mDeviceManager.setBluetoothRouteManager(this);
        mTimeoutsAdapter = timeoutsAdapter;

        mAudioOffState = new AudioOffState();
        addState(mAudioOffState);
        setInitialState(mAudioOffState);
        start();
    }

    @Override
    protected void onPreHandleMessage(Message msg) {
        if (msg.obj != null && msg.obj instanceof SomeArgs) {
            SomeArgs args = (SomeArgs) msg.obj;

            Log.continueSession(((Session) args.arg1), "BRM.pM_" + msg.what);
            Log.i(LOG_TAG, "Message received: %s.", MESSAGE_CODE_TO_NAME.get(msg.what));
        } else if (msg.what == RUN_RUNNABLE && msg.obj instanceof Runnable) {
            Log.i(LOG_TAG, "Running runnable for testing");
        } else {
            Log.w(LOG_TAG, "Message sent must be of type nonnull SomeArgs, but got " +
                    (msg.obj == null ? "null" : msg.obj.getClass().getSimpleName()));
            Log.w(LOG_TAG, "The message was of code %d = %s",
                    msg.what, MESSAGE_CODE_TO_NAME.get(msg.what));
        }
    }

    @Override
    protected void onPostHandleMessage(Message msg) {
        Log.endSession();
    }

    /**
     * Returns whether there is a HFP device available to route audio to.
     * @return true if there is a device, false otherwise.
     */
    public boolean isBluetoothAvailable() {
        return mDeviceManager.getNumConnectedDevices() > 0;
    }

    /**
     * This method needs be synchronized with the local looper because getCurrentState() depends
     * on the internal state of the state machine being consistent. Therefore, there may be a
     * delay when calling this method.
     * @return
     */
    public boolean isBluetoothAudioConnectedOrPending() {
        SomeArgs args = SomeArgs.obtain();
        args.arg1 = Log.createSubsession();
        BlockingQueue<IState> stateQueue = new LinkedBlockingQueue<>();
        // Use arg3 because arg2 is reserved for the device address
        args.arg3 = stateQueue;
        sendMessage(GET_CURRENT_STATE, args);

        try {
            IState currentState = stateQueue.poll(GET_STATE_TIMEOUT, TimeUnit.MILLISECONDS);
            if (currentState == null) {
                Log.w(LOG_TAG, "Failed to get a state from the state machine in time -- Handler " +
                        "stuck?");
                return false;
            }
            return currentState != mAudioOffState;
        } catch (InterruptedException e) {
            Log.w(LOG_TAG, "isBluetoothAudioConnectedOrPending -- interrupted getting state");
            return false;
        }
    }

    /**
     * Attempts to connect to Bluetooth audio. If the first connection attempt synchronously
     * fails, schedules a retry at a later time.
     * @param address The MAC address of the bluetooth device to connect to. If null, the most
     *                recently used device will be used.
     */
    public void connectBluetoothAudio(String address) {
        SomeArgs args = SomeArgs.obtain();
        args.arg1 = Log.createSubsession();
        args.arg2 = address;
        sendMessage(CONNECT_HFP, args);
    }

    /**
     * Disconnects Bluetooth HFP audio.
     */
    public void disconnectBluetoothAudio() {
        SomeArgs args = SomeArgs.obtain();
        args.arg1 = Log.createSubsession();
        sendMessage(DISCONNECT_HFP, args);
    }

    public void disconnectSco() {
        mDeviceManager.disconnectSco();
    }

    public void cacheHearingAidDevice() {
        mDeviceManager.cacheHearingAidDevice();
    }

    public void restoreHearingAidDevice() {
        mDeviceManager.restoreHearingAidDevice();
    }

    public void setListener(BluetoothStateListener listener) {
        mListener = listener;
    }

    public void onDeviceAdded(String newDeviceAddress) {
        SomeArgs args = SomeArgs.obtain();
        args.arg1 = Log.createSubsession();
        args.arg2 = newDeviceAddress;
        sendMessage(NEW_DEVICE_CONNECTED, args);

        mListener.onBluetoothDeviceListChanged();
    }

    public void onDeviceLost(String lostDeviceAddress) {
        SomeArgs args = SomeArgs.obtain();
        args.arg1 = Log.createSubsession();
        args.arg2 = lostDeviceAddress;
        sendMessage(LOST_DEVICE, args);

        mListener.onBluetoothDeviceListChanged();
    }

    public void onActiveDeviceChanged(BluetoothDevice device, boolean isHearingAid) {
        boolean wasActiveDevicePresent = mHearingAidActiveDeviceCache != null
                || mHfpActiveDeviceCache != null;
        if (isHearingAid) {
            mHearingAidActiveDeviceCache = device;
        } else {
            mHfpActiveDeviceCache = device;
        }

        if (device != null) mMostRecentlyReportedActiveDevice = device;

        boolean isActiveDevicePresent = mHearingAidActiveDeviceCache != null
                || mHfpActiveDeviceCache != null;

        if (wasActiveDevicePresent && !isActiveDevicePresent) {
            mListener.onBluetoothActiveDeviceGone();
        } else if (!wasActiveDevicePresent && isActiveDevicePresent) {
            mListener.onBluetoothActiveDevicePresent();
        }
    }

    public boolean hasBtActiveDevice() {
        return mHearingAidActiveDeviceCache != null || mHfpActiveDeviceCache != null;
    }

    public Collection<BluetoothDevice> getConnectedDevices() {
        return mDeviceManager.getUniqueConnectedDevices();
    }

    private String connectBtAudio(String address) {
        return connectBtAudio(address, 0);
    }

    /**
     * Initiates a connection to the BT address specified.
     * Note: This method is not synchronized on the Telecom lock, so don't try and call back into
     * Telecom from within it.
     * @param address The address that should be tried first. May be null.
     * @param retryCount The number of times this connection attempt has been retried.
     * @return The address of the device that's actually being connected to, or null if no
     * connection was successful.
     */
    private String connectBtAudio(String address, int retryCount) {
        Collection<BluetoothDevice> deviceList = mDeviceManager.getConnectedDevices();
        Optional<BluetoothDevice> matchingDevice = deviceList.stream()
                .filter(d -> Objects.equals(d.getAddress(), address))
                .findAny();

        String actualAddress = matchingDevice.isPresent()
                ? address : getActiveDeviceAddress();
        if (actualAddress == null) {
            Log.i(this, "No device specified and BT stack has no active device."
                    + " Using arbitrary device");
            if (deviceList.size() > 0) {
                actualAddress = deviceList.iterator().next().getAddress();
            } else {
                Log.i(this, "No devices available at all. Not connecting.");
                return null;
            }
        }
        if (!matchingDevice.isPresent()) {
            Log.i(this, "No device with address %s available. Using %s instead.",
                    address, actualAddress);
        }
        if (!mDeviceManager.connectAudio(actualAddress)) {
            boolean shouldRetry = retryCount < MAX_CONNECTION_RETRIES;
            Log.w(LOG_TAG, "Could not connect to %s. Will %s", actualAddress,
                    shouldRetry ? "retry" : "not retry");
            if (shouldRetry) {
                SomeArgs args = SomeArgs.obtain();
                args.arg1 = Log.createSubsession();
                args.arg2 = actualAddress;
                args.argi1 = retryCount + 1;
                sendMessageDelayed(RETRY_HFP_CONNECTION, args,
                        mTimeoutsAdapter.getRetryBluetoothConnectAudioBackoffMillis(
                                mContext.getContentResolver()));
            }
            return null;
        }

        return actualAddress;
    }

    private String getActiveDeviceAddress() {
        if (mHfpActiveDeviceCache != null) {
            return mHfpActiveDeviceCache.getAddress();
        }
        if (mHearingAidActiveDeviceCache != null) {
            return mHearingAidActiveDeviceCache.getAddress();
        }
        return null;
    }

    private void transitionToActualState() {
        BluetoothDevice possiblyAlreadyConnectedDevice = getBluetoothAudioConnectedDevice();
        if (possiblyAlreadyConnectedDevice != null) {
            Log.i(LOG_TAG, "Device %s is already connected; going to AudioConnected.",
                    possiblyAlreadyConnectedDevice);
            transitionTo(getConnectedStateForAddress(
                    possiblyAlreadyConnectedDevice.getAddress(), "transitionToActualState"));
        } else {
            transitionTo(mAudioOffState);
        }
    }

    /**
     * @return The BluetoothDevice that is connected to BT audio, null if none are connected.
     */
    @VisibleForTesting
    public BluetoothDevice getBluetoothAudioConnectedDevice() {
        BluetoothHeadsetProxy bluetoothHeadset = mDeviceManager.getHeadsetService();
        BluetoothHearingAid bluetoothHearingAid = mDeviceManager.getHearingAidService();

        BluetoothDevice hfpAudioOnDevice = null;
        BluetoothDevice hearingAidActiveDevice = null;

        if (bluetoothHeadset == null && bluetoothHearingAid == null) {
            Log.i(this, "getBluetoothAudioConnectedDevice: no service available.");
            return null;
        }

        if (bluetoothHeadset != null) {
            hfpAudioOnDevice = bluetoothHeadset.getActiveDevice();

            if (bluetoothHeadset.getAudioState(hfpAudioOnDevice)
                    == BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
                hfpAudioOnDevice = null;
            }
        }

        if (bluetoothHearingAid != null) {
            for (BluetoothDevice device : bluetoothHearingAid.getActiveDevices()) {
                if (device != null) {
                    hearingAidActiveDevice = device;
                    break;
                }
            }
        }

        // Return the active device reported by either HFP or hearing aid. If both are reporting
        // active devices, go with the most recent one as reported by the receiver.
        if (hfpAudioOnDevice != null) {
            if (hearingAidActiveDevice != null) {
                Log.i(this, "Both HFP and hearing aid are reporting active devices. Going with"
                        + " the most recently reported active device: %s");
                return mMostRecentlyReportedActiveDevice;
            }
            return hfpAudioOnDevice;
        }
        return hearingAidActiveDevice;
    }

    /**
     * Check if in-band ringing is currently enabled. In-band ringing could be disabled during an
     * active connection.
     *
     * @return true if in-band ringing is enabled, false if in-band ringing is disabled
     */
    @VisibleForTesting
    public boolean isInbandRingingEnabled() {
        BluetoothHeadsetProxy bluetoothHeadset = mDeviceManager.getHeadsetService();
        if (bluetoothHeadset == null) {
            Log.i(this, "isInbandRingingEnabled: no headset service available.");
            return false;
        }
        return bluetoothHeadset.isInbandRingingEnabled();
    }

    private boolean addDevice(String address) {
        if (mAudioConnectingStates.containsKey(address)) {
            Log.i(this, "Attempting to add device %s twice.", address);
            return false;
        }
        AudioConnectedState audioConnectedState = new AudioConnectedState(address);
        AudioConnectingState audioConnectingState = new AudioConnectingState(address);
        mAudioConnectingStates.put(address, audioConnectingState);
        mAudioConnectedStates.put(address, audioConnectedState);
        addState(audioConnectedState);
        addState(audioConnectingState);
        return true;
    }

    private boolean removeDevice(String address) {
        if (!mAudioConnectingStates.containsKey(address)) {
            Log.i(this, "Attempting to remove already-removed device %s", address);
            return false;
        }
        statesToCleanUp.add(mAudioConnectingStates.remove(address));
        statesToCleanUp.add(mAudioConnectedStates.remove(address));
        mMostRecentlyUsedDevices.remove(address);
        return true;
    }

    private AudioConnectingState getConnectingStateForAddress(String address, String error) {
        if (!mAudioConnectingStates.containsKey(address)) {
            Log.w(LOG_TAG, "Device being connected to does not have a corresponding state: %s",
                    error);
            addDevice(address);
        }
        return mAudioConnectingStates.get(address);
    }

    private AudioConnectedState getConnectedStateForAddress(String address, String error) {
        if (!mAudioConnectedStates.containsKey(address)) {
            Log.w(LOG_TAG, "Device already connected to does" +
                    " not have a corresponding state: %s", error);
            addDevice(address);
        }
        return mAudioConnectedStates.get(address);
    }

    /**
     * Removes the states for disconnected devices from the state machine. Called when entering
     * AudioOff so that none of the states-to-be-removed are active.
     */
    private void cleanupStatesForDisconnectedDevices() {
        for (State state : statesToCleanUp) {
            if (state != null) {
                removeState(state);
            }
        }
        statesToCleanUp.clear();
    }

    @VisibleForTesting
    public void setInitialStateForTesting(String stateName, BluetoothDevice device) {
        sendMessage(RUN_RUNNABLE, (Runnable) () -> {
            switch (stateName) {
                case AUDIO_OFF_STATE_NAME:
                    transitionTo(mAudioOffState);
                    break;
                case AUDIO_CONNECTING_STATE_NAME_PREFIX:
                    transitionTo(getConnectingStateForAddress(device.getAddress(),
                            "setInitialStateForTesting"));
                    break;
                case AUDIO_CONNECTED_STATE_NAME_PREFIX:
                    transitionTo(getConnectedStateForAddress(device.getAddress(),
                            "setInitialStateForTesting"));
                    break;
            }
            Log.i(LOG_TAG, "transition for testing done: %s", stateName);
        });
    }

    @VisibleForTesting
    public void setActiveDeviceCacheForTesting(BluetoothDevice device, boolean isHearingAid) {
        if (isHearingAid) {
            mHearingAidActiveDeviceCache = device;
        } else {
            mHfpActiveDeviceCache = device;
        }
    }
}
