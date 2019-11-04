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

package com.android.car.settings.applications.assist;

import android.car.drivingstate.CarUxRestrictions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.annotation.Nullable;

import com.android.car.settings.applications.defaultapps.DefaultAppsPickerEntryBasePreferenceController;
import com.android.car.settings.common.FragmentController;
import com.android.internal.app.AssistUtils;
import com.android.settingslib.applications.DefaultAppInfo;

import java.util.Objects;

/**
 * Business logic to show the currently selected default voice input service and also link to the
 * service settings, if it exists.
 */
public class DefaultVoiceInputPickerEntryPreferenceController extends
        DefaultAppsPickerEntryBasePreferenceController {

    private static final Uri ASSIST_URI = Settings.Secure.getUriFor(Settings.Secure.ASSISTANT);

    private final ContentObserver mSettingObserver = new ContentObserver(
            new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);

            if (ASSIST_URI.equals(uri)) {
                refreshUi();
            }
        }
    };

    private final VoiceInputInfoProvider mVoiceInputInfoProvider;
    private final AssistUtils mAssistUtils;

    public DefaultVoiceInputPickerEntryPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mVoiceInputInfoProvider = new VoiceInputInfoProvider(context);
        mAssistUtils = new AssistUtils(context);
    }

    @Override
    protected int getAvailabilityStatus() {
        ComponentName currentVoiceService = VoiceInputUtils.getCurrentService(getContext());
        ComponentName currentAssist = mAssistUtils.getAssistComponentForUser(
                getCurrentProcessUserId());

        return Objects.equals(currentAssist, currentVoiceService) ? CONDITIONALLY_UNAVAILABLE
                : AVAILABLE;
    }

    @Override
    protected void onStartInternal() {
        getContext().getContentResolver().registerContentObserver(ASSIST_URI,
                /* notifyForDescendants= */ false, mSettingObserver);
    }

    @Override
    protected void onStopInternal() {
        getContext().getContentResolver().unregisterContentObserver(mSettingObserver);
    }

    @Nullable
    @Override
    protected DefaultAppInfo getCurrentDefaultAppInfo() {
        VoiceInputInfoProvider.VoiceInputInfo info = mVoiceInputInfoProvider.getInfoForComponent(
                VoiceInputUtils.getCurrentService(getContext()));
        return (info == null) ? null : new DefaultVoiceInputServiceInfo(getContext(),
                getContext().getPackageManager(), getCurrentProcessUserId(), info,
                /* enabled= */ true);
    }

    @Nullable
    @Override
    protected Intent getSettingIntent(@Nullable DefaultAppInfo info) {
        if (info instanceof DefaultVoiceInputServiceInfo) {
            return ((DefaultVoiceInputServiceInfo) info).getSettingIntent();
        }
        return null;
    }
}
