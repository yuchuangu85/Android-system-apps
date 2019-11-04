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

package com.android.car.settings.tts;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import androidx.preference.Preference;

/** Preference which also encapsulates the associated Text-to-speech engine. */
public class TtsPreference extends Preference {

    private TextToSpeech.EngineInfo mEngineInfo;

    public TtsPreference(Context context, TextToSpeech.EngineInfo engineInfo) {
        super(context);
        mEngineInfo = engineInfo;
    }

    /** Gets the engine info associated with this preference. */
    public TextToSpeech.EngineInfo getEngineInfo() {
        return mEngineInfo;
    }
}
