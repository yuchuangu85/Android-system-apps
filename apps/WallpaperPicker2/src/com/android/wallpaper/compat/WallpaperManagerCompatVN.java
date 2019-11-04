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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

/**
 * The Android N implementation of {@link WallpaperManagerCompat} which uses the new N API methods
 * if available and delegates back to older implementations if not.
 */
public class WallpaperManagerCompatVN extends WallpaperManagerCompatV16 {

    private static final String TAG = "WallpaperMgrCompatVN";

    public WallpaperManagerCompatVN(Context context) {
        super(context);
    }

    @Override
    public int setStream(final InputStream data, Rect visibleCropHint, boolean allowBackup,
                         int whichWallpaper) throws IOException {
        return mWallpaperManager.setStream(data, visibleCropHint, allowBackup, whichWallpaper);
    }

    @Override
    public int setBitmap(Bitmap fullImage, Rect visibleCropHint, boolean allowBackup,
                         int whichWallpaper) throws IOException {
        return mWallpaperManager.setBitmap(fullImage, visibleCropHint, allowBackup, whichWallpaper);
    }

    @Override
    public int getWallpaperId(@WallpaperLocation int whichWallpaper) {
        return mWallpaperManager.getWallpaperId(whichWallpaper);
    }

    @Override
    public ParcelFileDescriptor getWallpaperFile(int whichWallpaper) {
        ParcelFileDescriptor parcelFd = null;
        try {
            parcelFd = mWallpaperManager.getWallpaperFile(whichWallpaper);
        } catch (Exception e) {
            // Note: We put a catch-all exception handler here to handle RemoteException /
            // DeadSystemException that can happen due to an Android N framework bug manifesting if a user
            // had restored their device from a previous device backup.
            Log.e(TAG, "Exception on getWallpaperFile", e);
        }
        return parcelFd;
    }
}
