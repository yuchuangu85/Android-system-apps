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

package com.google.android.car.kitchensink.connectivity;

import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.car.kitchensink.R;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Timer;
import java.util.TimerTask;

@SuppressLint("SetTextI18n")
public class ConnectivityFragment extends Fragment {
    private static final String TAG = ConnectivityFragment.class.getSimpleName();
    private final Handler mHandler = new Handler();

    private ConnectivityManager mConnectivityManager;
    private WifiManager mWifiManager;
    private LocationManager mLocationManager;

    // Sort out current Network objects (NetId -> Network)
    private SparseArray<Network> mNetworks = new SparseArray<Network>();

    private TextView mWifiStatusPolled;
    private TextView mTetheringStatus;
    private TextView mTetheringStatusPolled;
    private TextView mLocalOnlyStatus;

    private Timer mWifiUpdater;

    /**
     * Create our own network callback object to use with NetworkRequests. Contains a reference to
     * a Network so we can be sure to only surface updates on the network we want to see them on.
     * We have to do this because there isn't a way to say "give me this SPECIFIC network." There's
     * only "give me A network with these capabilities/transports."
     */
    public class NetworkByIdCallback extends NetworkCallback {
        private final Network mNetwork;

        NetworkByIdCallback(Network n) {
            mNetwork = n;
        }

        @Override
        public void onAvailable(Network n) {
            if (mNetwork.equals(n)) {
                showToast("onAvailable(), netId: " + n);
            }
        }

        @Override
        public void onLosing(Network n, int maxMsToLive) {
            if (mNetwork.equals(n)) {
                showToast("onLosing(), netId: " + n);
            }
        }

        @Override
        public void onLost(Network n) {
            if (mNetwork.equals(n)) {
                showToast("onLost(), netId: " + n);
            }
        }
    }

    // Map of NetId -> NetworkByIdCallback Objects -- Used to release requested networks
    SparseArray<NetworkByIdCallback> mNetworkCallbacks = new SparseArray<NetworkByIdCallback>();

    /**
     * Implement a swipe-to-refresh list of available networks. NetworkListAdapter takes an array
     * of NetworkItems that it cascades to the view. SwipeRefreshLayout wraps the adapter.
     */
    public static class NetworkItem {
        public int mNetId;
        public String mType;
        public String mState;
        public String mConnected;
        public String mAvailable;
        public String mRoaming;
        public String mInterfaceName;
        public String mHwAddress;
        public String mIpAddresses;
        public String mDnsAddresses;
        public String mDomains;
        public String mRoutes;
        public String mTransports;
        public String mCapabilities;
        public String mBandwidth;
        public boolean mDefault;
        public boolean mRequested;
    }

    private NetworkItem[] mNetworkItems = new NetworkItem[0];
    private NetworkListAdapter mNetworksAdapter;
    private SwipeRefreshLayout mNetworkListRefresher;

    /**
     * Builds a NetworkRequest fit to a given network in the hope that we just get updates on that
     * one network. This is the best way to get single network updates right now, as the request
     * system works only on transport and capability requirements. There aaaare "network
     * specifiers" but those only work based on the transport (i.e "eth0" would ask type ETHERNET
     * for the correct interface where as "GoogleGuest" might ask type WIFI for the Network on SSID
     * "GoogleGuest"). Ends up being paired with the custom callback above to only surface events
     * for the specific network in question as well.
     */
    private NetworkRequest getRequestForNetwork(Network n) {
        NetworkCapabilities nc = mConnectivityManager.getNetworkCapabilities(n);

        NetworkRequest.Builder b = new NetworkRequest.Builder();
        b.clearCapabilities();

        for (int transportType : nc.getTransportTypes()) {
            b.addTransportType(transportType);
        }

        for (int capability : nc.getCapabilities()) {
            // Not all capabilities are requestable. According to source, all mutable capabilities
            // except trusted are not requestable. Trying to request them results in an error being
            // thrown
            if (isRequestableCapability(capability)) {
                b.addCapability(capability);
            }
        }

        return b.build();
    }

