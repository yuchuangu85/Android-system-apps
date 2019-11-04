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

import android.annotation.NonNull;
import android.content.Context;
import android.os.Handler;
import android.permission.PermissionControllerManager;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.util.List;

/**
 * A RuntimePermissionPresenter that changes nothing and <u>never returns callbacks</u>.
 */
@Implements(PermissionControllerManager.class)
public class ShadowPermissionControllerManager {
    @Implementation
    protected void __constructor__(Context context, @NonNull Handler handler) {
        // no nothing, everything is shadowed
    }

    @Implementation
    protected void getAppPermissions(String packageName,
            PermissionControllerManager.OnGetAppPermissionResultCallback callback, Handler handler) {
    }

    @Implementation
    protected void revokeRuntimePermission(String packageName, String permissionName) {
    }

    @Implementation
    protected void countPermissionApps(List<String> permissionNames, boolean countOnlyGranted,
            boolean countSystem,
            PermissionControllerManager.OnCountPermissionAppsResultCallback callback,
            Handler handler) {
    }
}
