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
package com.android.car.settings.wifi.details;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.net.LinkAddress;
import android.net.NetworkUtils;

import com.android.car.settings.common.FragmentController;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Shows info about Wifi subnet.
 */
public class WifiSubnetPreferenceController extends
        WifiDetailsBasePreferenceController<WifiDetailsPreference> {

    public WifiSubnetPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<WifiDetailsPreference> getPreferenceType() {
        return WifiDetailsPreference.class;
    }

    @Override
    protected void updateState(WifiDetailsPreference preference) {
        String subnet = null;

        for (LinkAddress addr : getWifiInfoProvider().getLinkProperties().getLinkAddresses()) {
            if (addr.getAddress() instanceof Inet4Address) {
                subnet = ipv4PrefixLengthToSubnetMask(addr.getPrefixLength());
            }
        }

        if (subnet == null) {
            preference.setVisible(false);
        } else {
            preference.setDetailText(subnet);
            preference.setVisible(true);
        }
    }

    private static String ipv4PrefixLengthToSubnetMask(int prefixLength) {
        try {
            InetAddress all = InetAddress.getByAddress(
                    new byte[]{(byte) 255, (byte) 255, (byte) 255, (byte) 255});
            return NetworkUtils.getNetworkPart(all, prefixLength).getHostAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
