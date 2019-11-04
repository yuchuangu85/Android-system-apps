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

package com.android.car.developeroptions.security.screenlock;

import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;

import android.content.Context;
import android.os.UserHandle;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.internal.widget.LockPatternUtils;
import com.android.car.developeroptions.core.BasePreferenceController;
import com.android.car.developeroptions.notification.LockScreenNotificationPreferenceController;
import com.android.car.developeroptions.overlay.FeatureFactory;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnResume;

public class LockScreenPreferenceController extends BasePreferenceController implements
        LifecycleObserver, OnResume {

    private static final int MY_USER_ID = UserHandle.myUserId();
    private final LockPatternUtils mLockPatternUtils;
    private Preference mPreference;

    public LockScreenPreferenceController(Context context, String key) {
        super(context, key);
        mLockPatternUtils = FeatureFactory.getFactory(context)
                .getSecurityFeatureProvider().getLockPatternUtils(context);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
    }

    @Override
    public int getAvailabilityStatus() {
        if (!mLockPatternUtils.isSecure(MY_USER_ID)) {
            return mLockPatternUtils.isLockScreenDisabled(MY_USER_ID)
                    ? DISABLED_FOR_USER : AVAILABLE_UNSEARCHABLE;
        } else {
            return mLockPatternUtils.getKeyguardStoredPasswordQuality(MY_USER_ID)
                    == PASSWORD_QUALITY_UNSPECIFIED
                    ? DISABLED_FOR_USER : AVAILABLE_UNSEARCHABLE;
        }
    }

    @Override
    public void updateState(Preference preference) {
        preference.setSummary(
                LockScreenNotificationPreferenceController.getSummaryResource(mContext));
    }

    @Override
    public void onResume() {
        mPreference.setVisible(isAvailable());
    }
}
