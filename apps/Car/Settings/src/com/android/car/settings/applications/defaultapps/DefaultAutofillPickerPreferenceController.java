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

import android.Manifest;
import android.car.drivingstate.CarUxRestrictions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.service.autofill.AutofillService;
import android.text.Html;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.settingslib.applications.DefaultAppInfo;

import java.util.ArrayList;
import java.util.List;

/** Business logic for displaying and choosing the default autofill service. */
public class DefaultAutofillPickerPreferenceController extends
        DefaultAppsPickerBasePreferenceController {

    public DefaultAutofillPickerPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @NonNull
    @Override
    protected List<DefaultAppInfo> getCandidates() {
        List<DefaultAppInfo> candidates = new ArrayList<>();
        List<ResolveInfo> resolveInfos = getContext().getPackageManager().queryIntentServices(
                new Intent(AutofillService.SERVICE_INTERFACE), PackageManager.GET_META_DATA);
        for (ResolveInfo info : resolveInfos) {
            String permission = info.serviceInfo.permission;
            if (Manifest.permission.BIND_AUTOFILL_SERVICE.equals(permission)) {
                candidates.add(new DefaultAppInfo(getContext(), getContext().getPackageManager(),
                        getCurrentProcessUserId(),
                        new ComponentName(info.serviceInfo.packageName, info.serviceInfo.name)));
            }
        }
        return candidates;
    }

    @Override
    protected String getCurrentDefaultKey() {
        String setting = Settings.Secure.getString(getContext().getContentResolver(),
                Settings.Secure.AUTOFILL_SERVICE);
        if (setting != null) {
            ComponentName componentName = ComponentName.unflattenFromString(setting);
            if (componentName != null) {
                return componentName.flattenToString();
            }
        }
        return DefaultAppsPickerBasePreferenceController.NONE_PREFERENCE_KEY;
    }

    @Override
    protected void setCurrentDefault(String key) {
        Settings.Secure.putString(getContext().getContentResolver(),
                Settings.Secure.AUTOFILL_SERVICE, key);
    }

    @Override
    @Nullable
    protected CharSequence getConfirmationMessage(DefaultAppInfo info) {
        if (info == null) {
            return null;
        }

        CharSequence appName = info.loadLabel();
        String message = getContext().getString(R.string.autofill_confirmation_message, appName);
        return Html.fromHtml(message, Html.FROM_HTML_MODE_LEGACY);
    }
}
