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

package com.android.car.settings.testutils;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;

import java.util.Locale;

@Implements(TextToSpeech.class)
public class ShadowTextToSpeech {

    private static TextToSpeech sInstance;
    private static TextToSpeech.OnInitListener sOnInitListener;
    private static String sEngine;

    public static void setInstance(TextToSpeech textToSpeech) {
        sInstance = textToSpeech;
    }

    /**
     * Override constructor and only store the name of the last constructed engine and init
     * listener.
     */
    public void __constructor__(Context context, TextToSpeech.OnInitListener listener,
            String engine,
            String packageName, boolean useFallback) {
        sOnInitListener = listener;
        sEngine = engine;
    }

    public void __constructor__(Context context, TextToSpeech.OnInitListener listener,
            String engine) {
        __constructor__(context, listener, engine, null, false);
    }

    public void __constructor__(Context context, TextToSpeech.OnInitListener listener) {
        __constructor__(context, listener, null, null, false);
    }

    @Implementation
    protected String getCurrentEngine() {
        return sInstance.getCurrentEngine();
    }

    @Implementation
    protected int setLanguage(final Locale loc) {
        return sInstance.setLanguage(loc);
    }

    @Implementation
    protected void shutdown() {
        sInstance.shutdown();
    }

    @Implementation
    protected int setSpeechRate(float speechRate) {
        return sInstance.setSpeechRate(speechRate);
    }

    @Implementation
    protected int setPitch(float pitch) {
        return sInstance.setPitch(pitch);
    }

    @Implementation
    protected Voice getVoice() {
        return sInstance.getVoice();
    }

    @Implementation
    protected int isLanguageAvailable(final Locale loc) {
        return sInstance.isLanguageAvailable(loc);
    }

    @Implementation
    protected int speak(final CharSequence text,
            final int queueMode,
            final Bundle params,
            final String utteranceId) {
        return sInstance.speak(text, queueMode, params, utteranceId);
    }

    @Resetter
    public static void reset() {
        sInstance = null;
        sOnInitListener = null;
        sEngine = null;
    }

    /** Check for the last constructed engine name. */
    public static String getLastConstructedEngine() {
        return sEngine;
    }

    /** Trigger the initializtion callback given the input status. */
    public static void callInitializationCallbackWithStatus(int status) {
        sOnInitListener.onInit(status);
    }
}
