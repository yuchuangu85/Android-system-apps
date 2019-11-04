/*
 * Copyright 2014, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.telecom;

import android.annotation.Nullable;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Log;
import android.telecom.Logging.Runnable;
import android.telecom.Logging.Session;

import com.android.internal.annotations.VisibleForTesting;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Play a call-related tone (ringback, busy signal, etc.) either through ToneGenerator, or using a
 * media resource file.
 * To use, create an instance using InCallTonePlayer.Factory (passing in the TONE_* constant for
 * the tone you want) and start() it. Implemented on top of {@link Thread} so that the tone plays in
 * its own thread.
 */
public class InCallTonePlayer extends Thread {

    /**
     * Factory used to create InCallTonePlayers. Exists to aid with testing mocks.
     */
    public static class Factory {
        private CallAudioManager mCallAudioManager;
        private final CallAudioRoutePeripheralAdapter mCallAudioRoutePeripheralAdapter;
        private final TelecomSystem.SyncRoot mLock;
        private final ToneGeneratorFactory mToneGeneratorFactory;
        private final MediaPlayerFactory mMediaPlayerFactory;
        private final AudioManagerAdapter mAudioManagerAdapter;

        public Factory(CallAudioRoutePeripheralAdapter callAudioRoutePeripheralAdapter,
                TelecomSystem.SyncRoot lock, ToneGeneratorFactory toneGeneratorFactory,
                MediaPlayerFactory mediaPlayerFactory, AudioManagerAdapter audioManagerAdapter) {
            mCallAudioRoutePeripheralAdapter = callAudioRoutePeripheralAdapter;
            mLock = lock;
            mToneGeneratorFactory = toneGeneratorFactory;
            mMediaPlayerFactory = mediaPlayerFactory;
            mAudioManagerAdapter = audioManagerAdapter;
        }

        public void setCallAudioManager(CallAudioManager callAudioManager) {
            mCallAudioManager = callAudioManager;
        }

        public InCallTonePlayer createPlayer(int tone) {
            return new InCallTonePlayer(tone, mCallAudioManager,
                    mCallAudioRoutePeripheralAdapter, mLock, mToneGeneratorFactory,
                    mMediaPlayerFactory, mAudioManagerAdapter);
        }
    }

    public interface ToneGeneratorFactory {
        ToneGenerator get (int streamType, int volume);
    }

    public interface MediaPlayerAdapter {
        void setLooping(boolean isLooping);
        void setOnCompletionListener(MediaPlayer.OnCompletionListener listener);
        void start();
        void release();
        int getDuration();
    }

    public static class MediaPlayerAdapterImpl implements MediaPlayerAdapter {
        private MediaPlayer mMediaPlayer;

        /**
         * Create new media player adapter backed by a real mediaplayer.
         * Note: Its possible for the mediaplayer to be null if
         * {@link MediaPlayer#create(Context, Uri)} fails for some reason; in this case we can
         * continue but not bother playing the audio.
         * @param mediaPlayer The media player.
         */
        public MediaPlayerAdapterImpl(@Nullable MediaPlayer mediaPlayer) {
            mMediaPlayer = mediaPlayer;
        }

        @Override
        public void setLooping(boolean isLooping) {
            if (mMediaPlayer != null) {
                mMediaPlayer.setLooping(isLooping);
            }
        }

        @Override
        public void setOnCompletionListener(MediaPlayer.OnCompletionListener listener) {
            if (mMediaPlayer != null) {
                mMediaPlayer.setOnCompletionListener(listener);
            }
        }

        @Override
        public void start() {
            if (mMediaPlayer != null) {
                mMediaPlayer.start();
            }
        }

        @Override
        public void release() {
            if (mMediaPlayer != null) {
                mMediaPlayer.release();
            }
        }

        @Override
        public int getDuration() {
            if (mMediaPlayer != null) {
                return mMediaPlayer.getDuration();
            }
            return 0;
        }
    }

    public interface MediaPlayerFactory {
        MediaPlayerAdapter get (int resourceId, AudioAttributes attributes);
    }

    public interface AudioManagerAdapter {
        boolean isVolumeOverZero();
    }

