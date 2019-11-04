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

package com.android.car.media.common;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageButton;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;

import com.android.car.apps.common.ControlBar;
import com.android.car.media.common.playback.PlaybackViewModel;

/**
 * Basic playback control bar (doesn't display any metadata).
 */
public class PlaybackControlsActionBar extends ControlBar {

    private ImageButton mOverflowButton;
    private ProgressBar mCircularProgressBar;

    private MediaButtonController mMediaButtonController;

    private boolean mShowCircularProgressBar;

    /** Creates a {@link PlaybackControlsActionBar} view */
    public PlaybackControlsActionBar(Context context) {
        this(context, null, 0, 0);
    }

    /** Creates a {@link PlaybackControlsActionBar} view */
    public PlaybackControlsActionBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0, 0);
    }

    /** Creates a {@link PlaybackControlsActionBar} view */
    public PlaybackControlsActionBar(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    /** Creates a {@link PlaybackControlsActionBar} view */
    public PlaybackControlsActionBar(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    private void init(Context context) {
        mOverflowButton = createIconButton(context.getDrawable(R.drawable.ic_overflow_button),
                R.layout.control_bar_selectable_button);
        mOverflowButton.setId(R.id.overflow);
        setExpandCollapseView(mOverflowButton);

        mMediaButtonController = new MediaButtonController(context, this,
                R.color.playback_control_color, R.layout.play_pause_stop_button_layout,
                R.drawable.ic_skip_previous, R.drawable.ic_skip_next);

        mShowCircularProgressBar = context.getResources().getBoolean(
                R.bool.show_circular_progress_bar);
        mCircularProgressBar = findViewById(R.id.circular_progress_bar);
    }

    public void setModel(@NonNull PlaybackViewModel model, @NonNull LifecycleOwner owner) {
        mMediaButtonController.setModel(model, owner);
        ControlBarHelper.initProgressBar(getContext(), owner, model, mCircularProgressBar,
                mShowCircularProgressBar);
    }
}
