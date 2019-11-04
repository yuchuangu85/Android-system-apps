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

package com.android.car.settings.development;

import android.app.ActivityManager;
import android.car.userlib.CarUserManagerHelper;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;

import com.android.car.settings.R;
import com.android.settingslib.development.DevelopmentSettingsEnabler;

/**
 * A utility to set/check development settings mode.
 *
 * <p>Shared logic with {@link com.android.settingslib.development.DevelopmentSettingsEnabler} with
 * modifications to use CarUserManagerHelper instead of UserManager.
 */
public class DevelopmentSettingsUtil {

    private DevelopmentSettingsUtil() {
    }

    /**
     * Sets the global toggle for developer settings and sends out a local broadcast to notify other
     * of this change.
     */
    public static void setDevelopmentSettingsEnabled(Context context, boolean enable) {
        Settings.Global.putInt(context.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, enable ? 1 : 0);

        // Used to enable developer options module.
        ComponentName targetName = ComponentName.unflattenFromString(
                context.getString(R.string.config_dev_options_module));
        setDeveloperOptionsEnabledState(context, targetName, showDeveloperOptions(context));
    }

    /**
     * Checks that the development settings should be enabled. Returns true if global toggle is set,
     * debugging is allowed for user, and the user is an admin or a demo user.
     */
    public static boolean isDevelopmentSettingsEnabled(Context context,
            CarUserManagerHelper carUserManagerHelper) {
        boolean settingEnabled = Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, Build.IS_ENG ? 1 : 0) != 0;
        boolean hasRestriction = carUserManagerHelper.hasUserRestriction(
                UserManager.DISALLOW_DEBUGGING_FEATURES,
                carUserManagerHelper.getCurrentProcessUserInfo());
        boolean isAdminOrDemo = carUserManagerHelper.isCurrentProcessAdminUser()
                || carUserManagerHelper.isCurrentProcessDemoUser();
        return isAdminOrDemo && !hasRestriction && settingEnabled;
    }

    /** Checks whether the device is provisioned or not. */
    public static boolean isDeviceProvisioned(Context context) {
        return Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.DEVICE_PROVISIONED, 0) != 0;
    }

    private static boolean showDeveloperOptions(Context context) {
        CarUserManagerHelper carUserManagerHelper = new CarUserManagerHelper(context);
        boolean showDev = DevelopmentSettingsEnabler.isDevelopmentSettingsEnabled(context)
                && !isMonkeyRunning();
        boolean isAdminOrDemo = carUserManagerHelper.isCurrentProcessAdminUser()
                || carUserManagerHelper.isCurrentProcessDemoUser();
        if (UserHandle.MU_ENABLED && !isAdminOrDemo) {
            showDev = false;
        }

        return showDev;
    }

    private static void setDeveloperOptionsEnabledState(Context context, ComponentName component,
            boolean enabled) {
        PackageManager pm = context.getPackageManager();
        int state = pm.getComponentEnabledSetting(component);
        boolean isEnabled = state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        if (isEnabled != enabled || state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
            pm.setComponentEnabledSetting(component, enabled
                            ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        }
    }

    private static boolean isMonkeyRunning() {
        return ActivityManager.isUserAMonkey();
    }
}
