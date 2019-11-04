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

package com.android.car.settings.testutils;

import android.annotation.Nullable;
import android.app.admin.DevicePolicyManager;
import android.util.ArraySet;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;

import java.util.List;
import java.util.Set;

@Implements(value = DevicePolicyManager.class)
public class ShadowDevicePolicyManager extends org.robolectric.shadows.ShadowDevicePolicyManager {
    @Nullable
    private static List<String> sPermittedInputMethods;

    private Set<String> mActiveAdminsPackages = new ArraySet<>();
    private boolean mIsInstallInQueue;

    @Resetter
    public static void reset() {
        sPermittedInputMethods = null;
    }

    @Implementation
    @Nullable
    protected List<String> getPermittedInputMethodsForCurrentUser() {
        return sPermittedInputMethods;
    }

    public static void setPermittedInputMethodsForCurrentUser(@Nullable List<String> inputMethods) {
        sPermittedInputMethods = inputMethods;
    }

    @Implementation
    protected boolean packageHasActiveAdmins(String packageName) {
        return mActiveAdminsPackages.contains(packageName);
    }

    public void setPackageHasActiveAdmins(String packageName, boolean hasActiveAdmins) {
        if (hasActiveAdmins) {
            mActiveAdminsPackages.add(packageName);
        } else {
            mActiveAdminsPackages.remove(packageName);
        }
    }

    @Implementation
    protected boolean isUninstallInQueue(String packageName) {
        return mIsInstallInQueue;
    }

    public void setIsUninstallInQueue(boolean isUninstallInQueue) {
        mIsInstallInQueue = isUninstallInQueue;
    }
}
