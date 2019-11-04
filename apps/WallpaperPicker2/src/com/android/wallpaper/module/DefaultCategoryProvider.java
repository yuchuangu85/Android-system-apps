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
import android.content.pm.PackageManager;
import android.os.AsyncTask;

import com.android.wallpaper.R;
import com.android.wallpaper.model.Category;
import com.android.wallpaper.model.CategoryProvider;
import com.android.wallpaper.model.CategoryReceiver;
import com.android.wallpaper.model.DefaultWallpaperInfo;
import com.android.wallpaper.model.DesktopCustomCategory;
import com.android.wallpaper.model.ImageCategory;
import com.android.wallpaper.model.LegacyPartnerWallpaperInfo;
import com.android.wallpaper.model.LiveWallpaperCategory;
import com.android.wallpaper.model.LiveWallpaperInfo;
import com.android.wallpaper.model.PartnerWallpaperInfo;
import com.android.wallpaper.model.ThirdPartyAppCategory;
import com.android.wallpaper.model.WallpaperCategory;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.FormFactorChecker.FormFactor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Default implementation of CategoryProvider.
 */
public class DefaultCategoryProvider implements CategoryProvider {

    /**
     * Relative category priorities. Lower numbers correspond to higher priorities (i.e., should
     * appear higher in the categories list).
     */
    private static final int PRIORITY_MY_PHOTOS = 100;
    private static final int PRIORITY_ON_DEVICE = 200;
    private static final int PRIORITY_LIVE = 300;
    private static final int PRIORITY_THIRD_PARTY = 400;

    protected final Context mAppContext;
    protected ArrayList<Category> mCategories;
    protected boolean mFetchedCategories;

    public DefaultCategoryProvider(Context context) {
        mAppContext = context.getApplicationContext();
        mCategories = new ArrayList<>();
    }

    @Override
    public void fetchCategories(CategoryReceiver receiver, boolean forceRefresh) {
        if (!forceRefresh && mFetchedCategories) {
            for (Category category : mCategories) {
                receiver.onCategoryReceived(category);
            }
            receiver.doneFetchingCategories();
            return;
        } else if (forceRefresh) {
            mCategories.clear();
            mFetchedCategories = false;
        }

        doFetch(receiver, forceRefresh);
    }

    @Override
    public int getSize() {
        return mFetchedCategories ? mCategories.size() : 0;
    }

    @Override
    public Category getCategory(int index) {
        if (!mFetchedCategories) {
            throw new IllegalStateException("Categories are not available");
        }
        return mCategories.get(index);
    }

    @Override
    public Category getCategory(String collectionId) {
        Category category;
        for (int i = 0; i < mCategories.size(); i++) {
            category = mCategories.get(i);
            if (category.getCollectionId().equals(collectionId)) {
                return category;
            }
        }
        return null;
    }

    protected void doFetch(final CategoryReceiver receiver, boolean forceRefresh) {
        CategoryReceiver delegatingReceiver = new CategoryReceiver() {
            @Override
            public void onCategoryReceived(Category category) {
                receiver.onCategoryReceived(category);
                mCategories.add(category);
            }

            @Override
            public void doneFetchingCategories() {
                receiver.doneFetchingCategories();
                mFetchedCategories = true;
            }
        };

        new FetchCategoriesTask(delegatingReceiver, mAppContext).execute();
    }

    /**
     * AsyncTask subclass used for fetching all the categories and pushing them one at a time to
     * the receiver.
     */
    protected static class FetchCategoriesTask extends AsyncTask<Void, Category, Void> {
        private CategoryReceiver mReceiver;
        protected final Context mAppContext;

        public FetchCategoriesTask(CategoryReceiver receiver, Context context) {
            mReceiver = receiver;
            mAppContext = context.getApplicationContext();
        }

        @Override
        protected Void doInBackground(Void... voids) {
            FormFactorChecker formFactorChecker =
                    InjectorProvider.getInjector().getFormFactorChecker(mAppContext);
            @FormFactor int formFactor = formFactorChecker.getFormFactor();

            // "My photos" wallpapers
            publishProgress(getMyPhotosCategory(formFactor));

            publishDeviceCategories(formFactor);

            // Live wallpapers -- if the device supports them.
            if (mAppContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LIVE_WALLPAPER)) {
                List<WallpaperInfo> liveWallpapers = LiveWallpaperInfo.getAll(
                        mAppContext, getExcludedLiveWallpaperPackageNames());
                if (liveWallpapers.size() > 0) {
                    publishProgress(
                            new LiveWallpaperCategory(
                                    mAppContext.getString(R.string.live_wallpapers_category_title),
                                    mAppContext.getString(R.string.live_wallpaper_collection_id),
                                    liveWallpapers,
                                    PRIORITY_LIVE,
                                    getExcludedLiveWallpaperPackageNames()));
                }
            }

