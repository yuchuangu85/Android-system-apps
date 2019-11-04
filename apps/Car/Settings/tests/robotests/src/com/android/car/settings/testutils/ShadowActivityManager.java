/*
 * Copyright 2019 The Android Open Source Project
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

import android.app.ActivityManager;
import android.content.pm.IPackageDataObserver;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;

@Implements(value = ActivityManager.class)
public class ShadowActivityManager extends org.robolectric.shadows.ShadowActivityManager {

    private static boolean sIsApplicationUserDataCleared;

    private String mMostRecentlyStoppedPackage;

    @Resetter
    public static void reset() {
        sIsApplicationUserDataCleared = false;
    }

    @Implementation
    protected void forceStopPackage(String packageName) {
        mMostRecentlyStoppedPackage = packageName;
    }

    @Implementation
    protected boolean clearApplicationUserData(String packageName, IPackageDataObserver observer) {
        return sIsApplicationUserDataCleared;
    }

    public String getMostRecentlyStoppedPackage() {
        return mMostRecentlyStoppedPackage;
    }

    public static void setApplicationUserDataCleared(boolean applicationUserDataCleared) {
        sIsApplicationUserDataCleared = applicationUserDataCleared;
    }
}
