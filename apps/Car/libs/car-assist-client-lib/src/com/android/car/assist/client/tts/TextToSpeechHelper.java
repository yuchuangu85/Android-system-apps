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
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.VisibleForTesting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Component that wraps platform TTS engine and supports play-out of batches of text.
 * <p>
 * It takes care of setting up TTS Engine when text is played out and shutting it down after an idle
 * period with no play-out. This is desirable since the owning app is long-lived and the TTS Engine
 * brings up another service-process.
 * <p>
 * As batches of text are played-out, they issue callbacks on the {@link Listener} provided with the
 * batch.
 */
public class TextToSpeechHelper {
    /**
     * Listener interface used by clients to be notified as batch of text is played out.
     */
    public interface Listener {
        /**
         * Called when play-out starts for batch. May never get called if batch has errors or
         * interruptions.
         */
        void onTextToSpeechStarted(long requestId);

        /**
         * Called when play-out ends for batch.
         *
         * @param error Whether play-out ended due to an error or not. Note: if it was aborted, it's
         *              not considered an error.
         */
        void onTextToSpeechStopped(long requestId, boolean error);
    }

    private static final String TAG = "CM#TextToSpeechHelper";

    private static final String UTTERANCE_ID_SEPARATOR = ";";
    private static final long DEFAULT_SHUTDOWN_DELAY_MILLIS = TimeUnit.MINUTES.toMillis(1);

    private final Map<String, BatchListener> mListeners = new HashMap<>();
    private final Handler mHandler = new Handler();
    private final Context mContext;
    private final TextToSpeechHelper.Listener mListener;
    private final AudioManager.OnAudioFocusChangeListener mNoOpListener = (f) -> { /* NO-OP */ };
    private final AudioManager mAudioManager;
    private final AudioAttributes mAudioAttributes;
    private final AudioFocusRequest mAudioFocusRequest;
    private final long mShutdownDelayMillis;
    private TextToSpeechEngine mTextToSpeechEngine;
    private int mInitStatus;
    private SpeechRequest mPendingRequest;
    private String mCurrentBatchId;

    private final Runnable mMaybeShutdownRunnable = new Runnable() {
        @Override
        public void run() {
            if (mListeners.isEmpty() || mPendingRequest == null) {
                shutdownEngine();
            } else {
                mHandler.postDelayed(this, mShutdownDelayMillis);
            }
        }
    };

    public TextToSpeechHelper(Context context, TextToSpeechHelper.Listener listener) {
        this(context, new AndroidTextToSpeechEngine(), DEFAULT_SHUTDOWN_DELAY_MILLIS, listener);
    }

    @VisibleForTesting
    TextToSpeechHelper(Context context, TextToSpeechEngine ttsEngine, long shutdownDelayMillis,
            TextToSpeechHelper.Listener listener) {
        mContext = context;
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mTextToSpeechEngine = ttsEngine;
        mShutdownDelayMillis = shutdownDelayMillis;
        // OnInitListener will only set to SUCCESS/ERROR. So we initialize to STOPPED.
        mInitStatus = TextToSpeech.STOPPED;
        mListener = listener;
        mAudioAttributes =  new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .build();
        mAudioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(mAudioAttributes)
                .setOnAudioFocusChangeListener(mNoOpListener)
                .build();
    }

