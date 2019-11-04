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

package com.android.car.settings.system;

import static android.content.Context.CARRIER_CONFIG_SERVICE;

import android.car.drivingstate.CarUxRestrictions;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.text.TextUtils;

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;

import java.util.List;

/**
 * Controller which determines if the system update preference should be displayed based on
 * device and user status. When the preference is clicked, this controller broadcasts a client
 * initiated action if an intent is available in carrier-specific telephony configuration.
 *
 * @see CarrierConfigManager#KEY_CI_ACTION_ON_SYS_UPDATE_BOOL
 */
public class SystemUpdatePreferenceController extends PreferenceController<Preference> {

    private static final Logger LOG = new Logger(SystemUpdatePreferenceController.class);

    private final CarUserManagerHelper mCarUserManagerHelper;
    private boolean mActivityFound;

    public SystemUpdatePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mCarUserManagerHelper = new CarUserManagerHelper(context);
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    protected int getAvailabilityStatus() {
        if (!getContext().getResources().getBoolean(R.bool.config_show_system_update_settings)) {
            return UNSUPPORTED_ON_DEVICE;
        }
        return mCarUserManagerHelper.isCurrentProcessAdminUser() ? AVAILABLE : DISABLED_FOR_USER;
    }

    @Override
    protected void onCreateInternal() {
        Preference preference = getPreference();
        Intent intent = preference.getIntent();
        if (intent != null) {
            // Find the activity that is in the system image.
            PackageManager pm = getContext().getPackageManager();
            List<ResolveInfo> list = pm.queryIntentActivities(intent, 0);
            int listSize = list.size();
            for (int i = 0; i < listSize; i++) {
                ResolveInfo resolveInfo = list.get(i);
                if ((resolveInfo.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM)
                        != 0) {
                    // Replace the intent with this specific activity.
                    preference.setIntent(
                            new Intent().setClassName(resolveInfo.activityInfo.packageName,
                                    resolveInfo.activityInfo.name));
                    // Set the preference title to the activity's label.
                    preference.setTitle(resolveInfo.loadLabel(pm));
                    mActivityFound = true;
                }
            }
        }
    }

    @Override
    protected void updateState(Preference preference) {
        preference.setVisible(mActivityFound);
    }

    @Override
    protected boolean handlePreferenceClicked(Preference preference) {
        CarrierConfigManager configManager = (CarrierConfigManager) getContext().getSystemService(
                CARRIER_CONFIG_SERVICE);
        PersistableBundle b = configManager.getConfig();
        if (b != null && b.getBoolean(CarrierConfigManager.KEY_CI_ACTION_ON_SYS_UPDATE_BOOL)) {
            ciActionOnSysUpdate(b);
        }
        // Don't handle so that preference framework will launch the preference intent.
        return false;
    }

    /** Trigger client initiated action (send intent) on system update. */
    private void ciActionOnSysUpdate(PersistableBundle b) {
        String intentStr = b.getString(
                CarrierConfigManager.KEY_CI_ACTION_ON_SYS_UPDATE_INTENT_STRING);
        if (!TextUtils.isEmpty(intentStr)) {
            String extra = b.getString(
                    CarrierConfigManager.KEY_CI_ACTION_ON_SYS_UPDATE_EXTRA_STRING);
            String extraVal = b.getString(
                    CarrierConfigManager.KEY_CI_ACTION_ON_SYS_UPDATE_EXTRA_VAL_STRING);

            Intent intent = new Intent(intentStr);
            if (!TextUtils.isEmpty(extra)) {
                intent.putExtra(extra, extraVal);
            }
            LOG.d("ciActionOnSysUpdate: broadcasting intent " + intentStr + " with extra " + extra
                    + ", " + extraVal);
            getContext().getApplicationContext().sendBroadcast(intent);
        }
    }
}
