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

import android.content.Context;

/**
 * Interface for objects which refresh the current wallpaper in a rotation.
 */
public interface WallpaperRotationRefresher {

    void refreshWallpaper(Context context, Listener listener);

    /**
     * Listener interface for clients to asynchronously receive status of wallpaper rotation refresh
     * tasks.
     */
    interface Listener {

        /**
         * Called when a new wallpaper in the rotation has successfully been set to the device.
         */
        void onRefreshed();

        /**
         * Called when there was an error setting a new wallpaper in the rotation to the device.
         */
        void onError();
    }
}
