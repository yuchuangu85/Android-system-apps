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

package com.android.car.assist.client.tts;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

/**
 * Interface for TTS Engine that closely matches {@link TextToSpeech}; facilitates mocking/faking.
 */
public interface TextToSpeechEngine {
    /**
     * Initializes the TTS engine.
     *
     * @param context the context to use
     * @param initListener listener to monitor initialization result
     */
    void initialize(Context context, TextToSpeech.OnInitListener initListener);

    /**
     * Returns true if the engine is initialized.
     */
    boolean isInitialized();

    /**
     * Sets the UtteranceProgressListener.
     *
     * @see TextToSpeech#setOnUtteranceProgressListener(UtteranceProgressListener)
     */
    void setOnUtteranceProgressListener(UtteranceProgressListener progressListener);

    /**
     * Sets the audio attributes to be used when speaking text or playing
     * back a file.
     *
     * @see TextToSpeech#setAudioAttributes(AudioAttributes)
     */
    void setAudioAttributes(AudioAttributes audioAttributes);

    /**
     * Speaks out the provided text.
     *
     * @see TextToSpeech#speak(CharSequence, int, Bundle, String)
     */
    int speak(CharSequence text, int queueMode, Bundle params, String utteranceId);

    /**
     * Stops play-out.
     *
     * @see TextToSpeech#stop()
     */
    void stop();

    /**
     * Returns true if the TTS engine is currently playing out.
     */
    boolean isSpeaking();

    /**
     * Shuts down the engine and releases resources.
     * {@link #initialize(Context, TextToSpeech.OnInitListener)} will need to be called again before
     * using this engine.
     */
    void shutdown();

    /**
     * Returns the stream used by this TTS engine.
     * <p/>
     * The streams are defined in {@link android.media.AudioManager}.
     */
    int getStream();
}
