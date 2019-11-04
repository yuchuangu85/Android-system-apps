/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.wallpaper.model.CategoryProvider;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.monitor.PerformanceMonitor;
import com.android.wallpaper.picker.PreviewFragment;

import androidx.fragment.app.Fragment;

/**
 * A concrete, real implementation of the dependency provider.
 */
public class WallpapersInjector extends BaseWallpaperInjector {
    private CategoryProvider mCategoryProvider;
    private UserEventLogger mUserEventLogger;
    private WallpaperRotationRefresher mWallpaperRotationRefresher;
    private PerformanceMonitor mPerformanceMonitor;

    @Override
    public synchronized CategoryProvider getCategoryProvider(Context context) {
        if (mCategoryProvider == null) {
            mCategoryProvider = new DefaultCategoryProvider(context.getApplicationContext());
        }
        return mCategoryProvider;
    }

    @Override
    public synchronized UserEventLogger getUserEventLogger(Context context) {
        if (mUserEventLogger == null) {
            mUserEventLogger = new NoOpUserEventLogger();
        }
        return mUserEventLogger;
    }

    @Override
    public synchronized WallpaperRotationRefresher getWallpaperRotationRefresher() {
        if (mWallpaperRotationRefresher == null) {
            mWallpaperRotationRefresher = new WallpaperRotationRefresher() {
                @Override
                public void refreshWallpaper(Context context, Listener listener) {
                    // Not implemented
                    listener.onError();
                }
            };
        }
        return mWallpaperRotationRefresher;
    }

    @Override
    public Fragment getPreviewFragment(
        Context context,
        WallpaperInfo wallpaperInfo,
        int mode,
        boolean testingModeEnabled) {
        return PreviewFragment.newInstance(wallpaperInfo, mode, testingModeEnabled);
    }

    @Override
    public synchronized PerformanceMonitor getPerformanceMonitor() {
        if (mPerformanceMonitor == null) {
            mPerformanceMonitor = new PerformanceMonitor() {
                @Override
                public void recordFullResPreviewLoadedMemorySnapshot() {
                    // No Op
                }
            };
        }
        return mPerformanceMonitor;
    }

    @Override
    public synchronized LoggingOptInStatusProvider getLoggingOptInStatusProvider(Context context) {
        return null;
    }
}
