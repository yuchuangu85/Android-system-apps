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

package com.android.car.settings.testutils;

import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;

import org.robolectric.Shadows;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;

import java.util.LinkedHashMap;
import java.util.Map;

@Implements(WifiManager.class)
public class ShadowWifiManager extends org.robolectric.shadows.ShadowWifiManager {

    private static int sResetCalledCount = 0;

    private final Map<Integer, WifiConfiguration> mNetworkIdToConfiguredNetworks =
            new LinkedHashMap<>();
    private int mLastForgottenNetwork = Integer.MIN_VALUE;

    @Implementation
    @Override
    protected int addNetwork(WifiConfiguration config) {
        int networkId = mNetworkIdToConfiguredNetworks.size();
        config.networkId = -1;
        mNetworkIdToConfiguredNetworks.put(networkId, makeCopy(config, networkId));
        return networkId;
    }

    public WifiConfiguration getLastAddedNetworkConfiguration() {
        return mNetworkIdToConfiguredNetworks.get(getLastAddedNetworkId());
    }

    public int getLastAddedNetworkId() {
        return mNetworkIdToConfiguredNetworks.size() - 1;
    }

    public static boolean verifyFactoryResetCalled(int numTimes) {
        return sResetCalledCount == numTimes;
    }

    @Implementation
    protected void forget(int netId, WifiManager.ActionListener listener) {
        mLastForgottenNetwork = netId;
    }

    public int getLastForgottenNetwork() {
        return mLastForgottenNetwork;
    }

    @Implementation
    protected void factoryReset() {
        sResetCalledCount++;
    }

    @Resetter
    public static void reset() {
        sResetCalledCount = 0;
    }

    private WifiConfiguration makeCopy(WifiConfiguration config, int networkId) {
        WifiConfiguration copy = Shadows.shadowOf(config).copy();
        copy.networkId = networkId;
        return copy;
    }
}
