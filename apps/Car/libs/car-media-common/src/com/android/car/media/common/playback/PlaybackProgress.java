/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.car.media.common.playback;

import android.annotation.SuppressLint;
import android.media.session.PlaybackState;

import java.util.concurrent.TimeUnit;

/**
 * This class represents the current playback progress, and provides methods to display the
 * progress in a human-readable way.
 */
public class PlaybackProgress {

    private final long mProgress;
    private final long mMaxProgress;

    public PlaybackProgress(long progress, long maxProgress) {
        mProgress = progress;
        mMaxProgress = maxProgress;
    }

    /**
     * Returns the current track's progress
     */
    public long getProgress() {
        return mProgress;
    }

    /**
     * Returns the current track's maximum progress
     */
    public long getMaxProgress() {
        return mMaxProgress;
    }

    /**
     * Returns the current track's progress in text form
     */
    public CharSequence getCurrentTimeText() {
        boolean showHours = TimeUnit.MILLISECONDS.toHours(mMaxProgress) > 0;
        return formatTime(mProgress, showHours);
    }

    /**
     * Returns the current track's maximum progress in text form
     */
    public CharSequence getMaxTimeText() {
        boolean showHours = TimeUnit.MILLISECONDS.toHours(mMaxProgress) > 0;
        return formatTime(mMaxProgress, showHours);
    }

    /**
     * Returns whether the current track's progress is available
     */
    public boolean hasTime() {
        return mMaxProgress > 0 && mProgress != PlaybackState.PLAYBACK_POSITION_UNKNOWN;
    }

    @SuppressLint("DefaultLocale")
    private static String formatTime(long millis, boolean showHours) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1);
        if (showHours) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }
}
