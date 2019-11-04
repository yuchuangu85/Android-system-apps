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

package com.android.car.settings.wifi.preferences;

import android.app.Service;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.widget.Toast;

import androidx.preference.TwoStatePreference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;

/** Business logic to allow auto-enabling of wifi near saved networks. */
public class WifiWakeupTogglePreferenceController extends
        PreferenceController<TwoStatePreference> implements
        ConfirmEnableWifiScanningDialogFragment.WifiScanningEnabledListener {

    private static final Logger LOG = new Logger(WifiWakeupTogglePreferenceController.class);
    private final LocationManager mLocationManager;

    public WifiWakeupTogglePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mLocationManager = (LocationManager) context.getSystemService(Service.LOCATION_SERVICE);
    }

    @Override
    protected Class<TwoStatePreference> getPreferenceType() {
        return TwoStatePreference.class;
    }

    @Override
    protected void onCreateInternal() {
        ConfirmEnableWifiScanningDialogFragment dialogFragment =
                (ConfirmEnableWifiScanningDialogFragment) getFragmentController().findDialogByTag(
                        ConfirmEnableWifiScanningDialogFragment.TAG);
        if (dialogFragment != null) {
            dialogFragment.setWifiScanningEnabledListener(this);
        }
    }

    @Override
    protected void updateState(TwoStatePreference preference) {
        preference.setChecked(getWifiWakeupEnabled()
                && getWifiScanningEnabled()
                && mLocationManager.isLocationEnabled());
        if (!mLocationManager.isLocationEnabled()) {
            preference.setSummary(getNoLocationSummary());
        } else {
            preference.setSummary(R.string.wifi_wakeup_summary);
        }
    }

    @Override
    protected boolean handlePreferenceClicked(TwoStatePreference preference) {
        if (!mLocationManager.isLocationEnabled()) {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            getContext().startActivity(intent);
        } else if (getWifiWakeupEnabled()) {
            setWifiWakeupEnabled(false);
        } else if (!getWifiScanningEnabled()) {
            showScanningDialog();
        } else {
            setWifiWakeupEnabled(true);
        }

        refreshUi();
        return true;
    }

    private boolean getWifiWakeupEnabled() {
        return Settings.Global.getInt(getContext().getContentResolver(),
                Settings.Global.WIFI_WAKEUP_ENABLED, 0) == 1;
    }

    private void setWifiWakeupEnabled(boolean enabled) {
        Settings.Global.putInt(getContext().getContentResolver(),
                Settings.Global.WIFI_WAKEUP_ENABLED,
                enabled ? 1 : 0);
    }

    private boolean getWifiScanningEnabled() {
        return Settings.Global.getInt(getContext().getContentResolver(),
                Settings.Global.WIFI_SCAN_ALWAYS_AVAILABLE, 0) == 1;
    }

    private void enableWifiScanning() {
        Settings.Global.putInt(getContext().getContentResolver(),
                Settings.Global.WIFI_SCAN_ALWAYS_AVAILABLE, 1);
        Toast.makeText(
                getContext(),
                getContext().getString(R.string.wifi_settings_scanning_required_enabled),
                Toast.LENGTH_SHORT).show();
    }

    private CharSequence getNoLocationSummary() {
        String highlightedWord = "link";
        CharSequence locationText = getContext()
                .getText(R.string.wifi_wakeup_summary_no_location);

        SpannableString msg = new SpannableString(locationText);
        int startIndex = locationText.toString().indexOf(highlightedWord);
        if (startIndex < 0) {
            LOG.e("Cannot create URL span");
            return locationText;
        }
        msg.setSpan(new URLSpan((String) null), startIndex, startIndex + highlightedWord.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return msg;
    }

    private void showScanningDialog() {
        ConfirmEnableWifiScanningDialogFragment dialogFragment =
                new ConfirmEnableWifiScanningDialogFragment();
        dialogFragment.setWifiScanningEnabledListener(this);
        getFragmentController().showDialog(dialogFragment,
                ConfirmEnableWifiScanningDialogFragment.TAG);
    }

    @Override
    public void onWifiScanningEnabled() {
        enableWifiScanning();
        if (mLocationManager.isLocationEnabled()) {
            setWifiWakeupEnabled(true);
        }
        refreshUi();
    }
}
