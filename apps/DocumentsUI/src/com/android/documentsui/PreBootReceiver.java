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

package com.android.documentsui;

import static com.android.documentsui.base.SharedMinimal.DEBUG;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.util.Log;

import com.android.documentsui.theme.ThemeOverlayManager;

/**
 * A receiver listening action.PRE_BOOT_COMPLETED event for setting component enable or disable.
 * Since there's limitation of overlay AndroidManifest.xml attrs at boot stage.
 * The workaround to retrieve config from DocumentsUI RRO package at boot time in Q.
 */
public class PreBootReceiver extends BroadcastReceiver {

    private static final String TAG = "PreBootReceiver";
    private static final String CONFIG_IS_LAUNCHER_ENABLED = "is_launcher_enabled";
    private static final String CONFIG_HANDLE_VIEW_DOWNLOADS = "handle_view_downloads_intent";
    private static final String LAUNCHER_TARGET_CLASS = "com.android.documentsui.LauncherActivity";
    private static final String DOWNLOADS_TARGET_CLASS =
            "com.android.documentsui.ViewDownloadsActivity";

    @Override
    public void onReceive(Context context, Intent intent) {
        final PackageManager pm = context.getPackageManager();
        if (pm == null) {
            Log.w(TAG, "Can't obtain PackageManager from System Service!");
            return;
        }

        final OverlayManager om = context.getSystemService(OverlayManager.class);
        if (om == null) {
            Log.w(TAG, "Can't obtain OverlayManager from System Service!");
            return;
        }

        final OverlayInfo info = new ThemeOverlayManager(om,
                context.getPackageName()).getValidOverlay(pm);

        if (info == null) {
            Log.w(TAG, "Can't get valid overlay info");
            return;
        }

        final String overlayPkg = info.getPackageName();
        final String packageName = context.getPackageName();

        Resources overlayRes;
        try {
            overlayRes = pm.getResourcesForApplication(overlayPkg);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Failed while parse package res.");
            overlayRes = null;
        }
        if (overlayRes == null) {
            return;
        }

        setComponentEnabledByConfigResources(pm, packageName, LAUNCHER_TARGET_CLASS,
                overlayPkg, overlayRes, CONFIG_IS_LAUNCHER_ENABLED);
        setComponentEnabledByConfigResources(pm, packageName, DOWNLOADS_TARGET_CLASS,
                overlayPkg, overlayRes, CONFIG_HANDLE_VIEW_DOWNLOADS);
    }

    private static void setComponentEnabledByConfigResources(PackageManager pm, String packageName,
            String className, String overlayPkg, Resources overlayRes, String config) {
        int resId = overlayRes.getIdentifier(config, "bool", overlayPkg);
        if (resId != 0) {
            final ComponentName component = new ComponentName(packageName, className);
            final boolean value = overlayRes.getBoolean(resId);
            if (DEBUG) {
                Log.i(TAG, "Overlay package:" + overlayPkg + ", customize " + config + ":" + value);
            }
            pm.setComponentEnabledSetting(component, value
                            ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        }
    }
}
