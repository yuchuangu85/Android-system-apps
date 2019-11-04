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

package com.android.car.settings.applications.defaultapps;

import android.car.drivingstate.CarUxRestrictions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.provider.Settings;
import android.service.autofill.AutofillService;
import android.service.autofill.AutofillServiceInfo;
import android.text.TextUtils;
import android.view.autofill.AutofillManager;

import androidx.annotation.Nullable;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.settingslib.applications.DefaultAppInfo;

import java.util.List;

/** Business logic for displaying the currently selected autofill app. */
public class DefaultAutofillPickerEntryPreferenceController extends
        DefaultAppsPickerEntryBasePreferenceController {

    private static final Logger LOG = new Logger(
            DefaultAutofillPickerEntryPreferenceController.class);
    private final AutofillManager mAutofillManager;

    public DefaultAutofillPickerEntryPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mAutofillManager = context.getSystemService(AutofillManager.class);
    }

    @Override
    protected int getAvailabilityStatus() {
        if (mAutofillManager != null && mAutofillManager.isAutofillSupported()) {
            return AVAILABLE;
        }
        return UNSUPPORTED_ON_DEVICE;
    }

    @Nullable
    @Override
    protected DefaultAppInfo getCurrentDefaultAppInfo() {
        String flattenComponent = Settings.Secure.getString(getContext().getContentResolver(),
                Settings.Secure.AUTOFILL_SERVICE);
        if (!TextUtils.isEmpty(flattenComponent)) {
            DefaultAppInfo appInfo = new DefaultAppInfo(getContext(),
                    getContext().getPackageManager(), getCurrentProcessUserId(),
                    ComponentName.unflattenFromString(flattenComponent));
            return appInfo;
        }
        return null;
    }

    @Nullable
    @Override
    protected Intent getSettingIntent(@Nullable DefaultAppInfo info) {
        if (info == null) {
            return null;
        }

        Intent intent = new Intent(AutofillService.SERVICE_INTERFACE);
        List<ResolveInfo> resolveInfos = getContext().getPackageManager().queryIntentServices(
                intent, PackageManager.GET_META_DATA);

        for (ResolveInfo resolveInfo : resolveInfos) {
            ServiceInfo serviceInfo = resolveInfo.serviceInfo;
            String flattenKey = new ComponentName(serviceInfo.packageName,
                    serviceInfo.name).flattenToString();
            if (TextUtils.equals(info.getKey(), flattenKey)) {
                String settingsActivity;
                try {
                    settingsActivity = new AutofillServiceInfo(getContext(), serviceInfo)
                            .getSettingsActivity();
                } catch (SecurityException e) {
                    // Service does not declare the proper permission, ignore it.
                    LOG.w("Error getting info for " + serviceInfo + ": " + e);
                    continue;
                }
                if (TextUtils.isEmpty(settingsActivity)) {
                    continue;
                }
                return new Intent(Intent.ACTION_MAIN).setComponent(
                        new ComponentName(serviceInfo.packageName, settingsActivity));
            }
        }

        return null;
    }
}
