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
import android.content.res.Resources;
import android.net.wifi.WifiConfiguration;

import androidx.preference.ListPreference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;

/**
 * Controls WiFi Hotspot AP Band configuration.
 */
public class WifiTetherApBandPreferenceController extends
        WifiTetherBasePreferenceController<ListPreference> {

    private String[] mBandEntries;
    private String[] mBandSummaries;
    private int mBandIndex;
    private boolean mIsDualMode;

    public WifiTetherApBandPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<ListPreference> getPreferenceType() {
        return ListPreference.class;
    }

    @Override
    protected void onCreateInternal() {
        super.onCreateInternal();
        mIsDualMode = getCarWifiManager().isDualModeSupported();
        updatePreferenceEntries();
        getPreference().setEntries(mBandSummaries);
        getPreference().setEntryValues(mBandEntries);
    }

    @Override
    public void updateState(ListPreference preference) {
        super.updateState(preference);

        WifiConfiguration config = getCarWifiApConfig();
        if (config == null) {
            mBandIndex = 0;
        } else if (is5GhzBandSupported()) {
            mBandIndex = validateSelection(config.apBand);
        } else {
            config.apBand = 0;
            setCarWifiApConfig(config);
            mBandIndex = config.apBand;
        }

        if (!is5GhzBandSupported()) {
            preference.setEnabled(false);
            preference.setSummary(R.string.wifi_ap_choose_2G);
        } else {
            preference.setValue(Integer.toString(config.apBand));
            preference.setSummary(getSummary());
        }

    }

    @Override
    protected String getSummary() {
        if (is5GhzBandSupported()) {
            if (mBandIndex != WifiConfiguration.AP_BAND_ANY) {
                return mBandSummaries[mBandIndex];
            } else {
                return getContext().getString(R.string.wifi_ap_prefer_5G);
            }
        } else {
            return getContext().getString(R.string.wifi_ap_choose_2G);
        }
    }

    @Override
    protected String getDefaultSummary() {
        return null;
    }

    @Override
    public boolean handlePreferenceChanged(ListPreference preference, Object newValue) {
        mBandIndex = validateSelection(Integer.parseInt((String) newValue));
        updateApBand(); // updating AP band because mBandIndex may have been assigned a new value.
        refreshUi();
        return true;
    }

    private int validateSelection(int band) {
        // Reset the band to 2.4 GHz if we get a weird config back to avoid a crash.
        boolean isDualMode = getCarWifiManager().isDualModeSupported();

        // unsupported states:
        // 1: no dual mode means we can't have AP_BAND_ANY - default to 5GHZ
        // 2: no 5 GHZ support means we can't have AP_BAND_5GHZ - default to 2GHZ
        // 3: With Dual mode support we can't have AP_BAND_5GHZ - default to ANY
        if (!isDualMode && WifiConfiguration.AP_BAND_ANY == band) {
            return WifiConfiguration.AP_BAND_5GHZ;
        } else if (!is5GhzBandSupported() && WifiConfiguration.AP_BAND_5GHZ == band) {
            return WifiConfiguration.AP_BAND_2GHZ;
        } else if (isDualMode && WifiConfiguration.AP_BAND_5GHZ == band) {
            return WifiConfiguration.AP_BAND_ANY;
        }

        return band;
    }

    private void updatePreferenceEntries() {
        Resources res = getContext().getResources();
        int entriesRes = R.array.wifi_ap_band_config_full;
        int summariesRes = R.array.wifi_ap_band_summary_full;
        // change the list options if this is a dual mode device
        if (mIsDualMode) {
            entriesRes = R.array.wifi_ap_band_dual_mode;
            summariesRes = R.array.wifi_ap_band_dual_mode_summary;
        }
        mBandEntries = res.getStringArray(entriesRes);
        mBandSummaries = res.getStringArray(summariesRes);
    }

    private void updateApBand() {
        WifiConfiguration config = getCarWifiApConfig();
        config.apBand = mBandIndex;
        setCarWifiApConfig(config);
        if (mBandIndex == WifiConfiguration.AP_BAND_ANY) {
            getPreference().setValue(mBandEntries[WifiConfiguration.AP_BAND_2GHZ]);
        } else {
            getPreference().setValue(mBandEntries[mBandIndex]);
        }
    }

    private boolean is5GhzBandSupported() {
        String countryCode = getCarWifiManager().getCountryCode();
        return getCarWifiManager().isDualBandSupported() && countryCode != null;
    }
}
