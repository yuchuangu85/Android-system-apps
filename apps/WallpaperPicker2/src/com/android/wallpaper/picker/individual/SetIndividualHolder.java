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
import android.content.Context;
import android.util.Log;
import android.view.View;

import com.android.wallpaper.R;
import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.Injector;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.UserEventLogger;
import com.android.wallpaper.module.UserEventLogger.WallpaperSetFailureReason;
import com.android.wallpaper.module.WallpaperPersister;
import com.android.wallpaper.module.WallpaperPersister.SetWallpaperCallback;
import com.android.wallpaper.util.ThrowableAnalyzer;

/**
 * IndividualHolder subclass for a wallpaper tile in the RecyclerView for which a click should
 * set the wallpaper as the current wallpaper on the device.
 */
class SetIndividualHolder extends IndividualHolder implements View.OnClickListener,
        SelectableHolder {

    private static final String TAG = "SetIndividualHolder";
    private SelectionAnimator mSelectionAnimator;
    private OnSetListener mOnSetListener;
    private View mTile;

    public SetIndividualHolder(
            Activity hostActivity, int tileHeightPx, View itemView,
            SelectionAnimator selectionAnimator,
            OnSetListener onSetListener) {
        super(hostActivity, tileHeightPx, itemView);

        mTile = itemView.findViewById(R.id.tile);

        mSelectionAnimator = selectionAnimator;
        mOnSetListener = onSetListener;
    }

    @Override
    public void bindWallpaper(WallpaperInfo wallpaper) {
        super.bindWallpaper(wallpaper);

        String wallpaperId = mWallpaper.getWallpaperId();
        String remoteWallpaperId = InjectorProvider.getInjector().getPreferences(
                mActivity.getApplicationContext()).getHomeWallpaperRemoteId();

        boolean selected = wallpaperId != null && wallpaperId.equals(remoteWallpaperId);
        if (selected) {
            mSelectionAnimator.selectImmediately();
        } else {
            mSelectionAnimator.deselectImmediately();
        }

        mTile.setOnClickListener(this);
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
    public void onClick(View unused) {
        setWallpaper();
    }

    /* package */ void setWallpaper() {
        // If this wallpaper is already selected, then do nothing.
        if (mSelectionAnimator.isSelected()) {
            return;
        }

        final int adapterPosition = getAdapterPosition();
        mOnSetListener.onPendingWallpaperSet(adapterPosition);

        final Context appContext = mActivity.getApplicationContext();

        mSelectionAnimator.showLoading();

        Injector injector = InjectorProvider.getInjector();
        final UserEventLogger eventLogger = injector.getUserEventLogger(appContext);
        eventLogger.logIndividualWallpaperSelected(mWallpaper.getCollectionId(mActivity));

        Asset desktopAsset = mWallpaper.getDesktopAsset(appContext);

        WallpaperPersister wallpaperPersister =
                InjectorProvider.getInjector().getWallpaperPersister(appContext);
        wallpaperPersister.setIndividualWallpaper(mWallpaper, desktopAsset, null /* cropRect */,
                1.0f /* scale */, WallpaperPersister.DEST_BOTH, new SetWallpaperCallback() {
                    @Override
                    public void onSuccess() {
                        mOnSetListener.onWallpaperSet(adapterPosition);
                        eventLogger.logWallpaperSet(
                                mWallpaper.getCollectionId(appContext), mWallpaper.getWallpaperId());
                        eventLogger.logWallpaperSetResult(UserEventLogger.WALLPAPER_SET_RESULT_SUCCESS);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        Log.e(TAG, "Could not set a wallpaper.");
                        eventLogger.logWallpaperSetResult(UserEventLogger.WALLPAPER_SET_RESULT_FAILURE);
                        @WallpaperSetFailureReason int failureReason = ThrowableAnalyzer.isOOM(throwable)
                                ? UserEventLogger.WALLPAPER_SET_FAILURE_REASON_OOM
                                : UserEventLogger.WALLPAPER_SET_FAILURE_REASON_OTHER;
                        eventLogger.logWallpaperSetFailureReason(failureReason);

                        mSelectionAnimator.showNotLoading();
                        mOnSetListener.onWallpaperSetFailed(SetIndividualHolder.this);
                    }
                });
    }

    interface OnSetListener {
        /**
         * Called to signal that the wallpaper at the given adapter position is starting to be set.
         */
        void onPendingWallpaperSet(int adapterPosition);

        /**
         * Called once the wallpaper at the given adapter position has been set.
         */
        void onWallpaperSet(int adapterPosition);

        /**
         * Called when setting the wallpaper represented by the provided holder failed.
         */
        void onWallpaperSetFailed(SetIndividualHolder holder);
    }
}
