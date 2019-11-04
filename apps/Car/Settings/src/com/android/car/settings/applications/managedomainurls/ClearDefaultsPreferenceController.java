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

package com.android.car.settings.applications.managedomainurls;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.usb.IUsbManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.settingslib.applications.AppUtils;

/**
 * Business logic to reset a user preference for auto launching an app.
 *
 * <p>i.e. if a user has both NavigationAppA and NavigationAppB installed and NavigationAppA is set
 * as the default navigation app, the user can reset that preference to pick a different default
 * navigation app.
 */
public class ClearDefaultsPreferenceController extends
        AppLaunchSettingsBasePreferenceController<Preference> {

    private static final Logger LOG = new Logger(ClearDefaultsPreferenceController.class);

    private final IUsbManager mUsbManager;
    private final PackageManager mPm;

    public ClearDefaultsPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        IBinder b = ServiceManager.getService(Context.USB_SERVICE);
        mUsbManager = IUsbManager.Stub.asInterface(b);
        mPm = context.getPackageManager();
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    protected void updateState(Preference preference) {
        boolean autoLaunchEnabled = AppUtils.hasPreferredActivities(mPm, getPackageName())
                || isDefaultBrowser(getPackageName())
                || hasUsbDefaults(mUsbManager, getPackageName());

        preference.setEnabled(autoLaunchEnabled);
        if (autoLaunchEnabled) {
            preference.setTitle(R.string.auto_launch_reset_text);
            preference.setSummary(R.string.auto_launch_enable_text);
        } else {
            preference.setTitle(R.string.auto_launch_disable_text);
            preference.setSummary(null);
        }
    }

    @Override
    protected boolean handlePreferenceClicked(Preference preference) {
        if (mUsbManager != null) {
            int userId = getCurrentUserId();
            mPm.clearPackagePreferredActivities(getPackageName());
            if (isDefaultBrowser(getPackageName())) {
                mPm.setDefaultBrowserPackageNameAsUser(/* packageName= */ null, userId);
            }
            try {
                mUsbManager.clearDefaults(getPackageName(), userId);
            } catch (RemoteException e) {
                LOG.e("mUsbManager.clearDefaults", e);
            }
            refreshUi();
        }
        return true;
    }

    private boolean isDefaultBrowser(String packageName) {
        String defaultBrowser = mPm.getDefaultBrowserPackageNameAsUser(getCurrentUserId());
        return packageName.equals(defaultBrowser);
    }

    private boolean hasUsbDefaults(IUsbManager usbManager, String packageName) {
        try {
            if (usbManager != null) {
                return usbManager.hasDefaults(packageName, getCurrentUserId());
            }
        } catch (RemoteException e) {
            LOG.e("mUsbManager.hasDefaults", e);
        }
        return false;
    }
}
