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
import android.os.Parcel;

import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.CurrentWallpaperAssetV16;
import com.android.wallpaper.asset.FileAsset;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.NoBackupImageWallpaper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

/**
 * Represents the wallpaper currently set to the device for API 16 through 23. Should not be used
 * to set a new wallpaper.
 */
public class CurrentWallpaperInfoV16 extends WallpaperInfo {

    public static final Creator<CurrentWallpaperInfoV16> CREATOR =
            new Creator<CurrentWallpaperInfoV16>() {
                @Override
                public CurrentWallpaperInfoV16 createFromParcel(Parcel source) {
                    return new CurrentWallpaperInfoV16(source);
                }

                @Override
                public CurrentWallpaperInfoV16[] newArray(int size) {
                    return new CurrentWallpaperInfoV16[size];
                }
            };
    private List<String> mAttributions;
    private Asset mAsset;
    private String mActionUrl;
    @StringRes
    private int mActionLabelRes;
    @DrawableRes
    private int mActionIconRes;
    private String mCollectionId;

    public CurrentWallpaperInfoV16(List<String> attributions, String actionUrl,
                                   @StringRes int actionLabelRes, @DrawableRes int actionIconRes,
                                   String collectionId) {
        mAttributions = attributions;
        mActionUrl = actionUrl;
        mActionLabelRes = actionLabelRes;
        mActionIconRes = actionIconRes;
        mCollectionId = collectionId;
    }

    private CurrentWallpaperInfoV16(Parcel in) {
        mAttributions = new ArrayList<>();
        in.readStringList(mAttributions);
        mActionUrl = in.readString();
        mCollectionId = in.readString();
        mActionLabelRes = in.readInt();
        mActionIconRes = in.readInt();
    }

    @Override
    public List<String> getAttributions(Context context) {
        return mAttributions;
    }

    @Override
    public Asset getAsset(Context context) {
        if (mAsset == null) {
            boolean isNoBackupImageWallpaperSet = InjectorProvider.getInjector()
                    .getLiveWallpaperStatusChecker(context).isNoBackupImageWallpaperSet();

            mAsset = isNoBackupImageWallpaperSet
                    ? new FileAsset(new File(context.getApplicationContext().getFilesDir(),
                    NoBackupImageWallpaper.ROTATING_WALLPAPER_FILE_PATH))
                    : new CurrentWallpaperAssetV16(context);
        }
        return mAsset;
    }

    @Override
    public Asset getThumbAsset(Context context) {
        return getAsset(context);
    }

    @Override
    public String getActionUrl(Context unused) {
        return mActionUrl;
    }

    @Override
    public int getActionIconRes(Context unused) {
        return mActionIconRes != 0 ? mActionIconRes : WallpaperInfo.getDefaultActionIcon();
    }

    @Override
    public int getActionLabelRes(Context unused) {
        return mActionLabelRes != 0 ? mActionLabelRes : WallpaperInfo.getDefaultActionLabel();
    }

    @Override
    public String getCollectionId(Context unused) {
        return mCollectionId;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeStringList(mAttributions);
        parcel.writeString(mActionUrl);
        parcel.writeString(mCollectionId);
        parcel.writeInt(mActionLabelRes);
        parcel.writeInt(mActionIconRes);
    }

    @Override
    public void showPreview(Activity srcActivity, InlinePreviewIntentFactory factory,
                            int requestCode) {
        srcActivity.startActivityForResult(factory.newIntent(srcActivity, this), requestCode);
    }
}
