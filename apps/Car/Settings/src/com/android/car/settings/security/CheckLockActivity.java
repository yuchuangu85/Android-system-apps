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

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.car.settings.common.BaseCarSettingsActivity;

/**
 * Prompts the user to enter their pin, password, or pattern lock (if set) and returns
 * {@link #RESULT_OK} on a successful entry or immediately if the user has no lock setup.
 */
public class CheckLockActivity extends BaseCarSettingsActivity implements CheckLockListener {
    @Override
    @Nullable
    protected Fragment getInitialFragment() {
        Fragment lockFragment = ConfirmPasswordFragmentFactory.getFragment(/* context= */ this);
        if (lockFragment == null) {
            // User has not set a password
            setResult(RESULT_OK);
            finish();
        }
        return lockFragment;
    }

    @Override
    public void onLockVerified(byte[] lock) {
        setResult(RESULT_OK);
        finish();
    }
}
