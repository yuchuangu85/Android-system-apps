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

import android.app.admin.DevicePolicyManager;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.car.settings.common.Logger;
import com.android.internal.widget.LockPatternUtils;

/**
 * Factory class which generate password fragment for current user.
 */
public class ConfirmPasswordFragmentFactory {

    private static final Logger LOG = new Logger(ConfirmPasswordFragmentFactory.class);

    /**
     * Gets the correct password fragment of current user, returns the corresponding password
     * fragment of current user
     *
     * @return {@code null} if no password is set for the current user.
     */
    @Nullable
    public static Fragment getFragment(Context context) {
        Fragment fragment;
        int passwordQuality = new LockPatternUtils(context).getKeyguardStoredPasswordQuality(
                new CarUserManagerHelper(context).getCurrentProcessUserId());
        switch (passwordQuality) {
            case DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED:
                // User has not set a password.
                return null;
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
                LOG.e("Unexpected password quality: " + passwordQuality);
                fragment = ConfirmLockPinPasswordFragment.newPasswordInstance();
        }

        Bundle bundle = fragment.getArguments();
        if (bundle == null) {
            bundle = new Bundle();
        }
        bundle.putInt(ChooseLockTypeFragment.EXTRA_CURRENT_PASSWORD_QUALITY, passwordQuality);
        fragment.setArguments(bundle);
        return fragment;
    }
}
