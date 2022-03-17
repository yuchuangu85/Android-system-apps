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
 * limitations under the License
 */

package com.android.settings.wifi.slice;

import static com.android.settings.slices.CustomSliceRegistry.CONTEXTUAL_WIFI_SLICE_URI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.net.wifi.WifiManager;
import android.os.UserHandle;

import com.android.settings.testutils.shadow.ShadowWifiManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = {
        ShadowWifiManager.class,
        WifiScanWorkerTest.ShadowWifiTracker.class,
})
public class ContextualWifiScanWorkerTest {

    private Context mContext;
    private WifiManager mWifiManager;
    private ConnectivityManager mConnectivityManager;
    private ContextualWifiScanWorker mWifiScanWorker;
    private ConnectToWifiHandler mConnectToWifiHandler;

    @Before
    public void setUp() {
        mContext = spy(RuntimeEnvironment.application);
        mWifiManager = mContext.getSystemService(WifiManager.class);
        mWifiManager.setWifiEnabled(true);

        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
        mWifiScanWorker = new ContextualWifiScanWorker(mContext, CONTEXTUAL_WIFI_SLICE_URI);
        mConnectToWifiHandler = new ConnectToWifiHandler();
    }

    @After
    public void tearDown() {
        mWifiScanWorker.clearClickedWifi();
    }

    @Test
    public void NetworkCallback_onCapabilitiesChanged_sliceIsUnpinned_shouldSendBroadcast() {
        final Intent intent = WifiScanWorkerTest.getIntentWithAccessPoint("ap1");
        WifiScanWorkerTest.setConnectionInfoSSID("ap1");
        final Network network = mConnectivityManager.getActiveNetwork();
        mWifiScanWorker.registerNetworkCallback(network);
        final NetworkCallback callback = mWifiScanWorker.mNetworkCallback;

        mWifiScanWorker.onSlicePinned();
        mConnectToWifiHandler.onReceive(mContext, intent);
        mWifiScanWorker.onSliceUnpinned();
        callback.onCapabilitiesChanged(network,
                WifiSliceTest.makeCaptivePortalNetworkCapabilities());

        verify(mContext).sendBroadcastAsUser(any(Intent.class), eq(UserHandle.CURRENT));
    }

    @Test
    public void NetworkCallback_onCapabilitiesChanged_newSession_shouldNotSendBroadcast() {
        final Intent intent = WifiScanWorkerTest.getIntentWithAccessPoint("ap1");
        WifiScanWorkerTest.setConnectionInfoSSID("ap1");
        final Network network = mConnectivityManager.getActiveNetwork();
        mWifiScanWorker.registerNetworkCallback(network);

        mConnectToWifiHandler.onReceive(mContext, intent);
        ContextualWifiScanWorker.newVisibleUiSession();
        mWifiScanWorker.mNetworkCallback.onCapabilitiesChanged(network,
                WifiSliceTest.makeCaptivePortalNetworkCapabilities());

        verify(mContext, never()).sendBroadcastAsUser(any(Intent.class), eq(UserHandle.CURRENT));
    }
}
