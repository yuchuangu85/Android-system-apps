/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.google.android.car.kitchensink.audio;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Class for playing music.
 *
 * This is not thread safe and all public calls should be made from main thread.
 *
 * MP3 used is from http://freemusicarchive.org/music/John_Harrison_with_the_Wichita_State_University_Chamber_Players/The_Four_Seasons_Vivaldi/05_-_Vivaldi_Summer_mvt_2_Adagio_-_John_Harrison_violin
 * from John Harrison with the Wichita State University Chamber Players
 * Copyright under Create Commons license.
 */
public class AudioPlayer {

    public interface PlayStateListener {
        void onCompletion();
    }

    private static final String TAG = AudioPlayer.class.getSimpleName();

    private final AudioManager.OnAudioFocusChangeListener mFocusListener =
            new AudioManager.OnAudioFocusChangeListener() {

        @Override
        public void onAudioFocusChange(int focusChange) {
            if (mPlayer == null) {
                Log.e(TAG, "mPlayer is null");
                return;
            }
            if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                Log.i(TAG, "Audio focus change AUDIOFOCUS_GAIN for usage " + mAttrib.getUsage());
                mPlayer.setVolume(1.0f, 1.0f);
                if (mRepeat && isPlaying()) {
                    // Resume
                    Log.i(TAG, "resuming player");
                    mPlayer.start();
                }
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                // While we used to setVolume on the player to 20%, we don't do this anymore
                // because we expect the car's audio hal do handle ducking as it sees fit.
                Log.i(TAG, "Audio focus change AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> do nothing");
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                Log.i(TAG, "Audio focus change AUDIOFOCUS_LOSS_TRANSIENT for usage "
                        + mAttrib.getUsage());
                if (mRepeat && isPlaying()) {
                    Log.i(TAG, "pausing repeating player");
                    mPlayer.pause();
                } else {
                    Log.i(TAG, "stopping one shot player");
                    stop();
                }
            } else {
                Log.e(TAG, "Unrecognized audio focus change " + focusChange);
                if (isPlaying()) {
                    Log.i(TAG, "stopping player");
                    stop();
                }
            }
        }
    };

    private AudioManager mAudioManager;
    private MediaPlayer mPlayer;

    private final Context mContext;
    private final int mResourceId;
    private final AudioAttributes mAttrib;

    private final AtomicBoolean mPlaying = new AtomicBoolean(false);

    private volatile boolean mHandleFocus;
    private volatile boolean mRepeat;

    private PlayStateListener mListener;

    public AudioPlayer(Context context, int resourceId, AudioAttributes attrib) {
        mContext = context;
        mResourceId = resourceId;
        mAttrib = attrib;
    }

    public void start(boolean handleFocus, boolean repeat, int focusRequest) {
        String nullDeviceAddress = null;
        start(handleFocus, repeat, focusRequest, nullDeviceAddress);
    }

    /**
     * Starts player
     * @param handleFocus true to handle focus
     * @param repeat true to repeat track
     * @param focusRequest type of focus to request
     * @param deviceAddress preferred device to attached to audio
     */
    public void start(boolean handleFocus, boolean repeat, int focusRequest,
            @Nullable String deviceAddress) {
        mHandleFocus = handleFocus;
        mRepeat = repeat;
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        int ret = AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if (mHandleFocus) {
            // NOTE:  We are CONSCIOUSLY asking for focus again even if already playing in order
            // exercise the framework's focus logic when faced with a (sloppy) application which
            // might do this.
            Log.i(TAG, "Asking for focus for usage " + mAttrib.getUsage());
            ret = mAudioManager.requestAudioFocus(mFocusListener, mAttrib,
                    focusRequest, 0);
        }
        if (ret == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.i(TAG, "MediaPlayer got focus for usage " + mAttrib.getUsage());
            doStart(deviceAddress);
        } else {
            Log.i(TAG, "MediaPlayer denied focus for usage " + mAttrib.getUsage());
        }
    }

    public void start(boolean handleFocus, boolean repeat, int focusRequest,
            PlayStateListener listener) {
        mListener = listener;
        start(handleFocus, repeat, focusRequest);
    }

    private void doStart(String deviceAddress) {
        if (mPlaying.getAndSet(true)) {
            Log.i(TAG, "already playing");
            return;
        }
        Log.i(TAG, "doStart audio");
        mPlayer = new MediaPlayer();
        mPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {

            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.e(TAG, "audio error what " + what + " extra " + extra);
                mPlaying.set(false);
                if (!mRepeat && mHandleFocus) {
                    mPlayer.stop();
                    mPlayer.release();
                    mPlayer = null;
                    mAudioManager.abandonAudioFocus(mFocusListener);
                }
                return false;
            }

        });
        mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                Log.i(TAG, "AudioPlayer onCompletion");
                mPlaying.set(false);
                if (!mRepeat && mHandleFocus) {
                    mPlayer.stop();
                    mPlayer = null;
                    mAudioManager.abandonAudioFocus(mFocusListener);
                    if (mListener != null) {
                        mListener.onCompletion();
                    }
                }
            }
        });
        mPlayer.setAudioAttributes(mAttrib);
        mPlayer.setLooping(mRepeat);
        mPlayer.setVolume(1.0f, 1.0f);
        try {
            AssetFileDescriptor afd =
                    mContext.getResources().openRawResourceFd(mResourceId);
            if (afd == null) {
                throw new RuntimeException("resource not found");
            }
            mPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(),
                    afd.getLength());
            afd.close();
            mPlayer.prepare();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Search for preferred device
        if (deviceAddress != null) {
            AudioDeviceInfo[] devices = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (AudioDeviceInfo deviceInfo : devices) {
                if (deviceInfo.getAddress().equals(deviceAddress)) {
                    mPlayer.setPreferredDevice(deviceInfo);
                    break;
                }
            }
        }

        mPlayer.start();
    }

    public void stop() {
        if (!mPlaying.getAndSet(false)) {
            Log.i(TAG, "already stopped");
            return;
        }
        Log.i(TAG, "stop");

        mPlayer.stop();
        mPlayer = null;

        if (mHandleFocus) {
            mAudioManager.abandonAudioFocus(mFocusListener);
        }
    }

    public boolean isPlaying() {
        return mPlaying.get();
    }
}
