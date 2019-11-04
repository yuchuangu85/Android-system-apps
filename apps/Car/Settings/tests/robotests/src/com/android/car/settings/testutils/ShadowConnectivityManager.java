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

import static org.mockito.Mockito.mock;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Handler;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;

import java.util.HashMap;
import java.util.Map;

@Implements(ConnectivityManager.class)
public class ShadowConnectivityManager extends org.robolectric.shadows.ShadowConnectivityManager {

    private static int sResetCalledCount = 0;

    private final Map<Network, NetworkCapabilities> mCapabilitiesMap = new HashMap<>();

    private int mStartTetheringCalledCount = 0;
    private int mStopTetheringCalledCount = 0;
    private int mTetheringType;

    public static boolean verifyFactoryResetCalled(int numTimes) {
        return sResetCalledCount == numTimes;
    }

    public boolean verifyStartTetheringCalled(int numTimes) {
        return mStartTetheringCalledCount == numTimes;
    }

    public boolean verifyStopTetheringCalled(int numTimes) {
        return mStopTetheringCalledCount == numTimes;
    }

    public int getTetheringType() {
        return mTetheringType;
    }

    public void addNetworkCapabilities(Network network, NetworkCapabilities capabilities) {
        super.addNetwork(network, mock(NetworkInfo.class));
        mCapabilitiesMap.put(network, capabilities);
    }

    @Implementation
    protected NetworkCapabilities getNetworkCapabilities(Network network) {
        return mCapabilitiesMap.get(network);
    }

    @Implementation
    public void startTethering(int type, boolean showProvisioningUi,
            final ConnectivityManager.OnStartTetheringCallback callback, Handler handler) {
        mTetheringType = type;
        mStartTetheringCalledCount++;
    }

    @Implementation
    public void stopTethering(int type) {
        mTetheringType = type;
        mStopTetheringCalledCount++;
    }

    @Implementation
    protected void factoryReset() {
        sResetCalledCount++;
    }

    @Resetter
    public static void reset() {
        sResetCalledCount = 0;
    }
}
