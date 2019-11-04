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

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Rect;

import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.model.WallpaperInfo;

import java.util.List;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

/**
 * Interface for classes which persist wallpapers to the system.
 */
public interface WallpaperPersister {

    int DEST_HOME_SCREEN = 0;
    int DEST_LOCK_SCREEN = 1;
    int DEST_BOTH = 2;
    int WALLPAPER_POSITION_CENTER = 0;
    int WALLPAPER_POSITION_CENTER_CROP = 1;
    int WALLPAPER_POSITION_STRETCH = 2;

    /**
     * Sets a static individual wallpaper to the system via the WallpaperManager.
     *
     * @param wallpaper   Wallpaper model object. Wallpaper image will be set from the asset provided
     *                    to this method.
     * @param asset       Wallpaper asset from which to retrieve image data.
     * @param cropRect    Desired crop area of the wallpaper in post-scale units. If null, then the
     *                    wallpaper image will be set without any scaling or cropping.
     * @param scale       Scaling factor applied to the source image before setting the wallpaper to the
     *                    device.
     * @param destination The destination - where to set the wallpaper to.
     * @param callback    Called once the wallpaper was set or if an error occurred.
     */
    void setIndividualWallpaper(WallpaperInfo wallpaper, Asset asset, @Nullable Rect cropRect,
                                float scale, @Destination int destination, SetWallpaperCallback callback);

    /**
     * Sets a static individual wallpaper to the system with the provided wallpaper position
     * preference to fit the device display. This method does not provide a destination option since
     * the UI that calls this interface does not support separate home and lock screens.
     *
     * @param wallpaper         Wallpaper model object. Wallpaper image will be set from the asset provided
     *                          by the wallpaper's default asset.
     * @param wallpaperPosition Crop strategy for fitting the wallpaper asset to the device display.
     * @param callback          Called once the wallpaper was set or if an error occurred.
     */
    void setIndividualWallpaperWithPosition(Activity activity, WallpaperInfo wallpaper,
                                            @WallpaperPosition int wallpaperPosition, SetWallpaperCallback callback);

    /**
     * Sets an individual wallpaper to the system as the wallpaper in the current rotation along with
     * its metadata. Prevents automatic wallpaper backup to conserve user data.
     * <p>
     * This method should only be called off the main UI thread because it will compress and set the
     * bitmap on the same thread as the caller.
     *
     * @param wallpaperBitmap Cropped and scaled wallpaper bitmap. This bitmap will be persisted as-is
     *                        with no additional processing.
     * @param attributions    List of attribution items.
     * @param actionUrl       The action or "explore" URL for the wallpaper.
     * @param collectionId    ID of this wallpaper's collection.
     * @return Whether the set wallpaper operation was successful.
     */
    boolean setWallpaperInRotation(Bitmap wallpaperBitmap, List<String> attributions,
                                   int actionLabelRes, int actionIconRes,
                                   String actionUrl, String collectionId);

    /**
     * Sets only the bitmap of a rotating wallpaper of the next rotation to the system (and not
     * metadata).
     *
     * @param wallpaperBitmap The rotating wallpaper's bitmap.
     * @return wallpaper ID, which is a positive integer if the set wallpaper operation was
     * successful, or 0 otherwise. On Android versions prior to N, this method will always return
     * 1 if the operation was successful because wallpaper IDs are not supported prior to N.
     */
    int setWallpaperBitmapInNextRotation(Bitmap wallpaperBitmap);

    /**
     * Persists rotating wallpaper metadata for the next rotation and finalizes the preview wallpaper
     * image so that it's visible as the actual device wallpaper.
     *
     * @param attributions List of attribution items.
     * @param actionUrl    The action or "explore" URL for the wallpaper.
     * @param collectionId ID of this wallpaper's collection.
     * @param wallpaperId  Wallpaper ID that on Android N and later uniquely identifies the wallpaper
     *                     image.
     * @return Whether the operation succeeded.
     */
    boolean finalizeWallpaperForNextRotation(List<String> attributions, String actionUrl,
                                             int actionLabelRes, int actionIconRes,
                                             String collectionId, int wallpaperId);

    /**
     * Saves the last wallpaper which showed a preview from this app.
     */
    void setWallpaperInfoInPreview(WallpaperInfo wallpaper);

    /**
     * Saves attributions to WallpaperPreferences for the last previewed wallpaper if it has an
     * {@link android.app.WallpaperInfo} component matching the one currently set to the
     * {@link android.app.WallpaperManager}.
     */
    void onLiveWallpaperSet();

    /**
     * Interface for tracking success or failure of set wallpaper operations.
     */
    interface SetWallpaperCallback {
        void onSuccess();

        void onError(@Nullable Throwable throwable);
    }

    /**
     * The possible destinations to which a wallpaper may be set.
     */
    @IntDef({
            DEST_HOME_SCREEN,
            DEST_LOCK_SCREEN,
            DEST_BOTH})
    @interface Destination {
    }

    /**
     * Possible wallpaper positions for setting an image wallpaper on desktop.
     */
    @IntDef({
            WALLPAPER_POSITION_CENTER,
            WALLPAPER_POSITION_CENTER_CROP,
            WALLPAPER_POSITION_STRETCH})
    @interface WallpaperPosition {
    }
}
