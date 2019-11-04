/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.bluetooth.pan;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.NetworkAgent;
import android.net.NetworkCapabilities;
import android.net.NetworkFactory;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.ip.IIpClient;
import android.net.ip.IpClientUtil;
import android.net.ip.IpClientUtil.WaitForProvisioningCallbacks;
import android.net.shared.ProvisioningConfiguration;
import android.os.Looper;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;

/**
 * This class tracks the data connection associated with Bluetooth
 * reverse tethering. PanService calls it when a reverse tethered
 * connection needs to be activated or deactivated.
 *
 * @hide
 */
public class BluetoothTetheringNetworkFactory extends NetworkFactory {
    private static final String NETWORK_TYPE = "Bluetooth Tethering";
    private static final String TAG = "BluetoothTetheringNetworkFactory";
    private static final int NETWORK_SCORE = 69;

    private final NetworkCapabilities mNetworkCapabilities;
    private final Context mContext;
    private final PanService mPanService;

    // All accesses to these must be synchronized(this).
    private final NetworkInfo mNetworkInfo;
    private IIpClient mIpClient;
    @GuardedBy("this")
    private int mIpClientStartIndex = 0;
    private String mInterfaceName;
    private NetworkAgent mNetworkAgent;

    public BluetoothTetheringNetworkFactory(Context context, Looper looper, PanService panService) {
        super(looper, context, NETWORK_TYPE, new NetworkCapabilities());

        mContext = context;
        mPanService = panService;

        mNetworkInfo = new NetworkInfo(ConnectivityManager.TYPE_BLUETOOTH, 0, NETWORK_TYPE, "");
        mNetworkCapabilities = new NetworkCapabilities();
        initNetworkCapabilities();
        setCapabilityFilter(mNetworkCapabilities);
    }

    private class BtIpClientCallback extends WaitForProvisioningCallbacks {
        private final int mCurrentStartIndex;

        private BtIpClientCallback(int currentStartIndex) {
            mCurrentStartIndex = currentStartIndex;
        }

        @Override
        public void onIpClientCreated(IIpClient ipClient) {
            synchronized (BluetoothTetheringNetworkFactory.this) {
                if (mCurrentStartIndex != mIpClientStartIndex) {
                    // Do not start IpClient: the current request is obsolete.
                    // IpClient will be GCed eventually as the IIpClient Binder token goes out
                    // of scope.
                    return;
                }
                mIpClient = ipClient;
                try {
                    mIpClient.startProvisioning(new ProvisioningConfiguration.Builder()
                            .withoutMultinetworkPolicyTracker()
                            .withoutIpReachabilityMonitor()
                            .build().toStableParcelable());
                } catch (RemoteException e) {
                    Slog.e(TAG, "Error starting IpClient provisioning", e);
                }
            }
        }

        @Override
        public void onLinkPropertiesChange(LinkProperties newLp) {
            synchronized (BluetoothTetheringNetworkFactory.this) {
                if (mNetworkAgent != null && mNetworkInfo.isConnected()) {
                    mNetworkAgent.sendLinkProperties(newLp);
                }
            }
        }
    }

    private void stopIpClientLocked() {
        // Mark all previous start requests as obsolete
        mIpClientStartIndex++;
        if (mIpClient != null) {
            try {
                mIpClient.shutdown();
            } catch (RemoteException e) {
                Slog.e(TAG, "Error shutting down IpClient", e);
            }
            mIpClient = null;
        }
    }

    private BtIpClientCallback startIpClientLocked() {
        mIpClientStartIndex++;
        final BtIpClientCallback callback = new BtIpClientCallback(mIpClientStartIndex);
        IpClientUtil.makeIpClient(mContext, mInterfaceName, callback);
        return callback;
    }

