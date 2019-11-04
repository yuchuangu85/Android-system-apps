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

import android.annotation.SuppressLint;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.android.wallpaper.R;
import com.android.wallpaper.asset.BitmapUtils;
import com.android.wallpaper.compat.BuildCompat;
import com.android.wallpaper.compat.WallpaperManagerCompat;
import com.android.wallpaper.model.WallpaperMetadata;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Default implementation of {@link WallpaperRefresher} which refreshes wallpaper metadata
 * asynchronously.
 */
@SuppressLint("ServiceCast")
public class DefaultWallpaperRefresher implements WallpaperRefresher {
    private static final String TAG = "DefaultWPRefresher";

    private final Context mAppContext;
    private final WallpaperPreferences mWallpaperPreferences;
    private final WallpaperManager mWallpaperManager;
    private final LiveWallpaperStatusChecker mLiveWallpaperStatusChecker;
    private final UserEventLogger mUserEventLogger;
    private final Context mDeviceProtectedContext;

    /**
     * @param context The application's context.
     */
    public DefaultWallpaperRefresher(Context context) {
        mAppContext = context.getApplicationContext();

        Injector injector = InjectorProvider.getInjector();
        mWallpaperPreferences = injector.getPreferences(mAppContext);
        mLiveWallpaperStatusChecker = injector.getLiveWallpaperStatusChecker(mAppContext);
        mUserEventLogger = injector.getUserEventLogger(mAppContext);

        // Retrieve WallpaperManager using Context#getSystemService instead of
        // WallpaperManager#getInstance so it can be mocked out in test.
        mWallpaperManager = (WallpaperManager) context.getSystemService(Context.WALLPAPER_SERVICE);
        mDeviceProtectedContext = mAppContext.createDeviceProtectedStorageContext();
    }

    @Override
    public void refresh(RefreshListener listener) {
        GetWallpaperMetadataAsyncTask task = new GetWallpaperMetadataAsyncTask(listener);
        task.execute();
    }

