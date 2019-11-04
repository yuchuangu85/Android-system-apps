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
import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.Asset.BitmapReceiver;
import com.android.wallpaper.asset.Asset.DimensionsReceiver;
import com.android.wallpaper.asset.BitmapUtils;
import com.android.wallpaper.asset.StreamableAsset;
import com.android.wallpaper.asset.StreamableAsset.StreamReceiver;
import com.android.wallpaper.compat.BuildCompat;
import com.android.wallpaper.compat.WallpaperManagerCompat;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.BitmapCropper.Callback;
import com.android.wallpaper.module.RotatingWallpaperComponentChecker.RotatingWallpaperComponent;
import com.android.wallpaper.util.BitmapTransformer;
import com.android.wallpaper.util.DiskBasedLogger;
import com.android.wallpaper.util.FileMover;
import com.android.wallpaper.util.ScreenSizeCalculator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import androidx.annotation.Nullable;

/**
 * Concrete implementation of WallpaperPersister which actually sets wallpapers to the system via
 * the WallpaperManager.
 */
public class DefaultWallpaperPersister implements WallpaperPersister {

    private static final int DEFAULT_COMPRESS_QUALITY = 100;
    private static final String TAG = "WallpaperPersister";

    private final Context mAppContext; // The application's context.
    // Context that accesses files in device protected storage
    private final Context mDeviceProtectedContext;
    private final WallpaperManager mWallpaperManager;
    private final WallpaperManagerCompat mWallpaperManagerCompat;
    private final WallpaperPreferences mWallpaperPreferences;
    private final RotatingWallpaperComponentChecker mRotatingWallpaperComponentChecker;
    private final WallpaperChangedNotifier mWallpaperChangedNotifier;

    private WallpaperInfo mWallpaperInfoInPreview;

    @SuppressLint("ServiceCast")
    public DefaultWallpaperPersister(Context context) {
        mAppContext = context.getApplicationContext();
        mDeviceProtectedContext = mAppContext.createDeviceProtectedStorageContext();
        // Retrieve WallpaperManager using Context#getSystemService instead of
        // WallpaperManager#getInstance so it can be mocked out in test.
        Injector injector = InjectorProvider.getInjector();
        mWallpaperManager = (WallpaperManager) context.getSystemService(Context.WALLPAPER_SERVICE);
        mWallpaperManagerCompat = injector.getWallpaperManagerCompat(context);
        mWallpaperPreferences = injector.getPreferences(context);
        mRotatingWallpaperComponentChecker = injector.getRotatingWallpaperComponentChecker();
        mWallpaperChangedNotifier = WallpaperChangedNotifier.getInstance();
    }

    @Override
    public void setIndividualWallpaper(final WallpaperInfo wallpaper, Asset asset,
                                       @Nullable Rect cropRect, float scale, @Destination final int destination,
                                       final SetWallpaperCallback callback) {
        // Set wallpaper without downscaling directly from an input stream if there's no crop rect
        // specified by the caller and the asset is streamable.
        if (cropRect == null && asset instanceof StreamableAsset) {
            ((StreamableAsset) asset).fetchInputStream(new StreamReceiver() {
                @Override
                public void onInputStreamOpened(@Nullable InputStream inputStream) {
                    if (inputStream == null) {
                        callback.onError(null /* throwable */);
                        return;
                    }
                    setIndividualWallpaper(wallpaper, inputStream, destination, callback);
                }
            });
            return;
        }

        // If no crop rect is specified but the wallpaper asset is not streamable, then fall back to
        // using the device's display size.
        if (cropRect == null) {
            Display display = ((WindowManager) mAppContext.getSystemService(Context.WINDOW_SERVICE))
                    .getDefaultDisplay();
            Point screenSize = ScreenSizeCalculator.getInstance().getScreenSize(display);
            asset.decodeBitmap(screenSize.x, screenSize.y, new BitmapReceiver() {
                @Override
                public void onBitmapDecoded(@Nullable Bitmap bitmap) {
                    if (bitmap == null) {
                        callback.onError(null /* throwable */);
                        return;
                    }
                    setIndividualWallpaper(wallpaper, bitmap, destination, callback);
                }
            });
            return;
        }

        BitmapCropper bitmapCropper = InjectorProvider.getInjector().getBitmapCropper();
        bitmapCropper.cropAndScaleBitmap(asset, scale, cropRect, new Callback() {
            @Override
            public void onBitmapCropped(Bitmap croppedBitmap) {
                setIndividualWallpaper(wallpaper, croppedBitmap, destination, callback);
            }

            @Override
            public void onError(@Nullable Throwable e) {
                callback.onError(e);
            }
        });
    }

