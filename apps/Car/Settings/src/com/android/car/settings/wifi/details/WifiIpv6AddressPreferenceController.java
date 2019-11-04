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

import androidx.core.text.BidiFormatter;
import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;

import java.net.Inet6Address;
import java.util.StringJoiner;

/**
 * Shows info about Wifi IPv6 address.
 */
public class WifiIpv6AddressPreferenceController extends
        WifiDetailsBasePreferenceController<Preference> {

    public WifiIpv6AddressPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    protected void updateState(Preference preference) {
        StringJoiner ipv6Addresses = new StringJoiner(System.lineSeparator());

        for (LinkAddress addr : getWifiInfoProvider().getLinkProperties().getLinkAddresses()) {
            if (addr.getAddress() instanceof Inet6Address) {
                ipv6Addresses.add(addr.getAddress().getHostAddress());
            }
        }

        if (ipv6Addresses.length() > 0) {
            preference.setSummary(
                    BidiFormatter.getInstance().unicodeWrap(ipv6Addresses.toString()));
            preference.setVisible(true);
        } else {
            preference.setVisible(false);
        }
    }
}

