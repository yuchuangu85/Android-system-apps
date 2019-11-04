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

package com.android.car.settings.display;

import static com.android.settingslib.display.BrightnessUtils.GAMMA_SPACE_MAX;
import static com.android.settingslib.display.BrightnessUtils.convertGammaToLinear;
import static com.android.settingslib.display.BrightnessUtils.convertLinearToGamma;

import android.car.drivingstate.CarUxRestrictions;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.os.PowerManager;
import android.provider.Settings;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;
import com.android.car.settings.common.SeekBarPreference;

/** Business logic for changing the brightness of the display. */
public class BrightnessLevelPreferenceController extends PreferenceController<SeekBarPreference> {

    private static final Logger LOG = new Logger(BrightnessLevelPreferenceController.class);
    private final CarUserManagerHelper mCarUserManagerHelper;
    private final int mMaximumBacklight;
    private final int mMinimumBacklight;

    public BrightnessLevelPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mCarUserManagerHelper = new CarUserManagerHelper(context);
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mMaximumBacklight = powerManager.getMaximumScreenBrightnessSetting();
        mMinimumBacklight = powerManager.getMinimumScreenBrightnessSetting();
    }

    @Override
    protected Class<SeekBarPreference> getPreferenceType() {
        return SeekBarPreference.class;
    }

    @Override
    protected void updateState(SeekBarPreference preference) {
        preference.setMax(GAMMA_SPACE_MAX);
        preference.setValue(getSeekbarValue());
        preference.setContinuousUpdate(true);
    }

    @Override
    protected boolean handlePreferenceChanged(SeekBarPreference preference, Object newValue) {
        int gamma = (Integer) newValue;
        int linear = convertGammaToLinear(gamma, mMinimumBacklight, mMaximumBacklight);
        Settings.System.putIntForUser(getContext().getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, linear,
                mCarUserManagerHelper.getCurrentProcessUserId());
        return true;
    }

    private int getSeekbarValue() {
        int gamma = GAMMA_SPACE_MAX;
        try {
            int linear = Settings.System.getIntForUser(getContext().getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS,
                    mCarUserManagerHelper.getCurrentProcessUserId());
            gamma = convertLinearToGamma(linear, mMinimumBacklight, mMaximumBacklight);
        } catch (Settings.SettingNotFoundException e) {
            LOG.w("Can't find setting for SCREEN_BRIGHTNESS.");
        }
        return gamma;
    }
}
