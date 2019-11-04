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
package com.android.wallpaper.picker.individual;

import android.Manifest.permission;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.android.wallpaper.R;
import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.ContentUriAsset;
import com.android.wallpaper.picker.MyPhotosStarter;

/**
 * ViewHolder for a "my photos" tile presented in an individual category grid.
 */
public class MyPhotosViewHolder extends ViewHolder implements View.OnClickListener,
        MyPhotosStarter.PermissionChangedListener {

    private final Activity mActivity;
    private final MyPhotosStarter mMyPhotosStarter;
    private final ImageView mThumbnailView;
    private final ImageView mOverlayIconView;

    /* package */ MyPhotosViewHolder(Activity activity, MyPhotosStarter myPhotosStarter,
            int tileHeightPx, View itemView) {
        super(itemView);

        mActivity = activity;
        mMyPhotosStarter = myPhotosStarter;
        itemView.getLayoutParams().height = tileHeightPx;

        itemView.findViewById(R.id.tile).setOnClickListener(this);

        mThumbnailView = itemView.findViewById(R.id.thumbnail);
        mOverlayIconView = itemView.findViewById(R.id.overlay_icon);
    }

    /**
     * Fetches a thumbnail asset to represent "my photos" (as the most recently taken photo from the
     * user's custom photo collection(s)) and calls back to the main thread with the asset.
     */
    private static void fetchThumbnail(final Context context, final AssetListener listener) {
        if (!isReadExternalStoragePermissionGranted(context)) {
            // MediaStore.Images.Media.EXTERNAL_CONTENT_URI requires the READ_EXTERNAL_STORAGE permission.
            listener.onAssetRetrieved(null);
        }

        new AsyncTask<Void, Void, Asset>() {
            @Override
            protected Asset doInBackground(Void... params) {
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
                        asset = new ContentUriAsset(context, Uri.parse(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI + "/" + cursor.getString(0)));
                    }
                    cursor.close();
                }

                return asset;
            }

            @Override
            protected void onPostExecute(Asset thumbnail) {
                listener.onAssetRetrieved(thumbnail);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Returns whether READ_EXTERNAL_STORAGE has been granted for the application.
     */
    private static boolean isReadExternalStoragePermissionGranted(Context context) {
        return context.getPackageManager().checkPermission(permission.READ_EXTERNAL_STORAGE,
                context.getPackageName()) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onClick(View view) {
        mMyPhotosStarter.requestCustomPhotoPicker(this);
    }

    /**
     * Draws the overlay icon or last-taken photo as thumbnail for the ViewHolder depending on whether
     * storage permission has been granted to the app.
     */
  /* package */ void bind() {
        if (isReadExternalStoragePermissionGranted(mActivity)) {
            mOverlayIconView.setVisibility(View.GONE);
            drawThumbnail();
        } else {
            mOverlayIconView.setVisibility(View.VISIBLE);
            mOverlayIconView.setImageDrawable(mActivity.getDrawable(
                    R.drawable.myphotos_empty_tile_illustration));
        }
    }

    @Override
    public void onPermissionsGranted() {
        bind();
    }

    @Override
    public void onPermissionsDenied(boolean dontAskAgain) {
        // No-op
    }

    private void drawThumbnail() {
        fetchThumbnail(mActivity, new AssetListener() {
            @Override
            public void onAssetRetrieved(@Nullable Asset thumbnail) {
                if (thumbnail == null) {
                    return;
                }

                thumbnail.loadDrawable(mActivity, mThumbnailView,
                        mActivity.getResources().getColor(R.color.secondary_color));
            }
        });
    }

    private interface AssetListener {
        /**
         * Called when the requested Asset is retrieved.
         */
        void onAssetRetrieved(@Nullable Asset asset);
    }
}
