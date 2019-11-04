/**
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.radio.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.session.PlaybackState;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.radio.platform.RadioTunerExt;
import com.android.car.radio.platform.RadioTunerExt.TuneCallback;
import com.android.car.radio.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * Manages radio's audio stream.
 */
public class AudioStreamController {
    private static final String TAG = "BcRadioApp.audio";

    /** Tune operation. */
    public static final int OPERATION_TUNE = 1;

    /** Seek forward operation. */
    public static final int OPERATION_SEEK_FWD = 2;

    /** Seek backwards operation. */
    public static final int OPERATION_SEEK_BKW = 3;

    /** Step forwards operation. */
    public static final int OPERATION_STEP_FWD = 4;

    /** Step backwards operation. */
    public static final int OPERATION_STEP_BKW = 5;

    /**
     * Operation types for {@link #preparePlayback}.
     */
    @IntDef(value = {
        OPERATION_TUNE,
        OPERATION_SEEK_FWD,
        OPERATION_SEEK_BKW,
        OPERATION_STEP_FWD,
        OPERATION_STEP_BKW,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface PlaybackOperation {}

    private final Object mLock = new Object();
    private final AudioManager mAudioManager;
    private final RadioTunerExt mRadioTunerExt;

    private final PlaybackStateCallback mCallback;
    private final AudioFocusRequest mGainFocusReq;

    /**
     * Indicates that the app has *some* focus or a promise of it.
     *
     * It may be ducked, transiently lost or delayed.
     */
    private boolean mHasSomeFocus = false;

    private int mCurrentPlaybackState = PlaybackState.STATE_NONE;
    private Object mTuningToken;

    /**
     * Callback for playback state changes.
     */
    public interface PlaybackStateCallback {
        /**
         * Called when playback state changes.
         */
        void onPlaybackStateChanged(int newState);
    }

    /**
     * New (and only) instance of Audio stream controller.
     *
     * This is a part of RadioAppService that handles audio streams and playback status.
     *
     * @param context Context
     * @param radioManager tuner hardware manager
     * @param callback Callback for playback state changes
     */
    public AudioStreamController(@NonNull Context context, @NonNull RadioTunerExt tuner,
            @NonNull PlaybackStateCallback callback) {
        mAudioManager = Objects.requireNonNull(
                (AudioManager) context.getSystemService(Context.AUDIO_SERVICE));
        mRadioTunerExt = Objects.requireNonNull(tuner);
        mCallback = Objects.requireNonNull(callback);

        AudioAttributes playbackAttr = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        mGainFocusReq = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttr)
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(this::onAudioFocusChange)
                .build();
    }

    private boolean unmuteLocked() {
        if (mRadioTunerExt.setMuted(false)) return true;
        Log.w(TAG, "Failed to unmute, dropping audio focus");
        abandonAudioFocusLocked();
        return false;
    }

    private boolean requestAudioFocusLocked() {
        if (mHasSomeFocus) return true;
        int res = mAudioManager.requestAudioFocus(mGainFocusReq);
        if (res == AudioManager.AUDIOFOCUS_REQUEST_DELAYED) {
            Log.d(TAG, "Audio focus request is delayed");
            mHasSomeFocus = true;
            return true;
        }
        if (res != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.w(TAG, "Couldn't obtain audio focus, res=" + res);
            return false;
        }

        Log.v(TAG, "Audio focus request succeeded");
        mHasSomeFocus = true;

        // we assume that audio focus was requested only when we mean to unmute
        if (!unmuteLocked()) return false;

        return true;
    }

    private boolean abandonAudioFocusLocked() {
        if (!mHasSomeFocus) return true;
        if (!mRadioTunerExt.setMuted(true)) return false;

        int res = mAudioManager.abandonAudioFocusRequest(mGainFocusReq);
        if (res != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.e(TAG, "Couldn't abandon audio focus, res=" + res);
            return false;
        }

        Log.v(TAG, "Audio focus abandoned");
        mHasSomeFocus = false;

        return true;
    }

    private void notifyPlaybackStateLocked(int newState) {
        if (mCurrentPlaybackState == newState) return;
        mCurrentPlaybackState = newState;
        Log.v(TAG, "Playback state changed to " + newState);
        mCallback.onPlaybackStateChanged(newState);
    }

    /**
     * Prepare playback for ongoing tune/scan operation.
     *
     * @param operation Playback operation type
     * @return result callback to be passed to {@link RadioTunerExt#tune} call,
     *         or {@code null} if couldn't get focus.
     */
    @Nullable
    public TuneCallback preparePlayback(@PlaybackOperation int operation) {
        synchronized (mLock) {
            Object token = new Object();
            mTuningToken = token;

            if (!requestAudioFocusLocked()) {
                mTuningToken = null;
                return null;
            }

            int state;
            switch (operation) {
                case OPERATION_TUNE:
                    state = PlaybackState.STATE_CONNECTING;
                    break;
                case OPERATION_SEEK_FWD:
                case OPERATION_STEP_FWD:
                    state = PlaybackState.STATE_SKIPPING_TO_NEXT;
                    break;
                case OPERATION_SEEK_BKW:
                case OPERATION_STEP_BKW:
                    state = PlaybackState.STATE_SKIPPING_TO_PREVIOUS;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid operation: " + operation);
            }
            notifyPlaybackStateLocked(state);

            return succeeded -> onTuneCompleted(token, succeeded);
        }
    }

    private void onTuneCompleted(@NonNull Object token, boolean succeeded) {
        synchronized (mLock) {
            if (mTuningToken != token) return;
            mTuningToken = null;
            notifyPlaybackStateLocked(succeeded
                    ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_ERROR);
        }
    }

    /**
     * Request audio stream muted or unmuted.
     *
     * @param muted true, if audio stream should be muted, false if unmuted
     * @return true, if request has succeeded (maybe delayed)
     */
    public boolean requestMuted(boolean muted) {
        synchronized (mLock) {
            if (muted) {
                if (mTuningToken == null) {
                    notifyPlaybackStateLocked(PlaybackState.STATE_STOPPED);
                }
                return abandonAudioFocusLocked();
            } else {
                if (!requestAudioFocusLocked()) return false;
                if (mTuningToken == null) {
                    notifyPlaybackStateLocked(PlaybackState.STATE_PLAYING);
                }
                return true;
            }
        }
    }

    private void onAudioFocusChange(int focusChange) {
        Log.v(TAG, "onAudioFocusChange(" + focusChange + ")");

        synchronized (mLock) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    mHasSomeFocus = true;
                    // we assume that audio focus was requested only when we mean to unmute
                    unmuteLocked();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    Log.i(TAG, "Unexpected audio focus loss");
                    mHasSomeFocus = false;
                    mRadioTunerExt.setMuted(true);
                    notifyPlaybackStateLocked(PlaybackState.STATE_STOPPED);
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    mRadioTunerExt.setMuted(true);
                    break;
                default:
                    Log.w(TAG, "Unexpected audio focus state: " + focusChange);
            }
        }
    }
}
