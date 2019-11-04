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

package com.android.car.settings.applications.specialaccess;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;

import androidx.annotation.VisibleForTesting;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;
import com.android.internal.telephony.ISms;
import com.android.internal.telephony.SmsUsageMonitor;
import com.android.settingslib.applications.ApplicationsState.AppEntry;
import com.android.settingslib.applications.ApplicationsState.AppFilter;

import java.util.List;

/**
 * Displays the list of apps which have a known premium SMS access state. When a user selects an
 * app, they are shown a dialog which allows them to configure the state to one of:
 *
 * <ul>
 * <li>Ask - the user will be prompted before app sends premium SMS.
 * <li>Never allow - app can never send premium SMS.
 * <li>Always allow - app can automatically send premium SMS.
 * </ul>
 */
public class PremiumSmsAccessPreferenceController extends PreferenceController<PreferenceGroup> {

    private static final Logger LOG = new Logger(PremiumSmsAccessPreferenceController.class);

    private static final AppFilter FILTER_SMS_STATE_KNOWN = new AppFilter() {
        @Override
        public void init() {
            // No op.
        }

        @Override
        public boolean filterApp(AppEntry info) {
            return info.extraInfo != null
                    && (Integer) info.extraInfo != SmsUsageMonitor.PREMIUM_SMS_PERMISSION_UNKNOWN;
        }
    };

    private final ISms mSmsManager;

    private final Preference.OnPreferenceChangeListener mOnPreferenceChangeListener =
            new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    PremiumSmsPreference appPreference = (PremiumSmsPreference) preference;
                    AppEntry entry = appPreference.mEntry;
                    int smsState = Integer.parseInt((String) newValue);
                    if (smsState != (Integer) entry.extraInfo) {
                        try {
                            mSmsManager.setPremiumSmsPermission(entry.info.packageName, smsState);
                        } catch (RemoteException e) {
                            LOG.w("Unable to set premium sms permission for "
                                    + entry.info.packageName + " " + entry.info.uid, e);
                            return false;
                        }
                        // Update the extra info of this entry so that it reflects the new state.
                        mAppEntryListManager.forceUpdate(entry);
                        return true;
                    }
                    return false;
                }
            };

    private final AppEntryListManager.Callback mCallback = new AppEntryListManager.Callback() {
        @Override
        public void onAppEntryListChanged(List<AppEntry> entries) {
            mEntries = entries;
            refreshUi();
        }
    };

    @VisibleForTesting
    AppEntryListManager mAppEntryListManager;
    private List<AppEntry> mEntries;

    public PremiumSmsAccessPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mSmsManager = ISms.Stub.asInterface(ServiceManager.getService("isms"));
        mAppEntryListManager = new AppEntryListManager(context);
    }

    @Override
    protected Class<PreferenceGroup> getPreferenceType() {
        return PreferenceGroup.class;
    }

    @Override
    protected void onCreateInternal() {
        mAppEntryListManager.init(new AppStatePremiumSmsBridge(mSmsManager),
                () -> FILTER_SMS_STATE_KNOWN, mCallback);
    }

    @Override
    protected void onStartInternal() {
        mAppEntryListManager.start();
    }

    @Override
    protected void onStopInternal() {
        mAppEntryListManager.stop();
    }

    @Override
    protected void onDestroyInternal() {
        mAppEntryListManager.destroy();
    }

    @Override
    protected void updateState(PreferenceGroup preference) {
        if (mEntries == null) {
            // Still loading.
            return;
        }
        preference.removeAll();
        for (AppEntry entry : mEntries) {
            Preference appPreference = new PremiumSmsPreference(getContext(), entry);
            appPreference.setOnPreferenceChangeListener(mOnPreferenceChangeListener);
            preference.addPreference(appPreference);
        }
    }

    private static class PremiumSmsPreference extends ListPreference {

        private final AppEntry mEntry;

        PremiumSmsPreference(Context context, AppEntry entry) {
            super(context);
            String key = entry.info.packageName + "|" + entry.info.uid;
            setKey(key);
            setTitle(entry.label);
            setIcon(entry.icon);
            setPersistent(false);
            setEntries(R.array.premium_sms_access_values);
            setEntryValues(new CharSequence[]{
                    String.valueOf(SmsUsageMonitor.PREMIUM_SMS_PERMISSION_ASK_USER),
                    String.valueOf(SmsUsageMonitor.PREMIUM_SMS_PERMISSION_NEVER_ALLOW),
                    String.valueOf(SmsUsageMonitor.PREMIUM_SMS_PERMISSION_ALWAYS_ALLOW)
            });
            setValue(String.valueOf(entry.extraInfo));
            setSummary("%s");
            mEntry = entry;
        }
    }
}
