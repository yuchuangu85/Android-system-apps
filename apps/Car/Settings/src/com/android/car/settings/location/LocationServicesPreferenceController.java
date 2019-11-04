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

package com.android.car.settings.location;

import android.car.drivingstate.CarUxRestrictions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.SettingInjectorService;
import android.os.UserHandle;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;
import com.android.settingslib.location.SettingsInjector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Injects Location Services into a {@link PreferenceGroup} with a matching key.
 */
public class LocationServicesPreferenceController extends PreferenceController<PreferenceGroup> {
    private static final Logger LOG = new Logger(LocationServicesPreferenceController.class);
    private static final IntentFilter INTENT_FILTER_INJECTED_SETTING_CHANGED = new IntentFilter(
            SettingInjectorService.ACTION_INJECTED_SETTING_CHANGED);
    private SettingsInjector mSettingsInjector;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            LOG.i("Received injected settings change intent: " + intent);
            mSettingsInjector.reloadStatusMessages();
        }
    };

    public LocationServicesPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mSettingsInjector = new SettingsInjector(context);
    }

    @VisibleForTesting
    void setSettingsInjector(SettingsInjector injector) {
        mSettingsInjector = injector;
    }

    @Override
    protected Class<PreferenceGroup> getPreferenceType() {
        return PreferenceGroup.class;
    }

    @Override
    protected void onCreateInternal() {
        int profileId = UserHandle.USER_CURRENT;
        List<Preference> injectedSettings = getSortedInjectedPreferences(profileId);
        for (Preference preference : injectedSettings) {
            getPreference().addPreference(preference);
        }
    }

    /**
     * Called when the controller is started.
     */
    @Override
    protected void onStartInternal() {
        getContext().registerReceiver(mReceiver, INTENT_FILTER_INJECTED_SETTING_CHANGED);
    }

    /**
     * Called when the controller is stopped.
     */
    @Override
    protected void onStopInternal() {
        getContext().unregisterReceiver(mReceiver);
    }

    @Override
    protected void updateState(PreferenceGroup preferenceGroup) {

        preferenceGroup.setVisible(preferenceGroup.getPreferenceCount() > 0);
    }

    private List<Preference> getSortedInjectedPreferences(int profileId) {
        List<Preference> sortedInjections = new ArrayList<>();
        Map<Integer, List<Preference>> injections =
                mSettingsInjector.getInjectedSettings(getContext(), profileId);
        for (Map.Entry<Integer, List<Preference>> entry : injections.entrySet()) {
            sortedInjections.addAll(entry.getValue());
        }
        Collections.sort(sortedInjections);
        return sortedInjections;
    }
}
