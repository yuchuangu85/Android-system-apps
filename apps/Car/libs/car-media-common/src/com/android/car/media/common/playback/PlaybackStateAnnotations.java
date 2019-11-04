/*
 * Copyright 2018 The Android Open Source Project
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

import android.annotation.IntDef;
import android.annotation.LongDef;
import android.media.session.PlaybackState;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Contains annotations for {@link PlaybackState PlaybackState's} constants.
 */
public class PlaybackStateAnnotations {

    /**
     * Indicates that a {@code long} is one of the commands defined in {@link PlaybackState}
     */
    @LongDef(flag = true, value = {PlaybackState.ACTION_STOP, PlaybackState.ACTION_PAUSE,
            PlaybackState.ACTION_PLAY, PlaybackState.ACTION_REWIND,
            PlaybackState.ACTION_SKIP_TO_PREVIOUS, PlaybackState.ACTION_SKIP_TO_NEXT,
            PlaybackState.ACTION_FAST_FORWARD, PlaybackState.ACTION_SET_RATING,
            PlaybackState.ACTION_SEEK_TO, PlaybackState.ACTION_PLAY_PAUSE,
            PlaybackState.ACTION_PLAY_FROM_MEDIA_ID, PlaybackState.ACTION_PLAY_FROM_SEARCH,
            PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM, PlaybackState.ACTION_PLAY_FROM_URI,
            PlaybackState.ACTION_PREPARE, PlaybackState.ACTION_PREPARE_FROM_MEDIA_ID,
            PlaybackState.ACTION_PREPARE_FROM_SEARCH, PlaybackState.ACTION_PREPARE_FROM_URI})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Actions {
    }


    /**
     * Indicates that a {@code int} is one of the states defined in {@link PlaybackState}
     */
    @IntDef({PlaybackState.STATE_NONE, PlaybackState.STATE_STOPPED, PlaybackState.STATE_PAUSED,
            PlaybackState.STATE_PLAYING, PlaybackState.STATE_FAST_FORWARDING,
            PlaybackState.STATE_REWINDING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_ERROR,
            PlaybackState.STATE_CONNECTING, PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
            PlaybackState.STATE_SKIPPING_TO_NEXT, PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM})
    @Retention(RetentionPolicy.SOURCE)
    public @interface State {
    }
}
