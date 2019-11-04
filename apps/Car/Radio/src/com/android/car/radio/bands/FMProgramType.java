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

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.android.car.radio.R;

class FMProgramType extends AMFMProgramType {
    FMProgramType(@TypeId int id) {
        super(id);
    }

    @Override
    @NonNull
    public String getEnglishName() {
        return "FM";
    }

    @Override
    @StringRes
    public int getLocalizedName() {
        return R.string.programtype_fm_text;
    }

    @Override
    protected int getLeadingDigitsFactor() {
        return 100;
    }

    @Override
    public String format(int leadingDigits) {
        /* Instead of writing general algorithm, let's exploit properties of FM ranges across all
         * regions:
         *  - if the leading digit is 1, the channel is always in 1XX.X format;
         *  - if the leading digit is anything else than 1, the channel is always in XX.X format;
         */
        if (leadingDigits < 0) throw new IllegalArgumentException();
        if (leadingDigits == 0) return "";

        String channel = Integer.toString(leadingDigits);
        int integralPartLength = (channel.charAt(0) == '1') ? 3 : 2;

        if (channel.length() < integralPartLength) return channel;
        return channel.substring(0, integralPartLength) + "."
                + channel.substring(integralPartLength, channel.length());
    }
}
