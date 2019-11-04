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

import static com.android.car.arch.common.LiveDataFunctions.combine;
import static com.android.car.arch.common.LiveDataFunctions.pair;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.text.TextUtils;
import android.util.Size;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.lifecycle.LifecycleOwner;

import com.android.car.apps.common.imaging.ImageViewBinder;
import com.android.car.apps.common.util.ViewUtils;
import com.android.car.media.common.playback.PlaybackViewModel;

/**
 * Common controller for displaying current track's metadata.
 */
public class MetadataController {
    private PlaybackViewModel.PlaybackController mController;
    private final ImageViewBinder<MediaItemMetadata.ArtworkRef> mAlbumArtBinder;

    private boolean mTrackingTouch;
    private SeekBar.OnSeekBarChangeListener mOnSeekBarChangeListener =
            new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    // Do nothing.
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    mTrackingTouch = true;
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    if (mTrackingTouch && mController != null) {
                        mController.seekTo(seekBar.getProgress());
                    }
                    mTrackingTouch = false;
                }
            };

    /**
     * Create a new MetadataController that operates on the provided Views
     *
     * Note: when the text of a TextView is empty, its visibility will be set to View.INVISIBLE
     * instead of View.GONE. Thus the views stay in the same position, and the constraint chains of
     * the layout won't be disrupted.
     *
     * @param lifecycleOwner    The lifecycle scope for the Views provided to this controller.
     * @param playbackViewModel The Model to provide metadata for display.
     * @param title             Displays the track's title. Must not be {@code null}.
     * @param artist            Displays the track's artist. May be {@code null}.
     * @param albumTitle        Displays the track's album title. May be {@code null}.
     * @param outerSeparator    Displays the separator between the album title and time. May be
     *                          {@code null}.
     * @param currentTime       Displays the track's current position as text. May be {@code null}.
     * @param innerSeparator    Displays the separator between the currentTime and the maxTime. May
     *                          be {@code null}.
     * @param maxTime           Displays the track's duration as text. May be {@code null}.
     * @param seekBar           Displays the track's progress visually. May be {@code null}.
     * @param albumArt          Displays the track's album art. May be {@code null}.
     * @param maxArtSize        Maximum size of the track's album art.
     */
    public MetadataController(@NonNull LifecycleOwner lifecycleOwner,
            @NonNull PlaybackViewModel playbackViewModel, @NonNull TextView title,
            @Nullable TextView artist, @Nullable TextView albumTitle,
            @Nullable TextView outerSeparator, @Nullable TextView currentTime,
            @Nullable TextView innerSeparator, @Nullable TextView maxTime,
            @Nullable SeekBar seekBar, @Nullable ImageView albumArt, Size maxArtSize) {

        Context context = title.getContext();
        mAlbumArtBinder = new ImageViewBinder<>(maxArtSize, albumArt);

        playbackViewModel.getPlaybackController().observe(lifecycleOwner,
                controller -> mController = controller);
        playbackViewModel.getMetadata().observe(lifecycleOwner,
                metadata -> {
                    if (metadata == null) {
                        ViewUtils.setVisible(title, false);
                        ViewUtils.setVisible(artist, false);
                        ViewUtils.setVisible(albumTitle, false);
                        ViewUtils.setVisible(albumArt, false);
                        return;
                    }
                    CharSequence titleName = metadata.getTitle();
                    if (TextUtils.isEmpty(titleName)) {
                        titleName = context.getString(R.string.metadata_default_title);
                    }
                    title.setText(titleName);
                    ViewUtils.setVisible(title, true);

                    if (artist != null) {
                        CharSequence artistName = metadata.getArtist();
                        artist.setText(artistName);
                        ViewUtils.setVisible(artist, !TextUtils.isEmpty(artistName));
                    }

                    mAlbumArtBinder.setImage(context, metadata.getArtworkKey());
                });

        playbackViewModel.getProgress().observe(lifecycleOwner,
                playbackProgress -> {
                    boolean hasTime = playbackProgress.hasTime();
                    ViewUtils.setVisible(currentTime, hasTime);
                    ViewUtils.setVisible(innerSeparator, hasTime);
                    ViewUtils.setVisible(maxTime, hasTime);

                    if (currentTime != null) {
                        currentTime.setText(playbackProgress.getCurrentTimeText());
                    }
                    if (maxTime != null) {
                        maxTime.setText(playbackProgress.getMaxTimeText());
                    }
                    if (seekBar != null) {
                        seekBar.setVisibility(hasTime ? View.VISIBLE : View.INVISIBLE);
                        seekBar.setMax((int) playbackProgress.getMaxProgress());
                        if (!mTrackingTouch) {
                            seekBar.setProgress((int) playbackProgress.getProgress());
                        }
                    }
                });

        if (seekBar != null) {
            playbackViewModel.getPlaybackStateWrapper().observe(lifecycleOwner,
                    state -> {
                        boolean enabled = state != null && state.isSeekToEnabled();
                        mTrackingTouch = false;
                        if (seekBar.getThumb() != null) {
                            seekBar.getThumb().mutate().setAlpha(enabled ? 255 : 0);
                        }
                        final boolean shouldHandleTouch = seekBar.getThumb() != null && enabled;
                        seekBar.setOnTouchListener(
                                (v, event) -> !shouldHandleTouch /* consumeEvent */);
                    });
            seekBar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
            playbackViewModel.getPlaybackStateWrapper().observe(lifecycleOwner,
                    state -> mTrackingTouch = false);
        }

        if (outerSeparator != null) {
            combine(playbackViewModel.getMetadata(), playbackViewModel.getProgress(),
                    (metadata, progress) -> metadata != null
                            && !TextUtils.isEmpty(metadata.getAlbumTitle()) && progress.hasTime())
                    .observe(lifecycleOwner,
                            visible -> ViewUtils.setVisible(outerSeparator, visible));
        }

        if (albumTitle != null) {
            pair(playbackViewModel.getMetadata(), playbackViewModel.getProgress()).observe(
                    lifecycleOwner, pair -> {
                        CharSequence albumName =
                                pair.first == null ? null : pair.first.getAlbumTitle();
                        albumTitle.setText(albumName);

                        boolean hasAlbumName = !TextUtils.isEmpty(albumName);
                        boolean hasTime = pair.second.hasTime();
                        if (hasAlbumName) {
                            ViewUtils.setVisible(albumTitle, true);
                        } else if (hasTime) {
                            // In layout file, artist is constrained to albumTitle. When album
                            // name is empty but progress is not empty, the visibility of
                            // albumTitle should be INVISIBLE instead of GONE, otherwise the
                            // constraint will be broken.
                            ViewUtils.setInvisible(albumTitle, true);
                        } else {
                            ViewUtils.setVisible(albumTitle, false);
                        }
                    });
        }
    }
}
