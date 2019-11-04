/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.server.telecom;

import android.content.Context;
import android.media.AudioManager;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.telecom.Log;

import com.android.internal.annotations.VisibleForTesting;

/**
 * Accesses the Global System settings for more control during testing.
 */
@VisibleForTesting
public class SystemSettingsUtil {

    /** Flag for ringer ramping time in milliseconds. */
    private static final String RAMPING_RINGER_DURATION_MILLIS = "ramping_ringer_duration";

    /** Flag for vibration time in milliseconds before ramping ringer starts. */
    private static final String RAMPING_RINGER_VIBRATION_DURATION =
            "ramping_ringer_vibration_duration";

    /** Flag for whether or not to apply ramping ringer on incoming phone calls. */
    private static final String RAMPING_RINGER_ENABLED = "ramping_ringer_enabled";

    /** Flag for whether or not to support audio coupled haptics in ramping ringer. */
    private static final String RAMPING_RINGER_AUDIO_COUPLED_VIBRATION_ENABLED =
            "ramping_ringer_audio_coupled_vibration_enabled";

    public boolean isTheaterModeOn(Context context) {
        return Settings.Global.getInt(context.getContentResolver(), Settings.Global.THEATER_MODE_ON,
                0) == 1;
    }

    public boolean canVibrateWhenRinging(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.VIBRATE_WHEN_RINGING, 0) != 0;
    }

    public boolean isEnhancedCallBlockingEnabled(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.DEBUG_ENABLE_ENHANCED_CALL_BLOCKING, 0) != 0;
    }

    public boolean setEnhancedCallBlockingEnabled(Context context, boolean enabled) {
        return Settings.System.putInt(context.getContentResolver(),
                Settings.System.DEBUG_ENABLE_ENHANCED_CALL_BLOCKING, enabled ? 1 : 0);
    }

    public boolean applyRampingRinger(Context context) {
        return Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.APPLY_RAMPING_RINGER, 0) == 1;
    }

    public boolean enableRampingRingerFromDeviceConfig() {
        return DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_TELEPHONY, RAMPING_RINGER_ENABLED,
                false);
    }

    public boolean enableAudioCoupledVibrationForRampingRinger() {
        return DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_TELEPHONY,
                RAMPING_RINGER_AUDIO_COUPLED_VIBRATION_ENABLED, false);
    }

    public int getRampingRingerDuration() {
	return DeviceConfig.getInt(DeviceConfig.NAMESPACE_TELEPHONY,
                RAMPING_RINGER_DURATION_MILLIS, -1);
    }

    public int getRampingRingerVibrationDuration() {
        return DeviceConfig.getInt(DeviceConfig.NAMESPACE_TELEPHONY, 
                RAMPING_RINGER_VIBRATION_DURATION, 0);
    }

    public boolean isHapticPlaybackSupported(Context context) {
        return context.getSystemService(AudioManager.class).isHapticPlaybackSupported();
    }
}

