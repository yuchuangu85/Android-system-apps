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

import android.app.role.RoleManager;
import android.car.drivingstate.CarUxRestrictions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.service.voice.VoiceInteractionService;
import android.service.voice.VoiceInteractionServiceInfo;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.car.settings.common.ButtonPreference;
import com.android.car.settings.common.FragmentController;
import com.android.internal.app.AssistUtils;
import com.android.settingslib.applications.DefaultAppInfo;

import java.util.List;

/**
 * Business logic to show the currently selected default assistant and also show the assistant
 * settings, if it exists.
 */
public class DefaultAssistantPickerEntryPreferenceController extends
        DefaultAppsPickerEntryBasePreferenceController {

    @VisibleForTesting
    static final Intent ASSISTANT_SERVICE = new Intent(
            VoiceInteractionService.SERVICE_INTERFACE);

    private final AssistUtils mAssistUtils;

    public DefaultAssistantPickerEntryPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mAssistUtils = new AssistUtils(context);
    }

    @Nullable
    @Override
    protected DefaultAppInfo getCurrentDefaultAppInfo() {
        ComponentName cn = mAssistUtils.getAssistComponentForUser(getCurrentProcessUserId());
        if (cn == null) {
            return null;
        }
        return new DefaultAppInfo(getContext(), getContext().getPackageManager(),
                getCurrentProcessUserId(), cn);
    }

    @Override
    protected boolean handlePreferenceClicked(ButtonPreference preference) {
        String packageName = getContext().getPackageManager().getPermissionControllerPackageName();
        if (packageName != null) {
            Intent intent = new Intent(Intent.ACTION_MANAGE_DEFAULT_APP)
                    .setPackage(packageName)
                    .putExtra(Intent.EXTRA_ROLE_NAME, RoleManager.ROLE_ASSISTANT);
            getContext().startActivity(intent);
        }
        return true;
    }

    @Nullable
    @Override
    protected Intent getSettingIntent(@Nullable DefaultAppInfo info) {
        ComponentName cn = mAssistUtils.getAssistComponentForUser(getCurrentProcessUserId());
        if (cn == null) {
            return null;
        }

        Intent probe = ASSISTANT_SERVICE.setPackage(cn.getPackageName());
        PackageManager pm = getContext().getPackageManager();
        List<ResolveInfo> services = pm.queryIntentServices(probe, PackageManager.GET_META_DATA);
        if (services == null || services.isEmpty()) {
            return null;
        }

        String activity = getAssistSettingsActivity(pm, services.get(0));
        if (activity == null) {
            return null;
        }

        return new Intent(Intent.ACTION_MAIN).setComponent(
                new ComponentName(cn.getPackageName(), activity));
    }

    private String getAssistSettingsActivity(PackageManager pm, ResolveInfo resolveInfo) {
        VoiceInteractionServiceInfo voiceInfo = new VoiceInteractionServiceInfo(pm,
                resolveInfo.serviceInfo);
        if (!voiceInfo.getSupportsAssist()) {
            return null;
        }
        return voiceInfo.getSettingsActivity();
    }
}
