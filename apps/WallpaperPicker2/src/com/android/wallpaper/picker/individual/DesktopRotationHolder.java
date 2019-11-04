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
import android.view.View;
import android.widget.ImageView;

import com.android.wallpaper.R;
import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.Asset.DrawableLoadedListener;
import com.android.wallpaper.model.WallpaperRotationInitializer;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.WallpaperPreferences;
import com.android.wallpaper.picker.RotationStarter;

import androidx.recyclerview.widget.RecyclerView.ViewHolder;

/**
 * IndividualHolder subclass for a wallpaper tile in the RecyclerView for which a click should
 * set the wallpaper as the current wallpaper on the device.
 */
class DesktopRotationHolder extends ViewHolder implements View.OnClickListener,
        SelectableHolder {

    static final int CROSSFADE_DURATION_MILLIS = 2000;
    static final int CROSSFADE_DURATION_PAUSE_MILLIS = 2000;
    static final int CROSSFADE_DURATION_MILLIS_SHORT = 300;

    private WallpaperPreferences mWallpaperPreferences;
    private Activity mActivity;
    private SelectionAnimator mSelectionAnimator;
    private RotationStarter mRotationStarter;
    private View mTile;
    private ImageView mThumbnailView;

    public DesktopRotationHolder(
            Activity hostActivity, int tileHeightPx, View itemView, SelectionAnimator selectionAnimator,
            RotationStarter rotationStarter) {
        super(itemView);

        mWallpaperPreferences = InjectorProvider.getInjector().getPreferences(hostActivity);
        mActivity = hostActivity;
        mTile = itemView.findViewById(R.id.tile);
        mThumbnailView = (ImageView) itemView.findViewById(R.id.thumbnail);

        mTile.setOnClickListener(this);
        mTile.getLayoutParams().height = tileHeightPx;
        itemView.getLayoutParams().height = tileHeightPx;

        mSelectionAnimator = selectionAnimator;
        mRotationStarter = rotationStarter;
    }

    @Override
    public void setSelectionState(@SelectionState int selectionState) {
        if (selectionState == SELECTION_STATE_SELECTED) {
            mSelectionAnimator.animateSelected();
        } else if (selectionState == SELECTION_STATE_DESELECTED) {
            mSelectionAnimator.animateDeselected();
        } else if (selectionState == SELECTION_STATE_LOADING) {
            mSelectionAnimator.showLoading();
        }
    }

    @Override
    public void onClick(View view) {
        // If this is already selected, then do nothing.
        if (mSelectionAnimator.isSelected()) {
            return;
        }

        mSelectionAnimator.showLoading();
        mRotationStarter.startRotation(WallpaperRotationInitializer.NETWORK_PREFERENCE_CELLULAR_OK);
    }

    /**
     * Binds the DesktopRotationHolder to a particular collection with the given collection ID.
     */
    public void bind(String collectionId) {
        if (mWallpaperPreferences.getWallpaperPresentationMode()
                == WallpaperPreferences.PRESENTATION_MODE_ROTATING
                && collectionId.equals(mWallpaperPreferences.getHomeWallpaperCollectionId())) {
            mSelectionAnimator.selectImmediately();
        } else {
            mSelectionAnimator.deselectImmediately();
        }
    }

    /**
     * Updates the thumbnail shown by replacing the current one (if present) with the one specified
     * by {@code newThumbnailAsset}; uses a smooth transition. Calls {@code drawableLoadedListener}
     * once the transition to the new thumbnail has begun.
     */
    public void updateThumbnail(
            Asset newThumbnailAsset, DrawableLoadedListener drawableLoadedListener) {
        int placeholderColor = mActivity.getResources().getColor(R.color.secondary_color);

        // Load the first image more quickly than subsequent ones in the rotation.
        int crossfadeDuration =
                (mThumbnailView.getDrawable() == null)
                        ? CROSSFADE_DURATION_MILLIS_SHORT
                        : CROSSFADE_DURATION_MILLIS;

        newThumbnailAsset.loadDrawableWithTransition(
                mActivity, mThumbnailView, crossfadeDuration, drawableLoadedListener, placeholderColor);
    }
}
