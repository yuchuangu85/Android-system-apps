/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tv.audio;

import android.app.Activity;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.support.annotation.Nullable;
import com.android.tv.features.TvFeatures;
import com.android.tv.ui.api.TunableTvViewPlayingApi;

/** A helper class to help {@code Activities} to handle audio-related stuffs. */
public class AudioManagerHelper implements AudioManager.OnAudioFocusChangeListener {
    private static final float AUDIO_MAX_VOLUME = 1.0f;
    private static final float AUDIO_MIN_VOLUME = 0.0f;
    private static final float AUDIO_DUCKING_VOLUME = 0.3f;

    private final Activity mActivity;
    private final TunableTvViewPlayingApi mTvView;
    private final AudioManager mAudioManager;
    @Nullable private final AudioFocusRequest mFocusRequest;

    private int mAudioFocusStatus = AudioManager.AUDIOFOCUS_NONE;

    public AudioManagerHelper(Activity activity, TunableTvViewPlayingApi tvView) {
        mActivity = activity;
        mTvView = tvView;
        mAudioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mFocusRequest =
                    new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                            .setAudioAttributes(
                                    new AudioAttributes.Builder()
                                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                                            .setUsage(AudioAttributes.USAGE_MEDIA)
                                            .build())
                            .setOnAudioFocusChangeListener(this)
                            // Auto ducking from the system does not mute the TV Input Service.
                            // Using will pause when ducked allows us to set the stream volume
                            // even when we are not pausing.
                            .setWillPauseWhenDucked(true)
                            .build();
        } else {
            mFocusRequest = null;
        }
    }

    /**
     * Sets suitable volume to {@link TunableTvViewPlayingApi} according to the current audio focus.
     *
     * <p>If the focus status is {@link AudioManager#AUDIOFOCUS_LOSS} or {@link
     * AudioManager#AUDIOFOCUS_NONE} and the activity is under PIP mode, this method will finish the
     * activity. Sets suitable volume to {@link TunableTvViewPlayingApi} according to the current
     * audio focus. If the focus status is {@link AudioManager#AUDIOFOCUS_LOSS} and the activity is
     * under PIP mode, this method will finish the activity.
     */
    public void setVolumeByAudioFocusStatus() {
        if (mTvView.isPlaying()) {
            switch (mAudioFocusStatus) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    if (mTvView.isTimeShiftAvailable()) {
                        mTvView.timeShiftPlay();
                    } else {
                        mTvView.setStreamVolume(AUDIO_MAX_VOLUME);
                    }
                    break;
                case AudioManager.AUDIOFOCUS_NONE:
                case AudioManager.AUDIOFOCUS_LOSS:
                    if (TvFeatures.PICTURE_IN_PICTURE.isEnabled(mActivity)
                            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                            && mActivity.isInPictureInPictureMode()) {
                        mActivity.finish();
                        break;
                    }
                    // fall through
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    if (mTvView.isTimeShiftAvailable()) {
                        mTvView.timeShiftPause();
                    } else {
                        mTvView.setStreamVolume(AUDIO_MIN_VOLUME);
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    if (mTvView.isTimeShiftAvailable()) {
                        mTvView.timeShiftPause();
                    } else {
                        mTvView.setStreamVolume(AUDIO_DUCKING_VOLUME);
                    }
                    break;
            }
        }
    }

    /**
     * Tries to request audio focus from {@link AudioManager} and set volume according to the
     * returned result.
     */
    public void requestAudioFocus() {
        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            result = mAudioManager.requestAudioFocus(mFocusRequest);
        } else {
            result =
                    mAudioManager.requestAudioFocus(
                            this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
        mAudioFocusStatus =
                (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                        ? AudioManager.AUDIOFOCUS_GAIN
                        : AudioManager.AUDIOFOCUS_LOSS;
        setVolumeByAudioFocusStatus();
    }

    /** Abandons audio focus. */
    public void abandonAudioFocus() {
        mAudioFocusStatus = AudioManager.AUDIOFOCUS_LOSS;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mAudioManager.abandonAudioFocusRequest(mFocusRequest);
        } else {
            mAudioManager.abandonAudioFocus(this);
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        mAudioFocusStatus = focusChange;
        setVolumeByAudioFocusStatus();
    }
}
