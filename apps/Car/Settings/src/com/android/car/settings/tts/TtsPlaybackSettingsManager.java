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

import android.app.AlertDialog;
import android.content.Context;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TtsEngines;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.settings.R;
import com.android.car.settings.common.Logger;

import java.util.Locale;

/** Handles interactions with TTS playback settings. */
class TtsPlaybackSettingsManager {

    private static final Logger LOG = new Logger(TtsPlaybackSettingsManager.class);

    /**
     * Maximum speech rate value.
     */
    public static final int MAX_SPEECH_RATE = 600;

    /**
     * Minimum speech rate value.
     */
    public static final int MIN_SPEECH_RATE = 10;

    /**
     * Maximum voice pitch value.
     */
    public static final int MAX_VOICE_PITCH = 400;

    /**
     * Minimum voice pitch value.
     */
    public static final int MIN_VOICE_PITCH = 25;

    /**
     * Scaling factor used to convert speech rate and pitch values between {@link Settings.Secure}
     * and {@link TextToSpeech}.
     */
    public static final float SCALING_FACTOR = 100.0f;
    private static final String UTTERANCE_ID = "Sample";

    private final Context mContext;
    private final TextToSpeech mTts;
    private final TtsEngines mEnginesHelper;

    TtsPlaybackSettingsManager(Context context, @NonNull TextToSpeech tts,
            @NonNull TtsEngines enginesHelper) {
        mContext = context;
        mTts = tts;
        mEnginesHelper = enginesHelper;
    }

    void updateSpeechRate(int speechRate) {
        Settings.Secure.putInt(
                mContext.getContentResolver(), Settings.Secure.TTS_DEFAULT_RATE, speechRate);
        mTts.setSpeechRate(speechRate / SCALING_FACTOR);
        LOG.d("TTS default rate changed, now " + speechRate);
    }

    int getCurrentSpeechRate() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.TTS_DEFAULT_RATE, TextToSpeech.Engine.DEFAULT_RATE);
    }

    void resetSpeechRate() {
        updateSpeechRate(TextToSpeech.Engine.DEFAULT_RATE);
    }

    void updateVoicePitch(int pitch) {
        Settings.Secure.putInt(mContext.getContentResolver(), Settings.Secure.TTS_DEFAULT_PITCH,
                pitch);
        mTts.setPitch(pitch / SCALING_FACTOR);
        LOG.d("TTS default pitch changed, now " + pitch);
    }

    int getCurrentVoicePitch() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.TTS_DEFAULT_PITCH, TextToSpeech.Engine.DEFAULT_PITCH);
    }

    void resetVoicePitch() {
        updateVoicePitch(TextToSpeech.Engine.DEFAULT_PITCH);
    }

    /**
     * Returns the currently stored locale for the given tts engine. It can return {@code null}, if
     * it is configured to use the system default locale.
     */
    @Nullable
    Locale getStoredTtsLocale() {
        Locale currentLocale = null;
        if (!mEnginesHelper.isLocaleSetToDefaultForEngine(mTts.getCurrentEngine())) {
            currentLocale = mEnginesHelper.getLocalePrefForEngine(mTts.getCurrentEngine());
        }
        return currentLocale;
    }

    /**
     * Similar to {@link #getStoredTtsLocale()}, but returns the language of the voice registered
     * to the actual TTS object. It is possible for the TTS voice to be {@code null} if TTS is not
     * yet initialized.
     */
    @Nullable
    Locale getEffectiveTtsLocale() {
        if (mTts.getVoice() == null) {
            return null;
        }
        return mEnginesHelper.parseLocaleString(mTts.getVoice().getLocale().toString());
    }

    /**
     * Attempts to update the default tts locale. Returns {@code true} if successful, false
     * otherwise.
     */
    boolean updateTtsLocale(Locale newLocale) {
        int resultCode = mTts.setLanguage((newLocale != null) ? newLocale : Locale.getDefault());
        boolean success = resultCode != TextToSpeech.LANG_NOT_SUPPORTED
                && resultCode != TextToSpeech.LANG_MISSING_DATA;
        if (success) {
            mEnginesHelper.updateLocalePrefForEngine(mTts.getCurrentEngine(), newLocale);
        }

        return success;
    }

    void speakSampleText(String text) {
        boolean networkRequired = mTts.getVoice().isNetworkConnectionRequired();
        Locale defaultLocale = getEffectiveTtsLocale();
        if (!networkRequired || networkRequired && mTts.isLanguageAvailable(defaultLocale)
                >= TextToSpeech.LANG_AVAILABLE) {
            mTts.speak(text, TextToSpeech.QUEUE_FLUSH, /* params= */ null, UTTERANCE_ID);
        } else {
            displayNetworkAlert();
        }
    }

    private void displayNetworkAlert() {
        AlertDialog dialog = new AlertDialog.Builder(mContext)
                .setTitle(android.R.string.dialog_alert_title)
                .setMessage(R.string.tts_engine_network_required)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, null).create();
        dialog.show();
    }
}
