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

import android.hardware.radio.ProgramSelector;
import android.hardware.radio.RadioManager;

import com.android.car.broadcastradio.support.Program;
import com.android.car.radio.bands.ProgramType;
import com.android.car.radio.bands.RegionConfig;
import com.android.car.radio.service.IRadioAppCallback;
import com.android.car.radio.service.ITuneCallback;

/**
 * An interface to the backend Radio app's service.
 */
interface IRadioAppService {
    /**
     * Adds {@link RadioAppService} callback.
     *
     * Triggers state updates on newly added callback.
     */
    void addCallback(in IRadioAppCallback callback);

    /**
     * Removes {@link RadioAppService} callback.
     */
    void removeCallback(in IRadioAppCallback callback);

    /**
     * Tunes to a given program.
     */
    void tune(in ProgramSelector sel, in ITuneCallback callback);

    /**
     * Seeks forward or backwards.
     */
    void seek(boolean forward, in ITuneCallback callback);

    /**
     * Steps forward or backwards.
     */
    void step(boolean forward, in ITuneCallback callback);

    /**
     * Mutes or resumes audio.
     *
     * @param muted {@code true} to mute, {@code false} to resume audio.
     */
    void setMuted(boolean muted);

    /**
     * Tune to a default channel of a given program type (band).
     *
     * Usually, this means tuning to the recently listened program of a given band.
     *
     * @param band Program type to switch to
     */
    void switchBand(in ProgramType band);

    /**
     * States whether program list is supported on current device or not.
     *
     * @return {@code true} if the program list is supported, {@code false} otherwise.
     */
    boolean isProgramListSupported();

    /**
     * Returns current region config (like frequency ranges for AM/FM).
     */
    RegionConfig getRegionConfig();
}