    // The possible tones that we can play.
    public static final int TONE_INVALID = 0;
    public static final int TONE_BUSY = 1;
    public static final int TONE_CALL_ENDED = 2;
    public static final int TONE_OTA_CALL_ENDED = 3;
    public static final int TONE_CALL_WAITING = 4;
    public static final int TONE_CDMA_DROP = 5;
    public static final int TONE_CONGESTION = 6;
    public static final int TONE_INTERCEPT = 7;
    public static final int TONE_OUT_OF_SERVICE = 8;
    public static final int TONE_REDIAL = 9;
    public static final int TONE_REORDER = 10;
    public static final int TONE_RING_BACK = 11;
    public static final int TONE_UNOBTAINABLE_NUMBER = 12;
    public static final int TONE_VOICE_PRIVACY = 13;
    public static final int TONE_VIDEO_UPGRADE = 14;

    private static final int TONE_RESOURCE_ID_UNDEFINED = -1;

    private static final int RELATIVE_VOLUME_EMERGENCY = 100;
    private static final int RELATIVE_VOLUME_HIPRI = 80;
    private static final int RELATIVE_VOLUME_LOPRI = 50;
    private static final int RELATIVE_VOLUME_UNDEFINED = -1;

    // Buffer time (in msec) to add on to the tone timeout value. Needed mainly when the timeout
    // value for a tone is exact duration of the tone itself.
    private static final int TIMEOUT_BUFFER_MILLIS = 20;

    // The tone state.
    private static final int STATE_OFF = 0;
    private static final int STATE_ON = 1;
    private static final int STATE_STOPPED = 2;

    /**
     * Keeps count of the number of actively playing tones so that we can notify CallAudioManager
     * when we need focus and when it can be release. This should only be manipulated from the main
     * thread.
     */
    private static int sTonesPlaying = 0;

    private final CallAudioManager mCallAudioManager;
    private final CallAudioRoutePeripheralAdapter mCallAudioRoutePeripheralAdapter;

