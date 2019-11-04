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

package com.android.car.settings.system;

import android.car.drivingstate.CarUxRestrictions;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.os.Build;
import android.widget.Toast;

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.car.settings.development.DevelopmentSettingsUtil;

/** Updates the build number entry summary with the build number. */
public class BuildNumberPreferenceController extends PreferenceController<Preference> {

    private final CarUserManagerHelper mCarUserManagerHelper;
    private Toast mDevHitToast;
    private int mDevHitCountdown;

    public BuildNumberPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mCarUserManagerHelper = new CarUserManagerHelper(context);
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    /**
     * Reset the toast and counter which tracks how many more clicks until development settings is
     * enabled.
     */
    @Override
    protected void onResumeInternal() {
        mDevHitToast = null;
        mDevHitCountdown = DevelopmentSettingsUtil.isDevelopmentSettingsEnabled(getContext(),
                mCarUserManagerHelper) ? -1 : getTapsToBecomeDeveloper();
    }

    @Override
    protected void updateState(Preference preference) {
        preference.setSummary(Build.DISPLAY);
    }

    @Override
    protected boolean handlePreferenceClicked(Preference preference) {
        if (!mCarUserManagerHelper.isCurrentProcessAdminUser()
                && !mCarUserManagerHelper.isCurrentProcessDemoUser()) {
            return false;
        }

        if (!DevelopmentSettingsUtil.isDeviceProvisioned(getContext())) {
            return false;
        }

        if (mDevHitCountdown > 0) {
            mDevHitCountdown--;
            if (mDevHitCountdown == 0) {
                DevelopmentSettingsUtil.setDevelopmentSettingsEnabled(getContext(), true);
                showToast(getContext().getString(R.string.show_dev_on), Toast.LENGTH_LONG);
            } else if (mDevHitCountdown <= getTapsToBecomeDeveloper() - getTapsToShowToast()) {
                showToast(getContext().getResources().getQuantityString(
                        R.plurals.show_dev_countdown, mDevHitCountdown, mDevHitCountdown),
                        Toast.LENGTH_SHORT);
            }
        } else if (mDevHitCountdown < 0) {
            showToast(getContext().getString(R.string.show_dev_already), Toast.LENGTH_LONG);
        }
        return true;
    }

    private void showToast(String text, @Toast.Duration int duration) {
        if (mDevHitToast != null) {
            mDevHitToast.cancel();
        }
        mDevHitToast = Toast.makeText(getContext(), text, duration);
        mDevHitToast.show();
    }

    private int getTapsToBecomeDeveloper() {
        return getContext().getResources().getInteger(
                R.integer.enable_developer_settings_click_count);
    }

    private int getTapsToShowToast() {
        return getContext().getResources().getInteger(
                R.integer.enable_developer_settings_clicks_to_show_toast_count);
    }
}
