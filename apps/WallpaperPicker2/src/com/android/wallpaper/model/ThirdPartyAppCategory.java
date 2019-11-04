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
package com.android.wallpaper.model;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import com.android.wallpaper.R;
import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.util.ActivityUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a third party wallpaper "provider" (app) from the system.
 */
public class ThirdPartyAppCategory extends Category {
    private final ResolveInfo mResolveInfo;

    public ThirdPartyAppCategory(Context context, ResolveInfo resolveInfo, String collectionId,
                                 int priority) {
        super(
                resolveInfo.loadLabel(context.getPackageManager()).toString() /* title */,
                collectionId,
                priority);
        mResolveInfo = resolveInfo;
    }

    /**
     * Returns a list of all third-party wallpaper apps found on the device.
     */
    public static List<ThirdPartyAppCategory> getAll(Context context, int priority,
                                                     List<String> excludedPackageNames) {
        final PackageManager pm = context.getPackageManager();

        final Intent pickWallpaperIntent = new Intent(Intent.ACTION_SET_WALLPAPER);
        final List<ResolveInfo> apps = pm.queryIntentActivities(pickWallpaperIntent, 0);

        List<ThirdPartyAppCategory> thirdPartyApps = new ArrayList<ThirdPartyAppCategory>();

        // Get list of image picker intents.
        Intent pickImageIntent = new Intent(Intent.ACTION_GET_CONTENT);
        pickImageIntent.setType("image/*");
        final List<ResolveInfo> imagePickerActivities =
                context.getPackageManager().queryIntentActivities(pickImageIntent, 0);

        outerLoop:
        for (int i = 0; i < apps.size(); i++) {
            ResolveInfo info = apps.get(i);

            final ComponentName itemComponentName =
                    new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
            final String itemPackageName = itemComponentName.getPackageName();

            if (excludedPackageNames.contains(itemPackageName)
                    || itemPackageName.equals(context.getPackageName())) {
                continue;
            }

            // Exclude any package that already responds to the image picker intent.
            for (ResolveInfo imagePickerActivityInfo : imagePickerActivities) {
                if (itemPackageName.equals(imagePickerActivityInfo.activityInfo.packageName)) {
                    continue outerLoop;
                }
            }

            ThirdPartyAppCategory category = new ThirdPartyAppCategory(
                    context,
                    info,
                    context.getString(R.string.third_party_app_wallpaper_collection_id) + "_"
                            + itemPackageName,
                    priority);
            thirdPartyApps.add(category);
        }

        return thirdPartyApps;
    }

    @Override
    public void show(Activity srcActivity, PickerIntentFactory unused, int requestCode) {
        final ComponentName itemComponentName = new ComponentName(
                mResolveInfo.activityInfo.packageName,
                mResolveInfo.activityInfo.name);
        Intent launchIntent = new Intent(Intent.ACTION_SET_WALLPAPER);
        launchIntent.setComponent(itemComponentName);
        ActivityUtils.startActivityForResultSafely(
                srcActivity, launchIntent, requestCode);
    }

    @Override
    public int getOverlayIconSizeDp() {
        // 48dp is the default launcher icon size.
        return 48;
    }

    @Override
    public Drawable getOverlayIcon(Context context) {
        return mResolveInfo.loadIcon(context.getPackageManager());
    }

    @Override
    public Asset getThumbnail(Context unused) {
        return null;
    }

    @Override
    public boolean supportsThirdParty() {
        return true;
    }

    @Override
    public boolean containsThirdParty(String packageName) {
        return mResolveInfo.activityInfo.packageName.equals(packageName);
    }
}