    private final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());

    /** The ID of the tone to play. */
    private final int mToneId;

    /** Current state of the tone player. */
    private int mState;

    /** For tones which are not generated using ToneGenerator. */
    private MediaPlayerAdapter mToneMediaPlayer = null;

    /** Telecom lock object. */
    private final TelecomSystem.SyncRoot mLock;

    private Session mSession;
    private final Object mSessionLock = new Object();

    private final ToneGeneratorFactory mToneGenerator;
    private final MediaPlayerFactory mMediaPlayerFactory;
    private final AudioManagerAdapter mAudioManagerAdapter;

    /**
     * Initializes the tone player. Private; use the {@link Factory} to create tone players.
     *
     * @param toneId ID of the tone to play, see TONE_* constants.
     */
    private InCallTonePlayer(
            int toneId,
            CallAudioManager callAudioManager,
            CallAudioRoutePeripheralAdapter callAudioRoutePeripheralAdapter,
            TelecomSystem.SyncRoot lock,
            ToneGeneratorFactory toneGeneratorFactory,
            MediaPlayerFactory mediaPlayerFactor,
            AudioManagerAdapter audioManagerAdapter) {
        mState = STATE_OFF;
        mToneId = toneId;
        mCallAudioManager = callAudioManager;
        mCallAudioRoutePeripheralAdapter = callAudioRoutePeripheralAdapter;
        mLock = lock;
        mToneGenerator = toneGeneratorFactory;
        mMediaPlayerFactory = mediaPlayerFactor;
        mAudioManagerAdapter = audioManagerAdapter;
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        try {
            synchronized (mSessionLock) {
                if (mSession != null) {
                    Log.continueSession(mSession, "ICTP.r");
                    mSession = null;
                }
            }
            Log.d(this, "run(toneId = %s)", mToneId);

            final int toneType;  // Passed to ToneGenerator.startTone.
            final int toneVolume;  // Passed to the ToneGenerator constructor.
            final int toneLengthMillis;
            final int mediaResourceId; // The resourceId of the tone to play.  Used for media-based
                                      // tones.

            switch (mToneId) {
                case TONE_BUSY:
                    // TODO: CDMA-specific tones
                    toneType = ToneGenerator.TONE_SUP_BUSY;
                    toneVolume = RELATIVE_VOLUME_HIPRI;
                    toneLengthMillis = 4000;
                    mediaResourceId = TONE_RESOURCE_ID_UNDEFINED;
                    break;
                case TONE_CALL_ENDED:
                    // Don't use tone generator
                    toneType = ToneGenerator.TONE_UNKNOWN;
                    toneVolume = RELATIVE_VOLUME_UNDEFINED;
                    toneLengthMillis = 0;

                    // Use a tone resource file for a more rich, full-bodied tone experience.
                    mediaResourceId = R.raw.endcall;
                    break;
                case TONE_OTA_CALL_ENDED:
                    // TODO: fill in
                    throw new IllegalStateException("OTA Call ended NYI.");
                case TONE_CALL_WAITING:
                    toneType = ToneGenerator.TONE_SUP_CALL_WAITING;
                    toneVolume = RELATIVE_VOLUME_HIPRI;
                    toneLengthMillis = Integer.MAX_VALUE - TIMEOUT_BUFFER_MILLIS;
                    mediaResourceId = TONE_RESOURCE_ID_UNDEFINED;
                    break;
                case TONE_CDMA_DROP:
                    toneType = ToneGenerator.TONE_CDMA_CALLDROP_LITE;
                    toneVolume = RELATIVE_VOLUME_LOPRI;
                    toneLengthMillis = 375;
                    mediaResourceId = TONE_RESOURCE_ID_UNDEFINED;
                    break;
                case TONE_CONGESTION:
                    toneType = ToneGenerator.TONE_SUP_CONGESTION;
                    toneVolume = RELATIVE_VOLUME_HIPRI;
                    toneLengthMillis = 4000;
                    mediaResourceId = TONE_RESOURCE_ID_UNDEFINED;
                    break;
                case TONE_INTERCEPT:
                    toneType = ToneGenerator.TONE_CDMA_ABBR_INTERCEPT;
                    toneVolume = RELATIVE_VOLUME_LOPRI;
                    toneLengthMillis = 500;
                    mediaResourceId = TONE_RESOURCE_ID_UNDEFINED;
                    break;
                case TONE_OUT_OF_SERVICE:
                    toneType = ToneGenerator.TONE_CDMA_CALLDROP_LITE;
                    toneVolume = RELATIVE_VOLUME_LOPRI;
                    toneLengthMillis = 375;
                    mediaResourceId = TONE_RESOURCE_ID_UNDEFINED;
                    break;
                case TONE_REDIAL:
                    toneType = ToneGenerator.TONE_CDMA_ALERT_AUTOREDIAL_LITE;
                    toneVolume = RELATIVE_VOLUME_LOPRI;
                    toneLengthMillis = 5000;
                    mediaResourceId = TONE_RESOURCE_ID_UNDEFINED;
                    break;
                case TONE_REORDER:
                    toneType = ToneGenerator.TONE_CDMA_REORDER;
                    toneVolume = RELATIVE_VOLUME_HIPRI;
                    toneLengthMillis = 4000;
                    mediaResourceId = TONE_RESOURCE_ID_UNDEFINED;
                    break;
                case TONE_RING_BACK:
                    toneType = ToneGenerator.TONE_SUP_RINGTONE;
                    toneVolume = RELATIVE_VOLUME_HIPRI;
                    toneLengthMillis = Integer.MAX_VALUE - TIMEOUT_BUFFER_MILLIS;
                    mediaResourceId = TONE_RESOURCE_ID_UNDEFINED;
                    break;
                case TONE_UNOBTAINABLE_NUMBER:
                    toneType = ToneGenerator.TONE_SUP_ERROR;
                    toneVolume = RELATIVE_VOLUME_HIPRI;
                    toneLengthMillis = 4000;
                    mediaResourceId = TONE_RESOURCE_ID_UNDEFINED;
                    break;
                case TONE_VOICE_PRIVACY:
                    // TODO: fill in.
                    throw new IllegalStateException("Voice privacy tone NYI.");
                case TONE_VIDEO_UPGRADE:
                    // Similar to the call waiting tone, but does not repeat.
                    toneType = ToneGenerator.TONE_SUP_CALL_WAITING;
                    toneVolume = RELATIVE_VOLUME_HIPRI;
                    toneLengthMillis = 4000;
                    mediaResourceId = TONE_RESOURCE_ID_UNDEFINED;
                    break;
                default:
                    throw new IllegalStateException("Bad toneId: " + mToneId);
            }

            int stream = AudioManager.STREAM_VOICE_CALL;
            if (mCallAudioRoutePeripheralAdapter.isBluetoothAudioOn()) {
                stream = AudioManager.STREAM_BLUETOOTH_SCO;
            }

            if (toneType != ToneGenerator.TONE_UNKNOWN) {
                playToneGeneratorTone(stream, toneVolume, toneType, toneLengthMillis);
            } else if (mediaResourceId != TONE_RESOURCE_ID_UNDEFINED) {
                playMediaTone(stream, mediaResourceId);
            }
        } finally {
            cleanUpTonePlayer();
            Log.endSession();
        }
    }

    /**
     * Play a tone generated by the {@link ToneGenerator}.
     * @param stream The stream on which the tone will be played.
     * @param toneVolume The volume of the tone.
     * @param toneType The type of tone to play.
     * @param toneLengthMillis How long to play the tone.
     */
    private void playToneGeneratorTone(int stream, int toneVolume, int toneType,
            int toneLengthMillis) {
        ToneGenerator toneGenerator = null;
        try {
            // If the ToneGenerator creation fails, just continue without it. It is a local audio
            // signal, and is not as important.
            try {
                toneGenerator = mToneGenerator.get(stream, toneVolume);
            } catch (RuntimeException e) {
                Log.w(this, "Failed to create ToneGenerator.", e);
                return;
            }

            Log.i(this, "playToneGeneratorTone: toneType=%d", toneType);
            // TODO: Certain CDMA tones need to check the ringer-volume state before
            // playing. See CallNotifier.InCallTonePlayer.

            // TODO: Some tones play through the end of a call so we need to inform
            // CallAudioManager that we want focus the same way that Ringer does.

            synchronized (this) {
                if (mState != STATE_STOPPED) {
                    mState = STATE_ON;
                    toneGenerator.startTone(toneType);
                    try {
                        Log.v(this, "Starting tone %d...waiting for %d ms.", mToneId,
                                toneLengthMillis + TIMEOUT_BUFFER_MILLIS);
                        wait(toneLengthMillis + TIMEOUT_BUFFER_MILLIS);
                    } catch (InterruptedException e) {
                        Log.w(this, "wait interrupted", e);
                    }
                }
            }
            mState = STATE_OFF;
        } finally {
            if (toneGenerator != null) {
                toneGenerator.release();
            }
        }
    }

    /**
     * Plays an audio-file based media tone.
     * @param stream The audio stream on which to play the tone.
     * @param toneResourceId The resource ID of the tone to play.
     */
    private void playMediaTone(int stream, int toneResourceId) {
        synchronized (this) {
            if (mState != STATE_STOPPED) {
                mState = STATE_ON;
            }
            Log.i(this, "playMediaTone: toneResourceId=%d", toneResourceId);
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setLegacyStreamType(stream)
                    .build();
            mToneMediaPlayer = mMediaPlayerFactory.get(toneResourceId, attributes);
            mToneMediaPlayer.setLooping(false);
            int durationMillis = mToneMediaPlayer.getDuration();
            final CountDownLatch toneLatch = new CountDownLatch(1);
            mToneMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    Log.i(this, "playMediaTone: toneResourceId=%d completed.", toneResourceId);
                    synchronized (this) {
                        mState = STATE_OFF;
                    }
                    mToneMediaPlayer.release();
                    mToneMediaPlayer = null;
                    toneLatch.countDown();
                }
            });
            mToneMediaPlayer.start();
            try {
                // Wait for the tone to stop playing; timeout at 2x the length of the file just to
                // be on the safe side.
                toneLatch.await(durationMillis * 2, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ie) {
                Log.e(this, ie, "playMediaTone: tone playback interrupted.");
            }
        }

    }

    @VisibleForTesting
    public boolean startTone() {
        // Skip playing the end call tone if the volume is silenced.
        if (mToneId == TONE_CALL_ENDED && !mAudioManagerAdapter.isVolumeOverZero()) {
            Log.i(this, "startTone: skip end-call tone as device is silenced.");
            return false;
        }

        sTonesPlaying++;
        if (sTonesPlaying == 1) {
            mCallAudioManager.setIsTonePlaying(true);
        }

        synchronized (mSessionLock) {
            if (mSession != null) {
                Log.cancelSubsession(mSession);
            }
            mSession = Log.createSubsession();
        }

        super.start();
        return true;
    }

    @Override
    public void start() {
        Log.w(this, "Do not call the start method directly; use startTone instead.");
    }

    /**
     * Stops the tone.
     */
    @VisibleForTesting
    public void stopTone() {
        synchronized (this) {
            if (mState == STATE_ON) {
                Log.d(this, "Stopping the tone %d.", mToneId);
                notify();
            }
            mState = STATE_STOPPED;
        }
    }

    @VisibleForTesting
    public void cleanup() {
        sTonesPlaying = 0;
    }

    private void cleanUpTonePlayer() {
        // Release focus on the main thread.
        mMainThreadHandler.post(new Runnable("ICTP.cUTP", mLock) {
            @Override
            public void loggedRun() {
                if (sTonesPlaying == 0) {
                    Log.wtf(this, "Over-releasing focus for tone player.");
                } else if (--sTonesPlaying == 0) {
                    mCallAudioManager.setIsTonePlaying(false);
                }
            }
        }.prepare());
    }
}
