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

import com.android.wallpaper.compat.BuildCompat;
import com.android.wallpaper.compat.WallpaperManagerCompat;
import com.android.wallpaper.model.CurrentWallpaperInfoV16;
import com.android.wallpaper.model.CurrentWallpaperInfoVN;
import com.android.wallpaper.model.LiveWallpaperInfo;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.WallpaperPreferences.PresentationMode;

import androidx.annotation.Nullable;

/**
 * Default implementation of {@link CurrentWallpaperInfoFactory} which actually constructs
 * {@link WallpaperInfo} instances representing the wallpapers currently set to the device.
 */
public class DefaultCurrentWallpaperInfoFactory implements CurrentWallpaperInfoFactory {

    private final Context mAppContext;
    private final WallpaperRefresher mWallpaperRefresher;
    private final LiveWallpaperStatusChecker mLiveWallpaperStatusChecker;
    private final LiveWallpaperInfoFactory mLiveWallpaperInfoFactory;

    // Cached copies of the currently-set WallpaperInfo(s) and presentation mode.
    private WallpaperInfo mHomeWallpaper;
    @Nullable
    private WallpaperInfo mLockWallpaper;
    @PresentationMode
    private int mPresentationMode;

    public DefaultCurrentWallpaperInfoFactory(Context context) {
        mAppContext = context.getApplicationContext();
        Injector injector = InjectorProvider.getInjector();
        mWallpaperRefresher = injector.getWallpaperRefresher(mAppContext);
        mLiveWallpaperStatusChecker =
                injector.getLiveWallpaperStatusChecker(mAppContext);
        mLiveWallpaperInfoFactory = injector.getLiveWallpaperInfoFactory(mAppContext);
    }

    @Override
    public synchronized void createCurrentWallpaperInfos(final WallpaperInfoCallback callback,
                                                         boolean forceRefresh) {
        if (!forceRefresh && mHomeWallpaper != null) {
            callback.onWallpaperInfoCreated(mHomeWallpaper, mLockWallpaper, mPresentationMode);
            return;
        }

        // Clear cached copies if we are refreshing the currently-set WallpaperInfo(s) from the
        // Refresher so that multiple calls to this method after a call with forceRefresh=true don't
        // provide old cached copies.
        if (forceRefresh) {
            mHomeWallpaper = null;
            mLockWallpaper = null;
        }

        mWallpaperRefresher.refresh(
                (homeWallpaperMetadata, lockWallpaperMetadata, presentationMode) -> {

                    WallpaperInfo homeWallpaper;

                    if (homeWallpaperMetadata.getWallpaperComponent() == null
                            || mLiveWallpaperStatusChecker.isNoBackupImageWallpaperSet()) {
                        // Image wallpaper
                        if (BuildCompat.isAtLeastN()) {
                            homeWallpaper = new CurrentWallpaperInfoVN(
                                    homeWallpaperMetadata.getAttributions(),
                                    homeWallpaperMetadata.getActionUrl(),
                                    homeWallpaperMetadata.getActionLabelRes(),
                                    homeWallpaperMetadata.getActionIconRes(),
                                    homeWallpaperMetadata.getCollectionId(),
                                    WallpaperManagerCompat.FLAG_SYSTEM);
                        } else {
                            homeWallpaper = new CurrentWallpaperInfoV16(
                                    homeWallpaperMetadata.getAttributions(),
                                    homeWallpaperMetadata.getActionUrl(),
                                    homeWallpaperMetadata.getActionLabelRes(),
                                    homeWallpaperMetadata.getActionIconRes(),
                                    homeWallpaperMetadata.getCollectionId());
                        }
                    } else { // Live wallpaper
                        homeWallpaper = mLiveWallpaperInfoFactory.getLiveWallpaperInfo(
                                homeWallpaperMetadata.getWallpaperComponent());
                    }

                    WallpaperInfo lockWallpaper = null;

                    if (lockWallpaperMetadata != null) {
                        lockWallpaper = new CurrentWallpaperInfoVN(
                                lockWallpaperMetadata.getAttributions(),
                                lockWallpaperMetadata.getActionUrl(),
                                lockWallpaperMetadata.getActionLabelRes(),
                                lockWallpaperMetadata.getActionIconRes(),
                                lockWallpaperMetadata.getCollectionId(),
                                WallpaperManagerCompat.FLAG_LOCK);
                    }

                    mHomeWallpaper = homeWallpaper;
                    mLockWallpaper = lockWallpaper;
                    mPresentationMode = presentationMode;

                    callback.onWallpaperInfoCreated(homeWallpaper, lockWallpaper, presentationMode);
                });
    }
}
