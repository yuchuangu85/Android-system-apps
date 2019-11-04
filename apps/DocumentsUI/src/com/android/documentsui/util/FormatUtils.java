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

package com.android.documentsui.util;

import android.icu.text.MeasureFormat;
import android.icu.text.MeasureFormat.FormatWidth;
import android.icu.util.Measure;
import android.icu.util.MeasureUnit;
import android.text.format.DateUtils;

import java.util.Locale;

/**
 * A utility class for formating different type of values to strings.
 */
public class FormatUtils {
    private FormatUtils() {}

    /**
     * Returns the given duration in a human-friendly format. For example,
     * "4 minutes" or "1 second". Returns only the largest meaningful unit of time,
     * and the result duration will round to that unit.
     * For example, 500 milliseconds round to 1 second,
     * 90000 milliseconds (90 seconds or 1.5 minutes) round to 2 minutes.
     * The returned unit of time is from seconds up to hours.
     * This founction is copied from {@link DateUtils}
     * @param millis the duration time in milliseconds.
     * @return String of the duration.
     */
    public static String formatDuration(long millis) {
        MeasureFormat formatter = MeasureFormat.getInstance(Locale.getDefault(), FormatWidth.WIDE);
        if (millis >= DateUtils.HOUR_IN_MILLIS) {
            final int hours =
                    (int) ((millis + DateUtils.HOUR_IN_MILLIS / 2) / DateUtils.HOUR_IN_MILLIS);
            return formatter.format(new Measure(hours, MeasureUnit.HOUR));
        } else if (millis >= DateUtils.MINUTE_IN_MILLIS) {
            final int minutes =
                    (int) ((millis + DateUtils.MINUTE_IN_MILLIS / 2) / DateUtils.MINUTE_IN_MILLIS);
            return formatter.format(new Measure(minutes, MeasureUnit.MINUTE));
        } else {
            final int seconds =
                    (int) ((millis + DateUtils.SECOND_IN_MILLIS / 2) / DateUtils.SECOND_IN_MILLIS);
            return formatter.format(new Measure(seconds, MeasureUnit.SECOND));
        }
    }
}