    /**
     * Retrieves the current wallpaper's thumbnail and metadata off the UI thread.
     */
    private class GetWallpaperMetadataAsyncTask extends
            AsyncTask<Void, Void, List<WallpaperMetadata>> {
        private final RefreshListener mListener;
        private final WallpaperManagerCompat mWallpaperManagerCompat;

        private long mCurrentHomeWallpaperHashCode;
        private long mCurrentLockWallpaperHashCode;
        private String mSystemWallpaperPackageName;

        @SuppressLint("ServiceCast")
        public GetWallpaperMetadataAsyncTask(RefreshListener listener) {
            mListener = listener;
            mWallpaperManagerCompat =
                    InjectorProvider.getInjector().getWallpaperManagerCompat(mAppContext);
        }

        @Override
        protected List<WallpaperMetadata> doInBackground(Void... unused) {
            List<WallpaperMetadata> wallpaperMetadatas = new ArrayList<>();

            if (!isHomeScreenMetadataCurrent() || isHomeScreenAttributionsEmpty()) {
                mWallpaperPreferences.clearHomeWallpaperMetadata();
                setFallbackHomeScreenWallpaperMetadata();
            }

            boolean isLockScreenWallpaperCurrentlySet = LockWallpaperStatusChecker
                    .isLockWallpaperSet(mAppContext);

            if (!BuildCompat.isAtLeastN() || !isLockScreenWallpaperCurrentlySet) {
                // Return only home metadata if pre-N device or lock screen wallpaper is not explicitly set.
                wallpaperMetadatas.add(new WallpaperMetadata(
                        mWallpaperPreferences.getHomeWallpaperAttributions(),
                        mWallpaperPreferences.getHomeWallpaperActionUrl(),
                        mWallpaperPreferences.getHomeWallpaperActionLabelRes(),
                        mWallpaperPreferences.getHomeWallpaperActionIconRes(),
                        mWallpaperPreferences.getHomeWallpaperCollectionId(),
                        mWallpaperPreferences.getHomeWallpaperBackingFileName(),
                        mWallpaperManager.getWallpaperInfo()));
                return wallpaperMetadatas;
            }

            if (!isLockScreenMetadataCurrent() || isLockScreenAttributionsEmpty()) {
                mWallpaperPreferences.clearLockWallpaperMetadata();
                setFallbackLockScreenWallpaperMetadata();
            }

            wallpaperMetadatas.add(new WallpaperMetadata(
                    mWallpaperPreferences.getHomeWallpaperAttributions(),
                    mWallpaperPreferences.getHomeWallpaperActionUrl(),
                    mWallpaperPreferences.getHomeWallpaperActionLabelRes(),
                    mWallpaperPreferences.getHomeWallpaperActionIconRes(),
                    mWallpaperPreferences.getHomeWallpaperCollectionId(),
                    mWallpaperPreferences.getHomeWallpaperBackingFileName(),
                    mWallpaperManager.getWallpaperInfo()));

            wallpaperMetadatas.add(new WallpaperMetadata(
                    mWallpaperPreferences.getLockWallpaperAttributions(),
                    mWallpaperPreferences.getLockWallpaperActionUrl(),
                    mWallpaperPreferences.getLockWallpaperActionLabelRes(),
                    mWallpaperPreferences.getLockWallpaperActionIconRes(),
                    mWallpaperPreferences.getLockWallpaperCollectionId(),
                    mWallpaperPreferences.getLockWallpaperBackingFileName(),
                    null /* wallpaperComponent */));

            return wallpaperMetadatas;
        }

        @Override
        protected void onPostExecute(List<WallpaperMetadata> metadatas) {
            if (metadatas.size() > 2) {
                Log.e(TAG, "Got more than 2 WallpaperMetadata objects - only home and (optionally) lock "
                        + "are permitted.");
                return;
            }

            mListener.onRefreshed(metadatas.get(0), metadatas.size() > 1 ? metadatas.get(1) : null,
                    mWallpaperPreferences.getWallpaperPresentationMode());
        }

        /**
         * Sets fallback wallpaper attributions to WallpaperPreferences when the saved metadata did not
         * match the system wallpaper. For live wallpapers, loads the label (title) but for image
         * wallpapers loads a generic title string.
         */
        private void setFallbackHomeScreenWallpaperMetadata() {
            android.app.WallpaperInfo wallpaperComponent = mWallpaperManager.getWallpaperInfo();
            if (wallpaperComponent == null) { // Image wallpaper
                mWallpaperPreferences.setHomeWallpaperAttributions(
                        Arrays.asList(mAppContext.getResources().getString(R.string.fallback_wallpaper_title)));

                // Set wallpaper ID if at least N or set a hash code if an earlier version of Android.
                if (BuildCompat.isAtLeastN()) {
                    mWallpaperPreferences.setHomeWallpaperManagerId(mWallpaperManagerCompat.getWallpaperId(
                            WallpaperManagerCompat.FLAG_SYSTEM));
                } else {
                    mWallpaperPreferences.setHomeWallpaperHashCode(getCurrentHomeWallpaperHashCode());
                }
            } else { // Live wallpaper
                mWallpaperPreferences.setHomeWallpaperAttributions(Arrays.asList(
                        wallpaperComponent.loadLabel(mAppContext.getPackageManager()).toString()));
                mWallpaperPreferences.setHomeWallpaperPackageName(mSystemWallpaperPackageName);
            }
            mWallpaperPreferences.setWallpaperPresentationMode(
                    WallpaperPreferences.PRESENTATION_MODE_STATIC);
        }

        /**
         * Sets fallback lock screen wallpaper attributions to WallpaperPreferences. This should be
         * called when the saved lock screen wallpaper metadata does not match the currently set lock
         * screen wallpaper.
         */
        private void setFallbackLockScreenWallpaperMetadata() {
            mWallpaperPreferences.setLockWallpaperAttributions(
                    Arrays.asList(mAppContext.getResources().getString(R.string.fallback_wallpaper_title)));
            mWallpaperPreferences.setLockWallpaperId(mWallpaperManagerCompat.getWallpaperId(
                    WallpaperManagerCompat.FLAG_LOCK));
        }

        /**
         * Returns whether the home screen metadata saved in WallpaperPreferences corresponds to the
         * current system wallpaper.
         */
        private boolean isHomeScreenMetadataCurrent() {
            return (mWallpaperManager.getWallpaperInfo() == null
                    || mLiveWallpaperStatusChecker.isNoBackupImageWallpaperSet())
                    ? isHomeScreenImageWallpaperCurrent()
                    : isHomeScreenLiveWallpaperCurrent();
        }

        /**
         * Returns whether the home screen attributions saved in WallpaperPreferences is empty.
         */
        private boolean isHomeScreenAttributionsEmpty() {
            List<String> homeScreenAttributions = mWallpaperPreferences.getHomeWallpaperAttributions();
            return homeScreenAttributions.get(0) == null
                    && homeScreenAttributions.get(1) == null
                    && homeScreenAttributions.get(2) == null;
        }

        private long getCurrentHomeWallpaperHashCode() {
            if (mCurrentHomeWallpaperHashCode == 0) {
                if (mLiveWallpaperStatusChecker.isNoBackupImageWallpaperSet()) {

                    synchronized (RotatingWallpaperLockProvider.getInstance()) {
                        Bitmap bitmap = null;
                        try {
                            FileInputStream fis =
                                    mDeviceProtectedContext.openFileInput(
                                            NoBackupImageWallpaper.ROTATING_WALLPAPER_FILE_PATH);
                            bitmap = BitmapFactory.decodeStream(fis);
                            fis.close();
                        } catch (FileNotFoundException e) {
                            Log.e(TAG, "Rotating wallpaper file not found at path: "
                                    + mDeviceProtectedContext.getFileStreamPath(
                                            NoBackupImageWallpaper.ROTATING_WALLPAPER_FILE_PATH),
                                    e);
                        } catch (IOException e) {
                            Log.e(TAG, "IOException when closing FileInputStream " + e);
                        }

                        if (bitmap != null) {
                            mCurrentHomeWallpaperHashCode = BitmapUtils.generateHashCode(bitmap);
                            mUserEventLogger.logDailyWallpaperDecodes(true);
                        } else {
                            // If an error occurred decoding the stream then we should just assume the current
                            // home wallpaper remained intact.
                            mCurrentHomeWallpaperHashCode = mWallpaperPreferences.getHomeWallpaperHashCode();
                            mUserEventLogger.logDailyWallpaperDecodes(false);
                        }
                    }
                } else {
                    BitmapDrawable wallpaperDrawable = (BitmapDrawable) mWallpaperManagerCompat.getDrawable();
                    Bitmap wallpaperBitmap = wallpaperDrawable.getBitmap();
                    mCurrentHomeWallpaperHashCode = BitmapUtils.generateHashCode(wallpaperBitmap);

                    // Manually request that WallpaperManager loses its reference to the current wallpaper
                    // bitmap, which can occupy a large memory allocation for the lifetime of the app.
                    mWallpaperManager.forgetLoadedWallpaper();
                }
            }
            return mCurrentHomeWallpaperHashCode;
        }

        private long getCurrentLockWallpaperHashCode() {
            if (mCurrentLockWallpaperHashCode == 0
                    && LockWallpaperStatusChecker.isLockWallpaperSet(mAppContext)) {
                Bitmap wallpaperBitmap = getLockWallpaperBitmap();
                mCurrentLockWallpaperHashCode = BitmapUtils.generateHashCode(wallpaperBitmap);
            }
            return mCurrentLockWallpaperHashCode;
        }

        /**
         * Returns the lock screen wallpaper currently set on the device as a Bitmap, or null if no
         * lock screen wallpaper is set.
         */
        private Bitmap getLockWallpaperBitmap() {
            Bitmap lockBitmap = null;

            ParcelFileDescriptor pfd = mWallpaperManagerCompat.getWallpaperFile(
                    WallpaperManagerCompat.FLAG_LOCK);
            // getWallpaperFile returns null if the lock screen isn't explicitly set, so need this
            // check.
            if (pfd != null) {
                InputStream fileStream = null;
                try {
                    fileStream = new FileInputStream(pfd.getFileDescriptor());
                    lockBitmap = BitmapFactory.decodeStream(fileStream);
                    pfd.close();
                    return lockBitmap;
                } catch (IOException e) {
                    Log.e(TAG, "IO exception when closing the file descriptor.");
                } finally {
                    if (fileStream != null) {
                        try {
                            fileStream.close();
                        } catch (IOException e) {
                            Log.e(TAG, "IO exception when closing input stream for lock screen WP.");
                        }
                    }
                }
            }

            return lockBitmap;
        }

        /**
         * Returns whether the image wallpaper set to the system matches the metadata in
         * WallpaperPreferences.
         */
        private boolean isHomeScreenImageWallpaperCurrent() {
            long savedBitmapHash = mWallpaperPreferences.getHomeWallpaperHashCode();

            // Use WallpaperManager IDs to check same-ness of image wallpaper on N+ versions of Android
            // only when there is no saved bitmap hash code (which could be leftover from a previous build
            // of the app that did not use wallpaper IDs).
            if (BuildCompat.isAtLeastN() && savedBitmapHash == 0) {
                return mWallpaperPreferences.getHomeWallpaperManagerId()
                        == mWallpaperManagerCompat.getWallpaperId(WallpaperManagerCompat.FLAG_SYSTEM);
            }

            return savedBitmapHash == getCurrentHomeWallpaperHashCode();
        }

        /**
         * Returns whether the live wallpaper set to the system's home screen matches the metadata in
         * WallpaperPreferences.
         */
        private boolean isHomeScreenLiveWallpaperCurrent() {
            mSystemWallpaperPackageName = mWallpaperManager.getWallpaperInfo().getPackageName();
            String homeWallpaperPackageName = mWallpaperPreferences.getHomeWallpaperPackageName();
            return mSystemWallpaperPackageName.equals(homeWallpaperPackageName);
        }

        /**
         * Returns whether the lock screen metadata saved in WallpaperPreferences corresponds to the
         * current lock screen wallpaper.
         */
        private boolean isLockScreenMetadataCurrent() {
            // Check for lock wallpaper image same-ness only when there is no stored lock wallpaper hash
            // code. Otherwise if there is a lock wallpaper hash code stored in
            // {@link WallpaperPreferences}, then check hash codes.
            long savedLockWallpaperHash = mWallpaperPreferences.getLockWallpaperHashCode();

            return (savedLockWallpaperHash == 0)
                    ? mWallpaperPreferences.getLockWallpaperId()
                    == mWallpaperManagerCompat.getWallpaperId(WallpaperManagerCompat.FLAG_LOCK)
                    : savedLockWallpaperHash == getCurrentLockWallpaperHashCode();
        }

        /**
         * Returns whether the lock screen attributions saved in WallpaperPreferences are empty.
         */
        private boolean isLockScreenAttributionsEmpty() {
            List<String> attributions = mWallpaperPreferences.getLockWallpaperAttributions();
            return attributions.get(0) == null
                    && attributions.get(1) == null
                    && attributions.get(2) == null;
        }
    }
}
