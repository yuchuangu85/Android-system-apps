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
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiConfiguration;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.ListPreference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;

/**
 * Controls WiFi Hotspot Security Type configuration.
 */
public class WifiTetherSecurityPreferenceController extends
        WifiTetherBasePreferenceController<ListPreference> {

    public static final String KEY_SECURITY_TYPE = "KEY_SECURITY_TYPE";
    public static final String ACTION_SECURITY_TYPE_CHANGED =
            "com.android.car.settings.wifi.ACTION_WIFI_TETHER_SECURITY_TYPE_CHANGED";

    private int mSecurityType;

    public WifiTetherSecurityPreferenceController(Context context, String preferenceKey,
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
        mSecurityType = getCarWifiApConfig().getAuthType();
        getPreference().setEntries(
                getContext().getResources().getStringArray(R.array.wifi_tether_security));
        String[] entryValues = {Integer.toString(WifiConfiguration.KeyMgmt.WPA2_PSK),
                Integer.toString(WifiConfiguration.KeyMgmt.NONE)};
        getPreference().setEntryValues(entryValues);
        getPreference().setValue(String.valueOf(mSecurityType));
    }

    @Override
    protected boolean handlePreferenceChanged(ListPreference preference,
            Object newValue) {
        mSecurityType = Integer.parseInt(newValue.toString());
        updateSecurityType();
        refreshUi();
        return true;
    }

    @Override
    protected void updateState(ListPreference preference) {
        super.updateState(preference);
        preference.setValue(Integer.toString(mSecurityType));
    }

    @Override
    protected String getSummary() {
        int stringResId = mSecurityType == WifiConfiguration.KeyMgmt.WPA2_PSK
                ? R.string.wifi_hotspot_wpa2_personal : R.string.wifi_hotspot_security_none;
        return getContext().getString(stringResId);
    }

    @Override
    protected String getDefaultSummary() {
        return null;
    }

    private void updateSecurityType() {
        WifiConfiguration config = getCarWifiApConfig();
        config.allowedKeyManagement.clear();
        config.allowedKeyManagement.set(mSecurityType);

        if (mSecurityType == WifiConfiguration.KeyMgmt.NONE) {
            config.preSharedKey = "";
        } else {
            config.preSharedKey = getSavedPassword();
        }

        setCarWifiApConfig(config);
        broadcastSecurityTypeChanged();
    }

    private void broadcastSecurityTypeChanged() {
        Intent intent = new Intent(ACTION_SECURITY_TYPE_CHANGED);
        intent.putExtra(KEY_SECURITY_TYPE, mSecurityType);
        LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent);
    }

    private String getSavedPassword() {
        SharedPreferences sp = getContext().getSharedPreferences(
                WifiTetherPasswordPreferenceController.SHARED_PREFERENCE_PATH,
                Context.MODE_PRIVATE);
        String savedPassword =
                sp.getString(WifiTetherPasswordPreferenceController.KEY_SAVED_PASSWORD,
                        /* defaultValue= */ null);
        return savedPassword;
    }
}
