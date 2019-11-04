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

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.wallpaper.R;
import com.android.wallpaper.model.WallpaperInfo;

import java.util.List;

import androidx.recyclerview.widget.RecyclerView.ViewHolder;

/**
 * Base class for ViewHolders for individual wallpaper tiles.
 */
abstract class IndividualHolder extends ViewHolder {
    protected Activity mActivity;
    protected RelativeLayout mTileLayout;
    protected ImageView mThumbnailView;
    protected ImageView mOverlayIconView;
    protected TextView mTitleView;
    protected WallpaperInfo mWallpaper;

    public IndividualHolder(Activity hostActivity, int tileHeightPx, View itemView) {
        super(itemView);

        mActivity = hostActivity;
        mTileLayout = (RelativeLayout) itemView.findViewById(R.id.tile);
        mThumbnailView = (ImageView) itemView.findViewById(R.id.thumbnail);
        mOverlayIconView = (ImageView) itemView.findViewById(R.id.overlay_icon);
        mTitleView = (TextView) itemView.findViewById(R.id.title);

        mTileLayout.getLayoutParams().height = tileHeightPx;
        itemView.getLayoutParams().height = tileHeightPx;
    }

    /**
     * Binds the given wallpaper to this IndividualHolder.
     */
    public void bindWallpaper(WallpaperInfo wallpaper) {
        mWallpaper = wallpaper;

        String title = wallpaper.getTitle(mActivity);

        List<String> attributions = wallpaper.getAttributions(mActivity);
        String firstAttribution = attributions.size() > 0 ? attributions.get(0) : null;

        if (title != null) {
            mTitleView.setText(title);
            mTitleView.setVisibility(View.VISIBLE);
            mTileLayout.setContentDescription(title);
        } else if (firstAttribution != null) {
            mTileLayout.setContentDescription(firstAttribution);
        }

        Drawable overlayIcon = wallpaper.getOverlayIcon(mActivity);
        if (overlayIcon != null) {
            mOverlayIconView.setImageDrawable(overlayIcon);
        } else {
            wallpaper.getThumbAsset(
                    mActivity.getApplicationContext()).loadDrawable(mActivity, mThumbnailView,
                    mActivity.getResources().getColor(R.color.secondary_color));
        }
    }
}
