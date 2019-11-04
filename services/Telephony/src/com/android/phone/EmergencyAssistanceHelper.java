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

package com.android.phone;

import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Process;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.util.CollectionUtils;

import java.util.List;

/**
 * A helper to query activities of emergency assistance.
 */
public class EmergencyAssistanceHelper {
    private static final String TAG = EmergencyAssistanceHelper.class.getSimpleName();
    /**
     * Get intent action of target emergency app.
     *
     * @return A string of intent action to launch target emergency app.
     */
    public static String getIntentAction() {
        return TelephonyManager.ACTION_EMERGENCY_ASSISTANCE;
    }

    /**
     * Query activities of emergency assistance.
     *
     * @param context The context of the application.
     * @return A list of {@link ResolveInfo} which is queried from default assistance package,
     * or null if there is no installed system application of emergency assistance.
     */
    public static List<ResolveInfo> resolveAssistPackageAndQueryActivities(Context context) {
        final String assistPackage = getDefaultEmergencyPackage(context);
        List<ResolveInfo> infos = queryAssistActivities(context, assistPackage);
        if (infos == null || infos.isEmpty()) {
            PackageManager packageManager = context.getPackageManager();
            Intent queryIntent = new Intent(getIntentAction());
            infos = packageManager.queryIntentActivities(queryIntent, 0);

            PackageInfo bestMatch = null;
            for (int i = 0; i < infos.size(); i++) {
                if (infos.get(i).activityInfo == null) continue;
                String packageName = infos.get(i).activityInfo.packageName;
                PackageInfo packageInfo;
                try {
                    packageInfo = packageManager.getPackageInfo(packageName, 0);
                } catch (PackageManager.NameNotFoundException e) {
                    continue;
                }
                // Get earliest installed system app.
                if (isSystemApp(packageInfo) && (bestMatch == null
                        || bestMatch.firstInstallTime > packageInfo.firstInstallTime)) {
                    bestMatch = packageInfo;
                }
            }

            if (bestMatch != null) {
                setDefaultEmergencyPackageAsync(context, bestMatch.packageName);
                return queryAssistActivities(context, bestMatch.packageName);
            } else {
                return null;
            }
        } else {
            return infos;
        }
    }

    /**
     * Compose {@link ComponentName} from {@link ResolveInfo}.
     */
    public static ComponentName getComponentName(ResolveInfo resolveInfo) {
        if (resolveInfo == null || resolveInfo.activityInfo == null) return null;
        return new ComponentName(resolveInfo.activityInfo.packageName,
                resolveInfo.activityInfo.name);
    }

    private static List<ResolveInfo> queryAssistActivities(Context context, String assistPackage) {
        List<ResolveInfo> infos = null;

        if (!TextUtils.isEmpty(assistPackage)) {
            Intent queryIntent = new Intent(getIntentAction())
                    .setPackage(assistPackage);
            infos = context.getPackageManager().queryIntentActivities(queryIntent, 0);
        }
        return infos;
    }

    private static boolean isSystemApp(PackageInfo info) {
        return info.applicationInfo != null
                && (info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    private static String getDefaultEmergencyPackage(Context context) {
        long identity = Binder.clearCallingIdentity();
        try {
            return CollectionUtils.firstOrNull(context.getSystemService(RoleManager.class)
                    .getRoleHolders(RoleManager.ROLE_EMERGENCY));
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private static boolean setDefaultEmergencyPackageAsync(Context context, String pkgName) {
        long identity = Binder.clearCallingIdentity();
        try {
            context.getSystemService(RoleManager.class).addRoleHolderAsUser(
                    RoleManager.ROLE_EMERGENCY, pkgName, 0, Process.myUserHandle(),
                    AsyncTask.THREAD_POOL_EXECUTOR, successful -> {
                        if (!successful) {
                            Log.e(TAG, "Failed to set emergency default app.");
                        }
                    });
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        return true;
    }
}
