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

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.annotation.Nullable;

import com.android.car.settings.applications.assist.VoiceInputInfoProvider.VoiceInteractionInfo;
import com.android.car.settings.applications.assist.VoiceInputInfoProvider.VoiceRecognitionInfo;
import com.android.settingslib.applications.DefaultAppInfo;

/** An extension of {@link DefaultAppInfo} to help represent voice input services. */
public class DefaultVoiceInputServiceInfo extends DefaultAppInfo {

    private VoiceInputInfoProvider.VoiceInputInfo mInfo;

    /**
     * Constructs a {@link DefaultVoiceInputServiceInfo}
     *
     * @param info    a {@link VoiceInteractionInfo} or {@link VoiceRecognitionInfo} that describes
     *                the Voice Input Service.
     * @param enabled determines whether the service should be selectable or not.
     */
    public DefaultVoiceInputServiceInfo(Context context, PackageManager pm, int userId,
            VoiceInputInfoProvider.VoiceInputInfo info, boolean enabled) {
        super(context, pm, userId, info.getComponentName(), /* summary= */ null, enabled);
        mInfo = info;
    }

    @Override
    public CharSequence loadLabel() {
        return mInfo.getLabel();
    }

    /** Gets the intent to open the related settings component if it exists. */
    @Nullable
    public Intent getSettingIntent() {
        if (mInfo.getSettingsActivityComponentName() == null) {
            return null;
        }
        return new Intent(Intent.ACTION_MAIN).setComponent(
                mInfo.getSettingsActivityComponentName());
    }
}
