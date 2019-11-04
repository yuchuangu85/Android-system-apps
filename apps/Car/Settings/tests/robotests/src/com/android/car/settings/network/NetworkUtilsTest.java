/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car.settings.network;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.telephony.TelephonyManager;

import com.android.car.settings.CarSettingsRobolectricTestRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class NetworkUtilsTest {

    @Test
    public void hasMobileNetwork_hasCellularCapabilities_returnsTrue() {
        ConnectivityManager connectivityManager = mock(ConnectivityManager.class);
        Network network = mock(Network.class);
        NetworkCapabilities capabilities = mock(NetworkCapabilities.class);

        when(connectivityManager.getAllNetworks()).thenReturn(new Network[]{network});
        when(connectivityManager.getNetworkCapabilities(network)).thenReturn(capabilities);
        when(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(true);

        assertThat(NetworkUtils.hasMobileNetwork(connectivityManager)).isTrue();
    }

    @Test
    public void hasMobileNetwork_hasNoCellularCapabilities_returnsFalse() {
        ConnectivityManager connectivityManager = mock(ConnectivityManager.class);
        Network network = mock(Network.class);
        NetworkCapabilities capabilities = mock(NetworkCapabilities.class);

        when(connectivityManager.getAllNetworks()).thenReturn(new Network[]{network});
        when(connectivityManager.getNetworkCapabilities(network)).thenReturn(capabilities);
        when(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(false);

        assertThat(NetworkUtils.hasMobileNetwork(connectivityManager)).isFalse();
    }

    @Test
    public void hasSim_simAbsent_returnsFalse() {
        TelephonyManager telephonyManager = mock(TelephonyManager.class);
        when(telephonyManager.getSimState()).thenReturn(TelephonyManager.SIM_STATE_ABSENT);

        assertThat(NetworkUtils.hasSim(telephonyManager)).isFalse();
    }

    @Test
    public void hasSim_simUnknown_returnsFalse() {
        TelephonyManager telephonyManager = mock(TelephonyManager.class);
        when(telephonyManager.getSimState()).thenReturn(TelephonyManager.SIM_STATE_UNKNOWN);

        assertThat(NetworkUtils.hasSim(telephonyManager)).isFalse();
    }

    @Test
    public void hasSim_otherStatus_returnsTrue() {
        TelephonyManager telephonyManager = mock(TelephonyManager.class);
        when(telephonyManager.getSimState()).thenReturn(TelephonyManager.SIM_STATE_LOADED);

        assertThat(NetworkUtils.hasSim(telephonyManager)).isTrue();
    }
}
