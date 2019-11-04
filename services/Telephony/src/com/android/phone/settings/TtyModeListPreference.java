/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.phone.settings;

import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.util.AttributeSet;
import android.util.Log;

import com.android.phone.PhoneGlobals;
import com.android.phone.R;

import java.util.Arrays;

public class TtyModeListPreference extends ListPreference
        implements Preference.OnPreferenceChangeListener {
    private static final String LOG_TAG = TtyModeListPreference.class.getSimpleName();
    private static final boolean DBG = (PhoneGlobals.DBG_LEVEL >= 2);

    public TtyModeListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void init() {
        setOnPreferenceChangeListener(this);

        int settingsTtyMode = Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.PREFERRED_TTY_MODE,
                TelecomManager.TTY_MODE_OFF);
        if (shouldHideHcoAndVco()) {
            setEntries(Arrays.copyOfRange(
                    getContext().getResources().getTextArray(R.array.tty_mode_entries), 0, 2));
            setEntryValues(Arrays.copyOfRange(
                    getContext().getResources().getTextArray(R.array.tty_mode_values), 0, 2));
            if (settingsTtyMode == TelecomManager.TTY_MODE_HCO
                    || settingsTtyMode == TelecomManager.TTY_MODE_VCO) {
                // If the persisted setting is HCO or VCO, set it to full here
                settingsTtyMode = TelecomManager.TTY_MODE_FULL;
                Settings.Secure.putInt(getContext().getContentResolver(), Secure.PREFERRED_TTY_MODE,
                        settingsTtyMode);
            }
        }
        setValue(Integer.toString(settingsTtyMode));
        updatePreferredTtyModeSummary(settingsTtyMode);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if (preference == this) {
            int buttonTtyMode = Integer.parseInt((String) objValue);
            int settingsTtyMode = android.provider.Settings.Secure.getInt(
                    getContext().getContentResolver(),
                    Settings.Secure.PREFERRED_TTY_MODE,
                    TelecomManager.TTY_MODE_OFF);
            if (DBG) log("handleTTYChange: requesting set TTY mode enable (TTY) to" +
                    Integer.toString(buttonTtyMode));

            if (buttonTtyMode != settingsTtyMode) {
                switch(buttonTtyMode) {
                    case TelecomManager.TTY_MODE_OFF:
                    case TelecomManager.TTY_MODE_FULL:
                    case TelecomManager.TTY_MODE_HCO:
                    case TelecomManager.TTY_MODE_VCO:
                        Settings.Secure.putInt(
                                getContext().getContentResolver(),
                                Settings.Secure.PREFERRED_TTY_MODE,
                                buttonTtyMode);
                        break;
                    default:
                        buttonTtyMode = TelecomManager.TTY_MODE_OFF;
                }

                setValue(Integer.toString(buttonTtyMode));
                updatePreferredTtyModeSummary(buttonTtyMode);
                Intent ttyModeChanged =
                        new Intent(TelecomManager.ACTION_TTY_PREFERRED_MODE_CHANGED);
                ttyModeChanged.putExtra(TelecomManager.EXTRA_TTY_PREFERRED_MODE, buttonTtyMode);
                getContext().sendBroadcastAsUser(ttyModeChanged, UserHandle.ALL);
            }
        }
        return true;
    }

    private void updatePreferredTtyModeSummary(int TtyMode) {
        String [] txts = getContext().getResources().getStringArray(R.array.tty_mode_entries);
        switch(TtyMode) {
            case TelecomManager.TTY_MODE_OFF:
            case TelecomManager.TTY_MODE_HCO:
            case TelecomManager.TTY_MODE_VCO:
            case TelecomManager.TTY_MODE_FULL:
                setSummary(txts[TtyMode]);
                break;
            default:
                setEnabled(false);
                setSummary(txts[TelecomManager.TTY_MODE_OFF]);
                break;
        }
    }

    private boolean shouldHideHcoAndVco() {
        CarrierConfigManager carrierConfigManager =
                getContext().getSystemService(CarrierConfigManager.class);
        PersistableBundle config = carrierConfigManager.getConfigForSubId(
                SubscriptionManager.getDefaultVoiceSubscriptionId());
        boolean carrierShouldHideHcoVco = config != null && config.getBoolean(
                CarrierConfigManager.KEY_HIDE_TTY_HCO_VCO_WITH_RTT_BOOL, false);
        boolean isRttSupported = PhoneGlobals.getInstance().phoneMgr.isRttSupported(
                SubscriptionManager.getDefaultVoiceSubscriptionId());
        return carrierShouldHideHcoVco && isRttSupported;
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
