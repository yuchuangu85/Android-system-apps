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

package com.android.car.settings.security;

import android.app.admin.DevicePolicyManager;
import android.os.Bundle;
import android.os.UserHandle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.car.settings.R;
import com.android.car.settings.common.BaseCarSettingsActivity;
import com.android.car.settings.common.Logger;
import com.android.internal.widget.LockPatternUtils;

/**
 * Activity for setting screen locks
 */
public class SettingsScreenLockActivity extends BaseCarSettingsActivity implements
        CheckLockListener {

    private static final Logger LOG = new Logger(SettingsScreenLockActivity.class);

    private int mPasswordQuality;

    @Override
    @Nullable
    protected Fragment getInitialFragment() {
        mPasswordQuality = new LockPatternUtils(this).getKeyguardStoredPasswordQuality(
                UserHandle.myUserId());

        Fragment fragment;
        switch (mPasswordQuality) {
            case DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED:
                fragment = new ChooseLockTypeFragment();
                break;
            case DevicePolicyManager.PASSWORD_QUALITY_SOMETHING:
                fragment = new ConfirmLockPatternFragment();
                break;
            case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC:
            case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX:
                fragment = ConfirmLockPinPasswordFragment.newPinInstance();
                break;
            case DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC:
            case DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC:
                fragment = ConfirmLockPinPasswordFragment.newPasswordInstance();
                break;
            default:
                LOG.e("Unexpected password quality: " + String.valueOf(mPasswordQuality));
                fragment = ConfirmLockPinPasswordFragment.newPasswordInstance();
        }

        Bundle bundle = fragment.getArguments();
        if (bundle == null) {
            bundle = new Bundle();
        }
        bundle.putInt(ChooseLockTypeFragment.EXTRA_CURRENT_PASSWORD_QUALITY, mPasswordQuality);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onLockVerified(byte[] lock) {
        Fragment fragment = new ChooseLockTypeFragment();
        Bundle bundle = fragment.getArguments();
        if (bundle == null) {
            bundle = new Bundle();
        }
        bundle.putByteArray(PasswordHelper.EXTRA_CURRENT_SCREEN_LOCK, lock);
        bundle.putInt(ChooseLockTypeFragment.EXTRA_CURRENT_PASSWORD_QUALITY, mPasswordQuality);
        fragment.setArguments(bundle);

        // Intentionally not using launchFragment(), since we do not want to add to the back stack.
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}
