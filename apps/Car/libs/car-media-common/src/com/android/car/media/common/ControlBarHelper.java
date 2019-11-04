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
import android.view.View;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import com.android.car.media.common.playback.PlaybackViewModel;

/**
 * Helper class for {@link PlaybackControlsActionBar} and {@link MinimizedPlaybackControlBar}.
 */
public class ControlBarHelper {

    /**
     * Initializes progress bar, i.e., sets progress tint and progress update listener.
     */
    public static void initProgressBar(@NonNull Context context, @NonNull LifecycleOwner owner,
            @NonNull PlaybackViewModel model, @Nullable ProgressBar progressBar,
            boolean showProgressBar) {
        if (progressBar == null) {
            return;
        }
        if (!showProgressBar) {
            progressBar.setVisibility(View.GONE);
            return;
        }
        boolean useMediaSourceColor =
                context.getResources().getBoolean(
                        R.bool.use_media_source_color_for_minimized_progress_bar);
        int defaultColor = context.getResources().getColor(R.color.minimized_progress_bar_highlight,
                null);
        if (useMediaSourceColor) {
            model.getMediaSourceColors().observe(owner,
                    sourceColors -> {
                        int color = sourceColors != null ? sourceColors.getAccentColor(
                                defaultColor)
                                : defaultColor;
                        progressBar.setProgressTintList(ColorStateList.valueOf(color));
                    });
        } else {
            progressBar.setProgressTintList(ColorStateList.valueOf(defaultColor));
        }

        model.getProgress().observe(owner,
                progress -> {
                    progressBar.setProgress((int) progress.getProgress());
                    progressBar.setMax((int) progress.getMaxProgress());
                });
    }
}
