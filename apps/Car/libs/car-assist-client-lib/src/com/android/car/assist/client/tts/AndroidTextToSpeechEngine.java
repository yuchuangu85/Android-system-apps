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
 * Implementation of {@link TextToSpeechEngine} by delegating to Android's {@link TextToSpeech} API.
 * <p>
 * NOTE: {@link #initialize(Context, TextToSpeech.OnInitListener)} must be called to use this
 * engine. After {@link #shutdown()}, {@link #initialize(Context, TextToSpeech.OnInitListener)} may
 * be called again to re-use it.
 */
class AndroidTextToSpeechEngine implements TextToSpeechEngine {
    private TextToSpeech mTextToSpeech;

    @Override
    public void initialize(Context context, TextToSpeech.OnInitListener initListener) {
        if (mTextToSpeech == null) {
            mTextToSpeech = new TextToSpeech(context, initListener);
        }
    }

    @Override
    public boolean isInitialized() {
        return mTextToSpeech != null;
    }

    @Override
    public void setOnUtteranceProgressListener(UtteranceProgressListener progressListener)
            throws IllegalStateException {
        assertInit();
        mTextToSpeech.setOnUtteranceProgressListener(progressListener);
    }

    @Override
    public void setAudioAttributes(AudioAttributes audioAttributes) {
        assertInit();
        mTextToSpeech.setAudioAttributes(audioAttributes);
    }

    @Override
    public int speak(CharSequence text, int queueMode, Bundle params, String utteranceId)
            throws IllegalStateException {
        assertInit();
        return mTextToSpeech.speak(text, queueMode, params, utteranceId);
    }

    @Override
    public void stop() throws IllegalStateException {
        assertInit();
        mTextToSpeech.stop();
    }

    @Override
    public boolean isSpeaking() {
        return mTextToSpeech != null && mTextToSpeech.isSpeaking();
    }

    @Override
    public void shutdown() throws IllegalStateException {
        assertInit();
        mTextToSpeech.shutdown();
        mTextToSpeech = null;
    }

    @Override
    public int getStream() {
        return TextToSpeech.Engine.DEFAULT_STREAM;
    }

    /**
     * Asserts that the TTS engine has been initialized.
     *
     * @throws IllegalStateException if the TTS has not been initialized.
     */
    private void assertInit() throws IllegalStateException {
        if (!isInitialized()) {
            throw new IllegalStateException("TTS Engine must be initialized before use.");
        }
    }
}
