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
package com.android.wallpaper.backup;

import android.annotation.SuppressLint;
import android.app.WallpaperManager;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.android.wallpaper.asset.BitmapUtils;
import com.android.wallpaper.compat.WallpaperManagerCompat;
import com.android.wallpaper.module.Injector;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.JobSchedulerJobIds;
import com.android.wallpaper.module.WallpaperPreferences;
import com.android.wallpaper.util.DiskBasedLogger;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/**
 * {@link android.app.job.JobScheduler} job for generating missing hash codes for static wallpapers
 * on N+ devices.
 */
@SuppressLint("ServiceCast")
public class MissingHashCodeGeneratorJobService extends JobService {

    private static final String TAG = "MissingHashCodeGenerato"; // max 23 characters

    private Thread mWorkerThread;

    public static void schedule(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        JobInfo newJob = new JobInfo.Builder(
                JobSchedulerJobIds.JOB_ID_GENERATE_MISSING_HASH_CODES,
                new ComponentName(context, MissingHashCodeGeneratorJobService.class))
                .setMinimumLatency(0)
                .setPersisted(true)
                .build();
        scheduler.schedule(newJob);
    }

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        Context context = getApplicationContext();

        // Retrieve WallpaperManager using Context#getSystemService instead of
        // WallpaperManager#getInstance so it can be mocked out in test.
        final WallpaperManager wallpaperManager = (WallpaperManager) context.getSystemService(
                Context.WALLPAPER_SERVICE);

        // Generate missing hash codes on a plain worker thread because we need to do some long-running
        // disk I/O and can call #jobFinished from a background thread.
        mWorkerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Injector injector = InjectorProvider.getInjector();
                WallpaperManagerCompat wallpaperManagerCompat = injector.getWallpaperManagerCompat(context);
                WallpaperPreferences wallpaperPreferences = injector.getPreferences(context);

                boolean isLiveWallpaperSet = wallpaperManager.getWallpaperInfo() != null;

                // Generate and set a home wallpaper hash code if there's no live wallpaper set and no hash
                // code stored already for the home wallpaper.
                if (!isLiveWallpaperSet && wallpaperPreferences.getHomeWallpaperHashCode() == 0) {
                    wallpaperManager.forgetLoadedWallpaper();

                    Drawable wallpaperDrawable = wallpaperManagerCompat.getDrawable();
                    // No work to do if the drawable returned is null due to an underlying platform issue --
                    // being extra defensive with this check due to instability and variability of underlying
                    // platform.
                    if (wallpaperDrawable == null) {
                        DiskBasedLogger.e(TAG, "WallpaperManager#getDrawable returned null and there's no live "
                                + "wallpaper set", context);
                        jobFinished(jobParameters, false /* needsReschedule */);
                        return;
                    }

                    Bitmap bitmap = ((BitmapDrawable) wallpaperDrawable).getBitmap();
                    long homeBitmapHash = BitmapUtils.generateHashCode(bitmap);

                    wallpaperPreferences.setHomeWallpaperHashCode(homeBitmapHash);
                }

                // Generate and set a lock wallpaper hash code if there's none saved.
                if (wallpaperPreferences.getLockWallpaperHashCode() == 0) {
                    ParcelFileDescriptor parcelFd =
                            wallpaperManagerCompat.getWallpaperFile(WallpaperManagerCompat.FLAG_LOCK);
                    boolean isLockWallpaperSet = parcelFd != null;

                    // Copy the home wallpaper's hash code to lock if there's no distinct lock wallpaper set.
                    if (!isLockWallpaperSet) {
                        wallpaperPreferences.setLockWallpaperHashCode(
                                wallpaperPreferences.getHomeWallpaperHashCode());
                        mWorkerThread = null;
                        jobFinished(jobParameters, false /* needsReschedule */);
                        return;
                    }

                    // Otherwise, generate and set the distinct lock wallpaper image's hash code.
                    Bitmap lockBitmap = null;
                    InputStream fileStream = null;
                    try {
                        fileStream = new FileInputStream(parcelFd.getFileDescriptor());
                        lockBitmap = BitmapFactory.decodeStream(fileStream);
                        parcelFd.close();
                    } catch (IOException e) {
                        Log.e(TAG, "IO exception when closing the file descriptor.", e);
                    } finally {
                        if (fileStream != null) {
                            try {
                                fileStream.close();
                            } catch (IOException e) {
                                Log.e(TAG, "IO exception when closing input stream for lock screen wallpaper.", e);
                            }
                        }
                    }

                    if (lockBitmap != null) {
                        wallpaperPreferences.setLockWallpaperHashCode(BitmapUtils.generateHashCode(lockBitmap));
                    }
                    mWorkerThread = null;

                    jobFinished(jobParameters, false /* needsReschedule */);
                }
            }
        });

        mWorkerThread.start();

        // Return true to indicate that this JobService needs to process work on a separate thread.
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        // This job has no special execution parameters (i.e., network capability, device idle or
        // charging), so Android should never call this method to stop the execution of this job early.
        // Return "false" to indicate that this job should not be rescheduled when it's stopped because
        // we have to provide an implementation of this method.
        return false;
    }

    @Nullable
    @VisibleForTesting
  /* package */ Thread getWorkerThread() {
        return mWorkerThread;
    }
}
