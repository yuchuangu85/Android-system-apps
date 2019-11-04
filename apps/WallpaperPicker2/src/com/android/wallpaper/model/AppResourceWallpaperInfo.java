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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.android.wallpaper.R;
import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.ResourceAsset;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a wallpaper coming from the resources of the host app for the wallpaper picker.
 */
public class AppResourceWallpaperInfo extends WallpaperInfo {
    public static final Parcelable.Creator<AppResourceWallpaperInfo> CREATOR =
            new Parcelable.Creator<AppResourceWallpaperInfo>() {
                @Override
                public AppResourceWallpaperInfo createFromParcel(Parcel in) {
                    return new AppResourceWallpaperInfo(in);
                }

                @Override
                public AppResourceWallpaperInfo[] newArray(int size) {
                    return new AppResourceWallpaperInfo[size];
                }
            };
    private static final String TAG = "AppResource";
    private static final String DRAWABLE_DEF_TYPE = "drawable";
    private int mThumbRes;
    private int mFullRes;
    private String mPackageName;
    private Resources mResources;
    private ResourceAsset mAsset;
    private ResourceAsset mThumbAsset;

    public AppResourceWallpaperInfo(String packageName, int thumbRes, int fullRes) {
        super();
        mPackageName = packageName;
        mThumbRes = thumbRes;
        mFullRes = fullRes;
    }

    private AppResourceWallpaperInfo(Parcel in) {
        mPackageName = in.readString();
        mThumbRes = in.readInt();
        mFullRes = in.readInt();
    }

    /**
     * Returns a list of wallpapers bundled with the app described by the given appInfo.
     *
     * @param context
     * @param appInfo   The ApplicationInfo for the app hosting the wallpapers.
     * @param listResId The ID of the list resource for the list of wallpapers.
     * @return The wallpapers.
     */
    public static List<WallpaperInfo> getAll(Context context, ApplicationInfo appInfo,
                                             int listResId) {
        ArrayList<WallpaperInfo> wallpapers = new ArrayList<>();

        try {
            Resources resources = context.getPackageManager().getResourcesForApplication(appInfo);

            final String[] wallpaperResNames = resources.getStringArray(listResId);
            for (String name : wallpaperResNames) {
                final int fullRes = resources.getIdentifier(name, DRAWABLE_DEF_TYPE, appInfo.packageName);
                final int thumbRes = resources.getIdentifier(
                        name + "_small", DRAWABLE_DEF_TYPE, appInfo.packageName);
                if (fullRes != 0 && thumbRes != 0) {
                    WallpaperInfo wallpaperInfo = new AppResourceWallpaperInfo(
                            appInfo.packageName, thumbRes, fullRes);
                    wallpapers.add(wallpaperInfo);
                }
            }

        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Hosting app package not found");
        }

        return wallpapers;
    }

    private Resources getPackageResources(Context ctx) {
        if (mResources != null) {
            return mResources;
        }

        try {
            mResources = ctx.getPackageManager().getResourcesForApplication(mPackageName);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Could not get app resources");
        }
        return mResources;
    }

    @Override
    public Asset getAsset(Context context) {
        if (mAsset == null) {
            Resources res = getPackageResources(context);
            mAsset = new ResourceAsset(res, mFullRes);
        }
        return mAsset;
    }

    @Override
    public Asset getThumbAsset(Context context) {
        if (mThumbAsset == null) {
            Resources res = getPackageResources(context);
            mThumbAsset = new ResourceAsset(res, mThumbRes);
        }
        return mThumbAsset;
    }

    @Override
    public List<String> getAttributions(Context context) {
        return Arrays.asList(context.getResources().getString(R.string.on_device_wallpaper_title));
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
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }
        if (object instanceof AppResourceWallpaperInfo) {
            return mPackageName.equals(((AppResourceWallpaperInfo) object).mPackageName)
                    && mThumbRes == ((AppResourceWallpaperInfo) object).mThumbRes
                    && mFullRes == ((AppResourceWallpaperInfo) object).mFullRes;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + mPackageName.hashCode();
        result = 31 * result + mThumbRes;
        result = 31 * result + mFullRes;
        return result;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(mPackageName);
        parcel.writeInt(mThumbRes);
        parcel.writeInt(mFullRes);
    }
}
