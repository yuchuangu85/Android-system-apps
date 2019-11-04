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

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.preference.TwoStatePreference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

/** Business logic to enable falling back to cellular data when wifi is poor. */
public class CellularFallbackTogglePreferenceController extends
        PreferenceController<TwoStatePreference> {

    private static final String ENABLED = "1";
    private static final String DISABLED = null;

    public CellularFallbackTogglePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<TwoStatePreference> getPreferenceType() {
        return TwoStatePreference.class;
    }

    @Override
    protected int getAvailabilityStatus() {
        return avoidBadWifiConfigEnabled() ? UNSUPPORTED_ON_DEVICE : AVAILABLE;
    }

    @Override
    protected void updateState(TwoStatePreference preference) {
        preference.setChecked(cellularFallbackEnabled());
    }

    @Override
    protected boolean handlePreferenceChanged(TwoStatePreference preference, Object newValue) {
        boolean enableCellularFallback = (Boolean) newValue;
        Settings.Global.putString(getContext().getContentResolver(),
                Settings.Global.NETWORK_AVOID_BAD_WIFI,
                enableCellularFallback ? ENABLED : DISABLED);
        return true;
    }

    /**
     * See {@link Settings.Global#NETWORK_AVOID_BAD_WIFI} for description of this configuration. In
     * short, if the device is configured to avoid bad wifi, the option to fallback to cellular is
     * not shown.
     */
    private boolean avoidBadWifiConfigEnabled() {
        return getContext().getResources().getInteger(R.integer.config_networkAvoidBadWifi) == 1;
    }

    private boolean cellularFallbackEnabled() {
        return TextUtils.equals(ENABLED,
                Settings.Global.getString(getContext().getContentResolver(),
                        Settings.Global.NETWORK_AVOID_BAD_WIFI));
    }
}
