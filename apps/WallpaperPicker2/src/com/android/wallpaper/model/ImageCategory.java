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

import android.Manifest.permission;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.MediaStore;

import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.ContentUriAsset;

/**
 * Category of wallpapers from user's images which are visible on the device.
 */
public class ImageCategory extends Category {

    /**
     * Resource ID for the ImageCategory's overlay icon. If the constructor that does not take an
     * {@code int overlayIconResId} is used to instantiate an ImageCategory, then the resource ID will
     * default to 0 (which is not a valid resource ID) and #getOverlayIcon will return null.
     */
    private int mOverlayIconResId;

    public ImageCategory(String title, String collectionId, int priority) {
        super(title, collectionId, priority);
        mOverlayIconResId = 0;
    }

    public ImageCategory(String title, String collectionId, int priority, int overlayIconResId) {
        super(title, collectionId, priority);
        mOverlayIconResId = overlayIconResId;
    }

    @Override
    public void show(Activity srcActivity, PickerIntentFactory factory, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        srcActivity.startActivityForResult(intent, requestCode);
    }

    @Override
    public int getOverlayIconSizeDp() {
        return 128;
    }

    @Override
    public Asset getThumbnail(Context context) {
        if (!isReadExternalStoragePermissionGranted(context)) {
            // MediaStore.Images.Media.EXTERNAL_CONTENT_URI requires
            // the READ_EXTERNAL_STORAGE permission.
            return null;
        }

        String[] projection = new String[]{
                MediaStore.Images.ImageColumns._ID,
                MediaStore.Images.ImageColumns.DATE_TAKEN,
        };
        String sortOrder = MediaStore.Images.ImageColumns.DATE_TAKEN + " DESC LIMIT 1";
        Cursor cursor = context.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null /* selection */,
                null /* selectionArgs */,
                sortOrder);

        Asset asset = null;
        if (cursor != null) {
            if (cursor.moveToNext()) {
                asset = new ContentUriAsset(context,
                        Uri.parse(MediaStore.Images.Media.EXTERNAL_CONTENT_URI + "/" + cursor.getString(0)));
            }
            cursor.close();
        }

        return asset;
    }

    @Override
    public Drawable getOverlayIcon(Context context) {
        // Only provide the overlay icon if the thumbnail asset is null.
        if (getThumbnail(context) == null && mOverlayIconResId > 0) {
            return context.getResources().getDrawable(mOverlayIconResId);
        } else {
            return null;
        }
    }

    @Override
    public boolean supportsCustomPhotos() {
        return true;
    }

    /**
     * Returns whether READ_EXTERNAL_STORAGE has been granted for the application.
     */
    private boolean isReadExternalStoragePermissionGranted(Context context) {
        return context.getPackageManager().checkPermission(permission.READ_EXTERNAL_STORAGE,
                context.getPackageName()) == PackageManager.PERMISSION_GRANTED;
    }
}
