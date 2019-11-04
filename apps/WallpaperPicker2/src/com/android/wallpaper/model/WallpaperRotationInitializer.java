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
package com.android.wallpaper.model;

import android.content.Context;
import android.os.Parcelable;

import androidx.annotation.IntDef;

/**
 * Interface for objects which initialize daily wallpaper rotations.
 */
public interface WallpaperRotationInitializer extends Parcelable {

    /**
     * OK to download on both metered (i.e., cellular) and unmetered (i.e., wifi) networks.
     */
    int NETWORK_PREFERENCE_CELLULAR_OK = 0;
    /**
     * Only download wallpapers on unmetered (i.e., wifi) networks.
     */
    int NETWORK_PREFERENCE_WIFI_ONLY = 1;
    int ROTATION_NOT_INITIALIZED = 0;
    int ROTATION_HOME_ONLY = 1;
    int ROTATION_HOME_AND_LOCK = 2;

    /**
     * Starts a daily wallpaper rotation.
     *
     * @param appContext The application's context.
     * @return Whether rotation started successfully.
     */
    boolean startRotation(Context appContext);

    /**
     * Sets the first wallpaper in a daily rotation to the device. Must be called before a call to
     * {@code startRotation(appContext)}.
     *
     * @param appContext        The application's context.
     * @param networkPreference The user's network preference for downloading wallpapers in rotation.
     * @param listener          Called when the first wallpaper in rotation has been downloaded and set to the
     *                          device.
     */
    void setFirstWallpaperInRotation(Context appContext, @NetworkPreference int networkPreference,
                                     Listener listener);

    /**
     * Returns whether the live wallpaper needs to be set to the device in order to be able to start
     * rotation or is already set but on home-only on N-MR2 or later, which means the user has the
     * option to pick a new destination preference.
     */
    boolean isNoBackupImageWallpaperPreviewNeeded(Context appContext);

    /**
     * Gets the current state of the possible wallpaper rotation represented by this object.
     */
    void fetchRotationInitializationState(Context context, RotationStateListener listener);

    /**
     * Checks and returns the last-known rotation intialization state without doing a full refresh,
     * which would perform some disk I/O. Therefore, this method can be called safely from the main
     * thread but the data returned here could be stale.
     */
    @RotationInitializationState
    int getRotationInitializationStateDirty(Context context);

    /**
     * Possible network preferences for downloading wallpapers in rotation.
     */
    @IntDef({
            NETWORK_PREFERENCE_CELLULAR_OK,
            NETWORK_PREFERENCE_WIFI_ONLY})
    @interface NetworkPreference {
    }

    /**
     * Possible states of rotation initialization.
     */
    @IntDef({
            ROTATION_NOT_INITIALIZED,
            ROTATION_HOME_ONLY,
            ROTATION_HOME_AND_LOCK})
    @interface RotationInitializationState {
    }

    /**
     * Listener interface for clients to asynchronously receive the rotation initialization state of
     * this rotation initializer.
     */
    interface RotationStateListener {
        void onRotationStateReceived(@RotationInitializationState int rotationInitializationState);
    }

    /**
     * Listener interface which can be implemented to listen for the initialization status of a
     * wallpaper rotation.
     */
    interface Listener {
        void onFirstWallpaperInRotationSet();

        void onError();
    }
}
