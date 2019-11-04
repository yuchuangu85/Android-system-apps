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
package com.android.car.settings.wifi;

import android.annotation.Nullable;
import android.content.Context;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;

import androidx.annotation.UiThread;

import com.android.settingslib.wifi.AccessPoint;
import com.android.settingslib.wifi.WifiTracker;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages Wifi configuration: e.g. monitors wifi states, change wifi setting etc.
 */
public class CarWifiManager implements WifiTracker.WifiListener {
    private final Context mContext;
    private final List<Listener> mListeners = new ArrayList<>();
    private boolean mStarted;

    private WifiTracker mWifiTracker;
    private WifiManager mWifiManager;

    public interface Listener {
        /**
         * Something about wifi setting changed.
         */
        void onAccessPointsChanged();

        /**
         * Called when the state of Wifi has changed, the state will be one of
         * the following.
         *
         * <li>{@link WifiManager#WIFI_STATE_DISABLED}</li>
         * <li>{@link WifiManager#WIFI_STATE_ENABLED}</li>
         * <li>{@link WifiManager#WIFI_STATE_DISABLING}</li>
         * <li>{@link WifiManager#WIFI_STATE_ENABLING}</li>
         * <li>{@link WifiManager#WIFI_STATE_UNKNOWN}</li>
         * <p>
         *
         * @param state The new state of wifi.
         */
        void onWifiStateChanged(int state);
    }

    public CarWifiManager(Context context) {
        mContext = context;
        mWifiManager = mContext.getSystemService(WifiManager.class);
        mWifiTracker = new WifiTracker(context, this, true, true);
    }

    /**
     * Adds {@link Listener}.
     */
    public boolean addListener(Listener listener) {
        return mListeners.add(listener);
    }

    /**
     * Removes {@link Listener}.
     */
    public boolean removeListener(Listener listener) {
        return mListeners.remove(listener);
    }

    /**
     * Starts {@link CarWifiManager}.
     * This should be called only from main thread.
     */
    @UiThread
    public void start() {
        if (!mStarted) {
            mStarted = true;
            mWifiTracker.onStart();
        }
    }

    /**
     * Stops {@link CarWifiManager}.
     * This should be called only from main thread.
     */
    @UiThread
    public void stop() {
        if (mStarted) {
            mStarted = false;
            mWifiTracker.onStop();
        }
    }

    /**
     * Destroys {@link CarWifiManager}
     * This should only be called from main thread.
     */
    @UiThread
    public void destroy() {
        mWifiTracker.onDestroy();
    }

    /**
     * Returns a list of all reachable access points.
     */
    public List<AccessPoint> getAllAccessPoints() {
        return getAccessPoints(false);
    }

    /**
     * Returns a list of saved access points.
     */
    public List<AccessPoint> getSavedAccessPoints() {
        return getAccessPoints(true);
    }

    private List<AccessPoint> getAccessPoints(boolean saved) {
        List<AccessPoint> accessPoints = new ArrayList<AccessPoint>();
        if (mWifiManager.isWifiEnabled()) {
            for (AccessPoint accessPoint : mWifiTracker.getAccessPoints()) {
                // ignore out of reach access points.
                if (shouldIncludeAp(accessPoint, saved)) {
                    accessPoints.add(accessPoint);
                }
            }
        }
        return accessPoints;
    }

    private boolean shouldIncludeAp(AccessPoint accessPoint, boolean saved) {
        return saved ? accessPoint.isReachable() && accessPoint.isSaved()
                : accessPoint.isReachable();
    }

    @Nullable
    public AccessPoint getConnectedAccessPoint() {
        for (AccessPoint accessPoint : getAllAccessPoints()) {
            if (accessPoint.getDetailedState() == NetworkInfo.DetailedState.CONNECTED) {
                return accessPoint;
            }
        }
        return null;
    }

    /**
     * Returns {@code true} if Wifi is enabled
     */
    public boolean isWifiEnabled() {
        return mWifiManager.isWifiEnabled();
    }

    /**
     * Returns {@code true} if Wifi tethering is enabled
     */
    public boolean isWifiApEnabled() {
        return mWifiManager.isWifiApEnabled();
    }

    /**
     * Gets {@link WifiConfiguration} for tethering
     */
    public WifiConfiguration getWifiApConfig() {
        return mWifiManager.getWifiApConfiguration();
    }

    /**
     * Sets {@link WifiConfiguration} for tethering
     */
    public void setWifiApConfig(WifiConfiguration config) {
        mWifiManager.setWifiApConfiguration(config);
    }

    /**
     * Gets the country code in ISO 3166 format.
     */
    public String getCountryCode() {
        return mWifiManager.getCountryCode();
    }

    /**
     * Checks if the chipset supports dual frequency band (2.4 GHz and 5 GHz).
     */
    public boolean isDualBandSupported() {
        return mWifiManager.isDualBandSupported();
    }

    /**
     * Check if the chipset requires conversion of 5GHz Only apBand to ANY.
     * @return {@code true} if required, {@code false} otherwise.
     */
    public boolean isDualModeSupported() {
        return mWifiManager.isDualModeSupported();
    }

    /** Gets the wifi state from {@link WifiManager}. */
    public int getWifiState() {
        return mWifiManager.getWifiState();
    }

    /** Sets whether wifi is enabled. */
    public boolean setWifiEnabled(boolean enabled) {
        return mWifiManager.setWifiEnabled(enabled);
    }

    /** Connects to an public wifi access point. */
    public void connectToPublicWifi(AccessPoint accessPoint, WifiManager.ActionListener listener) {
        accessPoint.generateOpenNetworkConfig();
        mWifiManager.connect(accessPoint.getConfig(), listener);
    }

    /** Connects to a saved access point. */
    public void connectToSavedWifi(AccessPoint accessPoint, WifiManager.ActionListener listener) {
        if (accessPoint.isSaved()) {
            mWifiManager.connect(accessPoint.getConfig(), listener);
        }
    }

    @Override
    public void onWifiStateChanged(int state) {
        for (Listener listener : mListeners) {
            listener.onWifiStateChanged(state);
        }
    }

    @Override
    public void onConnectedChanged() {
    }

    @Override
    public void onAccessPointsChanged() {
        for (Listener listener : mListeners) {
            listener.onAccessPointsChanged();
        }
    }
}
