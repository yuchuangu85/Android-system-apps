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
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import com.android.car.apps.common.CarControlBar;
import com.android.car.apps.common.CommonFlags;
import com.android.car.apps.common.ControlBar;
import com.android.car.media.common.playback.PlaybackViewModel;
import com.android.car.media.common.source.MediaSourceColors;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class manages the media control buttons that are added to a CarControlBar.
 *
 * It expects the icons to be {@link VectorDrawable}s (because they look better), and will flag
 * non compliant ones if {@link MediaItemMetadata#flagInvalidMediaArt} returns true.
 */
public class MediaButtonController {

    private static final String TAG = "MediaButton";
    private static final float ALPHA_ENABLED = 1.0F;
    private static final float ALPHA_DISABLED = 0.5F;

    private Context mContext;
    private PlayPauseStopImageView mPlayPauseStopImageView;
    private View mPlayPauseStopImageContainer;
    private ProgressBar mCircularProgressBar;
    private ImageButton mSkipPrevButton;
    private ImageButton mSkipNextButton;
    private ColorStateList mIconsColor;
    private boolean mSkipNextAdded;
    private boolean mSkipPrevAdded;
    private boolean mShowCircularProgressBar;

    private PlaybackViewModel mModel;
    private PlaybackViewModel.PlaybackController mController;

    private CarControlBar mControlBar;

    public MediaButtonController(Context context, CarControlBar controlBar,
            @ColorRes int iconColorsId, @LayoutRes int playPauseContainerId,
            @DrawableRes int skipPrevButtonId, @DrawableRes int skipNextButtonId) {
        mContext = context;
        mControlBar = controlBar;
        mPlayPauseStopImageContainer =
                LayoutInflater.from(context).inflate(playPauseContainerId, null);
        mPlayPauseStopImageContainer.setOnClickListener(this::onPlayPauseStopClicked);
        mPlayPauseStopImageView = mPlayPauseStopImageContainer.findViewById(R.id.play_pause_stop);
        mPlayPauseStopImageView.setVisibility(View.INVISIBLE);
        mCircularProgressBar = mPlayPauseStopImageContainer.findViewById(
                R.id.circular_progress_bar);
        mPlayPauseStopImageView.setAction(PlayPauseStopImageView.ACTION_DISABLED);
        mPlayPauseStopImageView.setOnClickListener(this::onPlayPauseStopClicked);

        mShowCircularProgressBar = context.getResources().getBoolean(
                R.bool.show_circular_progress_bar);
        mIconsColor = context.getResources().getColorStateList(iconColorsId, null);

        mSkipPrevButton = createIconButton(context.getDrawable(skipPrevButtonId));
        mSkipPrevButton.setId(R.id.skip_prev);
        mSkipPrevButton.setVisibility(View.VISIBLE);
        mSkipPrevButton.setOnClickListener(this::onPrevClicked);

        mSkipNextButton = createIconButton(context.getDrawable(skipNextButtonId));
        mSkipNextButton.setId(R.id.skip_next);
        mSkipNextButton.setVisibility(View.VISIBLE);
        mSkipNextButton.setOnClickListener(this::onNextClicked);

        resetInitialViews();
    }

    /**
     * Set the model that the available media buttons will be provided from
     */
    public void setModel(@NonNull PlaybackViewModel model, @NonNull LifecycleOwner owner) {
        if (mModel != null) {
            Log.w(TAG, "PlaybackViewModel set more than once. Ignoring subsequent call.");
        }
        mModel = model;

        model.getPlaybackController().observe(owner, controller -> {
            if (mController != controller) {
                mController = controller;
                resetInitialViews();
            }
        });
        mPlayPauseStopImageView.setVisibility(View.VISIBLE);
        boolean useMediaSourceColor =
                mContext.getResources().getBoolean(R.bool.use_media_source_color_for_fab_spinner);
        if (useMediaSourceColor) {
            model.getMediaSourceColors().observe(owner, this::updateSpinerColors);
        }
        model.getPlaybackStateWrapper().observe(owner, this::onPlaybackStateChanged);
    }

    private void resetInitialViews() {
        mControlBar.setViews(new View[0]);
        mControlBar.setView(mPlayPauseStopImageContainer, ControlBar.SLOT_MAIN);
        mControlBar.setView(null, ControlBar.SLOT_LEFT);
        mControlBar.setView(null, ControlBar.SLOT_RIGHT);
        mSkipNextAdded = false;
        mSkipPrevAdded = false;
    }

    private ImageButton createIconButton(Drawable icon) {
        ImageButton button = mControlBar.createIconButton(icon);
        boolean flagInvalidArt = CommonFlags.getInstance(mContext).shouldFlagImproperImageRefs();
        if (flagInvalidArt && !(icon instanceof VectorDrawable)) {
            button.setImageTintList(
                    ColorStateList.valueOf(MediaItemMetadata.INVALID_MEDIA_ART_TINT_COLOR));
        } else {
            button.setImageTintList(mIconsColor);
        }
        button.setImageTintMode(PorterDuff.Mode.SRC_ATOP);
        return button;
    }

    private void onPlaybackStateChanged(@Nullable PlaybackViewModel.PlaybackStateWrapper state) {

        boolean hasState = (state != null);
        mPlayPauseStopImageView.setAction(convertMainAction(state));
        boolean isLoading = hasState && state.isLoading();
        mCircularProgressBar.setVisibility(
                isLoading || mShowCircularProgressBar ? View.VISIBLE : View.INVISIBLE);
        mCircularProgressBar.setIndeterminate(isLoading);

        // If prev/next is reserved, but not enabled, the icon is displayed as disabled (inactive
        // or grayed out). For example some apps only allow a certain number of skips in a given
        // time.

        boolean skipPreviousReserved = hasState && state.iSkipPreviousReserved();
        boolean skipPreviousEnabled = hasState && state.isSkipPreviousEnabled();

        if (skipPreviousReserved || skipPreviousEnabled) {
            if (!mSkipPrevAdded) {
                mControlBar.setView(mSkipPrevButton, ControlBar.SLOT_LEFT);
                mSkipPrevAdded = true;
            }
        } else {
            mControlBar.setView(null, ControlBar.SLOT_LEFT);
            mSkipPrevAdded = false;
        }

        if (skipPreviousEnabled) {
            mSkipPrevButton.setAlpha(ALPHA_ENABLED);
        } else {
            mSkipPrevButton.setAlpha(ALPHA_DISABLED);
        }
        mSkipPrevButton.setEnabled(skipPreviousEnabled);

        boolean skipNextReserved = hasState && state.isSkipNextReserved();
        boolean skipNextEnabled = hasState && state.isSkipNextEnabled();

        if (skipNextReserved || skipNextEnabled) {
            if (!mSkipNextAdded) {
                mControlBar.setView(mSkipNextButton, ControlBar.SLOT_RIGHT);
                mSkipNextAdded = true;
            }
        } else {
            mControlBar.setView(null, ControlBar.SLOT_RIGHT);
            mSkipNextAdded = false;
        }

        if (skipNextEnabled) {
            mSkipNextButton.setAlpha(ALPHA_ENABLED);
        } else {
            mSkipNextButton.setAlpha(ALPHA_DISABLED);
        }
        mSkipNextButton.setEnabled(skipNextEnabled);

        updateCustomActions(state);
    }

    @PlayPauseStopImageView.Action
    private int convertMainAction(@Nullable PlaybackViewModel.PlaybackStateWrapper state) {
        @PlaybackViewModel.Action int action =
                (state != null) ? state.getMainAction() : PlaybackViewModel.ACTION_DISABLED;
        switch (action) {
            case PlaybackViewModel.ACTION_DISABLED:
                return PlayPauseStopImageView.ACTION_DISABLED;
            case PlaybackViewModel.ACTION_PLAY:
                return PlayPauseStopImageView.ACTION_PLAY;
            case PlaybackViewModel.ACTION_PAUSE:
                return PlayPauseStopImageView.ACTION_PAUSE;
            case PlaybackViewModel.ACTION_STOP:
                return PlayPauseStopImageView.ACTION_STOP;
        }
        Log.w(TAG, "Unknown action: " + action);
        return PlayPauseStopImageView.ACTION_DISABLED;
    }

    private void updateSpinerColors(MediaSourceColors colors) {
        int color = getMediaSourceColor(colors);
        mCircularProgressBar.setIndeterminateTintList(ColorStateList.valueOf(color));
    }

    private int getMediaSourceColor(@Nullable MediaSourceColors colors) {
        int defaultColor = mContext.getResources().getColor(R.color.media_source_default_color,
                null);
        return colors != null ? colors.getAccentColor(defaultColor) : defaultColor;
    }

    private void updateCustomActions(@Nullable PlaybackViewModel.PlaybackStateWrapper state) {
        List<ImageButton> imageButtons = new ArrayList<>();
        if (state != null) {
            imageButtons.addAll(state.getCustomActions()
                    .stream()
                    .map(rawAction -> rawAction.fetchDrawable(mContext))
                    .map(action -> {
                        ImageButton button = createIconButton(action.mIcon);
                        button.setOnClickListener(view ->
                                mController.doCustomAction(action.mAction, action.mExtras));
                        return button;
                    })
                    .collect(Collectors.toList()));
        }
        if (!mSkipPrevAdded && !imageButtons.isEmpty()) {
            mControlBar.setView(imageButtons.remove(0), CarControlBar.SLOT_LEFT);
        }
        if (!mSkipNextAdded && !imageButtons.isEmpty()) {
            mControlBar.setView(imageButtons.remove(0), CarControlBar.SLOT_RIGHT);
        }
        mControlBar.setViews(imageButtons.toArray(new ImageButton[0]));
    }

    private void onPlayPauseStopClicked(View view) {
        if (mController == null) {
            return;
        }
        switch (mPlayPauseStopImageView.getAction()) {
            case PlayPauseStopImageView.ACTION_PLAY:
                mController.play();
                break;
            case PlayPauseStopImageView.ACTION_PAUSE:
                mController.pause();
                break;
            case PlayPauseStopImageView.ACTION_STOP:
                mController.stop();
                break;
            default:
                Log.i(TAG, "Play/Pause/Stop clicked on invalid state");
                break;
        }
    }

    private void onNextClicked(View view) {
        PlaybackViewModel.PlaybackStateWrapper state = getPlaybackState();
        if ((mController != null) && (state != null) && (state.isSkipNextEnabled())) {
            mController.skipToNext();
        }
    }

    private void onPrevClicked(View view) {
        PlaybackViewModel.PlaybackStateWrapper state = getPlaybackState();
        if ((mController != null) && (state != null) && (state.isSkipPreviousEnabled())) {
            mController.skipToPrevious();
        }
    }

    private PlaybackViewModel.PlaybackStateWrapper getPlaybackState() {
        if (mModel != null) {
            return mModel.getPlaybackStateWrapper().getValue();
        }
        return null;
    }

}
