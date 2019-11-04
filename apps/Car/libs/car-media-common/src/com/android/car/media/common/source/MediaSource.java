/*
 * Copyright 2018 The Android Open Source Project
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

package com.android.car.media.common.source;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.service.media.MediaBrowserService;
import android.text.TextUtils;
import android.util.Log;

import com.android.car.apps.common.BitmapUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * This represents a source of media content. It provides convenient methods to access media source
 * metadata, such as application name and icon.
 */
public class MediaSource {
    private static final String TAG = "MediaSource";

    /**
     * Custom media sources which should not be templatized.
     */
    private static final Set<String> CUSTOM_MEDIA_SOURCES = new HashSet<>();

    static {
        CUSTOM_MEDIA_SOURCES.add("com.android.car.radio");
    }

    @NonNull
    private final ComponentName mBrowseService;
    @NonNull
    private final CharSequence mDisplayName;
    @NonNull
    private final Drawable mIcon;

    /**
     * Creates a {@link MediaSource} for the given {@link ComponentName}
     */
    @Nullable
    public static MediaSource create(@NonNull Context context,
            @NonNull ComponentName componentName) {
        ServiceInfo serviceInfo = getBrowseServiceInfo(context, componentName);

        String className = serviceInfo != null ? serviceInfo.name : null;
        if (TextUtils.isEmpty(className)) {
            Log.w(TAG,
                    "No MediaBrowserService found in component " + componentName.flattenToString());
            return null;
        }

        try {
            String packageName = componentName.getPackageName();
            CharSequence displayName = extractDisplayName(context, serviceInfo, packageName);
            Drawable icon = extractIcon(context, serviceInfo, packageName);
            ComponentName browseService = new ComponentName(packageName, className);
            return new MediaSource(browseService, displayName, icon);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Component not found " + componentName.flattenToString());
            return null;
        }
    }

    private MediaSource(@NonNull ComponentName browseService, @NonNull CharSequence displayName,
            @NonNull Drawable icon) {
        mBrowseService = browseService;
        mDisplayName = displayName;
        mIcon = icon;
    }

    /**
     * @return the {@link ServiceInfo} corresponding to a {@link MediaBrowserService} in the media
     * source, or null if the media source doesn't implement {@link MediaBrowserService}. A non-null
     * result doesn't imply that this service is accessible. The consumer code should attempt to
     * connect and handle rejections gracefully.
     */
    @Nullable
    private static ServiceInfo getBrowseServiceInfo(@NonNull Context context,
            @NonNull ComponentName componentName) {
        PackageManager packageManager = context.getPackageManager();
        Intent intent = new Intent();
        intent.setAction(MediaBrowserService.SERVICE_INTERFACE);
        intent.setPackage(componentName.getPackageName());
        List<ResolveInfo> resolveInfos = packageManager.queryIntentServices(intent,
                PackageManager.GET_RESOLVED_FILTER);
        if (resolveInfos == null || resolveInfos.isEmpty()) {
            return null;
        }
        String className = componentName.getClassName();
        if (TextUtils.isEmpty(className)) {
            return resolveInfos.get(0).serviceInfo;
        }
        for (ResolveInfo resolveInfo : resolveInfos) {
            ServiceInfo result = resolveInfo.serviceInfo;
            if (result.name.equals(className)) {
                return result;
            }
        }
        return null;
    }

    /**
     * @return a proper app name. Checks service label first. If failed, uses application label
     * as fallback.
     */
    @NonNull
    private static CharSequence extractDisplayName(@NonNull Context context,
            @Nullable ServiceInfo serviceInfo, @NonNull String packageName)
            throws PackageManager.NameNotFoundException {
        if (serviceInfo != null && serviceInfo.labelRes != 0) {
            return serviceInfo.loadLabel(context.getPackageManager());
        }
        ApplicationInfo applicationInfo =
                context.getPackageManager().getApplicationInfo(packageName,
                        PackageManager.GET_META_DATA);
        return applicationInfo.loadLabel(context.getPackageManager());
    }

    /**
     * @return a proper icon. Checks service icon first. If failed, uses application icon as
     * fallback.
     */
    @NonNull
    private static Drawable extractIcon(@NonNull Context context, @Nullable ServiceInfo serviceInfo,
            @NonNull String packageName) throws PackageManager.NameNotFoundException {
        Drawable appIcon = serviceInfo != null ? serviceInfo.loadIcon(context.getPackageManager())
                : context.getPackageManager().getApplicationIcon(packageName);

        return BitmapUtils.maybeFlagDrawable(context, appIcon);
    }

    /**
     * @return media source human readable name for display.
     */
    @NonNull
    public CharSequence getDisplayName() {
        return mDisplayName;
    }

    /**
     * @return the package name of this media source.
     */
    @NonNull
    public String getPackageName() {
        return mBrowseService.getPackageName();
    }

    /**
     * @return a {@link ComponentName} referencing this media source's {@link MediaBrowserService},
     * or NULL if this media source doesn't implement such service.
     */
    @NonNull
    public ComponentName getBrowseServiceComponentName() {
        return mBrowseService;
    }

    /**
     * @return a {@link Drawable} as the media source's icon.
     */
    @NonNull
    public Drawable getIcon() {
        return mIcon;
    }

    /**
     * Returns this media source's icon cropped to a circle.
     */
    public Bitmap getRoundPackageIcon() {
        return getRoundCroppedBitmap(BitmapUtils.fromDrawable(mIcon, null));
    }

    /**
     * Returns {@code true} iff this media source should not be templatized.
     */
    public boolean isCustom() {
        return CUSTOM_MEDIA_SOURCES.contains(getPackageName());
    }

    private static Bitmap getRoundCroppedBitmap(Bitmap bitmap) {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final int color = 0xff424242;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawCircle(bitmap.getWidth() / 2, bitmap.getHeight() / 2,
                bitmap.getWidth() / 2f, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
        return output;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MediaSource that = (MediaSource) o;
        return Objects.equals(mBrowseService, that.mBrowseService);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mBrowseService);
    }

    @Override
    @NonNull
    public String toString() {
        return mBrowseService.flattenToString();
    }
}
