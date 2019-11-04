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
import android.content.res.Resources;
import android.os.Parcel;
import android.util.Log;

import com.android.wallpaper.R;
import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.ResourceAsset;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.PartnerProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a wallpaper from the "partner customization" APK installed on the system.
 */
public class PartnerWallpaperInfo extends WallpaperInfo {
    public static final Creator<PartnerWallpaperInfo> CREATOR =
            new Creator<PartnerWallpaperInfo>() {
                @Override
                public PartnerWallpaperInfo createFromParcel(Parcel in) {
                    return new PartnerWallpaperInfo(in);
                }

                @Override
                public PartnerWallpaperInfo[] newArray(int size) {
                    return new PartnerWallpaperInfo[size];
                }
            };
    private int mThumbRes;
    private int mFullRes;
    private ResourceAsset mAsset;
    private ResourceAsset mThumbAsset;
    private Resources mPartnerResources;
    private boolean mFetchedPartnerResources;

    public PartnerWallpaperInfo(int thumbRes, int fullRes) {
        mThumbRes = thumbRes;
        mFullRes = fullRes;
    }

    private PartnerWallpaperInfo(Parcel in) {
        mThumbRes = in.readInt();
        mFullRes = in.readInt();
    }

    /**
     * @param ctx
     * @return All partner wallpapers found on the device.
     */
    public static List<WallpaperInfo> getAll(Context ctx) {
        PartnerProvider partnerProvider = InjectorProvider.getInjector().getPartnerProvider(ctx);

        List<WallpaperInfo> wallpaperInfos = new ArrayList<>();

        final Resources partnerRes = partnerProvider.getResources();
        final String packageName = partnerProvider.getPackageName();
        if (partnerRes == null) {
            return wallpaperInfos;
        }

        final int resId = partnerRes.getIdentifier(PartnerProvider.WALLPAPER_RES_ID, "array",
                packageName);
        // Certain partner configurations don't have wallpapers provided, so need to check; return
        // early if they are missing.
        if (resId == 0) {
            return wallpaperInfos;
        }

        final String[] extras = partnerRes.getStringArray(resId);
        for (String extra : extras) {
            int wpResId = partnerRes.getIdentifier(extra, "drawable", packageName);
            if (wpResId != 0) {
                final int thumbRes = partnerRes.getIdentifier(extra + "_small", "drawable", packageName);

                if (thumbRes != 0) {
                    final int fullRes = partnerRes.getIdentifier(extra, "drawable", packageName);
                    WallpaperInfo wallpaperInfo = new PartnerWallpaperInfo(thumbRes, fullRes);
                    wallpaperInfos.add(wallpaperInfo);
                }
            } else {
                Log.e("PartnerWallpaperInfo", "Couldn't find wallpaper " + extra);
            }
        }

        return wallpaperInfos;
    }

    private Resources getPartnerResources(Context context) {
        if (!mFetchedPartnerResources) {
            PartnerProvider partnerProvider = InjectorProvider.getInjector().getPartnerProvider(context);
            mPartnerResources = partnerProvider.getResources();
            mFetchedPartnerResources = true;
        }

        return mPartnerResources;
    }

    @Override
    public List<String> getAttributions(Context context) {
        return Arrays.asList(context.getResources().getString(R.string.on_device_wallpaper_title));
    }

    @Override
    public Asset getAsset(Context context) {
        if (mAsset == null) {
            Resources partnerRes = getPartnerResources(context);
            mAsset = new ResourceAsset(partnerRes, mFullRes);
        }
        return mAsset;
    }

    @Override
    public Asset getThumbAsset(Context context) {
        if (mThumbAsset == null) {
            Resources partnerRes = getPartnerResources(context);
            mThumbAsset = new ResourceAsset(partnerRes, mThumbRes);
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
        parcel.writeInt(mThumbRes);
        parcel.writeInt(mFullRes);
    }

}
