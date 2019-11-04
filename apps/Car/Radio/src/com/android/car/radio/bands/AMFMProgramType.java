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

package com.android.car.radio.bands;

import android.hardware.radio.ProgramSelector;
import android.hardware.radio.RadioManager.BandDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.broadcastradio.support.platform.ProgramSelectorExt;
import com.android.car.radio.platform.RadioTunerExt;
import com.android.car.radio.platform.RadioTunerExt.TuneCallback;
import com.android.car.radio.util.Log;

import java.util.List;
import java.util.Random;

abstract class AMFMProgramType extends ProgramType {
    private static final String TAG = "BcRadioApp.ProgramType";

    AMFMProgramType(@TypeId int id) {
        super(id);
    }

    protected abstract int getLeadingDigitsFactor();

    private List<BandDescriptor> getBands(@NonNull RegionConfig config) {
        return (this == ProgramType.AM) ? config.getAmConfig() : config.getFmConfig();
    }

    @Override
    public void tuneToDefault(@NonNull RadioTunerExt tuner, @NonNull RegionConfig config,
            @Nullable TuneCallback result) {
        List<BandDescriptor> bands = getBands(config);
        if (bands.size() == 0) {
            Log.e(TAG, "No " + getEnglishName() + " bands provided by the hardware");
            return;
        }

        /* Select random initial frequency to give some fairness in picking the initial station.
         * Please note it does not give uniform fairness for all radio stations (i.e. this
         * algorithm is biased towards stations that have a lot of unused channels before them),
         * but is a fair compromise between complexity and distribution.
         */
        Random rnd = new Random();
        BandDescriptor band = bands.get(rnd.nextInt(bands.size()));
        int freq = rnd.nextInt(band.getUpperLimit() - band.getLowerLimit()) + band.getLowerLimit();
        freq /= band.getSpacing();
        freq *= band.getSpacing();

        // tune to that frequency and seek forward, to find any station
        tuner.tune(ProgramSelectorExt.createAmFmSelector(freq), succeeded -> {
            if (!succeeded) {
                result.onFinished(false);
                return;
            }
            tuner.seek(true, result);
        });
    }