            // Third party apps -- only on mobile.
            if (formFactor == FormFactorChecker.FORM_FACTOR_MOBILE) {
                List<ThirdPartyAppCategory> thirdPartyApps = ThirdPartyAppCategory.getAll(
                        mAppContext, PRIORITY_THIRD_PARTY, getExcludedThirdPartyPackageNames());
                for (ThirdPartyAppCategory thirdPartyApp : thirdPartyApps) {
                    publishProgress(thirdPartyApp);
                }
            }

            return null;
        }

        /**
         * Publishes the device categories.
         */
        protected void publishDeviceCategories(@FormFactor int formFactor) {
            if (formFactor == FormFactorChecker.FORM_FACTOR_MOBILE) {
                // On-device wallpapers. Only show if on mobile.
                publishProgress(getOnDeviceCategory());
            }
        }

        public List<String> getExcludedLiveWallpaperPackageNames() {
            return new ArrayList<String>();
        }

        protected List<String> getExcludedThirdPartyPackageNames() {
            return Arrays.asList(
                    "com.android.launcher", // Legacy launcher
                    "com.android.wallpaper.livepicker"); // Live wallpaper picker
        }

        /**
         * Return a list of WallpaperInfos specific to this app. Overriding this method will
         * allow derivative projects to add custom wallpaper tiles to the
         * "On-device wallpapers" category.
         */
        protected List<WallpaperInfo> getPrivateDeviceWallpapers() {
            return null;
        }

        /**
         * Returns a category which incorporates both GEL and bundled wallpapers.
         */
        private Category getOnDeviceCategory() {
            List<WallpaperInfo> onDeviceWallpapers = new ArrayList<>();

            PartnerProvider partnerProvider = InjectorProvider.getInjector().getPartnerProvider(
                    mAppContext);
            if (!partnerProvider.shouldHideDefaultWallpaper()) {
                DefaultWallpaperInfo defaultWallpaperInfo = new DefaultWallpaperInfo();
                onDeviceWallpapers.add(defaultWallpaperInfo);
            }

            List<WallpaperInfo> partnerWallpaperInfos = PartnerWallpaperInfo.getAll(mAppContext);
            onDeviceWallpapers.addAll(partnerWallpaperInfos);

            List<WallpaperInfo> legacyPartnerWallpaperInfos = LegacyPartnerWallpaperInfo.getAll(
                    mAppContext);
            onDeviceWallpapers.addAll(legacyPartnerWallpaperInfos);

            List<WallpaperInfo> privateWallpapers = getPrivateDeviceWallpapers();
            if (privateWallpapers != null) {
                onDeviceWallpapers.addAll(privateWallpapers);
            }

            return new WallpaperCategory(
                    mAppContext.getString(R.string.on_device_wallpapers_category_title),
                    mAppContext.getString(R.string.on_device_wallpaper_collection_id),
                    onDeviceWallpapers,
                    PRIORITY_ON_DEVICE);
        }

        private Category getDesktopOnDeviceCategory() {
            List<WallpaperInfo> onDeviceWallpapers = new ArrayList<>();

            DefaultWallpaperInfo defaultWallpaperInfo = new DefaultWallpaperInfo();
            onDeviceWallpapers.add(defaultWallpaperInfo);

            return new DesktopCustomCategory(
                    mAppContext.getString(R.string.on_device_wallpapers_category_title_desktop),
                    mAppContext.getString(R.string.on_device_wallpaper_collection_id),
                    onDeviceWallpapers,
                    PRIORITY_MY_PHOTOS);
        }

        /**
         * Returns an appropriate "my photos" custom photo category for the given device form factor.
         */
        private Category getMyPhotosCategory(@FormFactor int formFactor) {
            return formFactor == FormFactorChecker.FORM_FACTOR_DESKTOP
                    ? getDesktopOnDeviceCategory()
                    : new ImageCategory(
                    mAppContext.getString(R.string.my_photos_category_title),
                    mAppContext.getString(R.string.image_wallpaper_collection_id),
                    PRIORITY_MY_PHOTOS,
                    R.drawable.myphotos_empty_tile_illustration /* overlayIconResId */);
        }

        @Override
        protected void onProgressUpdate(Category... values) {
            super.onProgressUpdate(values);

            for (int i = 0; i < values.length; i++) {
                Category category = values[i];
                mReceiver.onCategoryReceived(category);
            }
        }

        @Override
        protected void onPostExecute(Void unused) {
            mReceiver.doneFetchingCategories();
        }
    }
}
