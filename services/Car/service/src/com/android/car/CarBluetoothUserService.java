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

import android.bluetooth.BluetoothA2dpSink;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadsetClient;
import android.bluetooth.BluetoothMapClient;
import android.bluetooth.BluetoothPan;
import android.bluetooth.BluetoothPbapClient;
import android.bluetooth.BluetoothProfile;
import android.car.ICarBluetoothUserService;
import android.util.Log;
import android.util.SparseBooleanArray;

import com.android.internal.util.Preconditions;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class CarBluetoothUserService extends ICarBluetoothUserService.Stub {
    private static final String TAG = "CarBluetoothUserService";
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);
    private final PerUserCarService mService;
    private final BluetoothAdapter mBluetoothAdapter;

    // Profiles we support
    private static final List<Integer> sProfilesToConnect = Arrays.asList(
            BluetoothProfile.HEADSET_CLIENT,
            BluetoothProfile.PBAP_CLIENT,
            BluetoothProfile.A2DP_SINK,
            BluetoothProfile.MAP_CLIENT,
            BluetoothProfile.PAN
    );

    // Profile Proxies Objects to pair with above list. Access to these proxy objects will all be
    // guarded by this classes implicit monitor lock.
    private BluetoothA2dpSink mBluetoothA2dpSink = null;
    private BluetoothHeadsetClient mBluetoothHeadsetClient = null;
    private BluetoothPbapClient mBluetoothPbapClient = null;
    private BluetoothMapClient mBluetoothMapClient = null;
    private BluetoothPan mBluetoothPan = null;

    // Concurrency variables for waitForProxyConnections. Used so we can block with a timeout while
    // setting up or closing down proxy connections.
    private final ReentrantLock mBluetoothProxyStatusLock;
    private final Condition mConditionAllProxiesConnected;
    private final Condition mConditionAllProxiesDisconnected;
    private SparseBooleanArray mBluetoothProfileStatus;
    private int mConnectedProfiles;
    private static final int PROXY_OPERATION_TIMEOUT_MS = 8000;

    /**
     * Create a CarBluetoothUserService instance.
     *
     * @param serice - A reference to a PerUserCarService, so we can use its context to receive
     *                 updates as a particular user.
     */
    public CarBluetoothUserService(PerUserCarService service) {
        mService = service;
        mConnectedProfiles = 0;
        mBluetoothProfileStatus = new SparseBooleanArray();
        for (int profile : sProfilesToConnect) {
            mBluetoothProfileStatus.put(profile, false);
        }
        mBluetoothProxyStatusLock = new ReentrantLock();
        mConditionAllProxiesConnected = mBluetoothProxyStatusLock.newCondition();
        mConditionAllProxiesDisconnected = mBluetoothProxyStatusLock.newCondition();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    /**
     * Setup connections to the profile proxy objects that talk to the Bluetooth profile services.
     *
     * Connection requests are asynchronous in nature and return through the ProfileServiceListener
     * below. Since callers expect that the proxies are initialized by the time we call this, we
     * will block (with a timeout) until all proxies are connected.
     */
    @Override
    public void setupBluetoothConnectionProxies() {
        logd("Initiate connections to profile proxies");
        Preconditions.checkNotNull(mBluetoothAdapter, "Bluetooth adapter cannot be null");
        mBluetoothProxyStatusLock.lock();
        try {

            // Connect all the profiles that are unconnected, keep count so we can wait below
            for (int profile : sProfilesToConnect) {
                if (mBluetoothProfileStatus.get(profile, false)) {
                    logd(Utils.getProfileName(profile) + " is already connected");
                    continue;
                }
                logd("Connecting " + Utils.getProfileName(profile));
                mBluetoothAdapter.getProfileProxy(mService.getApplicationContext(),
                        mProfileListener, profile);
            }

            // Wait for all the profiles to connect with a generous timeout just in case
            while (mConnectedProfiles != sProfilesToConnect.size()) {
                if (!mConditionAllProxiesConnected.await(
                        PROXY_OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    Log.e(TAG, "Timeout while waiting for all proxies to connect. Connected only "
                            + mConnectedProfiles + "/" + sProfilesToConnect.size());
                    break;
                }
            }
        } catch (InterruptedException e) {
            Log.w(TAG, "setupBluetoothConnectionProxies: interrupted", e);
        } finally {
            mBluetoothProxyStatusLock.unlock();
        }
    }

    /**
     * Close connections to the profile proxy objects
     *
     * Proxy disconnection requests are asynchronous in nature and return through the
     * ProfileServiceListener below. This method will block (with a timeout) until all proxies have
     * disconnected.
     */
    @Override
    public synchronized void closeBluetoothConnectionProxies() {
        logd("Tear down profile proxy connections");
        Preconditions.checkNotNull(mBluetoothAdapter, "Bluetooth adapter cannot be null");
        mBluetoothProxyStatusLock.lock();
        try {
            if (mBluetoothA2dpSink != null) {
                mBluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP_SINK, mBluetoothA2dpSink);
            }
            if (mBluetoothHeadsetClient != null) {
                mBluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET_CLIENT,
                        mBluetoothHeadsetClient);
            }
            if (mBluetoothPbapClient != null) {
                mBluetoothAdapter.closeProfileProxy(BluetoothProfile.PBAP_CLIENT,
                        mBluetoothPbapClient);
            }
            if (mBluetoothMapClient != null) {
                mBluetoothAdapter.closeProfileProxy(BluetoothProfile.MAP_CLIENT,
                        mBluetoothMapClient);
            }
            if (mBluetoothPan != null) {
                mBluetoothAdapter.closeProfileProxy(BluetoothProfile.PAN, mBluetoothPan);
            }

            while (mConnectedProfiles != 0) {
                if (!mConditionAllProxiesDisconnected.await(
                        PROXY_OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    Log.e(TAG, "Timeout while waiting for all proxies to disconnect. There are "
                            + mConnectedProfiles + "/" + sProfilesToConnect.size() + "still "
                            + "connected");
                    break;
                }
            }

        } catch (InterruptedException e) {
            Log.w(TAG, "closeBluetoothConnectionProxies: interrupted", e);
        } finally {
            mBluetoothProxyStatusLock.unlock();
        }
    }

    /**
     * Listen for and collect Bluetooth profile proxy connections and disconnections.
     */
    private BluetoothProfile.ServiceListener mProfileListener =
            new BluetoothProfile.ServiceListener() {
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            logd("OnServiceConnected profile: " + Utils.getProfileName(profile));

            // Grab the profile proxy object and update the status book keeping in one step so the
            // book keeping and proxy objects never disagree
            synchronized (this) {
                switch (profile) {
                    case BluetoothProfile.A2DP_SINK:
                        mBluetoothA2dpSink = (BluetoothA2dpSink) proxy;
                        break;
                    case BluetoothProfile.HEADSET_CLIENT:
                        mBluetoothHeadsetClient = (BluetoothHeadsetClient) proxy;
                        break;
                    case BluetoothProfile.PBAP_CLIENT:
                        mBluetoothPbapClient = (BluetoothPbapClient) proxy;
                        break;
                    case BluetoothProfile.MAP_CLIENT:
                        mBluetoothMapClient = (BluetoothMapClient) proxy;
                        break;
                    case BluetoothProfile.PAN:
                        mBluetoothPan = (BluetoothPan) proxy;
                        break;
                    default:
                        logd("Unhandled profile connected: " + Utils.getProfileName(profile));
                        break;
                }

                mBluetoothProxyStatusLock.lock();
                try {
                    if (!mBluetoothProfileStatus.get(profile, false)) {
                        mBluetoothProfileStatus.put(profile, true);
                        mConnectedProfiles++;
                        if (mConnectedProfiles == sProfilesToConnect.size()) {
                            logd("All profiles have connected");
                            mConditionAllProxiesConnected.signal();
                        }
                    }
                } finally {
                    mBluetoothProxyStatusLock.unlock();
                }
            }
        }

        public void onServiceDisconnected(int profile) {
            logd("onServiceDisconnected profile: " + Utils.getProfileName(profile));

            // Null the profile proxy object and update the status book keeping in one step so the
            // book keeping and proxy objects never disagree
            synchronized (this) {
                switch (profile) {
                    case BluetoothProfile.A2DP_SINK:
                        mBluetoothA2dpSink = null;
                        break;
                    case BluetoothProfile.HEADSET_CLIENT:
                        mBluetoothHeadsetClient = null;
                        break;
                    case BluetoothProfile.PBAP_CLIENT:
                        mBluetoothPbapClient = null;
                        break;
                    case BluetoothProfile.MAP_CLIENT:
                        mBluetoothMapClient = null;
                        break;
                    case BluetoothProfile.PAN:
                        mBluetoothPan = null;
                        break;
                    default:
                        logd("Unhandled profile disconnected: " + Utils.getProfileName(profile));
                        break;
                }

                mBluetoothProxyStatusLock.lock();
                try {
                    if (mBluetoothProfileStatus.get(profile, false)) {
                        mBluetoothProfileStatus.put(profile, false);
                        mConnectedProfiles--;
                        if (mConnectedProfiles == 0) {
                            logd("All profiles have disconnected");
                            mConditionAllProxiesDisconnected.signal();
                        }
                    }
                } finally {
                    mBluetoothProxyStatusLock.unlock();
                }
            }
        }
    };

    /**
     * Check if a proxy is available for the given profile to talk to the Profile's bluetooth
     * service.
     * @param profile - Bluetooth profile to check for
     * @return - true if proxy available, false if not.
     */
    @Override
    public boolean isBluetoothConnectionProxyAvailable(int profile) {
        boolean proxyConnected = false;
        mBluetoothProxyStatusLock.lock();
        try {
            proxyConnected = mBluetoothProfileStatus.get(profile, false);
        } finally {
            mBluetoothProxyStatusLock.unlock();
        }
        if (!proxyConnected) {
            setupBluetoothConnectionProxies();
            return isBluetoothConnectionProxyAvailable(profile);
        }
        return proxyConnected;
    }

    @Override
    public boolean bluetoothConnectToProfile(int profile, BluetoothDevice device) {
        if (device == null) {
            Log.e(TAG, "Cannot connect to profile on null device");
            return false;
        }
        logd("Trying to connect to " + device.getName() + " (" + device.getAddress() + ") Profile: "
                + Utils.getProfileName(profile));
        synchronized (this) {
            if (!isBluetoothConnectionProxyAvailable(profile)) {
                Log.e(TAG, "Cannot connect to Profile. Proxy Unavailable");
                return false;
            }
            switch (profile) {
                case BluetoothProfile.A2DP_SINK:
                    return mBluetoothA2dpSink.connect(device);
                case BluetoothProfile.HEADSET_CLIENT:
                    return mBluetoothHeadsetClient.connect(device);
                case BluetoothProfile.MAP_CLIENT:
                    return mBluetoothMapClient.connect(device);
                case BluetoothProfile.PBAP_CLIENT:
                    return mBluetoothPbapClient.connect(device);
                case BluetoothProfile.PAN:
                    return mBluetoothPan.connect(device);
                default:
                    Log.w(TAG, "Unknown Profile: " + Utils.getProfileName(profile));
                    break;
            }
        }
        return false;
    }

    @Override
    public boolean bluetoothDisconnectFromProfile(int profile, BluetoothDevice device) {
        if (device == null) {
            Log.e(TAG, "Cannot disconnect from profile on null device");
            return false;
        }
        logd("Trying to disconnect from " + device.getName() + " (" + device.getAddress()
                + ") Profile: " + Utils.getProfileName(profile));
        synchronized (this) {
            if (!isBluetoothConnectionProxyAvailable(profile)) {
                Log.e(TAG, "Cannot disconnect from profile. Proxy Unavailable");
                return false;
            }
            switch (profile) {
                case BluetoothProfile.A2DP_SINK:
                    return mBluetoothA2dpSink.disconnect(device);
                case BluetoothProfile.HEADSET_CLIENT:
                    return mBluetoothHeadsetClient.disconnect(device);
                case BluetoothProfile.MAP_CLIENT:
                    return mBluetoothMapClient.disconnect(device);
                case BluetoothProfile.PBAP_CLIENT:
                    return mBluetoothPbapClient.disconnect(device);
                case BluetoothProfile.PAN:
                    return mBluetoothPan.disconnect(device);
                default:
                    Log.w(TAG, "Unknown Profile: " + Utils.getProfileName(profile));
                    break;
            }
        }
        return false;
    }

    /**
     * Get the priority of the given Bluetooth profile for the given remote device
     * @param profile - Bluetooth profile
     * @param device - remote Bluetooth device
     */
    @Override
    public int getProfilePriority(int profile, BluetoothDevice device) {
        if (device == null) {
            Log.e(TAG, "Cannot get " + Utils.getProfileName(profile)
                    + " profile priority on null device");
            return BluetoothProfile.PRIORITY_UNDEFINED;
        }
        int priority;
        synchronized (this) {
            if (!isBluetoothConnectionProxyAvailable(profile)) {
                Log.e(TAG, "Cannot get " + Utils.getProfileName(profile)
                        + " profile priority. Proxy Unavailable");
                return BluetoothProfile.PRIORITY_UNDEFINED;
            }
            switch (profile) {
                case BluetoothProfile.A2DP_SINK:
                    priority = mBluetoothA2dpSink.getPriority(device);
                    break;
                case BluetoothProfile.HEADSET_CLIENT:
                    priority = mBluetoothHeadsetClient.getPriority(device);
                    break;
                case BluetoothProfile.MAP_CLIENT:
                    priority = mBluetoothMapClient.getPriority(device);
                    break;
                case BluetoothProfile.PBAP_CLIENT:
                    priority = mBluetoothPbapClient.getPriority(device);
                    break;
                default:
                    Log.w(TAG, "Unknown Profile: " + Utils.getProfileName(profile));
                    priority = BluetoothProfile.PRIORITY_UNDEFINED;
                    break;
            }
        }
        logd(Utils.getProfileName(profile) + " priority for " + device.getName() + " ("
                + device.getAddress() + ") = " + priority);
        return priority;
    }

    /**
     * Set the priority of the given Bluetooth profile for the given remote device
     * @param profile - Bluetooth profile
     * @param device - remote Bluetooth device
     * @param priority - priority to set
     */
    @Override
    public void setProfilePriority(int profile, BluetoothDevice device, int priority) {
        if (device == null) {
            Log.e(TAG, "Cannot set " + Utils.getProfileName(profile)
                    + " profile priority on null device");
            return;
        }
        logd("Setting " + Utils.getProfileName(profile) + " priority for " + device.getName() + " ("
                + device.getAddress() + ") to " + priority);
        synchronized (this) {
            if (!isBluetoothConnectionProxyAvailable(profile)) {
                Log.e(TAG, "Cannot set " + Utils.getProfileName(profile)
                        + " profile priority. Proxy Unavailable");
                return;
            }
            switch (profile) {
                case BluetoothProfile.A2DP_SINK:
                    mBluetoothA2dpSink.setPriority(device, priority);
                    break;
                case BluetoothProfile.HEADSET_CLIENT:
                    mBluetoothHeadsetClient.setPriority(device, priority);
                    break;
                case BluetoothProfile.MAP_CLIENT:
                    mBluetoothMapClient.setPriority(device, priority);
                    break;
                case BluetoothProfile.PBAP_CLIENT:
                    mBluetoothPbapClient.setPriority(device, priority);
                    break;
                default:
                    Log.w(TAG, "Unknown Profile: " + Utils.getProfileName(profile));
                    break;
            }
        }
    }

    /**
     * Log to debug if debug output is enabled
     */
    private void logd(String msg) {
        if (DBG) {
            Log.d(TAG, msg);
        }
    }
}
