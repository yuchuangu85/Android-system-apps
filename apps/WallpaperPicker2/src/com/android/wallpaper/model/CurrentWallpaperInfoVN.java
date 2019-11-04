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
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.BuiltInWallpaperAsset;
import com.android.wallpaper.asset.CurrentWallpaperAssetVN;
import com.android.wallpaper.asset.FileAsset;
import com.android.wallpaper.compat.WallpaperManagerCompat;
import com.android.wallpaper.compat.WallpaperManagerCompat.WallpaperLocation;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.NoBackupImageWallpaper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the currently set wallpaper on N+ devices. Should not be used to set a new wallpaper.
 */
public class CurrentWallpaperInfoVN extends WallpaperInfo {

    public static final Creator<CurrentWallpaperInfoVN> CREATOR =
            new Creator<CurrentWallpaperInfoVN>() {
                @Override
                public CurrentWallpaperInfoVN createFromParcel(Parcel source) {
                    return new CurrentWallpaperInfoVN(source);
                }

                @Override
                public CurrentWallpaperInfoVN[] newArray(int size) {
                    return new CurrentWallpaperInfoVN[size];
                }
            };
    private static final String TAG = "CurrentWallpaperInfoVN";
    private List<String> mAttributions;
    private Asset mAsset;
    private String mActionUrl;
    @StringRes
    private int mActionLabelRes;
    @DrawableRes
    private int mActionIconRes;
    private String mCollectionId;
    @WallpaperLocation
    private int mWallpaperManagerFlag;

    /**
     * Constructs a new instance of this class.
     *
     * @param wallpaperManagerFlag Either SYSTEM or LOCK--the source of image data which this object
     *                             represents.
     */
    public CurrentWallpaperInfoVN(List<String> attributions, String actionUrl,
                                  @StringRes int actionLabelRes, @DrawableRes int actionIconRes,
                                  String collectionId,
                                  @WallpaperLocation int wallpaperManagerFlag) {
        mAttributions = attributions;
        mWallpaperManagerFlag = wallpaperManagerFlag;
        mActionUrl = actionUrl;
        mActionLabelRes = actionLabelRes;
        mActionIconRes = actionIconRes;
        mCollectionId = collectionId;
    }

    private CurrentWallpaperInfoVN(Parcel in) {
        mAttributions = new ArrayList<>();
        in.readStringList(mAttributions);
        //noinspection ResourceType
        mWallpaperManagerFlag = in.readInt();
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
            mAsset = createCurrentWallpaperAssetVN(context);
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
    public String getCollectionId(Context unused) {
        return mCollectionId;
    }

    @Override
    public int getActionIconRes(Context unused) {
        return mActionIconRes != 0 ? mActionIconRes : WallpaperInfo.getDefaultActionIcon();
    }

    @Override
    public int getActionLabelRes(Context unused) {
        return mActionLabelRes != 0 ? mActionLabelRes : WallpaperInfo.getDefaultActionLabel();
    }

    public int getWallpaperManagerFlag() {
        return mWallpaperManagerFlag;
    }

    /**
     * Constructs and returns an Asset instance representing the currently-set wallpaper asset.
     */
    private Asset createCurrentWallpaperAssetVN(Context context) {
        // If the live wallpaper for rotating wallpapers is set, then provide a file asset
        // representing that wallpaper.
        boolean isNoBackupImageWallpaperSet = InjectorProvider.getInjector()
                .getLiveWallpaperStatusChecker(context).isNoBackupImageWallpaperSet();
        if (mWallpaperManagerFlag == WallpaperManagerCompat.FLAG_SYSTEM
                && isNoBackupImageWallpaperSet) {
            Context deviceProtectedContext = context.createDeviceProtectedStorageContext();
            return new FileAsset(new File(deviceProtectedContext.getFilesDir(),
                    NoBackupImageWallpaper.ROTATING_WALLPAPER_FILE_PATH));
        }

        WallpaperManagerCompat wallpaperManagerCompat = InjectorProvider.getInjector()
                .getWallpaperManagerCompat(context);

        ParcelFileDescriptor systemWallpaperFile = wallpaperManagerCompat.getWallpaperFile(
                WallpaperManagerCompat.FLAG_SYSTEM);

        // Whether the wallpaper this object represents is the default built-in wallpaper.
        boolean isSystemBuiltIn = mWallpaperManagerFlag == WallpaperManagerCompat.FLAG_SYSTEM
                && systemWallpaperFile == null;

        if (systemWallpaperFile != null) {
            try {
                systemWallpaperFile.close();
            } catch (IOException e) {
                Log.e(TAG, "Unable to close system wallpaper ParcelFileDescriptor", e);
            }
        }

        return (isSystemBuiltIn)
                ? new BuiltInWallpaperAsset(context)
                : new CurrentWallpaperAssetVN(context, mWallpaperManagerFlag);
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeStringList(mAttributions);
        parcel.writeInt(mWallpaperManagerFlag);
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
