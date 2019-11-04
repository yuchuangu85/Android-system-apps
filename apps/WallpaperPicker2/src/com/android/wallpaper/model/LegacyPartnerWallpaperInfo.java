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
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Parcel;

import com.android.wallpaper.R;
import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.FileAsset;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.PartnerProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a wallpaper from the "partner customization" APK installed on the system.
 */
public class LegacyPartnerWallpaperInfo extends WallpaperInfo {
    public static final Creator<LegacyPartnerWallpaperInfo> CREATOR =
            new Creator<LegacyPartnerWallpaperInfo>() {
                @Override
                public LegacyPartnerWallpaperInfo createFromParcel(Parcel in) {
                    return new LegacyPartnerWallpaperInfo(in);
                }

                @Override
                public LegacyPartnerWallpaperInfo[] newArray(int size) {
                    return new LegacyPartnerWallpaperInfo[size];
                }
            };
    private String mThumbName;
    private String mFullName;
    private File mSystemLegacyDir;
    private boolean mFetchedSystemLegacyDir;
    private FileAsset mAsset;
    private FileAsset mThumbAsset;

    public LegacyPartnerWallpaperInfo(String thumbName, String fullName) {
        mThumbName = thumbName;
        mFullName = fullName;
    }

    private LegacyPartnerWallpaperInfo(Parcel in) {
        mThumbName = in.readString();
        mFullName = in.readString();
    }

    /**
     * @param ctx
     * @return All legacy partner wallpapers found on the device.
     */
    public static List<WallpaperInfo> getAll(Context ctx) {
        PartnerProvider partnerProvider = InjectorProvider.getInjector().getPartnerProvider(ctx);

        List<WallpaperInfo> wallpaperInfos = new ArrayList<>();

        // Add system wallpapers from the legacy wallpaper directory, if present.
        File systemDir = partnerProvider.getLegacyWallpaperDirectory();

        // None found, so return empty list.
        if (systemDir == null || !systemDir.isDirectory()) {
            return wallpaperInfos;
        }

        for (File file : systemDir.listFiles()) {
            if (!file.isFile()) {
                continue;
            }
            String fullName = file.getName();
            String name = file.getName();
            int dotPos = name.lastIndexOf('.');
            String extension = "";
            if (dotPos > -1) {
                extension = name.substring(dotPos);
                name = name.substring(0, dotPos);
            }

            if (name.endsWith("_small")) {
                // Skip thumbnails as they are handled when we iterate over the full size counterpart.
                continue;
            }

            String thumbName = name + "_small" + extension;
            wallpaperInfos.add(new LegacyPartnerWallpaperInfo(thumbName, fullName));
        }

        return wallpaperInfos;
    }

    /**
     * Gets (and caches) the system legacy directory. May return null if no such directory is found
     * (which is the case for newer devices).
     */
    private File getSystemLegacyDir(Context context) {
        if (!mFetchedSystemLegacyDir) {
            PartnerProvider partnerProvider = InjectorProvider.getInjector().getPartnerProvider(context);
            mSystemLegacyDir = partnerProvider.getLegacyWallpaperDirectory();
            mFetchedSystemLegacyDir = true;
        }

        return mSystemLegacyDir;
    }

    public Drawable getThumbnail(Context context) {
        final File systemDir = getSystemLegacyDir(context);
        if (systemDir == null) {
            return null;
        }

        File thumbnail = new File(systemDir, mThumbName);
        Bitmap thumbBitmap = BitmapFactory.decodeFile(thumbnail.getAbsolutePath());
        return new BitmapDrawable(context.getResources(), thumbBitmap);
    }

    @Override
    public List<String> getAttributions(Context context) {
        return Arrays.asList(context.getResources().getString(R.string.on_device_wallpaper_title));
    }

    @Override
    public Asset getAsset(Context context) {
        if (mAsset == null) {
            final File systemDir = getSystemLegacyDir(context);
            File fullSizeImage = (systemDir == null) ? null : new File(systemDir, mFullName);
            mAsset = new FileAsset(fullSizeImage);
        }
        return mAsset;
    }

    @Override
    public Asset getThumbAsset(Context context) {
        if (mThumbAsset == null) {
            final File systemDir = getSystemLegacyDir(context);
            File thumbnail = (systemDir == null) ? null : new File(systemDir, mThumbName);
            mThumbAsset = new FileAsset(thumbnail);
        }
        return mThumbAsset;
    }

    @Override
    public String getCollectionId(Context context) {
        return context.getString(R.string.on_device_wallpaper_collection_id);
    }

    @Override
    public void showPreview(Activity srcActivity, InlinePreviewIntentFactory factory,
                            int requestCode) {
        srcActivity.startActivityForResult(factory.newIntent(srcActivity, this), requestCode);
    }

    @Override
    @BackupPermission
    public int getBackupPermission() {
        return BACKUP_NOT_ALLOWED;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(mThumbName);
        parcel.writeString(mFullName);
    }

}
