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

package com.android.car.notification.testutils;

import android.annotation.UserIdInt;
import android.app.ApplicationPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;

@Implements(value = ApplicationPackageManager.class)
public class ShadowApplicationPackageManager extends
        org.robolectric.shadows.ShadowApplicationPackageManager {

    private static PackageInfo sPackageInfo;
    private static Resources sResources = null;

    @Resetter
    public static void reset() {
        sResources = null;
        sPackageInfo = null;
    }

    @Implementation
    public PackageInfo getPackageInfoAsUser(String packageName,
            @PackageManager.PackageInfoFlags int flags, @UserIdInt int userId) {
        return sPackageInfo;
    }

    @Implementation
    protected Resources getResourcesForApplication(String appPackageName)
            throws PackageManager.NameNotFoundException {
        return sResources;
    }

    public static void setResources(Resources resources) {
        sResources = resources;
    }

    public static void setPackageInfo(PackageInfo packageInfo) {
        sPackageInfo = packageInfo;
    }
}
