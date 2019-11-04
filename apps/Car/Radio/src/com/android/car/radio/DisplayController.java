/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.car.radio;

import android.animation.ValueAnimator;
import android.content.Context;
import android.hardware.radio.ProgramSelector;
import android.media.session.PlaybackState;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.android.car.broadcastradio.support.platform.ProgramSelectorExt;
import com.android.car.radio.bands.ProgramType;
import com.android.car.radio.service.RadioAppServiceWrapper;
import com.android.car.radio.service.RadioAppServiceWrapper.ConnectionState;
import com.android.car.radio.util.Log;
import com.android.car.radio.widget.PlayPauseButton;

import java.util.Objects;

/**
 * Controller that controls the appearance state of various UI elements in the radio.
 */
public class DisplayController {
    private static final String TAG = "BcRadioApp.display";

    private static final int CHANNEL_CHANGE_DURATION_MS = 200;
    private static final char EN_DASH = '\u2013';
    private static final String DETAILS_SEPARATOR = " " + EN_DASH + " ";

    private final Context mContext;

    private final View mTabs;
    private final View mViewpager;
    private final TextView mStatusMessage;
    private final TextView mChannel;
    private final TextView mDetails;
    private final TextView mStationName;

    private final ImageView mBackwardSeekButton;
    private final ImageView mForwardSeekButton;
    private final PlayPauseButton mPlayButton;
    private final View mBandButton;

    private boolean mIsFavorite = false;
    private final ImageView mFavoriteButton;
    private FavoriteToggleListener mFavoriteToggleListener;

    private final ValueAnimator mChannelAnimator = new ValueAnimator();
    private @Nullable ProgramSelector mDisplayedChannel;

    /**
     * Callback for favorite toggle button.
     */
    public interface FavoriteToggleListener {
        /**
         * Called when favorite toggle button was clicked.
         *
         * @param addFavorite {@code} true, if the callback should add the current program to
         *        favorites, {@code false} otherwise.
         */
        void onFavoriteToggled(boolean addFavorite);
    }

    public DisplayController(@NonNull FragmentActivity activity,
            @NonNull RadioController radioController) {
        mContext = Objects.requireNonNull(activity);

        mTabs = activity.findViewById(R.id.tabs);
        mViewpager = activity.findViewById(R.id.viewpager);
        mStatusMessage = activity.findViewById(R.id.status_message);
        mChannel = activity.findViewById(R.id.station_channel);
        mDetails = activity.findViewById(R.id.station_details);
        mStationName = activity.findViewById(R.id.station_name);
        mBackwardSeekButton = activity.findViewById(R.id.back_button);
        mForwardSeekButton = activity.findViewById(R.id.forward_button);
        mPlayButton = activity.findViewById(R.id.play_button);
        mBandButton = activity.findViewById(R.id.band_toggle_button);
        mFavoriteButton = activity.findViewById(R.id.add_presets_button);

        radioController.getPlaybackState().observe(activity, this::onPlaybackStateChanged);

        if (mFavoriteButton != null) {
            mFavoriteButton.setOnClickListener(v -> {
                FavoriteToggleListener listener = mFavoriteToggleListener;
                if (listener != null) listener.onFavoriteToggled(!mIsFavorite);
            });
        }
    }

