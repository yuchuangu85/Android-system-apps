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
package com.android.car.settings.quicksettings;

import static android.provider.Settings.System.SCREEN_BRIGHTNESS;

import static com.android.settingslib.display.BrightnessUtils.GAMMA_SPACE_MAX;
import static com.android.settingslib.display.BrightnessUtils.convertGammaToLinear;
import static com.android.settingslib.display.BrightnessUtils.convertLinearToGamma;

import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.os.PowerManager;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Settings.System;
import android.widget.SeekBar;

import com.android.car.settings.common.Logger;

/**
 * A slider to adjust the brightness of the screen
 */
public class BrightnessTile implements QuickSettingGridAdapter.SeekbarTile {
    private static final Logger LOG = new Logger(BrightnessTile.class);
    private CarUserManagerHelper mCarUserManagerHelper;
    private final Context mContext;
    private final int mMaximumBacklight;
    private final int mMinimumBacklight;

    public BrightnessTile(Context context) {
        mContext = context;
        mCarUserManagerHelper = new CarUserManagerHelper(mContext);
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mMaximumBacklight = powerManager.getMaximumScreenBrightnessSetting();
        mMinimumBacklight = powerManager.getMinimumScreenBrightnessSetting();
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        // don't care
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        // don't care
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int gamma, boolean fromUser) {
        int linear = convertGammaToLinear(gamma, mMinimumBacklight, mMaximumBacklight);
        System.putIntForUser(mContext.getContentResolver(), SCREEN_BRIGHTNESS, linear,
                             mCarUserManagerHelper.getCurrentForegroundUserId());
    }

    @Override
    public int getMax() {
        return GAMMA_SPACE_MAX;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public int getCurrent() {
        int gamma = GAMMA_SPACE_MAX;
        try {
            int linear = System.getIntForUser(mContext.getContentResolver(), SCREEN_BRIGHTNESS,
                                              mCarUserManagerHelper.getCurrentForegroundUserId());
            gamma = convertLinearToGamma(linear, mMinimumBacklight, mMaximumBacklight);
        } catch (SettingNotFoundException e) {
            LOG.w("Can't find setting for SCREEN_BRIGHTNESS.");
        }
        return gamma;
    }
}
