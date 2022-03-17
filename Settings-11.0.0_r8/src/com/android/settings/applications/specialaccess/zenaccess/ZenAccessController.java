/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.settings.applications.specialaccess.zenaccess;

import android.app.ActivityManager;
import android.app.AppGlobals;
import android.app.NotificationManager;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.ParceledListSlice;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.settings.core.BasePreferenceController;
import com.android.settings.overlay.FeatureFactory;

import java.util.List;
import java.util.Set;

public class ZenAccessController extends BasePreferenceController {

    private static final String TAG = "ZenAccessController";

    private final ActivityManager mActivityManager;

    public ZenAccessController(Context context, String preferenceKey) {
        super(context, preferenceKey);
        mActivityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
    }

    @Override
    public int getAvailabilityStatus() {
        return isSupported(mActivityManager) ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    public static boolean isSupported(ActivityManager activityManager) {
        return !activityManager.isLowRamDevice();
    }

    public static Set<String> getPackagesRequestingNotificationPolicyAccess() {
        final ArraySet<String> requestingPackages = new ArraySet<>();
        try {
            final String[] PERM = {
                    android.Manifest.permission.ACCESS_NOTIFICATION_POLICY
            };
            final ParceledListSlice list = AppGlobals.getPackageManager()
                    .getPackagesHoldingPermissions(PERM, 0 /*flags*/,
                            ActivityManager.getCurrentUser());
            final List<PackageInfo> pkgs = list.getList();
            if (pkgs != null) {
                for (PackageInfo info : pkgs) {
                    if (info.applicationInfo.enabled) {
                        requestingPackages.add(info.packageName);
                    }
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Cannot reach packagemanager", e);
        }
        return requestingPackages;
    }

    public static Set<String> getAutoApprovedPackages(Context context) {
        final Set<String> autoApproved = new ArraySet<>();
        autoApproved.addAll(context.getSystemService(NotificationManager.class)
                .getEnabledNotificationListenerPackages());
        return autoApproved;
    }

    public static boolean hasAccess(Context context, String pkg) {
        return context.getSystemService(
                NotificationManager.class).isNotificationPolicyAccessGrantedForPackage(pkg);
    }

    public static void setAccess(final Context context, final String pkg, final boolean access) {
        logSpecialPermissionChange(access, pkg, context);
        AsyncTask.execute(() -> {
            final NotificationManager mgr = context.getSystemService(NotificationManager.class);
            mgr.setNotificationPolicyAccessGranted(pkg, access);
        });
    }

    public static void deleteRules(final Context context, final String pkg) {
        AsyncTask.execute(() -> {
            final NotificationManager mgr = context.getSystemService(NotificationManager.class);
            mgr.removeAutomaticZenRules(pkg);
        });
    }

    @VisibleForTesting
    static void logSpecialPermissionChange(boolean enable, String packageName, Context context) {
        int logCategory = enable ? SettingsEnums.APP_SPECIAL_PERMISSION_DND_ALLOW
                : SettingsEnums.APP_SPECIAL_PERMISSION_DND_DENY;
        FeatureFactory.getFactory(context).getMetricsFeatureProvider().action(context,
                logCategory, packageName);
    }
}
