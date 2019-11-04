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

package com.android.documentsui.theme;

import android.content.Context;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;

import java.util.List;

/**
 * ThemeOverlayManager manage runtime resource overlay packages of DocumentsUI
 */
public class ThemeOverlayManager {
    private static final String TAG = ThemeOverlayManager.class.getSimpleName();
    private static final String PERMISSION_CHANGE_OVERLAY_PACKAGES =
            "android.permission.CHANGE_OVERLAY_PACKAGES";

    private final OverlayManager mOverlayManager;
    private String mTargetPackageId;
    private UserHandle mUserHandle;

    public ThemeOverlayManager(@NonNull OverlayManager overlayManager, String targetPackageId) {
        mOverlayManager = overlayManager;
        mTargetPackageId = targetPackageId;
        mUserHandle = UserHandle.of(UserHandle.myUserId());
    }

    /**
     * Apply runtime overlay package, dynamic enabled overlay do not support priority yet
     *
     * @param context the activity or context from caller
     * @param enabled whether or not enable overlay package
     */
    public void applyOverlays(Context context, boolean enabled, Consumer<Boolean> callback) {
        if (ContextCompat.checkSelfPermission(context, PERMISSION_CHANGE_OVERLAY_PACKAGES)
                == PackageManager.PERMISSION_GRANTED) {
            setEnabled(enabled, callback);
        } else {
            Log.w(TAG, "Permission: " + PERMISSION_CHANGE_OVERLAY_PACKAGES + " did not granted!");
            callback.accept(false);
        }
    }

    private List<OverlayInfo> getOverlayInfo() {
        // (b/132933212): Only static overlay package support priority attrs
        // TODO: Alternative way to support enabled multiple overlay packages by priority is
        //       tag meta-data in the application of overlay package's AndroidManifest.xml
        // TODO: Parse meta data through PM in DocumentsApplication and use collection to reorder
        return mOverlayManager.getOverlayInfosForTarget(mTargetPackageId, mUserHandle);
    }

    /**
     * Return the OverlayInfo which is provided by the docsUI overlay package located product,
     * system or vendor. We assume there should only one docsUI overlay package because priority
     * not work for non-static overlay, so vendor should put only one docsUI overlay package.
     *
     * @param pm the PackageManager
     */
    @Nullable
    public OverlayInfo getValidOverlay(@NonNull PackageManager pm) {
        for (OverlayInfo info : getOverlayInfo()) {
            try {
                final ApplicationInfo ai = pm.getApplicationInfo(info.getPackageName(), 0);
                // Since isProduct(), isVendor() and isSystemApp() functions in ApplicationInfo are
                // hidden. The best way to avoid unknown sideload APKs is filter path by string
                // comparison.
                final String sourceDir = ai.sourceDir;
                if (sourceDir.startsWith(Environment.getProductDirectory().getAbsolutePath())
                        || sourceDir.startsWith(Environment.getVendorDirectory().getAbsolutePath())
                        || sourceDir.startsWith(Environment.getRootDirectory().getAbsolutePath())) {
                    return info;
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "Can't get ApplicationInfo of overlay package " + info.getPackageName());
            }
        }
        return null;
    }

    private void setEnabled(boolean enabled, Consumer<Boolean> callback) {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                return setEnabledOverlayPackages(getOverlayInfo(), enabled);
            }

            @Override
            protected void onPostExecute(Boolean result) {
                super.onPostExecute(result);
                if (callback != null) {
                    callback.accept(result);
                }
            }
        }.execute();
    }

    private boolean setEnabledOverlayPackages(List<OverlayInfo> infos, boolean enabled) {
        boolean bSuccess = true;
        for (OverlayInfo info : infos) {
            try {
                if (info.isEnabled() != enabled) {
                    mOverlayManager.setEnabled(info.getPackageName(), enabled, mUserHandle);
                } else {
                    Log.w(TAG, "Skip enabled overlay package:" + info.getPackageName()
                            + ", user:" + mUserHandle);
                    bSuccess = false;
                }
            } catch (RuntimeException re) {
                Log.e(TAG, "Failed to enable overlay: " + info.getPackageName() + ", user: "
                        + mUserHandle);
                bSuccess = false;
            }
        }
        return bSuccess;
    }
}
