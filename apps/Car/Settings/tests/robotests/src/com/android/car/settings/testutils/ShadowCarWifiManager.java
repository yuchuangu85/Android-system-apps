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

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;

import com.android.car.settings.wifi.CarWifiManager;
import com.android.settingslib.wifi.AccessPoint;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;

import java.util.List;

/** TODO: Refactor all methods to run without relying on sInstance. */
@Implements(CarWifiManager.class)
public class ShadowCarWifiManager {

    public static final int STATE_UNKNOWN = -1;
    public static final int STATE_STARTED = 0;
    public static final int STATE_STOPPED = 1;
    public static final int STATE_DESTROYED = 2;

    private static CarWifiManager sInstance;
    private static int sCurrentState = STATE_UNKNOWN;
    private static WifiConfiguration sWifiConfiguration = new WifiConfiguration();
    private static boolean sIsDualModeSupported = true;
    private static boolean sIsDualBandSupported = true;

    public static void setInstance(CarWifiManager wifiManager) {
        sInstance = wifiManager;
    }

    @Resetter
    public static void reset() {
        sInstance = null;
        sWifiConfiguration = new WifiConfiguration();
        sCurrentState = STATE_UNKNOWN;
        sIsDualModeSupported = true;
        sIsDualBandSupported = true;
    }

    @Implementation
    public void __constructor__(Context context) {
    }

    @Implementation
    public void start() {
        if (sInstance != null) {
            sInstance.start();
        }
        sCurrentState = STATE_STARTED;
    }

    @Implementation
    public void stop() {
        if (sInstance != null) {
            sInstance.stop();
        }
        sCurrentState = STATE_STOPPED;
    }

    @Implementation
    public void destroy() {
        if (sInstance != null) {
            sInstance.destroy();
        }
        sCurrentState = STATE_DESTROYED;
    }

    @Implementation
    public void setWifiApConfig(WifiConfiguration config) {
        sWifiConfiguration = config;
    }

    @Implementation
    public WifiConfiguration getWifiApConfig() {
        return sWifiConfiguration;
    }

    @Implementation
    public boolean setWifiEnabled(boolean enabled) {
        return sInstance.setWifiEnabled(enabled);
    }

    @Implementation
    public boolean isWifiEnabled() {
        return sInstance.isWifiEnabled();
    }

    @Implementation
    public boolean isWifiApEnabled() {
        return sInstance.isWifiApEnabled();
    }

    @Implementation
    public List<AccessPoint> getAllAccessPoints() {
        return sInstance.getAllAccessPoints();
    }

    @Implementation
    public List<AccessPoint> getSavedAccessPoints() {
        return sInstance.getSavedAccessPoints();
    }

    @Implementation
    public void connectToPublicWifi(AccessPoint accessPoint, WifiManager.ActionListener listener) {
        sInstance.connectToPublicWifi(accessPoint, listener);
    }

    @Implementation
    protected void connectToSavedWifi(AccessPoint accessPoint,
            WifiManager.ActionListener listener) {
        sInstance.connectToSavedWifi(accessPoint, listener);
    }

    @Implementation
    protected boolean isDualModeSupported() {
        return sIsDualModeSupported;
    }

    @Implementation
    protected String getCountryCode() {
        return "1";
    }

    @Implementation
    protected boolean isDualBandSupported() {
        return sIsDualBandSupported;
    }

    public static void setIsDualModeSupported(boolean supported) {
        sIsDualModeSupported = supported;
    }

    public static void setIsDualBandSupported(boolean supported) {
        sIsDualBandSupported = supported;
    }

    public static int getCurrentState() {
        return sCurrentState;
    }
}
