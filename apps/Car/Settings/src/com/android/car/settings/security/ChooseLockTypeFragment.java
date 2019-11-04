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
 * limitations under the License
 */

package com.android.car.settings.security;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.XmlRes;

import com.android.car.settings.R;
import com.android.car.settings.common.SettingsFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * Give user choices of lock screen type: Pin/Pattern/Password or None.
 */
public class ChooseLockTypeFragment extends SettingsFragment {

    public static final String EXTRA_CURRENT_PASSWORD_QUALITY = "extra_current_password_quality";

    private byte[] mCurrPassword;
    private int mPasswordQuality;

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.choose_lock_type_fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        Bundle args = getArguments();
        if (args != null) {
            mCurrPassword = args.getByteArray(PasswordHelper.EXTRA_CURRENT_SCREEN_LOCK);
            mPasswordQuality = args.getInt(EXTRA_CURRENT_PASSWORD_QUALITY);
        }

        List<LockTypeBasePreferenceController> controllers = new ArrayList<>();
        controllers.add(use(NoLockPreferenceController.class, R.string.pk_no_lock));
        controllers.add(use(PatternLockPreferenceController.class, R.string.pk_pattern_lock));
        controllers.add(use(PasswordLockPreferenceController.class, R.string.pk_password_lock));
        controllers.add(use(PinLockPreferenceController.class, R.string.pk_pin_lock));

        for (LockTypeBasePreferenceController controller : controllers) {
            controller.setCurrentPassword(mCurrPassword);
            controller.setCurrentPasswordQuality(mPasswordQuality);
        }
    }
}
