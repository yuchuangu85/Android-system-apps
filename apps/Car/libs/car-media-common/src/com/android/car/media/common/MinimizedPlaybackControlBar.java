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

package com.android.car.media.common;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Size;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;

import com.android.car.apps.common.MinimizedControlBar;
import com.android.car.media.common.playback.PlaybackViewModel;

/**
 * This is a CarControlBar used for displaying Media content, including metadata for the currently
 * playing song and basic controls.
 */
public class MinimizedPlaybackControlBar extends MinimizedControlBar {

    private static final String TAG = "Media.ControlBar";

    private MediaButtonController mMediaButtonController;
    private MetadataController mMetadataController;
    private ProgressBar mLinearProgressBar;
    private ProgressBar mCircularProgressBar;
    private PlaybackViewModel mPlaybackViewModel;

    private boolean mShowLinearProgressBar;
    private boolean mShowCircularProgressBar;

    public MinimizedPlaybackControlBar(Context context) {
        this(context, null);
    }

    public MinimizedPlaybackControlBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MinimizedPlaybackControlBar(Context context, AttributeSet attrs, int defStyleAttrs) {
        super(context, attrs, defStyleAttrs, R.layout.minimized_playback_control_bar);
        init(context);
    }

    private void init(Context context) {
        mMediaButtonController = new MediaButtonController(context, this,
                R.color.playback_control_color, R.layout.play_pause_stop_button_layout,
                R.drawable.ic_skip_previous, R.drawable.ic_skip_next);

        mShowLinearProgressBar = context.getResources().getBoolean(R.bool.show_linear_progress_bar);
        mLinearProgressBar = findViewById(R.id.linear_progress_bar);

        mShowCircularProgressBar = context.getResources().getBoolean(
                R.bool.show_circular_progress_bar);
        mCircularProgressBar = findViewById(R.id.circular_progress_bar);
    }

    /** Connects the bar to the {@link PlaybackViewModel}. */
    public void setModel(@NonNull PlaybackViewModel model, @NonNull LifecycleOwner owner,
            @NonNull Size maxArtSize) {
        mMediaButtonController.setModel(model, owner);
        mMetadataController = new MetadataController(owner, model,
                mTitle, mSubtitle, null, null, null, null, null, null, mContentTile, maxArtSize);
        mPlaybackViewModel = model;

        ControlBarHelper.initProgressBar(getContext(), owner, mPlaybackViewModel,
                mLinearProgressBar, mShowLinearProgressBar);
        ControlBarHelper.initProgressBar(getContext(), owner, mPlaybackViewModel,
                mCircularProgressBar, mShowCircularProgressBar);
    }
}
