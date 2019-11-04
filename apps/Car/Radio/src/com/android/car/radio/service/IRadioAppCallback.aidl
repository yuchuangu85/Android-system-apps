/**
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

package com.android.car.radio.service;

import android.hardware.radio.RadioManager;

/**
 * Watches current program changes.
 */
oneway interface IRadioAppCallback {
    /**
     * Called when hardware error has occured.
     *
     * Client must unbind from the {@link RadioAppService} after getting this callback.
     */
    void onHardwareError();

    /**
     * Called when current program details changes.
     *
     * This might happen as a result of tuning to a different program or just metadata change.
     *
     * @param info Current program info
     */
    void onCurrentProgramChanged(in RadioManager.ProgramInfo info);

    /**
     * Called when playback state (play/pause) changes.
     *
     * @param state New playback state
     */
    void onPlaybackStateChanged(int state);

    /**
     * Called when program list changes.
     *
     * @param New program list
     */
    void onProgramListChanged(in List<RadioManager.ProgramInfo> plist);
}
