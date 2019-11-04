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
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.util.Log;
import android.util.Pair;

import java.io.File;


/**
 * Provides content from the partner customization apk on the device (if there is one).
 */
public class DefaultPartnerProvider implements PartnerProvider {

    private final String mPackageName;
    private final Resources mResources;

    public DefaultPartnerProvider(Context ctx) {
        Pair<String, Resources> apkInfo = findSystemApk(ctx.getPackageManager());
        if (apkInfo != null) {
            mPackageName = apkInfo.first;
            mResources = apkInfo.second;
        } else {
            mPackageName = null;
            mResources = null;
        }
    }

    /**
     * Finds the partner customization APK in the system directory.
     *
     * @param pm
     * @return Pair of the package name and the Resources for the APK, or null if the APK isn't found
     * on the device.
     */
    private static Pair<String, Resources> findSystemApk(PackageManager pm) {
        final Intent intent = new Intent(PartnerProvider.ACTION_PARTNER_CUSTOMIZATION);
        for (ResolveInfo info : pm.queryBroadcastReceivers(intent, 0)) {
            if (info.activityInfo != null
                    && (info.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                final String packageName = info.activityInfo.packageName;
                try {
                    final Resources res = pm.getResourcesForApplication(packageName);
                    return Pair.create(packageName, res);
                } catch (NameNotFoundException e) {
                    Log.w("DefaultPartnerProvider", "Failed to find resources for " + packageName);
                }
            }
        }
        return null;
    }

    @Override
    public Resources getResources() {
        return mResources;
    }

    @Override
    public File getLegacyWallpaperDirectory() {
        int resId = 0;
        Resources res = getResources();
        // Resources may be null if no partner customization APK has been placed on the system image, so
        // check if null before calling Resources#getIdentifier.
        if (res != null) {
            resId = res.getIdentifier(PartnerProvider.RES_LEGACY_SYSTEM_WALLPAPER_DIR,
                    "string", mPackageName);
        }
        return (resId != 0) ? new File(res.getString(resId)) : null;
    }

    @Override
    public String getPackageName() {
        return mPackageName;
    }

    @Override
    public boolean shouldHideDefaultWallpaper() {
        Resources res = getResources();
        // Resources may be null if no partner customization APK has been placed on the system image, so
        // check if null before calling Resources#getIdentifier.
        if (res != null) {
            final int resId = res.getIdentifier(
                    RES_DEFAULT_WALLPAPER_HIDDEN, /* defType */ "bool", mPackageName);
            return resId != 0 && res.getBoolean(resId);
        }
        return false;
    }
}
