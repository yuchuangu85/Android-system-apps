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
 * limitations under the License
 */

package com.android.server.telecom.settings;

import android.content.Context;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.BlockedNumberContract.SystemContract;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telecom.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.android.server.telecom.R;

public class EnhancedCallBlockingFragment extends PreferenceFragment
        implements Preference.OnPreferenceChangeListener {
    private static final String BLOCK_NUMBERS_NOT_IN_CONTACTS_KEY =
            "block_numbers_not_in_contacts_setting";
    private static final String BLOCK_RESTRICTED_NUMBERS_KEY =
            "block_private_number_calls_setting";
    private static final String BLOCK_UNKNOWN_NUMBERS_KEY =
            "block_unknown_calls_setting";
    private boolean mIsCombiningRestrictedAndUnknownOption = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.enhanced_call_blocking_settings);

        maybeConfigureCallBlockingOptions();

        setOnPreferenceChangeListener(SystemContract.ENHANCED_SETTING_KEY_BLOCK_UNREGISTERED);
        setOnPreferenceChangeListener(SystemContract.ENHANCED_SETTING_KEY_BLOCK_PRIVATE);
        setOnPreferenceChangeListener(SystemContract.ENHANCED_SETTING_KEY_BLOCK_PAYPHONE);
        setOnPreferenceChangeListener(SystemContract.ENHANCED_SETTING_KEY_BLOCK_UNKNOWN);
        if (!showPayPhoneBlocking()) {
            Preference payPhoneOption = getPreferenceScreen().findPreference(SystemContract.ENHANCED_SETTING_KEY_BLOCK_PAYPHONE);
            getPreferenceScreen().removePreference(payPhoneOption);
        }
    }

    private boolean showPayPhoneBlocking() {
        CarrierConfigManager configManager =
                (CarrierConfigManager) getContext()
                        .getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (configManager == null) {
            return false;
        }

        int subId = SubscriptionManager.getDefaultVoiceSubscriptionId();
        PersistableBundle b = configManager.getConfigForSubId(subId);
        if (b == null) {
            return false;
        }
        return b.getBoolean(CarrierConfigManager.KEY_SHOW_BLOCKING_PAY_PHONE_OPTION_BOOL);
    }

    private void maybeConfigureCallBlockingOptions() {
        PreferenceScreen screen = getPreferenceScreen();
        boolean isShowingNotInContactsOption =
                getResources().getBoolean(R.bool.show_option_to_block_callers_not_in_contacts);
        if (!isShowingNotInContactsOption) {
            Preference pref = findPreference(BLOCK_NUMBERS_NOT_IN_CONTACTS_KEY);
            screen.removePreference(pref);
            Log.i(this, "onCreate: removed block not in contacts preference.");
        }

        mIsCombiningRestrictedAndUnknownOption = getResources().getBoolean(
                        R.bool.combine_options_to_block_restricted_and_unknown_callers);
        if (mIsCombiningRestrictedAndUnknownOption) {
            Preference pref = findPreference(BLOCK_RESTRICTED_NUMBERS_KEY);
            screen.removePreference(pref);
            Log.i(this, "onCreate: removed block restricted preference.");
        }
    }

    /**
     * Set OnPreferenceChangeListener for the preference.
     */
    private void setOnPreferenceChangeListener(String key) {
        SwitchPreference pref = (SwitchPreference) findPreference(key);
        if (pref != null) {
            pref.setOnPreferenceChangeListener(this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        updateEnhancedBlockPref(SystemContract.ENHANCED_SETTING_KEY_BLOCK_UNREGISTERED);
        updateEnhancedBlockPref(SystemContract.ENHANCED_SETTING_KEY_BLOCK_PRIVATE);
        if (showPayPhoneBlocking()) {
            updateEnhancedBlockPref(SystemContract.ENHANCED_SETTING_KEY_BLOCK_PAYPHONE);
        }
        updateEnhancedBlockPref(SystemContract.ENHANCED_SETTING_KEY_BLOCK_UNKNOWN);
    }

    /**
     * Update preference checked status.
     */
    private void updateEnhancedBlockPref(String key) {
        SwitchPreference pref = (SwitchPreference) findPreference(key);
        if (pref != null) {
            pref.setChecked(BlockedNumbersUtil.getEnhancedBlockSetting(getActivity(), key));
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if (mIsCombiningRestrictedAndUnknownOption
                && preference.getKey().equals(BLOCK_UNKNOWN_NUMBERS_KEY)) {
            Log.i(this, "onPreferenceChange: changing %s and %s to %b",
                    preference.getKey(), BLOCK_RESTRICTED_NUMBERS_KEY, (boolean) objValue);
            BlockedNumbersUtil.setEnhancedBlockSetting(getActivity(), BLOCK_RESTRICTED_NUMBERS_KEY,
                    (boolean) objValue);
        }
        BlockedNumbersUtil.setEnhancedBlockSetting(getActivity(), preference.getKey(),
                (boolean) objValue);
        return true;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.xml.layout_customized_listview,
                container, false);
    }
}