    private boolean isRequestableCapability(int c) {
        if (c == NetworkCapabilities.NET_CAPABILITY_VALIDATED
                || c == NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL
                || c == NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING
                || c == NetworkCapabilities.NET_CAPABILITY_FOREGROUND
                || c == NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED
                || c == NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED) {
            return false;
        }
        return true;
    }

    public void requestNetworkById(int netId) {
        if (mNetworkCallbacks.get(netId) != null) {
            return;
        }

        Network network = mNetworks.get(netId);
        if (network == null) {
            return;
        }

        NetworkRequest request = getRequestForNetwork(network);
        NetworkByIdCallback cb = new NetworkByIdCallback(network);
        mNetworkCallbacks.put(netId, cb);
        mConnectivityManager.requestNetwork(request, cb);
        showToast("Requesting Network " + netId);
    }

    public void releaseNetworkById(int netId) {
        NetworkByIdCallback cb = mNetworkCallbacks.get(netId);
        if (cb != null) {
            mConnectivityManager.unregisterNetworkCallback(cb);
            mNetworkCallbacks.remove(netId);
            showToast("Released Network " + netId);
        }
    }

    public void releaseAllNetworks() {
        for (NetworkItem n : mNetworkItems) {
            releaseNetworkById(n.mNetId);
        }
    }

    public void bindToNetwork(int netId) {
        Network network = mNetworks.get(netId);
        if (network == null) {
            return;
        }

        Network def = mConnectivityManager.getBoundNetworkForProcess();
        if (def != null && def.netId != netId) {
            clearBoundNetwork();
        }
        mConnectivityManager.bindProcessToNetwork(network);
        showToast("Set process default network " + netId);
    }

    public void clearBoundNetwork() {
        mConnectivityManager.bindProcessToNetwork(null);
        showToast("Clear process default network");
    }

    public void reportNetworkbyId(int netId) {
        Network network = mNetworks.get(netId);
        if (network == null) {
            return;
        }
        mConnectivityManager.reportNetworkConnectivity(network, false);
        showToast("Reporting Network " + netId);
    }

    /**
    * Maps of NET_CAPABILITY_* and TRANSPORT_* to string representations. A network having these
    * capabilities will have the following strings print on their list entry.
    */
    private static final SparseArray<String> sTransportNames = new SparseArray<String>();
    private static final SparseArray<String> sCapabilityNames = new SparseArray<String>();
    static {
        sTransportNames.put(NetworkCapabilities.TRANSPORT_LOWPAN, "[LOWPAN]");
        sTransportNames.put(NetworkCapabilities.TRANSPORT_WIFI_AWARE, "[WIFI-AWARE]");
        sTransportNames.put(NetworkCapabilities.TRANSPORT_VPN, "[VPN]");
        sTransportNames.put(NetworkCapabilities.TRANSPORT_ETHERNET, "[ETHERNET]");
        sTransportNames.put(NetworkCapabilities.TRANSPORT_BLUETOOTH, "[BLUETOOTH]");
        sTransportNames.put(NetworkCapabilities.TRANSPORT_WIFI, "[WIFI]");
        sTransportNames.put(NetworkCapabilities.TRANSPORT_CELLULAR, "[CELLULAR]");

        sCapabilityNames.put(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL, "[CAPTIVE PORTAL]");
        sCapabilityNames.put(NetworkCapabilities.NET_CAPABILITY_CBS, "[CBS]");
        sCapabilityNames.put(NetworkCapabilities.NET_CAPABILITY_DUN, "[DUN]");
        sCapabilityNames.put(NetworkCapabilities.NET_CAPABILITY_EIMS, "[EIMS]");
        sCapabilityNames.put(NetworkCapabilities.NET_CAPABILITY_FOREGROUND, "[FOREGROUND]");
        sCapabilityNames.put(NetworkCapabilities.NET_CAPABILITY_FOTA, "[FOTA]");
        sCapabilityNames.put(NetworkCapabilities.NET_CAPABILITY_IA, "[IA]");
        sCapabilityNames.put(NetworkCapabilities.NET_CAPABILITY_IMS, "[IMS]");
        sCapabilityNames.put(NetworkCapabilities.NET_CAPABILITY_INTERNET, "[INTERNET]");
        sCapabilityNames.put(NetworkCapabilities.NET_CAPABILITY_MMS, "[MMS]");
        sCapabilityNames.put(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED, "[NOT CONGESTED]");
        sCapabilityNames.put(NetworkCapabilities.NET_CAPABILITY_NOT_METERED, "[NOT METERED]");
        sCapabilityNames.put(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED, "[NOT RESTRICTED]");
        sCapabilityNames.put(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING, "[NOT ROAMING]");
        sCapabilityNames.put(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED, "[NOT SUSPENDED]");
        sCapabilityNames.put(NetworkCapabilities.NET_CAPABILITY_NOT_VPN, "[NOT VPN]");
        sCapabilityNames.put(NetworkCapabilities.NET_CAPABILITY_RCS, "[RCS]");
        sCapabilityNames.put(NetworkCapabilities.NET_CAPABILITY_SUPL, "[SUPL]");
        sCapabilityNames.put(NetworkCapabilities.NET_CAPABILITY_TRUSTED, "[TRUSTED]");
        sCapabilityNames.put(NetworkCapabilities.NET_CAPABILITY_VALIDATED, "[VALIDATED]");
        sCapabilityNames.put(NetworkCapabilities.NET_CAPABILITY_WIFI_P2P, "[WIFI P2P]");
        sCapabilityNames.put(NetworkCapabilities.NET_CAPABILITY_XCAP, "[XCAP]");
    }

