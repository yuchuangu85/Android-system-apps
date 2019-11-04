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
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.android.wallpaper.compat.BuildCompat;
import com.android.wallpaper.compat.WallpaperManagerCompat;

import java.io.IOException;

/**
 * Checks whether an explicit lock wallpaper is set to the device (i.e., independently shown under
 * the keyguard and separate from the wallpaper shown under the user's launcher).
 */
public class LockWallpaperStatusChecker {

    private static final String TAG = "LockWPStatusChecker";

    /**
     * Returns whether a lock screen wallpaper is independently set to the device.
     */
    public static boolean isLockWallpaperSet(Context context) {
        // Lock screen wallpapers are not supported until Android N.
        if (!BuildCompat.isAtLeastN()) {
            return false;
        }

        WallpaperManagerCompat wallpaperManagerCompat = InjectorProvider.getInjector()
                .getWallpaperManagerCompat(context);
        ParcelFileDescriptor parcelFd =
                wallpaperManagerCompat.getWallpaperFile(WallpaperManagerCompat.FLAG_LOCK);
        boolean isSet = parcelFd != null;
        if (isSet) {
            try {
                // Close the ParcelFileDescriptor if it's not null to avoid a resource leak.
                parcelFd.close();
            } catch (IOException e) {
                Log.e(TAG, "IO exception when closing the lock screen wallpaper file descriptor.");
            }
        }
        return isSet;
    }
}
