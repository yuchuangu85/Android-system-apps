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
package com.android.tv.tuner.api;

import com.android.tv.tuner.data.nano.Channel;

/** Channel information gathered from a <em>scan</em> */
public final class ScanChannel {
    public final int type;
    public final int frequency;
    public final String modulation;
    public final String filename;
    /**
     * Radio frequency (channel) number specified at
     * https://en.wikipedia.org/wiki/North_American_television_frequencies This can be {@code null}
     * for cases like cable signal.
     */
    public final Integer radioFrequencyNumber;

    public static ScanChannel forTuner(
            int frequency, String modulation, Integer radioFrequencyNumber) {
        return new ScanChannel(
                Channel.TunerType.TYPE_TUNER, frequency, modulation, null, radioFrequencyNumber);
    }

    public static ScanChannel forFile(int frequency, String filename) {
        return new ScanChannel(Channel.TunerType.TYPE_FILE, frequency, "file:", filename, null);
    }

    private ScanChannel(
            int type,
            int frequency,
            String modulation,
            String filename,
            Integer radioFrequencyNumber) {
        this.type = type;
        this.frequency = frequency;
        this.modulation = modulation;
        this.filename = filename;
        this.radioFrequencyNumber = radioFrequencyNumber;
    }
}
