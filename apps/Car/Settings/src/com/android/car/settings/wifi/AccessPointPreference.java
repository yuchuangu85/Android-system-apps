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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;

import androidx.preference.PreferenceViewHolder;

import com.android.car.settings.common.Logger;
import com.android.settingslib.wifi.AccessPoint;

/** Renders a {@link AccessPoint} as a preference. */
public class AccessPointPreference extends ButtonPasswordEditTextPreference {
    private static final Logger LOG = new Logger(AccessPointPreference.class);
    private static final int[] STATE_SECURED = {
            com.android.settingslib.R.attr.state_encrypted
    };
    private static final int[] STATE_NONE = {};
    private static int[] sWifiSignalAttributes = {com.android.settingslib.R.attr.wifi_signal};

    private final StateListDrawable mWifiSld;
    private final AccessPoint mAccessPoint;

    public AccessPointPreference(
            Context context,
            AccessPoint accessPoint) {
        super(context);
        mWifiSld = (StateListDrawable) context.getTheme()
                .obtainStyledAttributes(sWifiSignalAttributes).getDrawable(0);
        mAccessPoint = accessPoint;
        LOG.d("creating preference for ap: " + mAccessPoint);
        setIcon(getAccessPointIcon());
    }

    /**
     * Returns the {@link AccessPoint}.
     */
    public AccessPoint getAccessPoint() {
        return mAccessPoint;
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        setIcon(getAccessPointIcon());
    }

    @Override
    protected void onClick() {
        if (shouldShowPasswordDialog()) {
            super.onClick();
        }
    }

    /**
     * Show password dialog for one of the following conditions:
     * 1. AP with some security but is not saved and not active
     * 2. AP that has been saved, but not enabled due to wrong password.
     */
    private boolean shouldShowPasswordDialog() {
        return mAccessPoint.getSecurity() != AccessPoint.SECURITY_NONE && (!mAccessPoint.isSaved()
                || WifiUtil.isAccessPointDisabledByWrongPassword(mAccessPoint));
    }

    private Drawable getAccessPointIcon() {
        if (mWifiSld == null) {
            LOG.w("wifiSld is null.");
            return null;
        }
        mWifiSld.setState(
                (mAccessPoint.getSecurity() != AccessPoint.SECURITY_NONE)
                        ? STATE_SECURED
                        : STATE_NONE);
        Drawable drawable = mWifiSld.getCurrent();
        drawable.setLevel(mAccessPoint.getLevel());
        return drawable;
    }
}
