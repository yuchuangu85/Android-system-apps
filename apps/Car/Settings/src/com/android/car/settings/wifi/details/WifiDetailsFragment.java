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

package com.android.car.settings.wifi.details;

import android.content.Context;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.LayoutRes;
import androidx.annotation.StringRes;
import androidx.annotation.XmlRes;

import com.android.car.settings.R;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.SettingsFragment;
import com.android.car.settings.wifi.WifiUtil;
import com.android.settingslib.wifi.AccessPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows details about a wifi network, including actions related to the network,
 * e.g. ignore, disconnect, etc. The intent should include information about
 * access point, use that to render UI, e.g. show SSID etc.
 */
public class WifiDetailsFragment extends SettingsFragment
        implements WifiInfoProvider.Listener {
    private static final String EXTRA_AP_STATE = "extra_ap_state";
    private static final Logger LOG = new Logger(WifiDetailsFragment.class);

    private WifiManager mWifiManager;
    private AccessPoint mAccessPoint;
    private Button mForgetButton;
    private Button mConnectButton;
    private List<WifiDetailsBasePreferenceController> mControllers = new ArrayList<>();

    private WifiInfoProvider mWifiInfoProvider;

    private class ActionFailListener implements WifiManager.ActionListener {
        @StringRes
        private final int mMessageResId;

        ActionFailListener(@StringRes int messageResId) {
            mMessageResId = messageResId;
        }

        @Override
        public void onSuccess() {
        }

        @Override
        public void onFailure(int reason) {
            Toast.makeText(getContext(), mMessageResId, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Gets an instance of this class.
     */
    public static WifiDetailsFragment getInstance(AccessPoint accessPoint) {
        WifiDetailsFragment wifiDetailsFragment = new WifiDetailsFragment();
        Bundle bundle = new Bundle();
        Bundle accessPointState = new Bundle();
        accessPoint.saveWifiState(accessPointState);
        bundle.putBundle(EXTRA_AP_STATE, accessPointState);
        wifiDetailsFragment.setArguments(bundle);
        return wifiDetailsFragment;
    }

    @Override
    @LayoutRes
    protected int getActionBarLayoutId() {
        return R.layout.action_bar_with_button;
    }

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.wifi_detail_fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mAccessPoint = new AccessPoint(getContext(), getArguments().getBundle(EXTRA_AP_STATE));
        mWifiManager = context.getSystemService(WifiManager.class);
        LOG.d("Creating WifiInfoProvider for " + mAccessPoint);
        if (mWifiInfoProvider == null) {
            mWifiInfoProvider = new WifiInfoProviderImpl(getContext(), mAccessPoint);
        }
        getLifecycle().addObserver(mWifiInfoProvider);

        LOG.d("Creating WifiInfoProvider.Listeners.");
        mControllers.add(use(
                WifiSignalStrengthPreferenceController.class, R.string.pk_wifi_signal_strength)
                .init(mAccessPoint, mWifiInfoProvider));
        mControllers.add(use(WifiFrequencyPreferenceController.class, R.string.pk_wifi_frequency)
                .init(mAccessPoint, mWifiInfoProvider));
        mControllers.add(use(WifiSecurityPreferenceController.class, R.string.pk_wifi_security)
                .init(mAccessPoint, mWifiInfoProvider));
        mControllers.add(use(WifiMacAddressPreferenceController.class, R.string.pk_wifi_mac_address)
                .init(mAccessPoint, mWifiInfoProvider));
        mControllers.add(use(WifiIpAddressPreferenceController.class, R.string.pk_wifi_ip).init(
                mAccessPoint, mWifiInfoProvider));
        mControllers.add(use(WifiGatewayPreferenceController.class, R.string.pk_wifi_gateway).init(
                mAccessPoint, mWifiInfoProvider));
        mControllers.add(use(WifiSubnetPreferenceController.class, R.string.pk_wifi_subnet_mask)
                .init(mAccessPoint, mWifiInfoProvider));
        mControllers.add(use(WifiDnsPreferenceController.class, R.string.pk_wifi_dns).init(
                mAccessPoint, mWifiInfoProvider));
        mControllers.add(use(WifiLinkSpeedPreferenceController.class, R.string.pk_wifi_link_speed)
                .init(mAccessPoint, mWifiInfoProvider));
        mControllers.add(use(WifiIpv6AddressPreferenceController.class, R.string.pk_wifi_ipv6).init(
                mAccessPoint, mWifiInfoProvider));
        LOG.d("Done init.");
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        ((TextView) getActivity().findViewById(R.id.title)).setText(mAccessPoint.getSsid());

        mConnectButton = getActivity().findViewById(R.id.action_button2);
        mConnectButton.setVisibility(View.VISIBLE);
        mConnectButton.setText(R.string.wifi_setup_connect);
        mConnectButton.setOnClickListener(v -> {
            mWifiManager.connect(mAccessPoint.getConfig(),
                    new ActionFailListener(R.string.wifi_failed_connect_message));
            goBack();
        });
        mForgetButton = getActivity().findViewById(R.id.action_button1);
        mForgetButton.setText(R.string.forget);
        mForgetButton.setOnClickListener(v -> {
            WifiUtil.forget(getContext(), mAccessPoint);
            goBack();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        mWifiInfoProvider.addListener(this);
        updateUi();
    }

    @Override
    public void onStop() {
        super.onStop();
        mWifiInfoProvider.removeListener(this);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        getLifecycle().removeObserver(mWifiInfoProvider);
    }

    @Override
    public void onWifiChanged(NetworkInfo networkInfo, WifiInfo wifiInfo) {
        updateUi();
    }

    @Override
    public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
        updateUi();
    }

    @Override
    public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
        updateUi();
    }

    @Override
    public void onLost(Network network) {
        updateUi();
    }

    @Override
    public void onWifiConfigurationChanged(WifiConfiguration wifiConfiguration,
            NetworkInfo networkInfo, WifiInfo wifiInfo) {
        updateUi();
    }

    private void updateUi() {
        LOG.d("updating.");
        // No need to fetch LinkProperties and NetworkCapabilities, they are updated by the
        // callbacks. mNetwork doesn't change except in onResume.
        if (mWifiInfoProvider.getNetwork() == null
                || mWifiInfoProvider.getNetworkInfo() == null
                || mWifiInfoProvider.getWifiInfo() == null) {
            LOG.d("WIFI not available.");
            return;
        }

        mConnectButton.setVisibility(needConnect() ? View.VISIBLE : View.INVISIBLE);
        mForgetButton.setVisibility(canForgetNetwork() ? View.VISIBLE : View.INVISIBLE);
        LOG.d("updated.");
    }

    private boolean needConnect() {
        return mAccessPoint.isSaved() && !mAccessPoint.isActive();
    }

    /**
     * Returns whether the network represented by this fragment can be forgotten.
     */
    private boolean canForgetNetwork() {
        return (mWifiInfoProvider.getWifiInfo() != null
                && mWifiInfoProvider.getWifiInfo().isEphemeral()) || canModifyNetwork();
    }

    /**
     * Returns whether the network represented by this preference can be modified.
     */
    private boolean canModifyNetwork() {
        WifiConfiguration wifiConfig = mWifiInfoProvider.getNetworkConfiguration();
        LOG.d("wifiConfig is: " + wifiConfig);
        return wifiConfig != null && !WifiUtil.isNetworkLockedDown(getContext(), wifiConfig);
    }
}
