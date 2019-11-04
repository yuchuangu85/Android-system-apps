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

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

/** Utilities to help interact with voice input services. */
final class VoiceInputUtils {

    private VoiceInputUtils() {
    }

    /**
     * Chooses the current service based on the current voice interaction service and current
     * recognizer service.
     */
    static ComponentName getCurrentService(Context context) {
        ComponentName currentVoiceInteraction = getComponentNameOrNull(context,
                Settings.Secure.VOICE_INTERACTION_SERVICE);
        ComponentName currentVoiceRecognizer = getComponentNameOrNull(context,
                Settings.Secure.VOICE_RECOGNITION_SERVICE);

        if (currentVoiceInteraction != null) {
            return currentVoiceInteraction;
        } else if (currentVoiceRecognizer != null) {
            return currentVoiceRecognizer;
        } else {
            return null;
        }
    }

    private static ComponentName getComponentNameOrNull(Context context, String secureSettingKey) {
        String currentSetting = Settings.Secure.getString(context.getContentResolver(),
                secureSettingKey);
        if (!TextUtils.isEmpty(currentSetting)) {
            return ComponentName.unflattenFromString(currentSetting);
        }
        return null;
    }
}