    private static final SparseArray<String> sWifiStaStates = new SparseArray<>();
    static {
        sWifiStaStates.put(WifiManager.WIFI_STATE_DISABLING, "STA_DISABLING");
        sWifiStaStates.put(WifiManager.WIFI_STATE_DISABLED, "STA_DISABLED");
        sWifiStaStates.put(WifiManager.WIFI_STATE_ENABLING, "STA_ENABLING");
        sWifiStaStates.put(WifiManager.WIFI_STATE_ENABLED, "STA_ENABLED");
        sWifiStaStates.put(WifiManager.WIFI_STATE_UNKNOWN, "STA_UNKNOWN");
    }

    private static final SparseArray<String> sWifiApStates = new SparseArray<>();
    static {
        sWifiApStates.put(WifiManager.WIFI_AP_STATE_DISABLING, "AP_DISABLING");
        sWifiApStates.put(WifiManager.WIFI_AP_STATE_DISABLED, "AP_DISABLED");
        sWifiApStates.put(WifiManager.WIFI_AP_STATE_ENABLING, "AP_ENABLING");
        sWifiApStates.put(WifiManager.WIFI_AP_STATE_ENABLED, "AP_ENABLED");
        sWifiApStates.put(WifiManager.WIFI_AP_STATE_FAILED, "AP_FAILED");
    }

    /**
     * Builds a string out of the possible transports that can be applied to a
     * NetworkCapabilities object.
     */
    private String getTransportString(NetworkCapabilities nCaps) {
        String transports = "";
        for (int transport : nCaps.getTransportTypes()) {
            transports += sTransportNames.get(transport, "");
        }
        return transports;
    }

    /**
     * Builds a string out of the possible capabilities that can be applied to
     * a NetworkCapabilities object.
    */
    private String getCapabilitiesString(NetworkCapabilities nCaps) {
        String caps = "";
        for (int capability : nCaps.getCapabilities()) {
            caps += sCapabilityNames.get(capability, "");
        }
        return caps;
    }

