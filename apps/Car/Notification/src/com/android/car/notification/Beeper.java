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

package com.android.car.notification;

import android.car.userlib.CarUserManagerHelper;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;

import java.util.HashMap;

/**
 * Helper class for playing notification beeps. For Feature_automotive the sounds for notification
 * will be disabled at the server level and notification center will handle playing all the sounds
 * using this class.
 */
class Beeper {
    private static final String TAG = "Beeper";
    private static final long ALLOWED_ALERT_INTERVAL = 1000;
    private static final boolean DEBUG = false;

    private final Context mContext;
    private final AudioManager mAudioManager;
    private final Uri mInCallSoundToPlayUri;
    private final CarUserManagerHelper mCarUserManagerHelper;
    private AudioAttributes mPlaybackAttributes;

    private boolean mInCall;

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
                mInCall = TelephonyManager.EXTRA_STATE_OFFHOOK
                        .equals(intent.getStringExtra(TelephonyManager.EXTRA_STATE));
            }
        }
    };

    /**
     * Map that contains all the package name as the key for which the notifications made
     * noise. The value will be the last notification post time from the package.
     */
    private final HashMap<String, Long> packageLastPostedTime;

    @Nullable
    private BeepRecord currentBeep;

    public Beeper(Context context) {
        this.mContext = context;
        mAudioManager = ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE));
        mInCallSoundToPlayUri = Uri.parse("file://" + context.getResources().getString(
                com.android.internal.R.string.config_inCallNotificationSound));
        mCarUserManagerHelper = new CarUserManagerHelper(context);
        packageLastPostedTime = new HashMap<>();
        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        context.registerReceiver(mIntentReceiver, filter);
    }

    /**
     * Beep with a provided sound.
     *
     * @param packageName of which {@link StatusBarNotification} belongs to.
     * @param soundToPlay {@link Uri} from where the sound will be played.
     */
    @MainThread
    public void beep(String packageName, Uri soundToPlay) {
        if (!canAlert(packageName)) {
            if (DEBUG) {
                Log.d(TAG, "Package recently made noise: " + packageName);
            }
            return;
        }

        packageLastPostedTime.put(packageName, System.currentTimeMillis());
        stopBeeping();
        if (mInCall) {
            currentBeep = new BeepRecord(mInCallSoundToPlayUri);
        } else {
            currentBeep = new BeepRecord(soundToPlay);
        }
        currentBeep.play();
    }

    /**
     * Checks if the package is allowed to make noise or not.
     */
    private boolean canAlert(String packageName) {
        if (packageLastPostedTime.containsKey(packageName)) {
            long lastPostedTime = packageLastPostedTime.get(packageName);
            return System.currentTimeMillis() - lastPostedTime > ALLOWED_ALERT_INTERVAL;
        }
        return true;
    }

    @MainThread
    void stopBeeping() {
        if (currentBeep != null) {
            currentBeep.stop();
            currentBeep = null;
        }
    }

    /** A class that represents a beep through its lifecycle. */
    private final class BeepRecord implements MediaPlayer.OnPreparedListener,
            MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener,
            AudioManager.OnAudioFocusChangeListener {

        private final Uri mBeepUri;
        private final int mBeepStream;
        private final MediaPlayer mPlayer;

        /** Only set in case of an error. See {@link #playViaRingtoneManager}. */
        @Nullable
        private Ringtone mRingtone;

        private int mAudiofocusRequestFailed = AudioManager.AUDIOFOCUS_REQUEST_FAILED;
        private boolean mCleanedUp;

        /**
         * Create a new {@link BeepRecord} that will play the given sound.
         *
         * @param beepUri The sound to play.
         */
        public BeepRecord(Uri beepUri) {
            this.mBeepUri = beepUri;
            this.mBeepStream = AudioManager.STREAM_MUSIC;
            mPlayer = new MediaPlayer();
            mPlayer.setOnPreparedListener(this);
            mPlayer.setOnCompletionListener(this);
            mPlayer.setOnErrorListener(this);
        }

        /** Start playing the sound. */
        @MainThread
        public void play() {
            if (DEBUG) {
                Log.d(TAG, "playing sound: ");
            }
            try {
                mPlayer.setDataSource(getContextForForegroundUser(), mBeepUri);
                mPlaybackAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                mPlayer.setAudioAttributes(mPlaybackAttributes);
                mPlayer.prepareAsync();
            } catch (Exception e) {
                Log.d(TAG, "playing via ringtone manager: " + e);
                handleError();
            }
        }

        /** Stop the currently playing sound, if it's playing. If it isn't, do nothing. */
        @MainThread
        public void stop() {
            if (!mCleanedUp && mPlayer.isPlaying()) {
                mPlayer.stop();
            }

            if (mRingtone != null) {
                mRingtone.stop();
                mRingtone = null;
            }
            cleanUp();
        }

        /** Handle MediaPlayer preparation completing - gain audio focus and play the sound. */
        @Override // MediaPlayer.OnPreparedListener
        public void onPrepared(MediaPlayer mediaPlayer) {
            if (mCleanedUp) {
                return;
            }
            AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(mPlaybackAttributes)
                    .setOnAudioFocusChangeListener(this, new Handler())
                    .build();

            mAudiofocusRequestFailed = mAudioManager.requestAudioFocus(focusRequest);
            if (mAudiofocusRequestFailed == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                // Only play the sound if we actually gained audio focus.
                mPlayer.start();
            } else {
                cleanUp();
            }
        }

        /** Handle completion by cleaning up our state. */
        @Override // MediaPlayer.OnCompletionListener
        public void onCompletion(MediaPlayer mediaPlayer) {
            cleanUp();
        }

        /** Handle errors that come from MediaPlayer. */
        @Override // MediaPlayer.OnErrorListener
        public boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
            handleError();
            return true;
        }

        /**
         * Not actually used for anything, but allows us to pass {@code this} to {@link
         * AudioManager#requestAudioFocus}, so that different audio focus requests from different
         * {@link BeepRecord}s don't collide.
         */
        @Override // AudioManager.OnAudioFocusChangeListener
        public void onAudioFocusChange(int i) {
        }

        /**
         * Notifications is running in the system process, so we want to make sure we lookup sounds
         * in the foreground user's space.
         */
        private Context getContextForForegroundUser() {
            try {
                return mContext.createPackageContextAsUser(mContext.getPackageName(), /* flags= */
                        0, UserHandle.of(mCarUserManagerHelper.getCurrentForegroundUserId()));
            } catch (PackageManager.NameNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        /** Handle an error by trying to play the sound through {@link RingtoneManager}. */
        private void handleError() {
            cleanUp();
            playViaRingtoneManager();
        }

        /** Clean up and release our state. */
        private void cleanUp() {
            if (mAudiofocusRequestFailed == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                mAudioManager.abandonAudioFocus(this);
                mAudiofocusRequestFailed = AudioManager.AUDIOFOCUS_REQUEST_FAILED;
            }
            mPlayer.release();
            mCleanedUp = true;
        }

        /**
         * Handle a failure to play the sound directly, by playing through {@link RingtoneManager}.
         *
         * <p>RingtoneManager is equipped to play sounds that require READ_EXTERNAL_STORAGE
         * permission (see b/30572189), but can't handle requesting and releasing audio focus.
         * Since we want audio focus in the common case, try playing the sound ourselves through
         * MediaPlayer before we give up and hand over to RingtoneManager.
         */
        private void playViaRingtoneManager() {
            mRingtone = RingtoneManager.getRingtone(getContextForForegroundUser(), mBeepUri);
            if (mRingtone != null) {
                mRingtone.setStreamType(mBeepStream);
                mRingtone.play();
            }
        }
    }
}
