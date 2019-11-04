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

package com.android.car.settings.security;

import static android.os.UserManager.DISALLOW_BLUETOOTH;
import static android.os.UserManager.DISALLOW_CONFIG_BLUETOOTH;

import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothAdapter;
import android.car.drivingstate.CarUxRestrictions;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.internal.widget.LockPatternUtils;

/**
 * Business logic which controls whether the preference is clickeble according to the password
 * quality of current user.
 */
public class AddTrustedDevicePreferenceController extends PreferenceController<Preference> {
    private CarUserManagerHelper mCarUserManagerHelper;
    private LockPatternUtils mLockPatternUtils;

    public AddTrustedDevicePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mCarUserManagerHelper = new CarUserManagerHelper(context);
        mLockPatternUtils = new LockPatternUtils(context);
    }

    @Override
    protected int getAvailabilityStatus() {
        if (!getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            return UNSUPPORTED_ON_DEVICE;
        }
        return isUserRestricted() ? DISABLED_FOR_USER : AVAILABLE;
    }

    private boolean isUserRestricted() {
        return mCarUserManagerHelper.isCurrentProcessUserHasRestriction(DISALLOW_BLUETOOTH)
                || mCarUserManagerHelper.isCurrentProcessUserHasRestriction(
                DISALLOW_CONFIG_BLUETOOTH);
    }

    @Override
    protected void updateState(Preference preference) {
        preference.setSummary(
                BluetoothAdapter.getDefaultAdapter().isEnabled() ? "" : getContext().getString(
                        R.string.add_device_summary));
        preference.setEnabled(hasPassword());
    }

    private boolean hasPassword() {
        return mLockPatternUtils.getKeyguardStoredPasswordQuality(
                mCarUserManagerHelper.getCurrentProcessUserId())
                != DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    public boolean handlePreferenceClicked(Preference preference) {
        // Enable the adapter if it is not on (user is notified via summary message).
        BluetoothAdapter.getDefaultAdapter().enable();
        /** Need to start {@link AddTrustedDeviceActivity} after the click. */
        return false;
    }
}