    // Gets the string representation of a MAC address from a given NetworkInterface object
    private String getMacAddress(NetworkInterface ni) {
        if (ni == null) {
            return "??:??:??:??:??:??";
        }

        byte[] mac = null;
        try {
            mac = ni.getHardwareAddress();
        } catch (SocketException exception) {
            Log.e(TAG, "SocketException -- Failed to get interface MAC address");
            return "??:??:??:??:??:??";
        }

        if (mac == null) {
            return "??:??:??:??:??:??";
        }

        StringBuilder sb = new StringBuilder(18);
        for (byte b : mac) {
            if (sb.length() > 0) {
                sb.append(':');
            }
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Builds a NetworkItem object from a given Network object, aggregating info across Network,
     * NetworkCapabilities, NetworkInfo, NetworkInterface, and LinkProperties objects and pass it
     * all as a string for the UI to use
     */
    private NetworkItem getNetworkItem(Network n) {

        // Get default network to assign the button text correctly
        // NOTE: activeNetwork != ProcessDefault when you set one, active is tracking the default
        //       request regardless of your process's default
        // Network defNetwork = mConnectivityManager.getActiveNetwork();
        Network defNetwork = mConnectivityManager.getBoundNetworkForProcess();

        // Used to get network state
        NetworkInfo nInfo = mConnectivityManager.getNetworkInfo(n);

        // Used to get transport type(s), capabilities
        NetworkCapabilities nCaps = mConnectivityManager.getNetworkCapabilities(n);

        // Properties of the actual physical link
        LinkProperties nLink = mConnectivityManager.getLinkProperties(n);

        // Object representing the actual interface
        NetworkInterface nIface = null;
        try {
            nIface = NetworkInterface.getByName(nLink.getInterfaceName());
        } catch (SocketException exception) {
            Log.e(TAG, "SocketException -- Failed to get interface info");
        }

        // Pack NetworkItem with all values
        NetworkItem ni = new NetworkItem();

        // Row key
        ni.mNetId = n.netId;

        // LinkProperties/NetworkInterface
        ni.mInterfaceName = "Interface: " + nLink.getInterfaceName()
                            + (nIface != null ? " (" + nIface.getName() + ")" : " ()");
        ni.mHwAddress = "HwAddress: " + getMacAddress(nIface);
        ni.mIpAddresses = "IP Addresses: " + nLink.getLinkAddresses().toString();
        ni.mDnsAddresses = "DNS: " + nLink.getDnsServers().toString();
        ni.mDomains = "Domains: " + nLink.getDomains();
        ni.mRoutes = "Routes: " + nLink.getRoutes().toString();

        // NetworkInfo
        ni.mType = "Type: " + nInfo.getTypeName() + " (" + nInfo.getSubtypeName() + ")";
        ni.mState = "State: " + nInfo.getState().name() + "/" + nInfo.getDetailedState().name();
        ni.mConnected = "Connected: " + (nInfo.isConnected() ? "Connected" : "Disconnected");
        ni.mAvailable = "Available: " + (nInfo.isAvailable() ? "Yes" : "No");
        ni.mRoaming = "Roaming: " + (nInfo.isRoaming() ? "Yes" : "No");

        // NetworkCapabilities
        ni.mTransports = "Transports: " + getTransportString(nCaps);
        ni.mCapabilities = "Capabilities: " + getCapabilitiesString(nCaps);
        ni.mBandwidth = "Bandwidth (Down/Up): " + nCaps.getLinkDownstreamBandwidthKbps()
                        + " Kbps/" + nCaps.getLinkUpstreamBandwidthKbps() + " Kbps";

        // Other inferred values
        ni.mDefault = sameNetworkId(n, defNetwork);
        ni.mRequested = (mNetworkCallbacks.get(n.netId) != null);

        return ni;
    }

    // Refresh the networks content and prompt the user that we did it
    private void refreshNetworksAndPrompt() {
        refreshNetworks();
        showToast("Refreshed Networks (" + mNetworkItems.length + ")");
    }

    /**
     * Gets the current set of networks from the connectivity manager and 1) stores the network
     * objects 2) builds NetworkItem objects for the view to render and 3) If a network we were
     * tracking disappears then it kills its callback.
     */
    private void refreshNetworks() {
        Log.i(TAG, "refreshNetworks()");
        Network[] networks = mConnectivityManager.getAllNetworks();
        mNetworkItems = new NetworkItem[networks.length];
        mNetworks.clear();

        // Add each network to the network info set, turning each field to a string
        for (int i = 0; i < networks.length; i++) {
            mNetworkItems[i] = getNetworkItem(networks[i]);
            mNetworks.put(networks[i].netId, networks[i]);
        }

        // Check for callbacks that belong to networks that don't exist anymore
        for (int i = 0; i < mNetworkCallbacks.size(); i++) {
            int key = mNetworkCallbacks.keyAt(i);
            if (mNetworks.get(key) == null) {
                mNetworkCallbacks.remove(key);
            }
        }

        // Update the view
        mNetworksAdapter.refreshNetworks(mNetworkItems);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context ctx = getContext();
        mConnectivityManager = ctx.getSystemService(ConnectivityManager.class);
        mWifiManager = ctx.getSystemService(WifiManager.class);
        mLocationManager = ctx.getSystemService(LocationManager.class);

        mConnectivityManager.addDefaultNetworkActiveListener(() -> refreshNetworks());
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.connectivity_fragment, container, false);

        // Create the ListView of all networks
        ListView networksView = view.findViewById(R.id.networks);
        mNetworksAdapter = new NetworkListAdapter(getContext(), mNetworkItems, this);
        networksView.setAdapter(mNetworksAdapter);

        // Find all networks ListView refresher and set the refresh callback
        mNetworkListRefresher = (SwipeRefreshLayout) view.findViewById(R.id.refreshNetworksList);
        mNetworkListRefresher.setOnRefreshListener(() -> {
            refreshNetworksAndPrompt();
            mNetworkListRefresher.setRefreshing(false);
        });

        view.findViewById(R.id.startWifi).setOnClickListener(v -> setWifiEnabled(true));
        view.findViewById(R.id.stopWifi).setOnClickListener(v -> setWifiEnabled(false));
        view.findViewById(R.id.startTethering).setOnClickListener(v -> startTethering());
        view.findViewById(R.id.stopTethering).setOnClickListener(v -> stopTethering());
        view.findViewById(R.id.startLocalOnly).setOnClickListener(v -> startLocalOnly());
        view.findViewById(R.id.stopLocalOnly).setOnClickListener(v -> stopLocalOnly());
        mWifiStatusPolled = (TextView) view.findViewById(R.id.wifiStatusPolled);
        mTetheringStatus = (TextView) view.findViewById(R.id.tetheringStatus);
        mTetheringStatusPolled = (TextView) view.findViewById(R.id.tetheringStatusPolled);
        mLocalOnlyStatus = (TextView) view.findViewById(R.id.localOnlyStatus);

        view.findViewById(R.id.networkEnableWifiIntent).setOnClickListener(v -> enableWifiIntent());
        view.findViewById(R.id.networkDisableWifiIntent)
                .setOnClickListener(v -> disableWifiIntent());
        view.findViewById(R.id.networkEnableBluetoothIntent)
                .setOnClickListener(v -> enableBluetoothIntent());
        view.findViewById(R.id.networkDisableBluetoothIntent)
                .setOnClickListener(v -> disableBluetoothIntent());
        view.findViewById(R.id.networkDiscoverableBluetoothIntent)
                .setOnClickListener(v -> discoverableBluetoothIntent());

        return view;
    }

    private void enableWifiIntent() {
        Intent enableWifi = new Intent(WifiManager.ACTION_REQUEST_ENABLE);
        enableWifi.putExtra(Intent.EXTRA_PACKAGE_NAME, getContext().getPackageName());
        startActivity(enableWifi);
    }

    private void disableWifiIntent() {
        Intent disableWifi = new Intent(WifiManager.ACTION_REQUEST_DISABLE);
        disableWifi.putExtra(Intent.EXTRA_PACKAGE_NAME, getContext().getPackageName());
        startActivity(disableWifi);
    }

    private void enableBluetoothIntent() {
        Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        enableBluetooth.putExtra(Intent.EXTRA_PACKAGE_NAME, getContext().getPackageName());
        startActivity(enableBluetooth);
    }

    private void disableBluetoothIntent() {
        Intent disableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_DISABLE);
        disableBluetooth.putExtra(Intent.EXTRA_PACKAGE_NAME, getContext().getPackageName());
        startActivity(disableBluetooth);
    }

