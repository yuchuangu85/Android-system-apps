/*
 * Copyright 2019, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.managedprovisioning.preprovisioning.consent;

import android.app.Activity;
import android.content.pm.PackageManager;
import com.android.managedprovisioning.common.AccessibilityContextMenuMaker;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.Utils;

/**
 * A factory which returns the appropriate {@link ConsentUiHelper} instance.
 */
public class ConsentUiHelperFactory {
    public static ConsentUiHelper getInstance(
        Activity activity, AccessibilityContextMenuMaker contextMenuMaker,
        ConsentUiHelperCallback callback, Utils utils,
        SettingsFacade settingsFacade) {
        if (shouldShowLegacyUi(activity)) {
            return new LegacyConsentUiHelper(activity, contextMenuMaker, callback, utils);
        } else {
            return new PrimaryConsentUiHelper(activity, callback, utils);
        }
    }

    private static boolean shouldShowLegacyUi(Activity activity) {
        // Android Auto still uses the old UI, so we need to ensure compatibility with it.
        return activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }
}
