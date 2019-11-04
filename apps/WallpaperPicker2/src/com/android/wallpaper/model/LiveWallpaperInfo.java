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
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Parcel;
import android.service.wallpaper.WallpaperService;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.wallpaper.R;
import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.LiveWallpaperThumbAsset;
import com.android.wallpaper.compat.BuildCompat;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.LiveWallpaperInfoFactory;
import com.android.wallpaper.util.ActivityUtils;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a live wallpaper from the system.
 */
public class LiveWallpaperInfo extends WallpaperInfo {
    public static final Creator<LiveWallpaperInfo> CREATOR =
            new Creator<LiveWallpaperInfo>() {
                @Override
                public LiveWallpaperInfo createFromParcel(Parcel in) {
                    return new LiveWallpaperInfo(in);
                }

                @Override
                public LiveWallpaperInfo[] newArray(int size) {
                    return new LiveWallpaperInfo[size];
                }
            };
    private static final String TAG = "LiveWallpaperInfo";
    protected android.app.WallpaperInfo mInfo;
    protected LiveWallpaperThumbAsset mThumbAsset;
    private boolean mVisibleTitle;

    /**
     * Constructs a LiveWallpaperInfo wrapping the given system WallpaperInfo object, representing
     * a particular live wallpaper.
     *
     * @param info
     */
    public LiveWallpaperInfo(android.app.WallpaperInfo info) {
        this(info, true);
    }

    /**
     * Constructs a LiveWallpaperInfo wrapping the given system WallpaperInfo object, representing
     * a particular live wallpaper.
     */
    public LiveWallpaperInfo(android.app.WallpaperInfo info, boolean visibleTitle) {
        mInfo = info;
        mVisibleTitle = visibleTitle;
    }

    LiveWallpaperInfo(Parcel in) {
        mInfo = in.readParcelable(android.app.WallpaperInfo.class.getClassLoader());
        mVisibleTitle = in.readInt() == 1;
    }

    /**
     * Returns all live wallpapers found on the device, excluding those residing in APKs described by
     * the package names in excludedPackageNames.
     */
    public static List<WallpaperInfo> getAll(Context context,
                                             @Nullable List<String> excludedPackageNames) {
        List<ResolveInfo> resolveInfos = getAllOnDevice(context);
        List<WallpaperInfo> wallpaperInfos = new ArrayList<>();
        LiveWallpaperInfoFactory factory =
                InjectorProvider.getInjector().getLiveWallpaperInfoFactory(context);
        for (int i = 0; i < resolveInfos.size(); i++) {
            ResolveInfo resolveInfo = resolveInfos.get(i);
            android.app.WallpaperInfo wallpaperInfo;
            try {
                wallpaperInfo = new android.app.WallpaperInfo(context, resolveInfo);
            } catch (XmlPullParserException e) {
                Log.w(TAG, "Skipping wallpaper " + resolveInfo.serviceInfo, e);
                continue;
            } catch (IOException e) {
                Log.w(TAG, "Skipping wallpaper " + resolveInfo.serviceInfo, e);
                continue;
            }

            if (excludedPackageNames != null && excludedPackageNames.contains(
                    wallpaperInfo.getPackageName())) {
                continue;
            }

            wallpaperInfos.add(factory.getLiveWallpaperInfo(wallpaperInfo));
        }

        return wallpaperInfos;
    }

    /**
     * Returns the live wallpapers having the given service names, found within the APK with the
     * given package name.
     */
    public static List<WallpaperInfo> getFromSpecifiedPackage(
            Context context, String packageName, @Nullable List<String> serviceNames,
            boolean shouldShowTitle) {
        List<ResolveInfo> resolveInfos;
        if (serviceNames != null) {
            resolveInfos = getAllContainingServiceNames(context, serviceNames);
        } else {
            resolveInfos = getAllOnDevice(context);
        }
        List<WallpaperInfo> wallpaperInfos = new ArrayList<>();
        LiveWallpaperInfoFactory factory =
                InjectorProvider.getInjector().getLiveWallpaperInfoFactory(context);

        for (int i = 0; i < resolveInfos.size(); i++) {
            ResolveInfo resolveInfo = resolveInfos.get(i);
            if (resolveInfo == null) {
                Log.e(TAG, "Found a null resolve info");
                continue;
            }

            android.app.WallpaperInfo wallpaperInfo;
            try {
                wallpaperInfo = new android.app.WallpaperInfo(context, resolveInfo);
            } catch (XmlPullParserException e) {
                Log.w(TAG, "Skipping wallpaper " + resolveInfo.serviceInfo, e);
                continue;
            } catch (IOException e) {
                Log.w(TAG, "Skipping wallpaper " + resolveInfo.serviceInfo, e);
                continue;
            }

            if (!packageName.equals(wallpaperInfo.getPackageName())) {
                continue;
            }

            wallpaperInfos.add(factory.getLiveWallpaperInfo(wallpaperInfo, shouldShowTitle));
        }

        return wallpaperInfos;
    }

