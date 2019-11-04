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

package com.android.car.carlauncher;

import android.annotation.Nullable;
import android.app.ActivityOptions;
import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.content.pm.CarPackageManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Process;
import android.service.media.MediaBrowserService;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Util class that contains helper method used by app launcher classes.
 */
class AppLauncherUtils {
    private static final String TAG = "AppLauncherUtils";

    private AppLauncherUtils() {
    }

    /**
     * Comparator for {@link AppMetaData} that sorts the list
     * by the "displayName" property in ascending order.
     */
    static final Comparator<AppMetaData> ALPHABETICAL_COMPARATOR = Comparator
            .comparing(AppMetaData::getDisplayName, String::compareToIgnoreCase);

    /**
     * Helper method that launches the app given the app's AppMetaData.
     *
     * @param app the requesting app's AppMetaData
     */
    static void launchApp(Context context, AppMetaData app) {
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(context.getDisplayId());
        context.startActivity(app.getMainLaunchIntent(), options.toBundle());
    }

    /** Bundles application and services info. */
    static class LauncherAppsInfo {
        /*
         * Map of all car launcher components' (including launcher activities and media services)
          * metadata keyed by ComponentName.
         */
        private final Map<ComponentName, AppMetaData> mLaunchables;

        /** Map of all the media services keyed by ComponentName. */
        private final Map<ComponentName, ResolveInfo> mMediaServices;

        LauncherAppsInfo(@NonNull Map<ComponentName, AppMetaData> components,
                @NonNull Map<ComponentName, ResolveInfo> mediaServices) {
            mLaunchables = components;
            mMediaServices = mediaServices;
        }

        /** Returns true if all maps are empty. */
        boolean isEmpty() {
            return mLaunchables.isEmpty() && mMediaServices.isEmpty();
        }

        /**
         * Returns whether the given componentName is a media service.
         */
        boolean isMediaService(ComponentName componentName) {
            return mMediaServices.containsKey(componentName);
        }

        /** Returns the {@link AppMetaData} for the given componentName. */
        @Nullable
        AppMetaData getAppMetaData(ComponentName componentName) {
            return mLaunchables.get(componentName);
        }

        /** Returns a new list of all launchable components' {@link AppMetaData}. */
        @NonNull
        List<AppMetaData> getLaunchableComponentsList() {
            return new ArrayList<>(mLaunchables.values());
        }
    }

    private final static LauncherAppsInfo EMPTY_APPS_INFO = new LauncherAppsInfo(
            Collections.emptyMap(), Collections.emptyMap());

    /*
     * Gets the media source in a given package. If there are multiple sources in the package,
     * returns the first one.
     */
    static ComponentName getMediaSource(@NonNull PackageManager packageManager,
            @NonNull String packageName) {
        Intent mediaIntent = new Intent();
        mediaIntent.setPackage(packageName);
        mediaIntent.setAction(MediaBrowserService.SERVICE_INTERFACE);

        List<ResolveInfo> mediaServices = packageManager.queryIntentServices(mediaIntent,
                PackageManager.GET_RESOLVED_FILTER);

        if (mediaServices == null || mediaServices.isEmpty()) {
            return null;
        }
        String defaultService = mediaServices.get(0).serviceInfo.name;
        if (!TextUtils.isEmpty(defaultService)) {
            return new ComponentName(packageName, defaultService);
        }
        return null;
    }

    /**
     * Gets all the components that we want to see in the launcher in unsorted order, including
     * launcher activities and media services.
     *
     * @param blackList         A (possibly empty) list of apps (package names) to hide
     * @param launcherApps      The {@link LauncherApps} system service
     * @param carPackageManager The {@link CarPackageManager} system service
     * @param packageManager    The {@link PackageManager} system service
     * @return a new {@link LauncherAppsInfo}
     */
    @NonNull
    static LauncherAppsInfo getAllLauncherApps(
            @NonNull Set<String> blackList,
            LauncherApps launcherApps,
            CarPackageManager carPackageManager,
            PackageManager packageManager) {

        if (launcherApps == null || carPackageManager == null || packageManager == null) {
            return EMPTY_APPS_INFO;
        }

        List<ResolveInfo> mediaServices = packageManager.queryIntentServices(
                new Intent(MediaBrowserService.SERVICE_INTERFACE),
                PackageManager.GET_RESOLVED_FILTER);
        List<LauncherActivityInfo> availableActivities =
                launcherApps.getActivityList(null, Process.myUserHandle());

        Map<ComponentName, AppMetaData> components = new HashMap<>(
                mediaServices.size() + availableActivities.size());
        Map<ComponentName, ResolveInfo> mediaServicesMap = new HashMap<>(mediaServices.size());
        Set<String> mediaPackages = new HashSet<>();

        // Process media services
        for (ResolveInfo info : mediaServices) {
            String packageName = info.serviceInfo.packageName;
            String className = info.serviceInfo.name;
            ComponentName componentName = new ComponentName(packageName, className);
            mediaServicesMap.put(componentName, info);
            if (shouldAdd(componentName, components, blackList)) {
                mediaPackages.add(packageName);
                final boolean isDistractionOptimized = true;

                Intent intent = new Intent(Car.CAR_INTENT_ACTION_MEDIA_TEMPLATE);
                intent.putExtra(Car.CAR_EXTRA_MEDIA_COMPONENT, componentName.flattenToString());

                AppMetaData appMetaData = new AppMetaData(
                        info.serviceInfo.loadLabel(packageManager),
                        componentName,
                        info.serviceInfo.loadIcon(packageManager),
                        isDistractionOptimized,
                        intent,
                        packageManager.getLaunchIntentForPackage(packageName));
                components.put(componentName, appMetaData);
            }
        }

        // Process activities
        for (LauncherActivityInfo info : availableActivities) {
            ComponentName componentName = info.getComponentName();
            String packageName = componentName.getPackageName();
            // If a media service has been added to the map, don't add the activity belonging to the
            // same package.
            if (mediaPackages.contains(packageName)) {
                continue;
            }
            if (shouldAdd(componentName, components, blackList)) {
                boolean isDistractionOptimized =
                        isActivityDistractionOptimized(carPackageManager, packageName,
                                info.getName());

                AppMetaData appMetaData = new AppMetaData(
                        info.getLabel(),
                        componentName,
                        info.getBadgedIcon(0),
                        isDistractionOptimized,
                        packageManager.getLaunchIntentForPackage(packageName),
                        null);
                components.put(componentName, appMetaData);
            }
        }

        return new LauncherAppsInfo(components, mediaServicesMap);
    }

    private static boolean shouldAdd(ComponentName componentName,
            Map<ComponentName, AppMetaData> components,
            @NonNull Set<String> blackList) {
        return !components.containsKey(componentName) && !blackList.contains(
                componentName.getPackageName());
    }

    /**
     * Gets if an activity is distraction optimized.
     *
     * @param carPackageManager The {@link CarPackageManager} system service
     * @param packageName       The package name of the app
     * @param activityName      The requested activity name
     * @return true if the supplied activity is distraction optimized
     */
    static boolean isActivityDistractionOptimized(
            CarPackageManager carPackageManager, String packageName, String activityName) {
        boolean isDistractionOptimized = false;
        // try getting distraction optimization info
        try {
            if (carPackageManager != null) {
                isDistractionOptimized =
                        carPackageManager.isActivityDistractionOptimized(packageName, activityName);
            }
        } catch (CarNotConnectedException e) {
            Log.e(TAG, "Car not connected when getting DO info", e);
        }
        return isDistractionOptimized;
    }
}