    @Override
    public boolean isComplete(@NonNull RegionConfig config, int leadingDigits) {
        int frequencyKhz = leadingDigits * getLeadingDigitsFactor();

        for (BandDescriptor band : getBands(config)) {
            if (band.getLowerLimit() <= frequencyKhz && frequencyKhz <= band.getUpperLimit()
                    && (frequencyKhz - band.getLowerLimit()) % band.getSpacing() == 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    @NonNull
    public ProgramSelector parseDigits(int leadingDigits) {
        return ProgramSelectorExt.createAmFmSelector(leadingDigits * getLeadingDigitsFactor());
    }

    private static int numLength(int number) {
        if (number == 0) return 0;
        return (int) Math.floor(Math.log10(number)) + 1;
    }

    private static int divCeil(int a, int b) {
        return ((a - 1) / b) + 1;
    }

    @Override
    @NonNull
    public boolean[] getValidAppendices(@NonNull RegionConfig config, int leadingDigits) {
        /* TL;DR: This algorithm iterates through all [3] channels within the range [2] to determine
         * all valid digits on the specified index [1].
         *
         * [1] The index we're checking the digits on is the next position user will add digit to
         * the partial channel number. I.e. if he entered 88, we're looking for valid digits to add
         * at the index 2 (88x). We need to check that for all possible channel lengths (i.e.
         * 88.5/103.3 FM or 153/1503/12000 AM).
         *
         * [2] The range is limited to the actual regional frequency bounds and what the user
         * already entered (i.e. for 8, we're checking 80.0 through 89.9).
         *
         * [3] It's actually optimized: instead of iterating through all channels, we maximize jumps
         * so a single digit to add is checked at most two times. Only the n-th channel has a chance
         * to advance the digit on next position.
         *
         * The complexity of this algorithm is O(n * log m * k), where:
         *  - n is the number of ranges (bands, i.e. AM SW, MW and LW);
         *  - log m is the length of 10-based channel representation;
         *  - k is the alphabet length (digits 0-9).
         *
         *
         * In this algorithm, the channel is represented in three forms:
         *  - raw frequency in KHz (i.e. 88700) - denoted by KHz suffix;
         *  - display form (i.e. 887 for 88.7 MHz) - denoted by Disp suffix;
         *  - channel prefix (i.e. 8, 88 or 887) - leadingDigits variable only.
         *
         * All number lengths are "display length", what means it refers to the length of the
         * display form (i.e. 3 for 88.7 MHz and 4 for 1600 kHz).
         *
         * Current position: the index of digit recently entered by the user.
         * Next position: the digit index we're evaluating if it's valid to append.
         */
        boolean[] digits = new boolean[10];
        // the factor that converts display form to raw frequency (i.e. x100 for FM, x1 for AM).
        int displayFactor = getLeadingDigitsFactor();
        int curLenDisp = numLength(leadingDigits);
        int[] pow10 = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000,
                10_000_000, 100_000_000, 1_000_000_000};

        for (BandDescriptor band : getBands(config)) {
            int lowerLimitKHz = band.getLowerLimit();
            int upperLimitKHz = band.getUpperLimit();
            int spacingKHz = band.getSpacing();
            int minLenDisp = numLength(lowerLimitKHz / displayFactor);  // display length
            int maxLenDisp = numLength(upperLimitKHz / displayFactor);

            // let's try making up channels exactly of length expLenDisp
            for (int expLenDisp = Math.max(curLenDisp + 1, minLenDisp);
                    expLenDisp <= maxLenDisp; expLenDisp++) {
                // 1 shifted to the current digit position (recently added by the user)
                int curPosFactor = pow10[expLenDisp - curLenDisp];
                // 1 shifted to the next digit position (evaluated whether is valid to append)
                int nextPosFactor = pow10[expLenDisp - curLenDisp - 1];

                // Let's figure out the spacing jump: see [3].
                // first of all, set the jump to the digit we want to advance
                int spacingJumpKHz = nextPosFactor * displayFactor;
                // align to the spacing (so the jump will not advance next digit more than by 1)
                spacingJumpKHz = (spacingJumpKHz / spacingKHz) * spacingKHz;
                // if the jump is less than spacing, make it even
                spacingJumpKHz = Math.max(spacingJumpKHz, spacingKHz);

                // let's figure out the range to scan (iterate)
                // start with the number already entered (i.e. 87 -> 87.0; 8 -> 80.0)
                int scanStartKHz = leadingDigits * curPosFactor * displayFactor;
                // end with the already entered number as well (i.e. 87 -> 87.999; 8 -> 89.999)
                int scanEndKHz = (leadingDigits + 1) * curPosFactor * displayFactor - 1;
                // filter out channels with leading zeros (087.9, if we expect 1xx.x)
                scanStartKHz = Math.max(scanStartKHz, nextPosFactor * displayFactor);
                // align to the first valid channel
                int scanStartShiftKHz = Math.max(0, scanStartKHz - lowerLimitKHz);
                scanStartShiftKHz = divCeil(scanStartShiftKHz, spacingKHz) * spacingKHz;
                scanStartKHz = lowerLimitKHz + scanStartShiftKHz;
                // check the bounds (start was already checked in scanStartShiftKhz definition)
                scanEndKHz = Math.min(scanEndKHz, upperLimitKHz);

                for (int channelKHz = scanStartKHz; channelKHz <= scanEndKHz;
                        channelKHz += spacingJumpKHz) {
                    int channelDisp = channelKHz / displayFactor;
                    // pick the digit from the next index
                    int nextDigit = (channelDisp % curPosFactor) / nextPosFactor;

                    digits[nextDigit] = true;
                }
            }
        }

        return digits;
    }
}
