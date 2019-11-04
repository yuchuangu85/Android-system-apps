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
package com.android.car.settings.testutils;

import static android.content.pm.PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_UNDEFINED;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.app.ApplicationPackageManager;
import android.content.ComponentName;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.ModuleInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.util.Pair;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shadow of ApplicationPackageManager that allows the getting of content providers per user. */
@Implements(value = ApplicationPackageManager.class)
public class ShadowApplicationPackageManager extends
        org.robolectric.shadows.ShadowApplicationPackageManager {

    private static Resources sResources = null;
    private static PackageManager sPackageManager;

    private final Map<Integer, String> mUserIdToDefaultBrowserMap = new HashMap<>();
    private final Map<String, ComponentName> mPkgToDefaultActivityMap = new HashMap<>();
    private final Map<String, IntentFilter> mPkgToDefaultActivityIntentFilterMap = new HashMap<>();
    private final Map<IntentFilter, ComponentName> mPreferredActivities = new LinkedHashMap<>();
    private final Map<Pair<String, Integer>, Integer> mPkgAndUserIdToIntentVerificationStatusMap =
            new HashMap<>();
    private List<ResolveInfo> mHomeActivities = Collections.emptyList();
    private ComponentName mDefaultHomeActivity;
    private String mPermissionControllerPackageName;

    @Resetter
    public static void reset() {
        sResources = null;
        sPackageManager = null;
    }

    @Implementation
    @NonNull
    protected List<ModuleInfo> getInstalledModules(@PackageManager.ModuleInfoFlags int flags) {
        return Collections.emptyList();
    }

    @Implementation
    protected Drawable getUserBadgedIcon(Drawable icon, UserHandle user) {
        return icon;
    }

    @Override
    @Implementation
    protected ProviderInfo resolveContentProviderAsUser(String name, int flags,
            @UserIdInt int userId) {
        return resolveContentProvider(name, flags);
    }

    @Implementation
    protected int getPackageUidAsUser(String packageName, int flags, int userId)
            throws PackageManager.NameNotFoundException {
        return 0;
    }

    @Implementation
    protected void deleteApplicationCacheFiles(String packageName, IPackageDataObserver observer) {
        sPackageManager.deleteApplicationCacheFiles(packageName, observer);
    }

    @Implementation
    protected Resources getResourcesForApplication(String appPackageName)
            throws PackageManager.NameNotFoundException {
        return sResources;
    }

    @Implementation
    protected List<ApplicationInfo> getInstalledApplicationsAsUser(int flags, int userId) {
        return getInstalledApplications(flags);
    }

    @Implementation
    protected ApplicationInfo getApplicationInfoAsUser(String packageName, int flags, int userId)
            throws PackageManager.NameNotFoundException {
        return getApplicationInfo(packageName, flags);
    }

    @Implementation
    @Override
    protected ComponentName getHomeActivities(List<ResolveInfo> outActivities) {
        outActivities.addAll(mHomeActivities);
        return mDefaultHomeActivity;
    }

    @Implementation
    @Override
    protected void clearPackagePreferredActivities(String packageName) {
        mPreferredActivities.clear();
    }

    @Implementation
    @Override
    public int getPreferredActivities(List<IntentFilter> outFilters,
            List<ComponentName> outActivities, String packageName) {
        for (IntentFilter filter : mPreferredActivities.keySet()) {
            ComponentName name = mPreferredActivities.get(filter);
            // If packageName is null, match everything, else filter by packageName.
            if (packageName == null) {
                outFilters.add(filter);
                outActivities.add(name);
            } else if (name.getPackageName().equals(packageName)) {
                outFilters.add(filter);
                outActivities.add(name);
            }
        }
        return 0;
    }

    @Implementation
    @Override
    public void addPreferredActivity(IntentFilter filter, int match, ComponentName[] set,
            ComponentName activity) {
        mPreferredActivities.put(filter, activity);
    }

    @Implementation
    @Override
    protected String getDefaultBrowserPackageNameAsUser(int userId) {
        return mUserIdToDefaultBrowserMap.getOrDefault(userId, null);
    }

    @Implementation
    @Override
    protected boolean setDefaultBrowserPackageNameAsUser(String packageName, int userId) {
        mUserIdToDefaultBrowserMap.put(userId, packageName);
        return true;
    }

    @Implementation
    @Override
    protected int getIntentVerificationStatusAsUser(String packageName, int userId) {
        Pair<String, Integer> key = new Pair<>(packageName, userId);
        return mPkgAndUserIdToIntentVerificationStatusMap.getOrDefault(key,
                INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_UNDEFINED);
    }

    @Implementation
    @Override
    protected boolean updateIntentVerificationStatusAsUser(String packageName, int status,
            int userId) {
        Pair<String, Integer> key = new Pair<>(packageName, userId);
        mPkgAndUserIdToIntentVerificationStatusMap.put(key, status);
        return true;
    }

    @Implementation
    protected String getPermissionControllerPackageName() {
        return mPermissionControllerPackageName;
    }

    public void setPermissionControllerPackageName(String packageName) {
        mPermissionControllerPackageName = packageName;
    }

    public void setHomeActivities(List<ResolveInfo> homeActivities) {
        mHomeActivities = homeActivities;
    }

    public void setDefaultHomeActivity(ComponentName defaultHomeActivity) {
        mDefaultHomeActivity = defaultHomeActivity;
    }

    public static void setResources(Resources resources) {
        sResources = resources;
    }

    public static void setPackageManager(PackageManager packageManager) {
        sPackageManager = packageManager;
    }
}
