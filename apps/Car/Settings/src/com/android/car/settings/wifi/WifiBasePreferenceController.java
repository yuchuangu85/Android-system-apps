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

package com.android.car.settings.wifi;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

/**
 * Abstract controls preference for Wifi.
 *
 * @param <V> the upper bound on the type of {@link Preference} on which the controller
 *            expects to operate.
 */
public abstract class WifiBasePreferenceController<V extends Preference> extends
        PreferenceController<V> implements CarWifiManager.Listener {

    private CarWifiManager mCarWifiManager;

    public WifiBasePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected void onCreateInternal() {
        mCarWifiManager = new CarWifiManager(getContext());
    }

    @Override
    protected void onStartInternal() {
        mCarWifiManager.addListener(this);
        mCarWifiManager.start();
    }

    @Override
    protected void onStopInternal() {
        mCarWifiManager.removeListener(this);
        mCarWifiManager.stop();
    }

    @Override
    protected void onDestroyInternal() {
        mCarWifiManager.destroy();
    }

    @Override
    public int getAvailabilityStatus() {
        return WifiUtil.isWifiAvailable(getContext()) ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public void onAccessPointsChanged() {
        // don't care
    }

    protected CarWifiManager getCarWifiManager() {
        return mCarWifiManager;
    }
}
