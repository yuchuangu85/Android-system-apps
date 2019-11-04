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

import androidx.fragment.app.Fragment;

import com.android.wallpaper.compat.WallpaperManagerCompat;
import com.android.wallpaper.model.CategoryProvider;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.monitor.PerformanceMonitor;
import com.android.wallpaper.network.Requester;
import com.android.wallpaper.picker.PreviewFragment.PreviewMode;
import com.android.wallpaper.picker.individual.IndividualPickerFragment;

/**
 * Interface for a provider of "injected dependencies." (NOTE: The term "injector" is somewhat of a
 * misnomer; this is more aptly a service registry as part of a service locator design pattern.)
 */
public interface Injector {
    AlarmManagerWrapper getAlarmManagerWrapper(Context context);

    BitmapCropper getBitmapCropper();

    CategoryProvider getCategoryProvider(Context context);

    CurrentWallpaperInfoFactory getCurrentWallpaperFactory(Context context);

    ExploreIntentChecker getExploreIntentChecker(Context context);

    FormFactorChecker getFormFactorChecker(Context context);

    LiveWallpaperStatusChecker getLiveWallpaperStatusChecker(Context context);

    LoggingOptInStatusProvider getLoggingOptInStatusProvider(Context context);

    NetworkStatusNotifier getNetworkStatusNotifier(Context context);

    PartnerProvider getPartnerProvider(Context context);

    PerformanceMonitor getPerformanceMonitor();

    Requester getRequester(Context context);

    RotatingWallpaperComponentChecker getRotatingWallpaperComponentChecker();

    SystemFeatureChecker getSystemFeatureChecker();

    UserEventLogger getUserEventLogger(Context context);

    WallpaperManagerCompat getWallpaperManagerCompat(Context context);

    WallpaperPersister getWallpaperPersister(Context context);

    WallpaperPreferences getPreferences(Context context);

    WallpaperRefresher getWallpaperRefresher(Context context);

    WallpaperRotationRefresher getWallpaperRotationRefresher();

    Fragment getPreviewFragment(
        Context context,
        WallpaperInfo wallpaperInfo,
        @PreviewMode int mode,
        boolean testingModeEnabled);

    PackageStatusNotifier getPackageStatusNotifier(Context context);

    IndividualPickerFragment getIndividualPickerFragment(String collectionId);

    LiveWallpaperInfoFactory getLiveWallpaperInfoFactory(Context context);

    DrawableLayerResolver getDrawableLayerResolver();
}
