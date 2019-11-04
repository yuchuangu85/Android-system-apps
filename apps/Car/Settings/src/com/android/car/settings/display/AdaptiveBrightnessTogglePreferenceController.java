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

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.provider.Settings;

import androidx.preference.TwoStatePreference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

/** Business logic for controlling the adaptive brightness setting. */
public class AdaptiveBrightnessTogglePreferenceController extends
        PreferenceController<TwoStatePreference> {

    public AdaptiveBrightnessTogglePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<TwoStatePreference> getPreferenceType() {
        return TwoStatePreference.class;
    }

    @Override
    protected void updateState(TwoStatePreference preference) {
        preference.setChecked(isAdaptiveBrightnessChecked());
    }

    @Override
    public int getAvailabilityStatus() {
        return supportsAdaptiveBrightness() ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    protected boolean handlePreferenceChanged(TwoStatePreference preference, Object newValue) {
        boolean enableAdaptiveBrightness = (boolean) newValue;
        Settings.System.putInt(getContext().getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                enableAdaptiveBrightness ? Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                        : Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        return true;
    }

    private boolean isAdaptiveBrightnessChecked() {
        int brightnessMode = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        return brightnessMode != Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL;
    }

    private boolean supportsAdaptiveBrightness() {
        return getContext().getResources().getBoolean(R.bool.config_automatic_brightness_available);
    }
}