    // Called by NetworkFactory when PanService and NetworkFactory both desire a Bluetooth
    // reverse-tether connection.  A network interface for Bluetooth reverse-tethering can be
    // assumed to be available because we only register our NetworkFactory when it is so.
    @Override
    protected void startNetwork() {
        // TODO: Figure out how to replace this thread with simple invocations
        // of IpClient. This will likely necessitate a rethink about
        // NetworkAgent, NetworkInfo, and associated instance lifetimes.
        Thread ipProvisioningThread = new Thread(new Runnable() {
            @Override
            public void run() {
                LinkProperties linkProperties;
                final WaitForProvisioningCallbacks ipcCallback;

                synchronized (BluetoothTetheringNetworkFactory.this) {
                    if (TextUtils.isEmpty(mInterfaceName)) {
                        Slog.e(TAG, "attempted to reverse tether without interface name");
                        return;
                    }
                    log("ipProvisioningThread(+" + mInterfaceName + "): " + "mNetworkInfo="
                            + mNetworkInfo);
                    ipcCallback = startIpClientLocked();
                    mNetworkInfo.setDetailedState(DetailedState.OBTAINING_IPADDR, null, null);
                }

                linkProperties = ipcCallback.waitForProvisioning();
                if (linkProperties == null) {
                    Slog.e(TAG, "IP provisioning error.");
                    synchronized (BluetoothTetheringNetworkFactory.this) {
                        stopIpClientLocked();
                        setScoreFilter(-1);
                    }
                    return;
                }

                synchronized (BluetoothTetheringNetworkFactory.this) {
                    mNetworkInfo.setIsAvailable(true);
                    mNetworkInfo.setDetailedState(DetailedState.CONNECTED, null, null);

                    // Create our NetworkAgent.
                    mNetworkAgent =
                            new NetworkAgent(getLooper(), mContext, NETWORK_TYPE, mNetworkInfo,
                                    mNetworkCapabilities, linkProperties, NETWORK_SCORE) {
                                @Override
                                public void unwanted() {
                                    BluetoothTetheringNetworkFactory.this.onCancelRequest();
                                }

                                ;
                            };
                }
            }
        });
        ipProvisioningThread.start();
    }

    // Called from NetworkFactory to indicate ConnectivityService no longer desires a Bluetooth
    // reverse-tether network.
    @Override
    protected void stopNetwork() {
        // Let NetworkAgent disconnect do the teardown.
    }

    // Called by the NetworkFactory, NetworkAgent or PanService to tear down network.
    private synchronized void onCancelRequest() {
        stopIpClientLocked();
        mInterfaceName = "";

        mNetworkInfo.setDetailedState(DetailedState.DISCONNECTED, null, null);
        if (mNetworkAgent != null) {
            mNetworkAgent.sendNetworkInfo(mNetworkInfo);
            mNetworkAgent = null;
        }
        for (BluetoothDevice device : mPanService.getConnectedDevices()) {
            mPanService.disconnect(device);
        }
    }

    // Called by PanService when a network interface for Bluetooth reverse-tethering
    // becomes available.  We register our NetworkFactory at this point.
    public void startReverseTether(final String iface) {
        if (iface == null || TextUtils.isEmpty(iface)) {
            Slog.e(TAG, "attempted to reverse tether with empty interface");
            return;
        }
        synchronized (this) {
            if (!TextUtils.isEmpty(mInterfaceName)) {
                Slog.e(TAG, "attempted to reverse tether while already in process");
                return;
            }
            mInterfaceName = iface;
            // Advertise ourselves to ConnectivityService.
            register();
            setScoreFilter(NETWORK_SCORE);
        }
    }

    // Called by PanService when a network interface for Bluetooth reverse-tethering
    // goes away.  We stop advertising ourselves to ConnectivityService at this point.
    public synchronized void stopReverseTether() {
        if (TextUtils.isEmpty(mInterfaceName)) {
            Slog.e(TAG, "attempted to stop reverse tether with nothing tethered");
            return;
        }
        onCancelRequest();
        setScoreFilter(-1);
        unregister();
    }

    private void initNetworkCapabilities() {
        mNetworkCapabilities.addTransportType(NetworkCapabilities.TRANSPORT_BLUETOOTH);
        mNetworkCapabilities.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        mNetworkCapabilities.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
        mNetworkCapabilities.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING);
        // Bluetooth v3 and v4 go up to 24 Mbps.
        // TODO: Adjust this to actual connection bandwidth.
        mNetworkCapabilities.setLinkUpstreamBandwidthKbps(24 * 1000);
        mNetworkCapabilities.setLinkDownstreamBandwidthKbps(24 * 1000);
    }
}
