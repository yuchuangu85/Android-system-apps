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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.android.wallpaper.compat.BuildCompat;
import com.android.wallpaper.module.Injector;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.WallpaperPreferences;

/**
 * Generates hash codes for currently set static image wallpapers on N+ devices where they are
 * missing because older versions of the app did not generate and set them.
 * <p>
 * Static image wallpaper hash codes are necessary on N+ devices for the purposes of backup &
 * restore because N+ WallpaperManager integer IDs are local to physical devices and not backed up
 * and restored on the framework side.
 */
@SuppressLint("ServiceCast")
public class MissingHashCodeGenerator extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // This receiver is a no-op on pre-N Android and should only respond to a MY_PACKAGE_REPLACED
        // intent.
        if (!intent.getAction().equals(Intent.ACTION_MY_PACKAGE_REPLACED)
                || !BuildCompat.isAtLeastN()) {
            return;
        }

        WallpaperManager wallpaperManager = WallpaperManager.getInstance(context);

        // Make this receiver a no-op if running in the context of profile where wallpapers are not
        // supported.
        if (!wallpaperManager.isWallpaperSupported()) {
            return;
        }

        Injector injector = InjectorProvider.getInjector();
        WallpaperPreferences wallpaperPreferences = injector.getPreferences(context);
        // Delegate the longer-running work of generating missing hash codes to a JobScheduler job if
        // there's no hash codes saved.
        if (wallpaperPreferences.getHomeWallpaperHashCode() != 0
                && wallpaperPreferences.getLockWallpaperHashCode() != 0) {
            return;
        }

        MissingHashCodeGeneratorJobService.schedule(context);
    }
}