    /**
     * Sets application state.
     *
     * This shows/hides the UI elements and may display error messages (depending on the current
     * application state).
     *
     * If the UI is disabled/hidden, then no callbacks will be triggered.
     *
     * @param state Current application state
     */
    public void setState(@ConnectionState int state) {
        Log.d(TAG, "Adjusting the UI to a new application state: " + state);

        boolean enabled = (state == RadioAppServiceWrapper.STATE_CONNECTED);

        // Color the buttons so that they are grey in appearance if they are disabled.
        int tint = enabled
                ? mContext.getColor(R.color.control_button_color)
                : mContext.getColor(R.color.control_button_disabled_color);

        if (mPlayButton != null) {
            // No need to tint the play button because its drawable already contains a disabled
            // state.
            mPlayButton.setEnabled(enabled);
        }

        if (mForwardSeekButton != null) {
            mForwardSeekButton.setEnabled(enabled);
            mForwardSeekButton.setColorFilter(tint);
        }
        if (mBackwardSeekButton != null) {
            mBackwardSeekButton.setEnabled(enabled);
            mBackwardSeekButton.setColorFilter(tint);
        }
        if (mBandButton != null) {
            mBandButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
        if (mFavoriteButton != null) {
            mFavoriteButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
        if (mTabs != null) {
            mTabs.setVisibility(enabled ? View.VISIBLE : View.INVISIBLE);
        }
        if (mViewpager != null) {
            mViewpager.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }

        if (mStatusMessage != null) {
            mStatusMessage.setVisibility(enabled ? View.GONE : View.VISIBLE);
            switch (state) {
                case RadioAppServiceWrapper.STATE_CONNECTING:
                case RadioAppServiceWrapper.STATE_CONNECTED:
                    mStatusMessage.setText(null);
                    break;
                case RadioAppServiceWrapper.STATE_NOT_SUPPORTED:
                    mStatusMessage.setText(R.string.radio_not_supported_text);
                    break;
                case RadioAppServiceWrapper.STATE_ERROR:
                    mStatusMessage.setText(R.string.radio_failure_text);
                    break;
            }
        }
    }

    /**
     * Sets the {@link android.view.View.OnClickListener} for the backwards seek button.
     */
    public void setBackwardSeekButtonListener(View.OnClickListener listener) {
        if (mBackwardSeekButton != null) {
            mBackwardSeekButton.setOnClickListener(listener);
        }
    }

    /**
     * Sets the {@link android.view.View.OnClickListener} for the forward seek button.
     */
    public void setForwardSeekButtonListener(View.OnClickListener listener) {
        if (mForwardSeekButton != null) {
            mForwardSeekButton.setOnClickListener(listener);
        }
    }

    /**
     * Sets the callback for the play/pause button.
     */
    public void setPlayButtonCallback(@Nullable PlayPauseButton.Callback callback) {
        if (mPlayButton == null) return;
        mPlayButton.setCallback(callback);
    }

    /**
     * Sets the listener for favorite toggle button.
     *
     * @param listener Listener to set, or {@code null} to remove
     */
    public void setFavoriteToggleListener(@Nullable FavoriteToggleListener listener) {
        mFavoriteToggleListener = listener;
    }

    /**
     * Sets the current radio channel (e.g. 88.5 FM).
     *
     * If the channel is of the same type (band) as currently displayed, animates the transition.
     *
     * @param sel Channel to display
     */
    public void setChannel(@Nullable ProgramSelector sel) {
        if (mChannel == null) return;

        mChannelAnimator.cancel();

        if (sel == null) {
            mChannel.setText(null);
        } else if (!ProgramSelectorExt.isAmFmProgram(sel)
                || !ProgramSelectorExt.hasId(sel, ProgramSelector.IDENTIFIER_TYPE_AMFM_FREQUENCY)) {
            // channel animation is implemented for AM/FM only
            mChannel.setText(ProgramSelectorExt.getDisplayName(sel, 0));
        } else if (ProgramType.fromSelector(mDisplayedChannel)
                != ProgramType.fromSelector(sel)) {
            // it's a different band - don't animate
            mChannel.setText(ProgramSelectorExt.getDisplayName(sel, 0));
        } else {
            int fromFreq = (int) mDisplayedChannel
                    .getFirstId(ProgramSelector.IDENTIFIER_TYPE_AMFM_FREQUENCY);
            int toFreq = (int) sel.getFirstId(ProgramSelector.IDENTIFIER_TYPE_AMFM_FREQUENCY);
            mChannelAnimator.setIntValues((int) fromFreq, (int) toFreq);
            mChannelAnimator.setDuration(CHANNEL_CHANGE_DURATION_MS);
            mChannelAnimator.addUpdateListener(animation -> mChannel.setText(
                    ProgramSelectorExt.formatAmFmFrequency((int) animation.getAnimatedValue(), 0)));
            mChannelAnimator.start();
        }

        mDisplayedChannel = sel;
    }

    /**
     * Sets program details.
     *
     * @param details Program details (title/artist or radio text).
     */
    public void setDetails(@Nullable String details) {
        if (mDetails == null) return;
        mDetails.setText(details);
        mDetails.setVisibility(TextUtils.isEmpty(details) ? View.INVISIBLE : View.VISIBLE);
    }

    /**
     * Sets program details (title/artist of currently playing song).
     *
     * @param songTitle Title of currently playing song
     * @param songArtist Artist of currently playing song
     */
    public void setDetails(@Nullable String songTitle, @Nullable String songArtist) {
        if (mDetails == null) return;
        songTitle = songTitle.trim();
        songArtist = songArtist.trim();
        if (TextUtils.isEmpty(songTitle)) songTitle = null;
        if (TextUtils.isEmpty(songArtist)) songArtist = null;

        String details;
        if (songTitle == null && songArtist == null) {
            details = null;
        } else if (songTitle == null) {
            details = songArtist;
        } else if (songArtist == null) {
            details = songTitle;
        } else {
            details = songArtist + DETAILS_SEPARATOR + songTitle;
        }

        setDetails(details);
    }

    /**
     * Sets the artist(s) of the currently playing song or current radio station information
     * (e.g. KOIT).
     */
    public void setStationName(@Nullable String name) {
        if (mStationName == null) return;
        boolean isEmpty = TextUtils.isEmpty(name);
        mStationName.setText(isEmpty ? null : name.trim());
        mStationName.setVisibility(isEmpty ? View.INVISIBLE : View.VISIBLE);
    }

    private void onPlaybackStateChanged(@PlaybackState.State int state) {
        if (mPlayButton != null) {
            mPlayButton.setPlayState(state);
            mPlayButton.refreshDrawableState();
        }
    }

    /**
     * Sets whether or not the current program is stored as a favorite. If it is, then the
     * icon will be updatd to reflect this state.
     */
    public void setCurrentIsFavorite(boolean isFavorite) {
        mIsFavorite = isFavorite;
        if (mFavoriteButton == null) return;
        mFavoriteButton.setImageResource(
                isFavorite ? R.drawable.ic_star_filled : R.drawable.ic_star_empty);
    }

    /**
     * Starts seek animation.
     *
     * TODO(b/111340798): implement actual animation
     * TODO(b/111340798): remove forward parameter, if not necessary for animation
     *
     * @param forward {@code true} for forward seek, {@code false} otherwise.
     */
    public void startSeekAnimation(boolean forward) {
        // TODO(b/111340798): watch for timeout and if it happens, display metadata back
        setStationName(null);
        setDetails(null);
    }
}
