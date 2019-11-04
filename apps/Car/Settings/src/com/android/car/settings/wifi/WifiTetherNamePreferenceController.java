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

package com.android.car.settings.wifi;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.net.wifi.WifiConfiguration;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.ValidatedEditTextPreference;

/**
 * Controls WiFi Hotspot name configuration. When Hotspot is enabled, this name that will be
 * displayed as the Access Point to the Hotspot.
 */
public class WifiTetherNamePreferenceController extends
        WifiTetherBasePreferenceController<ValidatedEditTextPreference> {

    private static final int HOTSPOT_NAME_MIN_LENGTH = 1;
    private static final int HOTSPOT_NAME_MAX_LENGTH = 32;
    private static final ValidatedEditTextPreference.Validator NAME_VALIDATOR =
            value -> value.length() >= HOTSPOT_NAME_MIN_LENGTH
                    && value.length() <= HOTSPOT_NAME_MAX_LENGTH;

    private String mName;

    public WifiTetherNamePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<ValidatedEditTextPreference> getPreferenceType() {
        return ValidatedEditTextPreference.class;
    }

    @Override
    protected void onCreateInternal() {
        super.onCreateInternal();
        getPreference().setValidator(NAME_VALIDATOR);
        mName = getCarWifiApConfig().SSID;
    }

    @Override
    protected boolean handlePreferenceChanged(ValidatedEditTextPreference preference,
            Object newValue) {
        mName = newValue.toString();
        updateSSID(mName);
        refreshUi();
        return true;
    }

    @Override
    protected void updateState(ValidatedEditTextPreference preference) {
        super.updateState(preference);
        preference.setText(mName);
    }

    private void updateSSID(String ssid) {
        WifiConfiguration config = getCarWifiApConfig();
        config.SSID = ssid;
        setCarWifiApConfig(config);
    }

    @Override
    protected String getSummary() {
        return mName;
    }

    @Override
    protected String getDefaultSummary() {
        return null;
    }
}
