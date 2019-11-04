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
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.android.car.settings.applications.defaultapps.DefaultAppsPickerBasePreferenceController;
import com.android.car.settings.common.FragmentController;
import com.android.internal.app.AssistUtils;
import com.android.settingslib.applications.DefaultAppInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Business logic for displaying and choosing the default voice input service. */
public class DefaultVoiceInputPickerPreferenceController extends
        DefaultAppsPickerBasePreferenceController {

    private final AssistUtils mAssistUtils;
    private final VoiceInputInfoProvider mVoiceInputInfoProvider;

    // Current assistant component name, used to restrict available voice inputs.
    private String mAssistComponentName = null;

    public DefaultVoiceInputPickerPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mAssistUtils = new AssistUtils(context);
        mVoiceInputInfoProvider = new VoiceInputInfoProvider(context);
        if (Objects.equals(mAssistUtils.getAssistComponentForUser(getCurrentProcessUserId()),
                VoiceInputUtils.getCurrentService(getContext()))) {
            ComponentName cn = mAssistUtils.getAssistComponentForUser(getCurrentProcessUserId());
            if (cn != null) {
                mAssistComponentName = cn.flattenToString();
            }
        }
    }

    @NonNull
    @Override
    protected List<DefaultAppInfo> getCandidates() {
        List<DefaultAppInfo> candidates = new ArrayList<>();
        for (VoiceInputInfoProvider.VoiceInteractionInfo info :
                mVoiceInputInfoProvider.getVoiceInteractionInfoList()) {
            boolean enabled = TextUtils.equals(info.getComponentName().flattenToString(),
                    mAssistComponentName);
            candidates.add(
                    new DefaultVoiceInputServiceInfo(getContext(), getContext().getPackageManager(),
                            getCurrentProcessUserId(), info, enabled));
        }

        for (VoiceInputInfoProvider.VoiceRecognitionInfo info :
                mVoiceInputInfoProvider.getVoiceRecognitionInfoList()) {
            candidates.add(
                    new DefaultVoiceInputServiceInfo(getContext(), getContext().getPackageManager(),
                            getCurrentProcessUserId(), info, /* enabled= */ true));
        }
        return candidates;
    }

    @Override
    protected String getCurrentDefaultKey() {
        ComponentName cn = VoiceInputUtils.getCurrentService(getContext());
        if (cn == null) {
            return null;
        }
        return cn.flattenToString();
    }

    @Override
    protected void setCurrentDefault(String key) {
        ComponentName cn = ComponentName.unflattenFromString(key);
        VoiceInputInfoProvider.VoiceInputInfo info = mVoiceInputInfoProvider.getInfoForComponent(
                cn);

        if (info instanceof VoiceInputInfoProvider.VoiceInteractionInfo) {
            VoiceInputInfoProvider.VoiceInteractionInfo interactionInfo =
                    (VoiceInputInfoProvider.VoiceInteractionInfo) info;
            Settings.Secure.putString(getContext().getContentResolver(),
                    Settings.Secure.VOICE_INTERACTION_SERVICE, key);
            Settings.Secure.putString(getContext().getContentResolver(),
                    Settings.Secure.VOICE_RECOGNITION_SERVICE,
                    new ComponentName(interactionInfo.getPackageName(),
                            interactionInfo.getRecognitionService())
                            .flattenToString());
        } else if (info instanceof VoiceInputInfoProvider.VoiceRecognitionInfo) {
            Settings.Secure.putString(getContext().getContentResolver(),
                    Settings.Secure.VOICE_INTERACTION_SERVICE, "");
            Settings.Secure.putString(getContext().getContentResolver(),
                    Settings.Secure.VOICE_RECOGNITION_SERVICE, key);
        }
    }

    @Override
    protected boolean includeNonePreference() {
        return false;
    }
}
