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

package com.android.car;

import android.util.StatsLog;

/**
 * CarStatsLog provides API to send Car events to statd.
 * @hide
 */
public class CarStatsLog {
    /**
     * Logs a power state change event.
     * @param state an integer defined in CarPowerManagementService.CpmsState
     */
    public static void logPowerState(int state) {
        StatsLog.write(StatsLog.CAR_POWER_STATE_CHANGED, state);
    }

    /** Logs a GarageMode start event. */
    public static void logGarageModeStart() {
        StatsLog.write(StatsLog.GARAGE_MODE_INFO, true);
    }

    /** Logs a GarageMode stop event. */
    public static void logGarageModeStop() {
        StatsLog.write(StatsLog.GARAGE_MODE_INFO, false);
    }
}
