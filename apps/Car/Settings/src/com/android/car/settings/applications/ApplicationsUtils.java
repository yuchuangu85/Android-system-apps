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

package com.android.car.settings.applications;

import android.app.admin.DevicePolicyManager;
import android.car.userlib.CarUserManagerHelper;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.UserInfo;
import android.telecom.DefaultDialerManager;
import android.text.TextUtils;
import android.util.ArraySet;

import com.android.internal.telephony.SmsApplication;

import java.util.List;
import java.util.Set;

/** Utility functions for use in applications settings. */
public class ApplicationsUtils {

    private ApplicationsUtils() {
    }

    /**
     * Returns {@code true} if the package should always remain enabled.
     */
    // TODO: investigate making this behavior configurable via a feature factory with the current
    //  contents as the default.
    public static boolean isKeepEnabledPackage(Context context, String packageName) {
        // Find current default phone/sms app. We should keep them enabled.
        Set<String> keepEnabledPackages = new ArraySet<>();
        String defaultDialer = DefaultDialerManager.getDefaultDialerApplication(context);
        if (!TextUtils.isEmpty(defaultDialer)) {
            keepEnabledPackages.add(defaultDialer);
        }
        ComponentName defaultSms = SmsApplication.getDefaultSmsApplication(
                context, /* updateIfNeeded= */ true);
        if (defaultSms != null) {
            keepEnabledPackages.add(defaultSms.getPackageName());
        }
        return keepEnabledPackages.contains(packageName);
    }

    /**
     * Returns {@code true} if the given {@code packageName} is device owner or profile owner of at
     * least one user.
     */
    public static boolean isProfileOrDeviceOwner(String packageName, DevicePolicyManager dpm,
            CarUserManagerHelper um) {
        if (dpm.isDeviceOwnerAppOnAnyUser(packageName)) {
            return true;
        }
        List<UserInfo> userInfos = um.getAllUsers();
        for (int i = 0; i < userInfos.size(); i++) {
            ComponentName cn = dpm.getProfileOwnerAsUser(userInfos.get(i).id);
            if (cn != null && cn.getPackageName().equals(packageName)) {
                return true;
            }
        }
        return false;
    }
}
