/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.wallpaper.module;

import com.android.wallpaper.model.WallpaperMetadata;
import com.android.wallpaper.module.WallpaperPreferences.PresentationMode;

import androidx.annotation.Nullable;

/**
 * Interface for classes which refresh stored wallpaper metadata against the currently set
 * wallpaper.
 */
public interface WallpaperRefresher {

    /**
     * Refreshes the wallpaper metadata stored in {@link WallpaperPreferences} against the wallpaper
     * manager and calls the provided listener with refreshed metadata.
     */
    void refresh(RefreshListener listener);

    /**
     * Interface for receiving refreshed wallpaper metadata.
     */
    interface RefreshListener {

        /**
         * Provides metadata representing the current home screen and lock screen wallpaper as well as
         * the current wallpaper presentation mode.
         */
        void onRefreshed(WallpaperMetadata homeWallpaperMetadata,
                         @Nullable WallpaperMetadata lockWallpaperMetadata, @PresentationMode int presentationMode);
    }
}
