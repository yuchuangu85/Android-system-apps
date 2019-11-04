/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tv.settings.system;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Process;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Keep;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settingslib.location.RecentLocationApps;
import com.android.tv.settings.R;
import com.android.tv.settings.SettingsPreferenceFragment;
import com.android.tv.settings.device.apps.AppManagementFragment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The location settings screen in TV settings.
 */
@Keep
public class LocationFragment extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String TAG = "LocationFragment";

    private static final String LOCATION_MODE_WIFI = "wifi";
    private static final String LOCATION_MODE_OFF = "off";

    private static final String KEY_LOCATION_MODE = "locationMode";

    private static final String MODE_CHANGING_ACTION =
            "com.android.settings.location.MODE_CHANGING";
    private static final String CURRENT_MODE_KEY = "CURRENT_MODE";
    private static final String NEW_MODE_KEY = "NEW_MODE";

    private ListPreference mLocationMode;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Received location mode change intent: " + intent);
            }
            refreshLocationMode();
        }
    };

    public static LocationFragment newInstance() {
        return new LocationFragment();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        final Context themedContext = getPreferenceManager().getContext();
        final PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(
                themedContext);
        screen.setTitle(R.string.system_location);

        mLocationMode = new ListPreference(themedContext);
        screen.addPreference(mLocationMode);
        mLocationMode.setKey(KEY_LOCATION_MODE);
        mLocationMode.setPersistent(false);
        mLocationMode.setTitle(R.string.location_status);
        mLocationMode.setDialogTitle(R.string.location_status);
        mLocationMode.setSummary("%s");
        mLocationMode.setEntries(new CharSequence[] {
                getString(R.string.location_mode_wifi_description),
                getString(R.string.off)
        });
        mLocationMode.setEntryValues(new CharSequence[] {
                LOCATION_MODE_WIFI,
                LOCATION_MODE_OFF
        });
        mLocationMode.setOnPreferenceChangeListener(this);

        final UserManager um = UserManager.get(getContext());
        mLocationMode.setEnabled(!um.hasUserRestriction(UserManager.DISALLOW_SHARE_LOCATION));

        final PreferenceCategory recentRequests = new PreferenceCategory(themedContext);
        screen.addPreference(recentRequests);
        recentRequests.setTitle(R.string.location_category_recent_location_requests);
        recentRequests.setLayoutResource(R.layout.preference_category_compact_layout);

        List<RecentLocationApps.Request> recentLocationRequests =
                new RecentLocationApps(themedContext).getAppList(true);
        List<Preference> recentLocationPrefs = new ArrayList<>(recentLocationRequests.size());
        for (final RecentLocationApps.Request request : recentLocationRequests) {
            Preference pref = new Preference(themedContext);
            pref.setIcon(request.icon);
            pref.setTitle(request.label);
            // Most Android TV devices don't have built-in batteries and we ONLY show "High/Low
            // battery use" for devices with built-in batteries when they are not plugged-in.
            final BatteryManager batteryManager = (BatteryManager) getContext()
                    .getSystemService(Context.BATTERY_SERVICE);
            if (batteryManager != null && !batteryManager.isCharging()) {
                if (request.isHighBattery) {
                    pref.setSummary(R.string.location_high_battery_use);
                } else {
                    pref.setSummary(R.string.location_low_battery_use);
                }
            }
            pref.setFragment(AppManagementFragment.class.getName());
            AppManagementFragment.prepareArgs(pref.getExtras(), request.packageName);
            recentLocationPrefs.add(pref);
        }

        if (recentLocationRequests.size() > 0) {
            addPreferencesSorted(recentLocationPrefs, recentRequests);
        } else {
            // If there's no item to display, add a "No recent apps" item.
            Preference banner = new Preference(themedContext);
            banner.setTitle(R.string.location_no_recent_apps);
            banner.setSelectable(false);
            recentRequests.addPreference(banner);
        }

        // TODO: are location services relevant on TV?

        setPreferenceScreen(screen);
    }

    // When selecting the location preference, LeanbackPreferenceFragment
    // creates an inner view with the selection options; that's when we want to
    // register our receiver, bacause from now on user can change the location
    // providers.
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().registerReceiver(mReceiver,
                new IntentFilter(LocationManager.MODE_CHANGED_ACTION));
        refreshLocationMode();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getActivity().unregisterReceiver(mReceiver);
    }

    private void addPreferencesSorted(List<Preference> prefs, PreferenceGroup container) {
        // If there's some items to display, sort the items and add them to the container.
        prefs.sort(Comparator.comparing(lhs -> lhs.getTitle().toString()));
        for (Preference entry : prefs) {
            container.addPreference(entry);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (TextUtils.equals(preference.getKey(), KEY_LOCATION_MODE)) {
            int mode = Settings.Secure.LOCATION_MODE_OFF;
            if (TextUtils.equals((CharSequence) newValue, LOCATION_MODE_WIFI)) {
                mode = Settings.Secure.LOCATION_MODE_ON;
            } else if (TextUtils.equals((CharSequence) newValue, LOCATION_MODE_OFF)) {
                mode = Settings.Secure.LOCATION_MODE_OFF;
            } else {
                Log.wtf(TAG, "Tried to set unknown location mode!");
            }

            writeLocationMode(mode);
            refreshLocationMode();
        }
        return true;
    }

    private void writeLocationMode(int mode) {
        int currentMode = Settings.Secure.getInt(getActivity().getContentResolver(),
                Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF);
        Intent intent = new Intent(MODE_CHANGING_ACTION);
        intent.putExtra(CURRENT_MODE_KEY, currentMode);
        intent.putExtra(NEW_MODE_KEY, mode);
        getActivity().sendBroadcast(intent, android.Manifest.permission.WRITE_SECURE_SETTINGS);
        getActivity().getSystemService(LocationManager.class).setLocationEnabledForUser(
                mode != Settings.Secure.LOCATION_MODE_OFF,
                Process.myUserHandle());
    }

    private void refreshLocationMode() {
        if (mLocationMode == null) {
            return;
        }
        if (getActivity().getSystemService(LocationManager.class).isLocationEnabled()) {
            mLocationMode.setValue(LOCATION_MODE_WIFI);
        } else {
            mLocationMode.setValue(LOCATION_MODE_OFF);
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.LOCATION;
    }
}