    /**
     * Returns ResolveInfo objects for all live wallpaper services with the specified fully qualified
     * service names, keeping order intact.
     */
    private static List<ResolveInfo> getAllContainingServiceNames(Context context,
                                                                  List<String> serviceNames) {
        final PackageManager pm = context.getPackageManager();

        List<ResolveInfo> allResolveInfos = pm.queryIntentServices(
                new Intent(WallpaperService.SERVICE_INTERFACE),
                PackageManager.GET_META_DATA);

        // Filter ALL live wallpapers for only those in the list of specified service names.
        // Prefer this approach so we can make only one call to PackageManager (expensive!) rather than
        // one call per live wallpaper.
        ResolveInfo[] specifiedResolveInfos = new ResolveInfo[serviceNames.size()];
        for (ResolveInfo resolveInfo : allResolveInfos) {
            int index = serviceNames.indexOf(resolveInfo.serviceInfo.name);
            if (index != -1) {
                specifiedResolveInfos[index] = resolveInfo;
            }
        }

        return Arrays.asList(specifiedResolveInfos);
    }

    /**
     * Returns ResolveInfo objects for all live wallpaper services installed on the device. System
     * wallpapers are listed first, unsorted, with other installed wallpapers following sorted
     * in alphabetical order.
     */
    private static List<ResolveInfo> getAllOnDevice(Context context) {
        final PackageManager pm = context.getPackageManager();
        final String packageName = context.getPackageName();

        List<ResolveInfo> resolveInfos = pm.queryIntentServices(
                new Intent(WallpaperService.SERVICE_INTERFACE),
                PackageManager.GET_META_DATA);

        List<ResolveInfo> wallpaperInfos = new ArrayList<>();

        // Remove the "Rotating Image Wallpaper" live wallpaper, which is owned by this package,
        // and separate system wallpapers to sort only non-system ones.
        Iterator<ResolveInfo> iter = resolveInfos.iterator();
        while (iter.hasNext()) {
            ResolveInfo resolveInfo = iter.next();
            if (packageName.equals(resolveInfo.serviceInfo.packageName)) {
                iter.remove();
            } else if (isSystemApp(resolveInfo.serviceInfo.applicationInfo)) {
                wallpaperInfos.add(resolveInfo);
                iter.remove();
            }
        }

        if (resolveInfos.isEmpty()) {
            return wallpaperInfos;
        }

        // Sort non-system wallpapers alphabetically and append them to system ones
        Collections.sort(resolveInfos, new Comparator<ResolveInfo>() {
            final Collator mCollator = Collator.getInstance();

            @Override
            public int compare(ResolveInfo info1, ResolveInfo info2) {
                return mCollator.compare(info1.loadLabel(pm), info2.loadLabel(pm));
            }
        });
        wallpaperInfos.addAll(resolveInfos);

        return wallpaperInfos;
    }

    static boolean isSystemApp(ApplicationInfo appInfo) {
        return (appInfo.flags & (ApplicationInfo.FLAG_SYSTEM
                | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
    }

    @Override
    public String getTitle(Context context) {
        if (mVisibleTitle) {
            CharSequence labelCharSeq = mInfo.loadLabel(context.getPackageManager());
            return labelCharSeq == null ? null : labelCharSeq.toString();
        }
        return null;
    }

    @Override
    public List<String> getAttributions(Context context) {
        List<String> attributions = new ArrayList<>();
        CharSequence labelCharSeq = mInfo.loadLabel(context.getPackageManager());
        attributions.add(labelCharSeq == null ? null : labelCharSeq.toString());

        try {
            CharSequence authorCharSeq = mInfo.loadAuthor(context.getPackageManager());
            if (authorCharSeq != null) {
                String author = authorCharSeq.toString();
                attributions.add(author);
            }
        } catch (Resources.NotFoundException e) {
            // No author specified, so no other attribution to add.
        }

        return attributions;
    }

    @Override
    public String getActionUrl(Context context) {
        if (BuildCompat.isAtLeastNMR1()) {
            try {
                Uri wallpaperContextUri = mInfo.loadContextUri(context.getPackageManager());
                if (wallpaperContextUri != null) {
                    return wallpaperContextUri.toString();
                }
            } catch (Resources.NotFoundException e) {
                return null;
            }
        }

        return null;
    }

    @Override
    public Asset getAsset(Context context) {
        return null;
    }

    @Override
    public Asset getThumbAsset(Context context) {
        if (mThumbAsset == null) {
            mThumbAsset = new LiveWallpaperThumbAsset(context, mInfo);
        }
        return mThumbAsset;
    }

    @Override
    public void showPreview(Activity srcActivity, InlinePreviewIntentFactory factory,
                            int requestCode) {
        Intent preview = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        preview.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, mInfo.getComponent());
        ActivityUtils.startActivityForResultSafely(srcActivity, preview, requestCode);
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeParcelable(mInfo, 0 /* flags */);
        parcel.writeInt(mVisibleTitle ? 1 : 0);
    }

    @Override
    public android.app.WallpaperInfo getWallpaperComponent() {
        return mInfo;
    }

    @Override
    public String getCollectionId(Context context) {
        return context.getString(R.string.live_wallpaper_collection_id);
    }
}
