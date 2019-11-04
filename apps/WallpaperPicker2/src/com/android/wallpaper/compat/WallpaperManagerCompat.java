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
package com.android.wallpaper.compat;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.ParcelFileDescriptor;

import java.io.IOException;
import java.io.InputStream;

import androidx.annotation.IntDef;

/**
 * An abstraction over WallpaperManager to allow for the transitional state in which the N SDK
 * is not yet ready but we need to use new N API methods. Provides wrapper methods for the new
 * N API methods.
 */
public abstract class WallpaperManagerCompat {
    public static final int FLAG_SYSTEM = WallpaperManager.FLAG_SYSTEM;
    public static final int FLAG_LOCK = WallpaperManager.FLAG_LOCK;
    private static final Object sInstanceLock = new Object();
    private static WallpaperManagerCompat sInstance;

    public static WallpaperManagerCompat getInstance(Context context) {
        synchronized (sInstanceLock) {
            if (sInstance == null) {
                if (BuildCompat.isAtLeastN()) {
                    sInstance = new WallpaperManagerCompatVN(context.getApplicationContext());
                } else {
                    sInstance = new WallpaperManagerCompatV16(context.getApplicationContext());
                }
            }
            return sInstance;
        }
    }

    /**
     * Sets the static instance of {@link WallpaperManagerCompat} as the provided object. Used for
     * testing.
     */
    public static void setInstance(WallpaperManagerCompat wallpaperManagerCompat) {
        synchronized (sInstanceLock) {
            sInstance = wallpaperManagerCompat;
        }
    }

    /**
     * Thin wrapper around WallpaperManager's setStream method as defined in the N API.
     */
    public abstract int setStream(InputStream stream, Rect visibleCropHint, boolean allowBackup,
                                  int whichWallpaper) throws IOException;

    /**
     * Thin wrapper around WallpaperManager's setBitmap method as defined in the N API.
     */
    public abstract int setBitmap(Bitmap fullImage, Rect visibleCropHint, boolean allowBackup,
                                  int whichWallpaper) throws IOException;

    /**
     * Thin wrapper around WallpaperManager's getWallpaperId method as defined in the N API.
     */
    public abstract int getWallpaperId(@WallpaperLocation int whichWallpaper);

    /**
     * Thin wrapper around WallpaperManager's getWallpaperFile method as defined ONLY in the N API.
     * This method must only be called when N is detected on the device as there is no pre-N fallback
     * counterpart! On pre-N devices, null is always returned.
     */
    public abstract ParcelFileDescriptor getWallpaperFile(int whichWallpaper);

    /**
     * Thin wrapper around WallpaperManager's getDrawable method. Needed to work around issue on
     * certain Samsung devices where a SecurityException is thrown if this is called when the
     * device had never changed from its default wallpaper.
     */
    public abstract Drawable getDrawable();

    /**
     * Possible locations to which a wallpaper may be set.
     */
    @IntDef({
            FLAG_SYSTEM,
            FLAG_LOCK})
    public @interface WallpaperLocation {
    }
}