    @Override
    public void setIndividualWallpaperWithPosition(Activity activity, WallpaperInfo wallpaper,
                                                   @WallpaperPosition int wallpaperPosition, SetWallpaperCallback callback) {
        Display display = ((WindowManager) mAppContext.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay();
        Point screenSize = ScreenSizeCalculator.getInstance().getScreenSize(display);

        Asset asset = wallpaper.getAsset(activity);
        asset.decodeRawDimensions(activity, new DimensionsReceiver() {
            @Override
            public void onDimensionsDecoded(@Nullable Point dimensions) {
                if (dimensions == null) {
                    callback.onError(null);
                    return;
                }

                switch (wallpaperPosition) {
                    // Crop out screen-sized center portion of the source image if it's larger than the screen
                    // in both dimensions. Otherwise, decode the entire bitmap and fill the space around it to
                    // fill a new screen-sized bitmap with plain black pixels.
                    case WALLPAPER_POSITION_CENTER:
                        setIndividualWallpaperWithCenterPosition(
                                wallpaper, asset, dimensions, screenSize, callback);
                        break;

                    // Crop out a screen-size portion of the source image and set the bitmap region.
                    case WALLPAPER_POSITION_CENTER_CROP:
                        setIndividualWallpaperWithCenterCropPosition(
                                wallpaper, asset, dimensions, screenSize, callback);
                        break;

                    // Decode full bitmap sized for screen and stretch it to fill the screen dimensions.
                    case WALLPAPER_POSITION_STRETCH:
                        asset.decodeBitmap(screenSize.x, screenSize.y, new BitmapReceiver() {
                            @Override
                            public void onBitmapDecoded(@Nullable Bitmap bitmap) {
                                setIndividualWallpaperStretch(wallpaper, bitmap, screenSize /* stretchSize */,
                                        WallpaperPersister.DEST_BOTH, callback);
                            }
                        });
                        break;

                    default:
                        Log.e(TAG, "Unsupported wallpaper position option specified: " + wallpaperPosition);
                        callback.onError(null);
                }
            }
        });
    }

    /**
     * Sets an individual wallpaper to both home + lock static wallpaper destinations with a center
     * wallpaper position.
     *
     * @param wallpaper  The wallpaper model object representing the wallpaper to be set.
     * @param asset      The wallpaper asset that should be used to set a wallpaper.
     * @param dimensions Raw dimensions of the wallpaper asset.
     * @param screenSize Dimensions of the device screen.
     * @param callback   Callback used to notify original caller of wallpaper set operation result.
     */
    private void setIndividualWallpaperWithCenterPosition(WallpaperInfo wallpaper, Asset asset,
                                                          Point dimensions, Point screenSize, SetWallpaperCallback callback) {
        if (dimensions.x >= screenSize.x && dimensions.y >= screenSize.y) {
            Rect cropRect = new Rect(
                    (dimensions.x - screenSize.x) / 2,
                    (dimensions.y - screenSize.y) / 2,
                    dimensions.x - ((dimensions.x - screenSize.x) / 2),
                    dimensions.y - ((dimensions.y - screenSize.y) / 2));
            asset.decodeBitmapRegion(cropRect, screenSize.x, screenSize.y, new BitmapReceiver() {
                @Override
                public void onBitmapDecoded(@Nullable Bitmap bitmap) {
                    setIndividualWallpaper(wallpaper, bitmap, WallpaperPersister.DEST_BOTH, callback);
                }
            });
        } else {
            // Decode the full bitmap and pass with the screen size as a fill rect.
            asset.decodeBitmap(dimensions.x, dimensions.y, new BitmapReceiver() {
                @Override
                public void onBitmapDecoded(@Nullable Bitmap bitmap) {
                    if (bitmap == null) {
                        callback.onError(null);
                        return;
                    }

                    setIndividualWallpaperFill(wallpaper, bitmap, screenSize /* fillSize */,
                            WallpaperPersister.DEST_BOTH, callback);
                }
            });
        }
    }

    /**
     * Sets an individual wallpaper to both home + lock static wallpaper destinations with a center
     * cropped wallpaper position.
     *
     * @param wallpaper  The wallpaper model object representing the wallpaper to be set.
     * @param asset      The wallpaper asset that should be used to set a wallpaper.
     * @param dimensions Raw dimensions of the wallpaper asset.
     * @param screenSize Dimensions of the device screen.
     * @param callback   Callback used to notify original caller of wallpaper set operation result.
     */
    private void setIndividualWallpaperWithCenterCropPosition(WallpaperInfo wallpaper, Asset asset,
                                                              Point dimensions, Point screenSize, SetWallpaperCallback callback) {
        float scale = Math.max((float) screenSize.x / dimensions.x,
                (float) screenSize.y / dimensions.y);

        int scaledImageWidth = (int) (dimensions.x * scale);
        int scaledImageHeight = (int) (dimensions.y * scale);

        // Crop rect is in post-scale units.
        Rect cropRect = new Rect(
                (scaledImageWidth - screenSize.x) / 2,
                (scaledImageHeight - screenSize.y) / 2,
                scaledImageWidth - ((scaledImageWidth - screenSize.x) / 2),
                scaledImageHeight - (((scaledImageHeight - screenSize.y) / 2)));

        setIndividualWallpaper(
                wallpaper, asset, cropRect, scale, WallpaperPersister.DEST_BOTH, callback);
    }

    /**
     * Sets a static individual wallpaper to the system via the WallpaperManager.
     *
     * @param wallpaper     Wallpaper model object.
     * @param croppedBitmap Bitmap representing the individual wallpaper image.
     * @param destination   The destination - where to set the wallpaper to.
     * @param callback      Called once the wallpaper was set or if an error occurred.
     */
    private void setIndividualWallpaper(WallpaperInfo wallpaper, Bitmap croppedBitmap,
                                        @Destination int destination, SetWallpaperCallback callback) {
        SetWallpaperTask setWallpaperTask =
                new SetWallpaperTask(wallpaper, croppedBitmap, destination, callback);
        setWallpaperTask.execute();
    }

    /**
     * Sets a static individual wallpaper to the system via the WallpaperManager with a fill option.
     *
     * @param wallpaper     Wallpaper model object.
     * @param croppedBitmap Bitmap representing the individual wallpaper image.
     * @param fillSize      Specifies the final bitmap size that should be set to WallpaperManager. This
     *                      final bitmap will show the visible area of the provided bitmap after applying a mask with
     *                      black background the source bitmap and centering. There may be black borders around the
     *                      original bitmap if it's smaller than the fillSize in one or both dimensions.
     * @param destination   The destination - where to set the wallpaper to.
     * @param callback      Called once the wallpaper was set or if an error occurred.
     */
    private void setIndividualWallpaperFill(WallpaperInfo wallpaper, Bitmap croppedBitmap,
                                            Point fillSize, @Destination int destination, SetWallpaperCallback callback) {
        SetWallpaperTask setWallpaperTask =
                new SetWallpaperTask(wallpaper, croppedBitmap, destination, callback);
        setWallpaperTask.setFillSize(fillSize);
        setWallpaperTask.execute();
    }

    /**
     * Sets a static individual wallpaper to the system via the WallpaperManager with a stretch
     * option.
     *
     * @param wallpaper     Wallpaper model object.
     * @param croppedBitmap Bitmap representing the individual wallpaper image.
     * @param stretchSize   Specifies the final size to which the the bitmap should be stretched prior
     *                      to being set to the device.
     * @param destination   The destination - where to set the wallpaper to.
     * @param callback      Called once the wallpaper was set or if an error occurred.
     */
    private void setIndividualWallpaperStretch(WallpaperInfo wallpaper, Bitmap croppedBitmap,
                                               Point stretchSize, @Destination int destination, SetWallpaperCallback callback) {
        SetWallpaperTask setWallpaperTask =
                new SetWallpaperTask(wallpaper, croppedBitmap, destination, callback);
        setWallpaperTask.setStretchSize(stretchSize);
        setWallpaperTask.execute();
    }

    /**
     * Sets a static individual wallpaper stream to the system via the WallpaperManager.
     *
     * @param wallpaper   Wallpaper model object.
     * @param inputStream JPEG or PNG stream of wallpaper image's bytes.
     * @param destination The destination - where to set the wallpaper to.
     * @param callback    Called once the wallpaper was set or if an error occurred.
     */
    private void setIndividualWallpaper(WallpaperInfo wallpaper, InputStream inputStream,
                                        @Destination int destination, SetWallpaperCallback callback) {
        SetWallpaperTask setWallpaperTask =
                new SetWallpaperTask(wallpaper, inputStream, destination, callback);
        setWallpaperTask.execute();
    }

    @Override
    public boolean setWallpaperInRotation(Bitmap wallpaperBitmap, List<String> attributions,
                                          int actionLabelRes, int actionIconRes,
                                          String actionUrl, String collectionId) {
        @RotatingWallpaperComponent int rotatingWallpaperComponent = mRotatingWallpaperComponentChecker
                .getCurrentRotatingWallpaperComponent(mAppContext);

        switch (rotatingWallpaperComponent) {
            case RotatingWallpaperComponentChecker.ROTATING_WALLPAPER_COMPONENT_STATIC:
                return setWallpaperInRotationStatic(wallpaperBitmap, attributions, actionUrl,
                        actionLabelRes, actionIconRes, collectionId);
            case RotatingWallpaperComponentChecker.ROTATING_WALLPAPER_COMPONENT_LIVE:
                return setWallpaperInRotationLive(wallpaperBitmap, attributions, actionUrl,
                        actionLabelRes, actionIconRes, collectionId);
            default:
                Log.e(TAG, "Unknown rotating wallpaper component: " + rotatingWallpaperComponent);
                return false;
        }
    }

    @Override
    public int setWallpaperBitmapInNextRotation(Bitmap wallpaperBitmap) {
        @RotatingWallpaperComponent int rotatingWallpaperComponent = mRotatingWallpaperComponentChecker
                .getNextRotatingWallpaperComponent(mAppContext);

        switch (rotatingWallpaperComponent) {
            case RotatingWallpaperComponentChecker.ROTATING_WALLPAPER_COMPONENT_STATIC:
                return setWallpaperBitmapInRotationStatic(wallpaperBitmap);
            case RotatingWallpaperComponentChecker.ROTATING_WALLPAPER_COMPONENT_LIVE:
                boolean isSuccess = setWallpaperBitmapInRotationLive(wallpaperBitmap, true /* isPreview */);
                return isSuccess ? 1 : 0;
            default:
                Log.e(TAG, "Unknown rotating wallpaper component: " + rotatingWallpaperComponent);
                return 0;
        }
    }

    @Override
    public boolean finalizeWallpaperForNextRotation(List<String> attributions, String actionUrl,
                                                    int actionLabelRes, int actionIconRes,
                                                    String collectionId, int wallpaperId) {
        @RotatingWallpaperComponent int rotatingWallpaperComponent =
                mRotatingWallpaperComponentChecker.getNextRotatingWallpaperComponent(mAppContext);
        return finalizeWallpaperForRotatingComponent(attributions, actionUrl, actionLabelRes,
                actionIconRes, collectionId, wallpaperId, rotatingWallpaperComponent);
    }

    /**
     * Sets wallpaper image and attributions when a static wallpaper is responsible for presenting the
     * current "daily wallpaper".
     */
    private boolean setWallpaperInRotationStatic(Bitmap wallpaperBitmap, List<String> attributions,
                                                 String actionUrl, int actionLabelRes,
                                                 int actionIconRes, String collectionId) {
        final int wallpaperId = setWallpaperBitmapInRotationStatic(wallpaperBitmap);

        if (wallpaperId == 0) {
            return false;
        }

        return finalizeWallpaperForRotatingComponent(attributions, actionUrl, actionLabelRes,
                actionIconRes, collectionId, wallpaperId,
                RotatingWallpaperComponentChecker.ROTATING_WALLPAPER_COMPONENT_STATIC);
    }

    /**
     * Finalizes wallpaper metadata by persisting them to SharedPreferences and finalizes the
     * wallpaper image for live rotating components by copying the "preview" image to the "final"
     * image file location.
     *
     * @return Whether the operation was successful.
     */
    private boolean finalizeWallpaperForRotatingComponent(List<String> attributions,
            String actionUrl,
            int actionLabelRes,
            int actionIconRes,
            String collectionId,
            int wallpaperId,
            @RotatingWallpaperComponent int rotatingWallpaperComponent) {
        mWallpaperPreferences.clearHomeWallpaperMetadata();

        boolean isLockWallpaperSet = isSeparateLockScreenWallpaperSet();

        // Persist wallpaper IDs if the rotating wallpaper component is static and this device is
        // running Android N or later.
        if (rotatingWallpaperComponent
                == RotatingWallpaperComponentChecker.ROTATING_WALLPAPER_COMPONENT_STATIC) {
            if (BuildCompat.isAtLeastN()) {
                mWallpaperPreferences.setHomeWallpaperManagerId(wallpaperId);

                // Only copy over wallpaper ID to lock wallpaper if no explicit lock wallpaper is set (so
                // metadata isn't lost if a user explicitly sets a home-only wallpaper).
                if (!isLockWallpaperSet) {
                    mWallpaperPreferences.setLockWallpaperId(wallpaperId);
                }
            } else { // Pre-N but using static component
                // Compute bitmap hash code after setting the wallpaper because JPEG compression has likely
                // changed many pixels' color values. Forget the previously loaded wallpaper bitmap so that
                // WallpaperManager doesn't return the old wallpaper drawable.
                mWallpaperManager.forgetLoadedWallpaper();
                Bitmap bitmap = ((BitmapDrawable) mWallpaperManagerCompat.getDrawable()).getBitmap();
                long bitmapHash = BitmapUtils.generateHashCode(bitmap);

                mWallpaperPreferences.setHomeWallpaperHashCode(bitmapHash);
            }
        } else { // Live wallpaper rotating component.

            // Copy "preview" JPEG to "rotating" JPEG if the preview file exists.
           File rotatingWallpaper;
            try {
                rotatingWallpaper = moveToDeviceProtectedStorage(
                        NoBackupImageWallpaper.PREVIEW_WALLPAPER_FILE_PATH,
                        NoBackupImageWallpaper.ROTATING_WALLPAPER_FILE_PATH);
            } catch (Exception e) {
                DiskBasedLogger.e(
                        TAG,
                        "Unable to move preview to final file for rotating wallpaper " +
                                "file (exception)" + e.toString(),
                        mAppContext);
                return false;
            }
            if (rotatingWallpaper == null) {
                rotatingWallpaper = mDeviceProtectedContext.getFileStreamPath(
                        NoBackupImageWallpaper.ROTATING_WALLPAPER_FILE_PATH);
            }
            try {
                FileInputStream fis = new FileInputStream(rotatingWallpaper.getAbsolutePath());
                Bitmap bitmap = BitmapFactory.decodeStream(fis);
                fis.close();

                if (bitmap != null) {
                    long bitmapHash = BitmapUtils.generateHashCode(bitmap);
                    mWallpaperPreferences.setHomeWallpaperHashCode(bitmapHash);
                } else {
                    Log.e(TAG, "Unable to decode rotating wallpaper file");
                    return false;
                }
            } catch (FileNotFoundException e) {
                Log.e(TAG, "Rotating wallpaper file not found at path: "
                        + rotatingWallpaper.getAbsolutePath());
                e.printStackTrace();
                return false;
            } catch (IOException e) {
                Log.e(TAG, "IOException when closing FileInputStream " + e);
                return false;
            }

            mWallpaperChangedNotifier.notifyWallpaperChanged();

            // Send a broadcast to {@link RotatingWallpaperChangedReceiver} in the :live_wallpaper
            // process so the currently displayed wallpaper updates.
            notifyLiveWallpaperBitmapChanged();
        }

        mWallpaperPreferences.setHomeWallpaperAttributions(attributions);
        mWallpaperPreferences.setHomeWallpaperActionUrl(actionUrl);
        mWallpaperPreferences.setHomeWallpaperActionLabelRes(actionLabelRes);
        mWallpaperPreferences.setHomeWallpaperActionIconRes(actionIconRes);
        // Only set base image URL for static Backdrop images, not for rotation.
        mWallpaperPreferences.setHomeWallpaperBaseImageUrl(null);
        mWallpaperPreferences.setHomeWallpaperCollectionId(collectionId);

        // Set metadata to lock screen also when the rotating wallpaper is a static one so if user sets
        // a home screen-only wallpaper later, these attributions will still be available.
        if (rotatingWallpaperComponent
                == RotatingWallpaperComponentChecker.ROTATING_WALLPAPER_COMPONENT_STATIC
                && !isLockWallpaperSet) {
            mWallpaperPreferences.setLockWallpaperAttributions(attributions);
            mWallpaperPreferences.setLockWallpaperActionUrl(actionUrl);
            mWallpaperPreferences.setLockWallpaperActionLabelRes(actionLabelRes);
            mWallpaperPreferences.setLockWallpaperActionIconRes(actionIconRes);
            mWallpaperPreferences.setLockWallpaperCollectionId(collectionId);
        }

        return true;
    }

    /**
     * Sets wallpaper image and attributions when a live wallpaper is responsible for presenting the
     * current "daily wallpaper".
     */
    private boolean setWallpaperInRotationLive(Bitmap wallpaperBitmap, List<String> attributions,
                                               String actionUrl, int actionLabelRes,
                                               int actionIconRes, String collectionId) {

        synchronized (RotatingWallpaperLockProvider.getInstance()) {
            if (!setWallpaperBitmapInRotationLive(wallpaperBitmap, false /* isPreview */)) {
                return false;
            }

            return finalizeWallpaperForRotatingComponent(attributions, actionUrl, actionLabelRes,
                    actionIconRes, collectionId,
                    0 /* wallpaperId */,
                    RotatingWallpaperComponentChecker.ROTATING_WALLPAPER_COMPONENT_LIVE);
        }
    }

    /**
     * Sets a wallpaper in rotation as a static wallpaper to the {@link WallpaperManager} with the
     * option allowBackup=false to save user data.
     *
     * @return wallpaper ID for the wallpaper bitmap.
     */
    private int setWallpaperBitmapInRotationStatic(Bitmap wallpaperBitmap) {
        // Set wallpaper to home-only instead of both home and lock if there's a distinct lock-only
        // static wallpaper set so we don't override the lock wallpaper.
        boolean isLockWallpaperSet = isSeparateLockScreenWallpaperSet();

        int whichWallpaper = (isLockWallpaperSet)
                ? WallpaperManagerCompat.FLAG_SYSTEM
                : WallpaperManagerCompat.FLAG_SYSTEM | WallpaperManagerCompat.FLAG_LOCK;

        return setBitmapToWallpaperManagerCompat(wallpaperBitmap, false /* allowBackup */,
                whichWallpaper);
    }

    /**
     * Sets a wallpaper in rotation as a live wallpaper. Writes wallpaper bitmap to a file in internal
     * storage and sends a broadcast to the live wallpaper notifying it that rotating wallpaper image
     * data changed.
     *
     * @return whether the set wallpaper operation was successful.
     */
    private boolean setWallpaperBitmapInRotationLive(Bitmap wallpaperBitmap, boolean isPreview) {
        File pendingFile;
        try {
            pendingFile = File.createTempFile("rotating_pending", ".jpg", mAppContext.getFilesDir());
        } catch (IOException e) {
            Log.e(TAG, "Unable to create temp file for rotating wallpaper");
            return false;
        }

        FileOutputStream fos;
        try {
            fos = mAppContext.openFileOutput(pendingFile.getName(), Context.MODE_PRIVATE);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Unable to open file output stream for pending rotating wallpaper file");
            return false;
        }

        boolean compressedSuccessfully =
                wallpaperBitmap.compress(CompressFormat.JPEG, DEFAULT_COMPRESS_QUALITY, fos);

        // Close the file stream.
        try {
            fos.flush();
            fos.close();
        } catch (IOException e) {
            Log.e(TAG, "Unable to close FileOutputStream for pending rotating wallpaper file"
                    + " (compress succeeded");
            return false;
        }

        if (compressedSuccessfully) {
            // Compressing/writing to disk succeeded, so move the pending file to the final location.
            try {
                if (isPreview) {
                    if (!pendingFile.renameTo(mAppContext.getFileStreamPath(
                            NoBackupImageWallpaper.PREVIEW_WALLPAPER_FILE_PATH))) {
                        return false;
                    }
                } else {
                    moveToDeviceProtectedStorage(pendingFile.getName(),
                            NoBackupImageWallpaper.ROTATING_WALLPAPER_FILE_PATH);
                }
            } catch (Exception e) {
                Log.e(TAG, "Unable to rename pending to final file for rotating wallpaper file"
                        + " (exception)" + e.toString());
                return false;
            }
        } else {
            Log.e(TAG, "Unable to compress the wallpaper bitmap");

            // Delete the pending file since compressing/writing the image to disk failed.
            try {
                pendingFile.delete();
            } catch (SecurityException e) {
                Log.e(TAG, "Unable to delete pending rotating wallpaper file");
                return false;
            }

            return false;
        }

        mWallpaperChangedNotifier.notifyWallpaperChanged();

        // Send a broadcast to {@link RotatingWallpaperChangedReceiver} in the :live_wallpaper
        // process so the currently displayed wallpaper updates if the live wallpaper is set to the
        // device.
        notifyLiveWallpaperBitmapChanged();

        return true;
    }

    /**
     * Sets a wallpaper bitmap to the {@link WallpaperManagerCompat}.
     *
     * @return an integer wallpaper ID. This is an actual wallpaper ID on N and later versions of
     * Android, otherwise on pre-N versions of Android will return a positive integer when the
     * operation was successful and zero if the operation encountered an error.
     */
    private int setBitmapToWallpaperManagerCompat(Bitmap wallpaperBitmap, boolean allowBackup,
                                                  int whichWallpaper) {
        ByteArrayOutputStream tmpOut = new ByteArrayOutputStream();
        if (wallpaperBitmap.compress(CompressFormat.JPEG, DEFAULT_COMPRESS_QUALITY, tmpOut)) {
            try {
                byte[] outByteArray = tmpOut.toByteArray();
                return mWallpaperManagerCompat.setStream(
                        new ByteArrayInputStream(outByteArray),
                        null /* visibleCropHint */,
                        allowBackup,
                        whichWallpaper);
            } catch (IOException e) {
                Log.e(TAG, "unable to write stream to wallpaper manager");
                return 0;
            }
        } else {
            Log.e(TAG, "unable to compress wallpaper");
            try {
                return mWallpaperManagerCompat.setBitmap(
                        wallpaperBitmap,
                        null /* visibleCropHint */,
                        allowBackup,
                        whichWallpaper);
            } catch (IOException e) {
                Log.e(TAG, "unable to set wallpaper");
                return 0;
            }
        }
    }

    private int setStreamToWallpaperManagerCompat(InputStream inputStream, boolean allowBackup,
                                                  int whichWallpaper) {
        try {
            return mWallpaperManagerCompat.setStream(inputStream, null, allowBackup, whichWallpaper);
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    public void setWallpaperInfoInPreview(WallpaperInfo wallpaper) {
        mWallpaperInfoInPreview = wallpaper;
    }

    @Override
    public void onLiveWallpaperSet() {
        android.app.WallpaperInfo currentWallpaperComponent = mWallpaperManager.getWallpaperInfo();
        android.app.WallpaperInfo previewedWallpaperComponent =
                mWallpaperInfoInPreview.getWallpaperComponent();

        // If there is no live wallpaper set on the WallpaperManager or it doesn't match the
        // WallpaperInfo which was last previewed, then do nothing and nullify last previewed wallpaper.
        if (currentWallpaperComponent == null || previewedWallpaperComponent == null
                || !currentWallpaperComponent.getPackageName()
                .equals(previewedWallpaperComponent.getPackageName())) {
            mWallpaperInfoInPreview = null;
            return;
        }

        setLiveWallpaperMetadata();
    }

    /**
     * Returns whether a separate lock-screen (static) wallpaper is set to the WallpaperManager.
     */
    private boolean isSeparateLockScreenWallpaperSet() {
        ParcelFileDescriptor lockWallpaperFile =
                mWallpaperManagerCompat.getWallpaperFile(WallpaperManagerCompat.FLAG_LOCK);

        boolean isLockWallpaperSet = false;

        if (lockWallpaperFile != null) {
            isLockWallpaperSet = true;

            try {
                lockWallpaperFile.close();
            } catch (IOException e) {
                Log.e(TAG, "Unable to close PFD for lock wallpaper", e);
            }
        }

        return isLockWallpaperSet;
    }

    /**
     * Sets the live wallpaper's metadata on SharedPreferences.
     */
    private void setLiveWallpaperMetadata() {
        android.app.WallpaperInfo previewedWallpaperComponent =
                mWallpaperInfoInPreview.getWallpaperComponent();

        mWallpaperPreferences.clearHomeWallpaperMetadata();
        // NOTE: We explicitly do not also clear the lock wallpaper metadata. Since the user may have
        // set the live wallpaper on the home screen only, we leave the lock wallpaper metadata intact.
        // If the user has set the live wallpaper for both home and lock screens, then the
        // WallpaperRefresher will pick up on that and update the preferences later.
        mWallpaperPreferences
                .setHomeWallpaperAttributions(mWallpaperInfoInPreview.getAttributions(mAppContext));
        mWallpaperPreferences.setHomeWallpaperPackageName(
                previewedWallpaperComponent.getPackageName());
        mWallpaperPreferences.setHomeWallpaperCollectionId(
                mWallpaperInfoInPreview.getCollectionId(mAppContext));
        mWallpaperPreferences.setWallpaperPresentationMode(
                WallpaperPreferences.PRESENTATION_MODE_STATIC);
        mWallpaperPreferences.clearDailyRotations();
    }

    /**
     * Notifies the :live_wallpaper process that the contents of the rotating live wallpaper bitmap
     * changed.
     */
    private void notifyLiveWallpaperBitmapChanged() {
        Intent intent = new Intent(mAppContext.getPackageName()
                + NoBackupImageWallpaper.ACTION_ROTATING_WALLPAPER_CHANGED);
        // Handled by a runtime-registered receiver in NoBackupImageWallpaper.
        intent.setPackage(mAppContext.getPackageName());
        mAppContext.sendBroadcast(intent);
    }

    /**
     * Moves a file from the app's files directory to the device content protected storage
     * directory.
     * @param srcFileName Name of the source file (just the name, no path). It's expected to be
     *                    located in {@link Context#getFilesDir()} for {@link #mAppContext}
     * @param dstFileName Name of the destination file (just the name, no path), which will be
     *                    located in {@link Context#getFilesDir()}
     *                    for {@link #mDeviceProtectedContext}
     * @return a {@link File} corresponding to the moved file in its new location, or null if
     *      nothing was moved (because srcFileName didn't exist).
     */
    @Nullable
    private File moveToDeviceProtectedStorage(String srcFileName, String dstFileName)
            throws IOException {
        return FileMover.moveFileBetweenContexts(mAppContext, srcFileName, mDeviceProtectedContext,
                dstFileName);
    }

    private class SetWallpaperTask extends AsyncTask<Void, Void, Boolean> {

        private final WallpaperInfo mWallpaper;
        @Destination
        private final int mDestination;
        private final WallpaperPersister.SetWallpaperCallback mCallback;

        private Bitmap mBitmap;
        private InputStream mInputStream;

        /**
         * Optional parameters for applying a post-decoding fill or stretch transformation.
         */
        @Nullable
        private Point mFillSize;
        @Nullable
        private Point mStretchSize;

        SetWallpaperTask(WallpaperInfo wallpaper, Bitmap bitmap, @Destination int destination,
                         WallpaperPersister.SetWallpaperCallback callback) {
            super();
            mWallpaper = wallpaper;
            mBitmap = bitmap;
            mDestination = destination;
            mCallback = callback;
        }

        /**
         * Constructor for SetWallpaperTask which takes an InputStream instead of a bitmap. The task
         * will close the InputStream once it is done with it.
         */
        SetWallpaperTask(WallpaperInfo wallpaper, InputStream stream,
                         @Destination int destination, WallpaperPersister.SetWallpaperCallback callback) {
            mWallpaper = wallpaper;
            mInputStream = stream;
            mDestination = destination;
            mCallback = callback;
        }

        void setFillSize(Point fillSize) {
            if (mStretchSize != null) {
                throw new IllegalArgumentException("Can't pass a fill size option if a stretch size is "
                        + "already set.");
            }
            mFillSize = fillSize;
        }

        void setStretchSize(Point stretchSize) {
            if (mFillSize != null) {
                throw new IllegalArgumentException("Can't pass a stretch size option if a fill size is "
                        + "already set.");
            }
            mStretchSize = stretchSize;
        }

        @Override
        protected Boolean doInBackground(Void... unused) {
            int whichWallpaper;
            if (mDestination == DEST_HOME_SCREEN) {
                whichWallpaper = WallpaperManagerCompat.FLAG_SYSTEM;
            } else if (mDestination == DEST_LOCK_SCREEN) {
                whichWallpaper = WallpaperManagerCompat.FLAG_LOCK;
            } else { // DEST_BOTH
                whichWallpaper = WallpaperManagerCompat.FLAG_SYSTEM
                        | WallpaperManagerCompat.FLAG_LOCK;
            }

            // NOTE: The rotating wallpaper component must be determined here, _before_ actually setting
            // the bitmap/stream on WallpaperManagerCompat, to ensure that the
            // RotatingWallpaperComponentChecker is doing its check while rotation is still enabled.
            // E.g., if "live wallpaper" is the component, then it needs to check while live wallpaper is
            // still set as the active wallpaper on the device. Otherwise, the checker would see a static
            // wallpaper is currently set and it would return the wrong value.
            @RotatingWallpaperComponent int currentRotatingWallpaperComponent =
                    mRotatingWallpaperComponentChecker.getCurrentRotatingWallpaperComponent(mAppContext);

            boolean wasLockWallpaperSet = LockWallpaperStatusChecker.isLockWallpaperSet(mAppContext);

            boolean allowBackup = mWallpaper.getBackupPermission() == WallpaperInfo.BACKUP_ALLOWED;
            final int wallpaperId;
            if (mBitmap != null) {
                // Apply fill or stretch transformations on mBitmap if necessary.
                if (mFillSize != null) {
                    mBitmap = BitmapTransformer.applyFillTransformation(mBitmap, mFillSize);
                }
                if (mStretchSize != null) {
                    mBitmap = Bitmap.createScaledBitmap(mBitmap, mStretchSize.x, mStretchSize.y, true);
                }

                wallpaperId = setBitmapToWallpaperManagerCompat(mBitmap, allowBackup, whichWallpaper);
            } else if (mInputStream != null) {
                wallpaperId = setStreamToWallpaperManagerCompat(mInputStream, allowBackup, whichWallpaper);
            } else {
                Log.e(TAG, "Both the wallpaper bitmap and input stream are null so we're unable to set any "
                        + "kind of wallpaper here.");
                wallpaperId = 0;
            }

            if (wallpaperId > 0) {
                if (mDestination == DEST_HOME_SCREEN
                        && mWallpaperPreferences.getWallpaperPresentationMode()
                        == WallpaperPreferences.PRESENTATION_MODE_ROTATING
                        && !wasLockWallpaperSet
                        && BuildCompat.isAtLeastN()) {
                    copyRotatingWallpaperToLock(currentRotatingWallpaperComponent);
                }
                setImageWallpaperMetadata(mDestination, wallpaperId);
                return true;
            } else {
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean isSuccess) {
            if (mInputStream != null) {
                try {
                    mInputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Failed to close input stream " + e);
                    mCallback.onError(e /* throwable */);
                    return;
                }
            }

            if (isSuccess) {
                mCallback.onSuccess();
                mWallpaperChangedNotifier.notifyWallpaperChanged();
            } else {
                mCallback.onError(null /* throwable */);
            }
        }

        /**
         * Copies home wallpaper metadata to lock, and if rotation was enabled with a live wallpaper
         * previously, then copies over the rotating wallpaper image to the WallpaperManager also.
         * <p>
         * Used to accommodate the case where a user had gone from a home+lock daily rotation to
         * selecting a static wallpaper on home-only. The image and metadata that was previously
         * rotating is now copied to the lock screen.
         *
         * @param currentRotatingWallpaperComponent The component in which rotating wallpapers were
         *                                          presented.
         */
        private void copyRotatingWallpaperToLock(
                @RotatingWallpaperComponent int currentRotatingWallpaperComponent) {

            mWallpaperPreferences.setLockWallpaperAttributions(
                    mWallpaperPreferences.getHomeWallpaperAttributions());
            mWallpaperPreferences.setLockWallpaperActionUrl(
                    mWallpaperPreferences.getHomeWallpaperActionUrl());
            mWallpaperPreferences.setLockWallpaperActionLabelRes(
                    mWallpaperPreferences.getHomeWallpaperActionLabelRes());
            mWallpaperPreferences.setLockWallpaperActionIconRes(
                    mWallpaperPreferences.getHomeWallpaperActionIconRes());
            mWallpaperPreferences.setLockWallpaperCollectionId(
                    mWallpaperPreferences.getHomeWallpaperCollectionId());

            // Set the lock wallpaper ID to what Android set it to, following its having
            // copied the system wallpaper over to the lock screen when we changed from
            // "both" to distinct system and lock screen wallpapers.
            if (currentRotatingWallpaperComponent
                    == RotatingWallpaperComponentChecker.ROTATING_WALLPAPER_COMPONENT_STATIC) {
                mWallpaperPreferences.setLockWallpaperId(
                        mWallpaperManagerCompat.getWallpaperId(WallpaperManagerCompat.FLAG_LOCK));
            } else {
                try {
                    FileInputStream fileInputStream = mDeviceProtectedContext.openFileInput(
                            NoBackupImageWallpaper.ROTATING_WALLPAPER_FILE_PATH);
                    int lockWallpaperId = setStreamToWallpaperManagerCompat(
                            fileInputStream, false /* allowBackup */, WallpaperManagerCompat.FLAG_LOCK);
                    fileInputStream.close();
                    mWallpaperPreferences.setLockWallpaperId(lockWallpaperId);
                } catch (FileNotFoundException e) {
                    Log.e(TAG, "Couldn't copy over previously rotating wallpaper to lock screen.");
                } catch (IOException e) {
                    Log.e(TAG, "IOException when closing the file input stream " + e);
                }
            }
        }

        /**
         * Sets the image wallpaper's metadata on SharedPreferences. This method is called after the set
         * wallpaper operation is successful.
         *
         * @param destination Which destination of wallpaper the metadata corresponds to (home screen,
         *                    lock screen, or both).
         * @param wallpaperId The ID of the static wallpaper returned by WallpaperManager, which on N
         *                    and later versions of Android uniquely identifies a wallpaper image.
         */
        private void setImageWallpaperMetadata(@Destination int destination, int wallpaperId) {
            if (destination == DEST_HOME_SCREEN || destination == DEST_BOTH) {
                mWallpaperPreferences.clearHomeWallpaperMetadata();
                setImageWallpaperHomeMetadata(wallpaperId);

                // Reset presentation mode to STATIC if an individual wallpaper is set to the home screen
                // because rotation always affects at least the home screen.
                mWallpaperPreferences.setWallpaperPresentationMode(
                        WallpaperPreferences.PRESENTATION_MODE_STATIC);
            }

            if (destination == DEST_LOCK_SCREEN || destination == DEST_BOTH) {
                mWallpaperPreferences.clearLockWallpaperMetadata();
                setImageWallpaperLockMetadata(wallpaperId);
            }

            mWallpaperPreferences.clearDailyRotations();
        }

        private void setImageWallpaperHomeMetadata(int homeWallpaperId) {
            if (BuildCompat.isAtLeastN()) {
                mWallpaperPreferences.setHomeWallpaperManagerId(homeWallpaperId);
            }

            // Compute bitmap hash code after setting the wallpaper because JPEG compression has likely
            // changed many pixels' color values. Forget the previously loaded wallpaper bitmap so that
            // WallpaperManager doesn't return the old wallpaper drawable. Do this on N+ devices in
            // addition to saving the wallpaper ID for the purpose of backup & restore.
            mWallpaperManager.forgetLoadedWallpaper();
            mBitmap = ((BitmapDrawable) mWallpaperManagerCompat.getDrawable()).getBitmap();
            long bitmapHash = BitmapUtils.generateHashCode(mBitmap);

            mWallpaperPreferences.setHomeWallpaperHashCode(bitmapHash);

            mWallpaperPreferences.setHomeWallpaperAttributions(
                    mWallpaper.getAttributions(mAppContext));
            mWallpaperPreferences.setHomeWallpaperBaseImageUrl(mWallpaper.getBaseImageUrl());
            mWallpaperPreferences.setHomeWallpaperActionUrl(mWallpaper.getActionUrl(mAppContext));
            mWallpaperPreferences.setHomeWallpaperActionLabelRes(
                    mWallpaper.getActionLabelRes(mAppContext));
            mWallpaperPreferences.setHomeWallpaperActionIconRes(
                    mWallpaper.getActionIconRes(mAppContext));
            mWallpaperPreferences.setHomeWallpaperCollectionId(
                    mWallpaper.getCollectionId(mAppContext));
            mWallpaperPreferences.setHomeWallpaperRemoteId(mWallpaper.getWallpaperId());
        }

        private void setImageWallpaperLockMetadata(int lockWallpaperId) {
            mWallpaperPreferences.setLockWallpaperId(lockWallpaperId);
            mWallpaperPreferences.setLockWallpaperAttributions(
                    mWallpaper.getAttributions(mAppContext));
            mWallpaperPreferences.setLockWallpaperActionUrl(mWallpaper.getActionUrl(mAppContext));
            mWallpaperPreferences.setLockWallpaperActionLabelRes(
                    mWallpaper.getActionLabelRes(mAppContext));
            mWallpaperPreferences.setLockWallpaperActionIconRes(
                    mWallpaper.getActionIconRes(mAppContext));
            mWallpaperPreferences.setLockWallpaperCollectionId(
                    mWallpaper.getCollectionId(mAppContext));

            // Save the lock wallpaper image's hash code as well for the sake of backup & restore because
            // WallpaperManager-generated IDs are specific to a physical device and cannot be used to
            // identify a wallpaper image on another device after restore is complete.
            saveLockWallpaperHashCode();
        }

        private void saveLockWallpaperHashCode() {
            Bitmap lockBitmap = null;

            ParcelFileDescriptor parcelFd = mWallpaperManagerCompat.getWallpaperFile(
                    WallpaperManagerCompat.FLAG_LOCK);

            if (parcelFd == null) {
                return;
            }

            InputStream fileStream = null;
            try {
                fileStream = new FileInputStream(parcelFd.getFileDescriptor());
                lockBitmap = BitmapFactory.decodeStream(fileStream);
                parcelFd.close();
            } catch (IOException e) {
                Log.e(TAG, "IO exception when closing the file descriptor.");
            } finally {
                if (fileStream != null) {
                    try {
                        fileStream.close();
                    } catch (IOException e) {
                        Log.e(TAG, "IO exception when closing the input stream for the lock screen WP.");
                    }
                }
            }

            if (lockBitmap != null) {
                long bitmapHash = BitmapUtils.generateHashCode(lockBitmap);
                mWallpaperPreferences.setLockWallpaperHashCode(bitmapHash);
            }
        }
    }
}
