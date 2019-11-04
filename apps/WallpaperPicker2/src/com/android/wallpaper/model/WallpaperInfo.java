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

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Parcelable;

import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.StringRes;

import com.android.wallpaper.R;
import com.android.wallpaper.asset.Asset;

import java.util.List;

/**
 * Interface for wallpaper info model.
 */
public abstract class WallpaperInfo implements Parcelable {

    @DrawableRes
    public static int getDefaultActionIcon() {
        return R.drawable.ic_explore_24px;
    }

    @StringRes
    public static int getDefaultActionLabel() {
        return R.string.explore;
    }

    public static final int BACKUP_NOT_ALLOWED = 0;
    public static final int BACKUP_ALLOWED = 1;

    /**
     * @param context
     * @return The title for this wallpaper, if applicable (as in a wallpaper "app" or live
     * wallpaper), or null if not applicable.
     */
    public String getTitle(Context context) {
        return null;
    }

    /**
     * @return The available attributions for this wallpaper, as a list of strings. These represent
     * the author / website or any other attribution required to be displayed for this wallpaper
     * regarding authorship, ownership, etc.
     */
    public abstract List<String> getAttributions(Context context);

    /**
     * Returns the base (remote) image URL for this wallpaper, or null if none exists.
     */
    public String getBaseImageUrl() {
        return null;
    }

    /**
     * Returns the action or "explore" URL for the wallpaper, or null if none exists.
     */
    public String getActionUrl(Context unused) {
        return null;
    }

    /** Returns the URI corresponding to the wallpaper, or null if none exists. */
    public Uri getUri() {
        return null;
    }

    /**
     * Returns the icon to use to represent the action link corresponding to
     * {@link #getActionUrl(Context)}
     */
    @DrawableRes
    public int getActionIconRes(Context context) {
        return getDefaultActionIcon();
    }

    /**
     * Returns the label to use for the action link corresponding to
     * {@link #getActionUrl(Context)}
     */
    @StringRes
    public int getActionLabelRes(Context context) {
        return getDefaultActionLabel();
    }

    /**
     * @param context
     * @return An overlay icon to be used instead of a thumbnail, if appropriate, or null if not
     * applicable.
     */
    public Drawable getOverlayIcon(Context context) {
        return null;
    }

    ;

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * @param context The client application's context.
     * @return The {@link Asset} representing the wallpaper image.
     */
    public abstract Asset getAsset(Context context);

    /**
     * @param context The client application's context.
     * @return The {@link Asset} representing the wallpaper's thumbnail.
     */
    public abstract Asset getThumbAsset(Context context);

    /**
     * @param context The client application's context.
     * @return An {@link Asset} that is appropriately sized to be directly set to the desktop. By
     * default, this just the full wallpaper image asset (#getAsset) but subclasses may provide an
     * Asset sized exactly for the device's primary display (i.e., cropped prior to providing a
     * bitmap or input stream).
     */
    public Asset getDesktopAsset(Context context) {
        return getAsset(context);
    }

    /**
     * @return the {@link android.app.WallpaperInfo} associated with this wallpaper, which is
     * generally present for live wallpapers, or null if there is none.
     */
    public android.app.WallpaperInfo getWallpaperComponent() {
        return null;
    }

    /**
     * Returns the ID of the collection this image is associated with, if any.
     */
    public abstract String getCollectionId(Context context);

    /**
     * Returns the ID of this wallpaper or null if there is no ID.
     */
    public String getWallpaperId() {
        return null;
    }

    /**
     * Returns whether backup is allowed for this wallpaper.
     */
    @BackupPermission
    public int getBackupPermission() {
        return BACKUP_ALLOWED;
    }

    /**
     * Shows the appropriate preview activity for this WallpaperInfo.
     *
     * @param srcActivity
     * @param factory     A factory for showing the inline preview activity for within this app.
     *                    Only used for certain WallpaperInfo implementations that require an inline preview
     *                    (as opposed to some external preview activity).
     * @param requestCode Request code to pass in when starting the inline preview activity.
     */
    public abstract void showPreview(Activity srcActivity, InlinePreviewIntentFactory factory,
                                     int requestCode);

    /**
     * Whether backup is allowed for this type of wallpaper.
     */
    @IntDef({
            BACKUP_NOT_ALLOWED,
            BACKUP_ALLOWED
    })
    public @interface BackupPermission {
    }
}
