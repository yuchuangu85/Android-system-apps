/*
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

package com.android.car.settings.sound;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/** Manges the audio played by the {@link VolumeSettingsPreferenceController}. */
public class VolumeSettingsRingtoneManager {

    private static final int AUDIO_FEEDBACK_DURATION_MS = 1000;

    private final Context mContext;
    private final Handler mUiHandler;
    private final Map<Integer, Ringtone> mGroupToRingtoneMap = new HashMap<>();

    @Nullable
    private Ringtone mCurrentRingtone;

    public VolumeSettingsRingtoneManager(Context context) {
        mContext = context;
        mUiHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Play the audio defined by the current group and usage. Stop the current ringtone if it is a
     * different ringtone than what is currently playing.
     */
    public void playAudioFeedback(int group, int usage) {
        Ringtone nextRingtone = lazyLoadRingtone(group, usage);
        if (mCurrentRingtone != null && mCurrentRingtone != nextRingtone
                && mCurrentRingtone.isPlaying()) {
            mCurrentRingtone.stop();
        }

        mUiHandler.removeCallbacksAndMessages(null);
        mCurrentRingtone = nextRingtone;
        mCurrentRingtone.play();
        mUiHandler.postDelayed(() -> {
            if (mCurrentRingtone.isPlaying()) {
                mCurrentRingtone.stop();
                mCurrentRingtone = null;
            }
        }, AUDIO_FEEDBACK_DURATION_MS);
    }

    /** Stop playing the current ringtone. */
    public void stopCurrentRingtone() {
        if (mCurrentRingtone != null) {
            mCurrentRingtone.stop();
        }
    }

    /** If we have already seen this ringtone, use it. Otherwise load when requested. */
    private Ringtone lazyLoadRingtone(int group, int usage) {
        if (!mGroupToRingtoneMap.containsKey(group)) {
            Ringtone ringtone = RingtoneManager.getRingtone(mContext, getRingtoneUri(usage));
            ringtone.setAudioAttributes(new AudioAttributes.Builder().setUsage(usage).build());
            mGroupToRingtoneMap.put(group, ringtone);
        }
        return mGroupToRingtoneMap.get(group);
    }

    // TODO: bundle car-specific audio sample assets in res/raw by usage
    private Uri getRingtoneUri(@AudioAttributes.AttributeUsage int usage) {
        switch (usage) {
            case AudioAttributes.USAGE_NOTIFICATION:
                return Settings.System.DEFAULT_NOTIFICATION_URI;
            case AudioAttributes.USAGE_ALARM:
                return Settings.System.DEFAULT_ALARM_ALERT_URI;
            default:
                return Settings.System.DEFAULT_RINGTONE_URI;
        }
    }
}
