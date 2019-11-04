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

import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;

import androidx.lifecycle.LifecycleObserver;

/**
 * Provides Wifi related info.
 */
public interface WifiInfoProvider extends LifecycleObserver {
    /**
     * Observers of Wifi info changes.
     */
    public interface Listener {
        /**
         * Called when NetworkInfo and/or WifiInfo is changed.
         */
        void onWifiChanged(NetworkInfo networkInfo, WifiInfo wifiInfo);

        /**
         * Called when network is lost.
         */
        void onLost(Network network);

        /**
         * Called when NetworkCapabilities changed.
         */
        void onCapabilitiesChanged(Network network, NetworkCapabilities nc);

        /**
         * Called when WifiConfiguration changed.
         */
        void onWifiConfigurationChanged(WifiConfiguration wifiConfiguration,
                NetworkInfo networkInfo, WifiInfo wifiInfo);

        /**
         * Called when LinkProperties changed.
         */
        void onLinkPropertiesChanged(Network network, LinkProperties lp);
    }

    /**
     * Adds the listener.
     */
    void addListener(Listener listener);

    /**
     * Removes the listener.
     */
    void removeListener(Listener listener);

    /**
     * Removes all listeners.
     */
    void clearListeners();

    /**
     * Getter for NetworkInfo
     */
    NetworkInfo getNetworkInfo();

    /**
     * Getter for WifiInfo
     */
    WifiInfo getWifiInfo();

    /**
     * Getter for Network
     */
    Network getNetwork();

    /**
     * Getter for NetworkCapabilities.
     */
    NetworkCapabilities getNetworkCapabilities();

    /**
     * Getter for NetworkConfiguration.
     */
    WifiConfiguration getNetworkConfiguration();

    /**
     * Getter for LinkProperties.
     */
    LinkProperties getLinkProperties();
}
