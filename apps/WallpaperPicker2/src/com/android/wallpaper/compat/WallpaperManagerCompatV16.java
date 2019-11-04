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

import android.annotation.SuppressLint;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.ParcelFileDescriptor;

import java.io.IOException;
import java.io.InputStream;

/**
 * The pre-N implementation of {@link WallpaperManagerCompat} which implements its methods by
 * delegating to the standard pre-N equivalent methods.
 */
public class WallpaperManagerCompatV16 extends WallpaperManagerCompat {
    protected WallpaperManager mWallpaperManager;

    @SuppressLint("ServiceCast")
    public WallpaperManagerCompatV16(Context context) {
        // Retrieve WallpaperManager using Context#getSystemService instead of
        // WallpaperManager#getInstance so it can be mocked out in test.
        mWallpaperManager = (WallpaperManager) context.getSystemService(Context.WALLPAPER_SERVICE);
    }

    @Override
    public int setStream(InputStream data, Rect visibleCropHint, boolean allowBackup,
                         int whichWallpaper) throws IOException {
        mWallpaperManager.setStream(data);
        // Return a value greater than zero to indicate success.
        return 1;
    }

    @Override
    public int setBitmap(Bitmap fullImage, Rect visibleCropHint, boolean allowBackup,
                         int whichWallpaper) throws IOException {
        mWallpaperManager.setBitmap(fullImage);
        // Return a value greater than zero to indicate success.
        return 1;
    }

    @Override
    public int getWallpaperId(@WallpaperLocation int whichWallpaper) {
        throw new UnsupportedOperationException("This method should not be called on pre-N versions "
                + "of Android.");
    }

    @Override
    public ParcelFileDescriptor getWallpaperFile(int whichWallpaper) {
        return null;
    }

    @Override
    public Drawable getDrawable() {
        Drawable drawable;
        try {
            drawable = mWallpaperManager.getDrawable();
        } catch (java.lang.Exception e) {
            // Work around Samsung bug where SecurityException is thrown if device is still using its
            // default wallpaper, and around Android 7.0 bug where SELinux issues can cause a perfectly
            // valid access of the current wallpaper to cause a failed Binder transaction manifest here as
            // a RuntimeException.
            drawable = mWallpaperManager.getBuiltInDrawable();
        }
        return drawable;
    }
}
