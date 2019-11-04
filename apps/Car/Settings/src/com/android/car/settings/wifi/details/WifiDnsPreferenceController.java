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

import com.android.car.settings.common.FragmentController;

import java.net.InetAddress;
import java.util.stream.Collectors;

/**
 * Shows info about Wifi DNS info.
 */
public class WifiDnsPreferenceController extends
        WifiDetailsBasePreferenceController<WifiDetailsPreference> {

    public WifiDnsPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<WifiDetailsPreference> getPreferenceType() {
        return WifiDetailsPreference.class;
    }

    @Override
    protected void updateState(WifiDetailsPreference preference) {
        String dnsServers = getWifiInfoProvider().getLinkProperties().getDnsServers().stream()
                .map(InetAddress::getHostAddress)
                .collect(Collectors.joining(System.lineSeparator()));

        if (dnsServers == null) {
            preference.setVisible(false);
        } else {
            preference.setDetailText(dnsServers);
            preference.setVisible(true);
        }
    }
}
