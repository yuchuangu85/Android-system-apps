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

import com.android.wallpaper.compat.WallpaperManagerCompat;
import com.android.wallpaper.network.Requester;
import com.android.wallpaper.network.WallpaperRequester;
import com.android.wallpaper.picker.individual.IndividualPickerFragment;

/**
 * Base implementation of Injector.
 */
public abstract class BaseWallpaperInjector implements Injector {
    private BitmapCropper mBitmapCropper;
    private PartnerProvider mPartnerProvider;
    private WallpaperPersister mWallpaperPersister;
    private WallpaperPreferences mPrefs;
    private WallpaperRefresher mWallpaperRefresher;
    private Requester mRequester;
    private WallpaperManagerCompat mWallpaperManagerCompat;
    private CurrentWallpaperInfoFactory mCurrentWallpaperFactory;
    private LiveWallpaperStatusChecker mLiveWallpaperStatusChecker;
    private NetworkStatusNotifier mNetworkStatusNotifier;
    private AlarmManagerWrapper mAlarmManagerWrapper;
    private ExploreIntentChecker mExploreIntentChecker;
    private SystemFeatureChecker mSystemFeatureChecker;
    private RotatingWallpaperComponentChecker mRotatingWallpaperComponentChecker;
    private FormFactorChecker mFormFactorChecker;
    private PackageStatusNotifier mPackageStatusNotifier;
    private LiveWallpaperInfoFactory mLiveWallpaperInfoFactory;
    private DrawableLayerResolver mDrawableLayerResolver;

    @Override
    public synchronized BitmapCropper getBitmapCropper() {
        if (mBitmapCropper == null) {
            mBitmapCropper = new DefaultBitmapCropper();
        }
        return mBitmapCropper;
    }

    @Override
    public synchronized PartnerProvider getPartnerProvider(Context context) {
        if (mPartnerProvider == null) {
            mPartnerProvider = new DefaultPartnerProvider(context.getApplicationContext());
        }
        return mPartnerProvider;
    }

    @Override
    public synchronized WallpaperPreferences getPreferences(Context context) {
        if (mPrefs == null) {
            mPrefs = new DefaultWallpaperPreferences(context.getApplicationContext());
        }
        return mPrefs;
    }

    @Override
    public synchronized WallpaperPersister getWallpaperPersister(Context context) {
        if (mWallpaperPersister == null) {
            mWallpaperPersister = new DefaultWallpaperPersister(context.getApplicationContext());
        }
        return mWallpaperPersister;
    }

    @Override
    public synchronized WallpaperRefresher getWallpaperRefresher(Context context) {
        if (mWallpaperRefresher == null) {
            mWallpaperRefresher = new DefaultWallpaperRefresher(context.getApplicationContext());
        }
        return mWallpaperRefresher;
    }

    @Override
    public synchronized Requester getRequester(Context context) {
        if (mRequester == null) {
            mRequester = new WallpaperRequester(context.getApplicationContext());
        }
        return mRequester;
    }

    @Override
    public synchronized WallpaperManagerCompat getWallpaperManagerCompat(Context context) {
        if (mWallpaperManagerCompat == null) {
            mWallpaperManagerCompat = WallpaperManagerCompat.getInstance(context);
        }
        return mWallpaperManagerCompat;
    }

    @Override
    public synchronized CurrentWallpaperInfoFactory getCurrentWallpaperFactory(Context context) {
        if (mCurrentWallpaperFactory == null) {
            mCurrentWallpaperFactory =
                    new DefaultCurrentWallpaperInfoFactory(context.getApplicationContext());
        }
        return mCurrentWallpaperFactory;
    }

    @Override
    public synchronized LiveWallpaperStatusChecker getLiveWallpaperStatusChecker(Context context) {
        if (mLiveWallpaperStatusChecker == null) {
            mLiveWallpaperStatusChecker =
                    new DefaultLiveWallpaperStatusChecker(context.getApplicationContext());
        }
        return mLiveWallpaperStatusChecker;
    }

    @Override
    public synchronized NetworkStatusNotifier getNetworkStatusNotifier(Context context) {
        if (mNetworkStatusNotifier == null) {
            mNetworkStatusNotifier = new DefaultNetworkStatusNotifier(context.getApplicationContext());
        }
        return mNetworkStatusNotifier;
    }

    @Override
    public synchronized PackageStatusNotifier getPackageStatusNotifier(Context context) {
        if (mPackageStatusNotifier == null) {
            mPackageStatusNotifier = new DefaultPackageStatusNotifier(
                    context.getApplicationContext());
        }
        return mPackageStatusNotifier;
    }

    @Override
    public synchronized AlarmManagerWrapper getAlarmManagerWrapper(Context context) {
        if (mAlarmManagerWrapper == null) {
            mAlarmManagerWrapper = new DefaultAlarmManagerWrapper(context.getApplicationContext());
        }
        return mAlarmManagerWrapper;
    }

    @Override
    public synchronized ExploreIntentChecker getExploreIntentChecker(Context context) {
        if (mExploreIntentChecker == null) {
            mExploreIntentChecker = new DefaultExploreIntentChecker(context.getApplicationContext());
        }
        return mExploreIntentChecker;
    }

    @Override
    public synchronized SystemFeatureChecker getSystemFeatureChecker() {
        if (mSystemFeatureChecker == null) {
            mSystemFeatureChecker = new DefaultSystemFeatureChecker();
        }
        return mSystemFeatureChecker;
    }

    @Override
    public synchronized RotatingWallpaperComponentChecker getRotatingWallpaperComponentChecker() {
        if (mRotatingWallpaperComponentChecker == null) {
            mRotatingWallpaperComponentChecker = new DefaultRotatingWallpaperComponentChecker();
        }
        return mRotatingWallpaperComponentChecker;
    }

    @Override
    public synchronized FormFactorChecker getFormFactorChecker(Context context) {
        if (mFormFactorChecker == null) {
            mFormFactorChecker = new DefaultFormFactorChecker(context.getApplicationContext());
        }
        return mFormFactorChecker;
    }

    @Override
    public synchronized IndividualPickerFragment getIndividualPickerFragment(String collectionId) {
        return IndividualPickerFragment.newInstance(collectionId);
    }

    @Override
    public LiveWallpaperInfoFactory getLiveWallpaperInfoFactory(Context context) {
        if (mLiveWallpaperInfoFactory == null) {
            mLiveWallpaperInfoFactory = new DefaultLiveWallpaperInfoFactory();
        }
        return mLiveWallpaperInfoFactory;
    }

    @Override
    public DrawableLayerResolver getDrawableLayerResolver() {
        if (mDrawableLayerResolver == null) {
            mDrawableLayerResolver = new DefaultDrawableLayerResolver();
        }
        return mDrawableLayerResolver;
    }
}