    private void discoverableBluetoothIntent() {
        Intent discoverableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableBluetooth.putExtra(Intent.EXTRA_PACKAGE_NAME, getContext().getPackageName());
        startActivity(discoverableBluetooth);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshNetworks();
        mWifiUpdater = new Timer();
        mWifiUpdater.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                updateApState();
            }
        }, 0, 500);
    }

    @Override
    public void onPause() {
        super.onPause();
        releaseAllNetworks();
        mWifiUpdater.cancel();
        mWifiUpdater = null;
    }

    private void updateApState() {
        int apState = mWifiManager.getWifiApState();
        String apStateTmp = sWifiApStates.get(apState, "?");
        final String staStateStr = sWifiStaStates.get(mWifiManager.getWifiState(), "?");

        WifiConfiguration config = mWifiManager.getWifiApConfiguration();
        if (config != null && config.SSID != null && apState == WifiManager.WIFI_AP_STATE_ENABLED) {
            apStateTmp += " (" + config.SSID + "/" + config.preSharedKey + ")";
        }

        final String apStateStr = apStateTmp;
        mTetheringStatusPolled.post(() -> {
            mTetheringStatusPolled.setText(apStateStr);
            mWifiStatusPolled.setText(staStateStr);
        });
    }

    private void setTetheringStatus(String status) {
        mTetheringStatus.post(() -> mTetheringStatus.setText(status));
    }

    private void setLocalOnlyStatus(String status) {
        mLocalOnlyStatus.post(() -> mLocalOnlyStatus.setText(status));
    }

    public void showToast(String text) {
        Toast toast = Toast.makeText(getContext(), text, Toast.LENGTH_SHORT);
        TextView v = (TextView) toast.getView().findViewById(android.R.id.message);
        v.setTextColor(Color.WHITE);
        toast.show();
    }

    private static boolean sameNetworkId(Network net1, Network net2) {
        return net1 != null && net2 != null && net1.netId == net2.netId;
    }

    private void setWifiEnabled(boolean enabled) {
        mWifiManager.setWifiEnabled(enabled);
    }

    private void startTethering() {
        setTetheringStatus("starting...");

        ConnectivityManager.OnStartTetheringCallback cb =
                new ConnectivityManager.OnStartTetheringCallback() {
            public void onTetheringStarted() {
                setTetheringStatus("started");
            }

            public void onTetheringFailed() {
                setTetheringStatus("failed");
            }
        };

        mConnectivityManager.startTethering(ConnectivityManager.TETHERING_WIFI, false, cb);
    }

    private void stopTethering() {
        setTetheringStatus("stopping...");
        mConnectivityManager.stopTethering(ConnectivityManager.TETHERING_WIFI);
        setTetheringStatus("stopped");
    }

    private WifiManager.LocalOnlyHotspotReservation mLocalOnlyReservation;

    private void startLocalOnly() {
        setLocalOnlyStatus("starting...");

        UserHandle user = Process.myUserHandle();
        if (!mLocationManager.isLocationEnabledForUser(user)) {
            setLocalOnlyStatus("enabling location...");
            mLocationManager.setLocationEnabledForUser(true, user);
            setLocalOnlyStatus("location enabled; starting...");
        }

        WifiManager.LocalOnlyHotspotCallback cb = new WifiManager.LocalOnlyHotspotCallback() {
            public void onStarted(WifiManager.LocalOnlyHotspotReservation reservation) {
                mLocalOnlyReservation = reservation;
                WifiConfiguration config = reservation.getWifiConfiguration();
                setLocalOnlyStatus("started ("
                        + config.SSID + "/" + config.preSharedKey + ")");
            };

            public void onStopped() {
                setLocalOnlyStatus("stopped");
            };

            public void onFailed(int reason) {
                setLocalOnlyStatus("failed " + reason);
            };
        };

        try {
            mWifiManager.startLocalOnlyHotspot(cb, null);
        } catch (IllegalStateException ex) {
            setLocalOnlyStatus(ex.getMessage());
        }
    }

    private void stopLocalOnly() {
        setLocalOnlyStatus("stopping...");

        WifiManager.LocalOnlyHotspotReservation reservation = mLocalOnlyReservation;
        mLocalOnlyReservation = null;

        if (reservation == null) {
            setLocalOnlyStatus("no reservation");
            return;
        }

        reservation.close();
        setLocalOnlyStatus("stopped");
    }
}