    private void maybeInitAndKeepAlive() {
        if (!mTextToSpeechEngine.isInitialized()) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Initializing TTS Engine");
            }
            mTextToSpeechEngine.initialize(mContext, this::handleInitCompleted);
            mTextToSpeechEngine.setOnUtteranceProgressListener(mProgressListener);
            mTextToSpeechEngine.setAudioAttributes(mAudioAttributes);
        }
        // Since we're handling a request, delay engine shutdown.
        mHandler.removeCallbacks(mMaybeShutdownRunnable);
        mHandler.postDelayed(mMaybeShutdownRunnable, mShutdownDelayMillis);
    }

    private void handleInitCompleted(int initStatus) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, String.format("Init completed. Status: %d", initStatus));
        }
        mInitStatus = initStatus;
        if (mPendingRequest != null) {
            playInternal(mPendingRequest.mTextToSpeak, mPendingRequest.mRequestId);
            mPendingRequest = null;
        }
    }

    /**
     * Plays out given batch of text. If engine is not active, it is setup and the request is stored
     * until then. Only one batch is supported at a time; If a previous batch is waiting engine
     * setup, that batch is dropped. If a previous batch is playing, the play-out is stopped and
     * next one is passed to the TTS Engine. Callbacks are issued on the provided {@code listener}.
     * Will request audio focus first, failure will trigger onAudioFocusFailed in listener.
     * <p/>
     * NOTE: Underlying engine may have limit on length of text in each element of the batch; it
     * will reject anything longer. See {@link TextToSpeech#getMaxSpeechInputLength()}.
     *
     * @param textToSpeak Batch of text to play-out.
     * @param requestId The tracking request id
     * @return true if the request to play was successful
     */
    public boolean requestPlay(List<CharSequence> textToSpeak, long requestId) {
        if (textToSpeak.isEmpty()) {
            /* no-op */
            return true;
        }
        int result = mAudioManager.requestAudioFocus(mAudioFocusRequest);
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return false;
        }
        maybeInitAndKeepAlive();

        // Check if its still initializing.
        if (mInitStatus == TextToSpeech.STOPPED) {
            // Squash any already queued request.
            if (mPendingRequest != null) {
                onTtsStopped(requestId, /* error= */ false);
            }
            mPendingRequest = new SpeechRequest(textToSpeak, requestId);
        } else {
            playInternal(textToSpeak, requestId);
        }
        return true;
    }

    /** Requests that all play-out be stopped. */
    public void requestStop() {
        mTextToSpeechEngine.stop();
        mCurrentBatchId = null;
    }

    public boolean isSpeaking() {
        return mTextToSpeechEngine.isSpeaking();
    }

    // wrap call back to listener.onTextToSpeechStopped with adandonAudioFocus.
    private void onTtsStopped(long requestId, boolean error) {
        mAudioManager.abandonAudioFocusRequest(mAudioFocusRequest);
        mHandler.post(() -> mListener.onTextToSpeechStopped(requestId, error));
    }

    private void playInternal(List<CharSequence> textToSpeak, long requestId) {
        if (mInitStatus == TextToSpeech.ERROR) {
            Log.e(TAG, "TTS setup failed!");
            onTtsStopped(requestId, /* error= */ true);
            return;
        }

        // Abort anything currently playing and flushes queue.
        mTextToSpeechEngine.stop();

        // Queue up new batch. We assign id's = "batchId;index" where index increments from 0
        // to batchSize - 1. If queueing fails, we abort the whole batch.
        mCurrentBatchId = Long.toString(requestId);
        for (int i = 0; i < textToSpeak.size(); i++) {
            CharSequence text = textToSpeak.get(i);
            String utteranceId =
                    String.format("%s%s%d", mCurrentBatchId, UTTERANCE_ID_SEPARATOR, i);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, String.format("Queueing tts: '%s' [%s]", text, utteranceId));
            }
            if (mTextToSpeechEngine.speak(text, TextToSpeech.QUEUE_ADD, /* params= */ null,
                    utteranceId) != TextToSpeech.SUCCESS) {
                mTextToSpeechEngine.stop();
                mCurrentBatchId = null;
                Log.e(TAG, "Queuing text failed!");
                onTtsStopped(requestId, /* error= */ true);
                return;
            }
        }
        // Register BatchListener for entire batch. Will invoke callbacks on Listener as batch
        // progresses.
        mListeners.put(mCurrentBatchId, new BatchListener(requestId, textToSpeak.size()));
    }

    /**
     * Releases resources and shuts down TTS Engine.
     */
    public void cleanup() {
        mHandler.removeCallbacksAndMessages(/* token= */ null);
        shutdownEngine();
    }

    /** Returns the stream used by the TTS engine. */
    public int getStream() {
        return mTextToSpeechEngine.getStream();
    }

    private void shutdownEngine() {
        if (mTextToSpeechEngine.isInitialized()) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Shutting down TTS Engine");
            }
            mTextToSpeechEngine.stop();
            mTextToSpeechEngine.shutdown();
            mInitStatus = TextToSpeech.STOPPED;
        }
    }

    private static Pair<String, Integer> parse(String utteranceId) {
        try {
            String[] pair = utteranceId.split(UTTERANCE_ID_SEPARATOR);
            String batchId = pair[0];
            int index = Integer.valueOf(pair[1]);
            return Pair.create(batchId, index);
        } catch (IndexOutOfBoundsException | NumberFormatException e) {
            throw new IllegalArgumentException(
                    String.format("Utterance ID is invalid: %s.", utteranceId)
            );
        }
    }

    // Handles all callbacks from TextToSpeechEngine. Possible order of callbacks:
    // - onStart, onDone: successful play-out.
    // - onStart, onStop: play-out starts, but interrupted.
    // - onStart, onError: play-out starts and fails.
    // - onStop: play-out never starts, but aborted.
    // - onError: play-out never starts, but fails.
    // Since the callbacks arrive on other threads, they are dispatched onto mHandler where the
    // appropriate BatchListener is invoked.
    private final UtteranceProgressListener mProgressListener = new UtteranceProgressListener() {
        private void safeInvokeAsync(String utteranceId,
                BiConsumer<BatchListener, Pair<String, Integer>> callback) {
            mHandler.post(() -> {
                Pair<String, Integer> parsedId = parse(utteranceId);
                BatchListener listener = mListeners.get(parsedId.first);
                if (listener != null) {
                    callback.accept(listener, parsedId);
                } else {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "Missing batch listener: " + utteranceId);
                    }
                }
            });
        }

        @Override
        public void onStart(String utteranceId) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "TTS onStart: " + utteranceId);
            }
            mHandler.post(() -> {
                Pair<String, Integer> parsedId = parse(utteranceId);
                BatchListener listener = mListeners.get(parsedId.first);
                if (listener != null) {
                    listener.onStart();
                } else {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "Missing batch listener: " + utteranceId);
                    }
                }
            });
        }

        @Override
        public void onDone(String utteranceId) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "TTS onDone: " + utteranceId);
            }
            safeInvokeAsync(utteranceId, BatchListener::onDone);
        }

        @Override
        public void onStop(String utteranceId, boolean interrupted) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "TTS onStop: " + utteranceId);
            }
            safeInvokeAsync(utteranceId, BatchListener::onStop);
        }

        @Override
        public void onError(String utteranceId) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "TTS onError: " + utteranceId);
            }
            safeInvokeAsync(utteranceId, BatchListener::onError);
        }
    };

    /**
     * Handles callbacks for a single batch of TTS text and issues callbacks on wrapped
     * {@link Listener} that client is listening on.
     */
    private class BatchListener {
        private boolean mBatchStarted;
        private final long mRequestId;
        private final int mUtteranceCount;

        BatchListener(long requestId, int utteranceCount) {
            mRequestId = requestId;
            mUtteranceCount = utteranceCount;
        }

        // Issues Listener.onTextToSpeechStarted when first item of batch starts.
        void onStart() {
            if (!mBatchStarted) {
                mBatchStarted = true;
                mListener.onTextToSpeechStarted(mRequestId);
            }
        }

        // Issues Listener.onTextToSpeechStopped when last item of batch finishes.
        void onDone(Pair<String, Integer> parsedId) {
            // parseId is zero-indexed, mUtteranceCount is not.
            if (parsedId.second == (mUtteranceCount - 1)) {
                handleBatchFinished(parsedId, /* error= */ false);
            }
        }

        // If any item of batch fails, abort the batch and issue Listener.onTextToSpeechStopped.
        void onError(Pair<String, Integer> parsedId) {
            if (parsedId.first.equals(mCurrentBatchId)) {
                mTextToSpeechEngine.stop();
            }
            handleBatchFinished(parsedId, /* error= */ true);
        }

        // If any item of batch is preempted (rest should also be),
        // issue Listener.onTextToSpeechStopped.
        void onStop(Pair<String, Integer> parsedId) {
            handleBatchFinished(parsedId, /* error= */ false);
        }

        // Handles terminal callbacks for the batch. We invoke stopped and remove ourselves.
        // No further callbacks will be handled for the batch.
        private void handleBatchFinished(Pair<String, Integer> parsedId, boolean error) {
            onTtsStopped(mRequestId, error);
            mListeners.remove(parsedId.first);
        }
    }

    private static class SpeechRequest {
        final List<CharSequence> mTextToSpeak;
        final long mRequestId;

        SpeechRequest(List<CharSequence> textToSpeak, long requestId) {
            mTextToSpeak = textToSpeak;
            mRequestId = requestId;
        }
    }
}
