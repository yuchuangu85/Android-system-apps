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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.wifi.details.WifiInfoProvider.Listener;
import com.android.settingslib.wifi.AccessPoint;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class WifiInfoProviderImplTest {
    private static final int LEVEL = 1;
    private static final int RSSI = -55;
    private static final int LINK_SPEED = 123;
    private static final String MAC_ADDRESS = WifiInfo.DEFAULT_MAC_ADDRESS;
    private static final String SECURITY = "None";

    @Mock
    private AccessPoint mMockAccessPoint;
    @Mock
    private ConnectivityManager mMockConnectivityManager;
    @Mock
    private Network mMockNetwork;
    @Mock
    private NetworkInfo mMockNetworkInfo;
    @Mock
    private NetworkInfo mMockNetworkInfo2;
    @Mock
    private WifiConfiguration mMockWifiConfig;
    @Mock
    private WifiInfo mMockWifiInfo;
    @Mock
    private WifiInfo mMockWifiInfo2;
    @Mock
    private LinkProperties mMockLinkProperties;
    @Mock
    private LinkProperties mMockChangedLinkProperties;
    @Mock
    private NetworkCapabilities mMockNetworkCapabilities;
    @Mock
    private NetworkCapabilities mMockChangedNetworkCapabilities;
    @Mock
    private WifiManager mMockWifiManager;
    @Mock
    private Listener mMockListener;

    @Captor
    private ArgumentCaptor<NetworkCallback> mCallbackCaptor;

    private Context mContext;
    private WifiInfoProviderImpl mWifiInfoProviderImpl;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mContext = spy(RuntimeEnvironment.application);
        when(mContext.getSystemService(ConnectivityManager.class))
                .thenReturn(mMockConnectivityManager);
        when(mContext.getSystemService(WifiManager.class)).thenReturn(mMockWifiManager);
        when(mMockAccessPoint.getConfig()).thenReturn(mMockWifiConfig);
        when(mMockAccessPoint.getLevel()).thenReturn(LEVEL);
        when(mMockAccessPoint.getSecurityString(false)).thenReturn(SECURITY);
        when(mMockConnectivityManager.getNetworkInfo(any(Network.class)))
                .thenReturn(mMockNetworkInfo);
        when(mMockConnectivityManager.getLinkProperties(any(Network.class)))
                .thenReturn(mMockLinkProperties);
        when(mMockConnectivityManager.getNetworkCapabilities(any(Network.class)))
                .thenReturn(mMockNetworkCapabilities);
        doNothing().when(mMockConnectivityManager).registerNetworkCallback(
                nullable(NetworkRequest.class), mCallbackCaptor.capture(), nullable(Handler.class));
        when(mMockWifiInfo.getLinkSpeed()).thenReturn(LINK_SPEED);
        when(mMockWifiInfo.getRssi()).thenReturn(RSSI);
        when(mMockWifiInfo.getMacAddress()).thenReturn(MAC_ADDRESS);
        when(mMockWifiManager.getConnectionInfo()).thenReturn(mMockWifiInfo);

        when(mMockWifiManager.getCurrentNetwork()).thenReturn(mMockNetwork);

        mWifiInfoProviderImpl = new WifiInfoProviderImpl(mContext, mMockAccessPoint);
        mWifiInfoProviderImpl.addListener(mMockListener);
    }

    @Test
    public void onStart_allFieldsInitialized() {
        mWifiInfoProviderImpl.onStart();

        assertThat(mWifiInfoProviderImpl.getNetworkInfo()).isNotNull();
        assertThat(mWifiInfoProviderImpl.getWifiInfo()).isNotNull();
        assertThat(mWifiInfoProviderImpl.getNetwork()).isNotNull();
        assertThat(mWifiInfoProviderImpl.getNetworkCapabilities()).isNotNull();
        assertThat(mWifiInfoProviderImpl.getNetworkConfiguration()).isNotNull();
        assertThat(mWifiInfoProviderImpl.getLinkProperties()).isNotNull();
    }

    @Test
    public void onStart_listenerCallback() {
        mWifiInfoProviderImpl.onStart();
        verify(mMockListener).onWifiChanged(eq(mMockNetworkInfo), eq(mMockWifiInfo));
    }

    @Test
    public void onStart_getsNetwork() {
        mWifiInfoProviderImpl.onStart();
        assertThat(mWifiInfoProviderImpl.getNetwork()).isEqualTo(mMockNetwork);
    }

    @Test
    public void networkCallback_shouldBeRegisteredOnStart() {
        mWifiInfoProviderImpl.onStart();

        verify(mMockConnectivityManager).registerNetworkCallback(
                nullable(NetworkRequest.class), mCallbackCaptor.capture(), nullable(Handler.class));
    }

    @Test
    public void networkCallback_shouldBeUnregisteredOnStop() {
        mWifiInfoProviderImpl.onStart();
        mWifiInfoProviderImpl.onStop();

        verify(mMockConnectivityManager)
                .unregisterNetworkCallback(mCallbackCaptor.getValue());
    }

    @Test
    public void networkStateChangedIntent_shouldRefetchInfo() {
        mWifiInfoProviderImpl.onStart();

        assertThat(mWifiInfoProviderImpl.getNetwork()).isEqualTo(mMockNetwork);
        assertThat(mWifiInfoProviderImpl.getWifiInfo()).isEqualTo(mMockWifiInfo);
        assertThat(mWifiInfoProviderImpl.getNetworkInfo()).isEqualTo(mMockNetworkInfo);

        when(mMockWifiManager.getConnectionInfo()).thenReturn(mMockWifiInfo2);
        when(mMockConnectivityManager.getNetworkInfo(any(Network.class)))
                .thenReturn(mMockNetworkInfo2);

        mContext.sendBroadcast(new Intent(WifiManager.NETWORK_STATE_CHANGED_ACTION));

        assertThat(mWifiInfoProviderImpl.getNetwork()).isEqualTo(mMockNetwork);
        assertThat(mWifiInfoProviderImpl.getWifiInfo()).isEqualTo(mMockWifiInfo2);
        assertThat(mWifiInfoProviderImpl.getNetworkInfo()).isEqualTo(mMockNetworkInfo2);
    }

    @Test
    public void rssiChangedIntent_shouldRefetchInfo() {
        mWifiInfoProviderImpl.onStart();

        assertThat(mWifiInfoProviderImpl.getNetwork()).isEqualTo(mMockNetwork);
        assertThat(mWifiInfoProviderImpl.getWifiInfo()).isEqualTo(mMockWifiInfo);
        assertThat(mWifiInfoProviderImpl.getNetworkInfo()).isEqualTo(mMockNetworkInfo);

        when(mMockWifiManager.getConnectionInfo()).thenReturn(mMockWifiInfo2);
        when(mMockConnectivityManager.getNetworkInfo(any(Network.class)))
                .thenReturn(mMockNetworkInfo2);

        mContext.sendBroadcast(new Intent(WifiManager.RSSI_CHANGED_ACTION));

        assertThat(mWifiInfoProviderImpl.getNetwork()).isEqualTo(mMockNetwork);
        assertThat(mWifiInfoProviderImpl.getWifiInfo()).isEqualTo(mMockWifiInfo2);
        assertThat(mWifiInfoProviderImpl.getNetworkInfo()).isEqualTo(mMockNetworkInfo2);
    }

    @Test
    public void onLost_lisntenerCallback() {
        mWifiInfoProviderImpl.onStart();

        mCallbackCaptor.getValue().onLost(mMockNetwork);

        verify(mMockListener).onLost(any(Network.class));
    }

    @Test
    public void onLinkPropertiesChanged_lisntenerCallback() {
        mWifiInfoProviderImpl.onStart();

        mCallbackCaptor.getValue().onLinkPropertiesChanged(
                mMockNetwork, mMockChangedLinkProperties);

        verify(mMockListener).onLinkPropertiesChanged(
                any(Network.class), eq(mMockChangedLinkProperties));
    }

    @Test
    public void onCapabilitiesChanged_lisntenerCallback() {
        mWifiInfoProviderImpl.onStart();

        mCallbackCaptor.getValue().onCapabilitiesChanged(
                mMockNetwork, mMockChangedNetworkCapabilities);

        verify(mMockListener).onCapabilitiesChanged(
                any(Network.class), eq(mMockChangedNetworkCapabilities));
    }
}
