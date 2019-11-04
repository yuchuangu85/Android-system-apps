/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.customization.model.theme;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Parcel;
import android.util.Log;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.ResourceAsset;
import com.android.wallpaper.model.InlinePreviewIntentFactory;
import com.android.wallpaper.model.WallpaperInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a wallpaper coming from the resources of the theme bundle container APK.
 */
public class ThemeBundledWallpaperInfo extends WallpaperInfo {
    public static final Creator<ThemeBundledWallpaperInfo> CREATOR =
            new Creator<ThemeBundledWallpaperInfo>() {
                @Override
                public ThemeBundledWallpaperInfo createFromParcel(Parcel in) {
                    return new ThemeBundledWallpaperInfo(in);
                }

                @Override
                public ThemeBundledWallpaperInfo[] newArray(int size) {
                    return new ThemeBundledWallpaperInfo[size];
                }
            };

    private static final String TAG = "ThemeBundledWallpaperInfo";

    private final String mPackageName;
    private final String mResName;
    private final String mCollectionId;
    @DrawableRes private final int mDrawableResId;
    @StringRes private final int mTitleResId;
    @StringRes private final int mAttributionResId;
    @StringRes private final int mActionUrlResId;
    private List<String> mAttributions;
    private String mActionUrl;
    private Resources mResources;
    private Asset mAsset;

    /**
     * Constructs a new theme-bundled static wallpaper model object.
     *
     * @param drawableResId  Resource ID of the raw wallpaper image.
     * @param resName        The unique name of the wallpaper resource, e.g. "z_wp001".
     * @param themeName   Unique name of the collection this wallpaper belongs in; used for logging.
     * @param titleResId     Resource ID of the string for the title attribution.
     * @param attributionResId Resource ID of the string for the first subtitle attribution.
     */
    public ThemeBundledWallpaperInfo(String packageName, String resName, String themeName,
            int drawableResId, int titleResId, int attributionResId, int actionUrlResId) {
        mPackageName = packageName;
        mResName = resName;
        mCollectionId = themeName;
        mDrawableResId = drawableResId;
        mTitleResId = titleResId;
        mAttributionResId = attributionResId;
        mActionUrlResId = actionUrlResId;
    }

    private ThemeBundledWallpaperInfo(Parcel in) {
        mPackageName = in.readString();
        mResName = in.readString();
        mCollectionId = in.readString();
        mDrawableResId = in.readInt();
        mTitleResId = in.readInt();
        mAttributionResId = in.readInt();
        mActionUrlResId = in.readInt();
    }

    @Override
    public List<String> getAttributions(Context context) {
        if (mAttributions == null) {
            Resources res = getPackageResources(context);
            mAttributions = new ArrayList<>();
            if (mTitleResId != 0) {
                mAttributions.add(res.getString(mTitleResId));
            }
            if (mAttributionResId != 0) {
                mAttributions.add(res.getString(mAttributionResId));
            }
        }

        return mAttributions;
    }

    @Override
    public String getActionUrl(Context context) {
        if (mActionUrl == null && mActionUrlResId != 0) {
            mActionUrl = getPackageResources(context).getString(mActionUrlResId);
        }
        return mActionUrl;
    }

    @Override
    public Asset getAsset(Context context) {
        if (mAsset == null) {
            Resources res = getPackageResources(context);
            mAsset = new ResourceAsset(res, mDrawableResId);
        }

        return mAsset;
    }

    @Override
    public Asset getThumbAsset(Context context) {
        return getAsset(context);
    }

    @Override
    public void showPreview(Activity srcActivity, InlinePreviewIntentFactory factory,
            int requestCode) {
        try {
            srcActivity.startActivityForResult(factory.newIntent(srcActivity, this), requestCode);
        } catch (ActivityNotFoundException |SecurityException e) {
            Log.e(TAG, "App isn't installed or ThemePicker doesn't have permission to launch", e);
        }

    }

    @Override
    public String getCollectionId(Context unused) {
        return mCollectionId;
    }

    @Override
    public String getWallpaperId() {
        return mResName;
    }

    public String getResName() {
        return mResName;
    }

    /**
     * Returns the {@link Resources} instance for the theme bundles stub APK.
     */
    private Resources getPackageResources(Context context) {
        if (mResources != null) {
            return mResources;
        }

        try {
            mResources = context.getPackageManager().getResourcesForApplication(mPackageName);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Could not get app resources for " + mPackageName);
        }
        return mResources;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mPackageName);
        dest.writeString(mResName);
        dest.writeString(mCollectionId);
        dest.writeInt(mDrawableResId);
        dest.writeInt(mTitleResId);
        dest.writeInt(mAttributionResId);
        dest.writeInt(mActionUrlResId);
    }
}
