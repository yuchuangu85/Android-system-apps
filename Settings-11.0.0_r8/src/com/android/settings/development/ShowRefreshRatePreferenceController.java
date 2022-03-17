/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.settings.development;

import android.content.Context;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;

import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.development.DeveloperOptionsPreferenceController;

/**
 * Controller class for controlling the refresh rate overlay on SurfaceFlinger
 */
public class ShowRefreshRatePreferenceController extends DeveloperOptionsPreferenceController
        implements Preference.OnPreferenceChangeListener, PreferenceControllerMixin {

    private static final String SHOW_REFRESH_RATE_KEY = "show_refresh_rate";

    private static final int SETTING_VALUE_QUERY = 2;
    private static final int SETTING_VALUE_ON = 1;
    private static final int SETTING_VALUE_OFF = 0;

    @VisibleForTesting
    static final String SURFACE_FLINGER_SERVICE_KEY = "SurfaceFlinger";
    @VisibleForTesting
    static final int SURFACE_FLINGER_CODE = 1034;

    private static final String SURFACE_COMPOSER_INTERFACE_KEY = "android.ui.ISurfaceComposer";

    private final IBinder mSurfaceFlinger;

    public ShowRefreshRatePreferenceController(Context context) {
        super(context);
        mSurfaceFlinger = ServiceManager.getService(SURFACE_FLINGER_SERVICE_KEY);
    }

    @Override
    public String getPreferenceKey() {
        return SHOW_REFRESH_RATE_KEY;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final boolean isEnabled = (Boolean) newValue;
        writeShowRefreshRateSetting(isEnabled);
        return true;
    }

    @Override
    public void updateState(Preference preference) {
        updateShowRefreshRateSetting();
    }

    @Override
    protected void onDeveloperOptionsSwitchDisabled() {
        super.onDeveloperOptionsSwitchDisabled();
        final SwitchPreference preference = (SwitchPreference) mPreference;
        if (preference.isChecked()) {
            // Writing false to the preference when the setting is already off will have a
            // side effect of turning on the preference that we wish to avoid
            writeShowRefreshRateSetting(false);
            preference.setChecked(false);
        }
    }

    @VisibleForTesting
    void updateShowRefreshRateSetting() {
        // magic communication with surface flinger.
        try {
            if (mSurfaceFlinger != null) {
                final Parcel data = Parcel.obtain();
                final Parcel reply = Parcel.obtain();
                data.writeInterfaceToken(SURFACE_COMPOSER_INTERFACE_KEY);
                data.writeInt(SETTING_VALUE_QUERY);
                mSurfaceFlinger.transact(SURFACE_FLINGER_CODE, data, reply, 0 /* flags */);
                final boolean enabled = reply.readBoolean();
                ((SwitchPreference) mPreference).setChecked(enabled);
                reply.recycle();
                data.recycle();
            }
        } catch (RemoteException ex) {
            // intentional no-op
        }
    }

    @VisibleForTesting
    void writeShowRefreshRateSetting(boolean isEnabled) {
        try {
            if (mSurfaceFlinger != null) {
                final Parcel data = Parcel.obtain();
                data.writeInterfaceToken(SURFACE_COMPOSER_INTERFACE_KEY);
                final int showRefreshRate = isEnabled ? SETTING_VALUE_ON : SETTING_VALUE_OFF;
                data.writeInt(showRefreshRate);
                mSurfaceFlinger.transact(SURFACE_FLINGER_CODE, data,
                        null /* reply */, 0 /* flags */);
                data.recycle();
            }
        } catch (RemoteException ex) {
            // intentional no-op
        }
        updateShowRefreshRateSetting();
    }
}
