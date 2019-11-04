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
package com.android.tv.common.compat;

/** Temp TIF Compatibility for {@link TvInputManager} constants. */
public class TvInputConstantCompat {

    /**
     * Status for {@link TisSessionCompat#notifySignalStrength(int)} and
     * {@link TvViewCompat.TvInputCallback#onTimeShiftStatusChanged(String, int)}:
     *
     * <p>SIGNAL_STRENGTH_NOT_USED means the TV Input does not report signal strength. Each onTune
     * command implicitly resets the TV App's signal strength state to SIGNAL_STRENGTH_NOT_USED.
     */
    public static final int SIGNAL_STRENGTH_NOT_USED = -3;

    /**
     * Status for {@link TisSessionCompat#notifySignalStrength(int)} and
     * {@link TvViewCompat.TvInputCallback#onTimeShiftStatusChanged(String, int)}:
     *
     * <p>SIGNAL_STRENGTH_ERROR means exception/error when handling signal strength.
     */
    public static final int SIGNAL_STRENGTH_ERROR = -2;

    /**
     * Status for {@link TisSessionCompat#notifySignalStrength(int)} and
     * {@link TvViewCompat.TvInputCallback#onTimeShiftStatusChanged(String, int)}:
     *
     * <p>SIGNAL_STRENGTH_UNKNOWN means the TV Input supports signal strength, but does not
     * currently know what the strength is.
     */
    public static final int SIGNAL_STRENGTH_UNKNOWN = -1;
}
