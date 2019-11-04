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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FormatUtilsTest {
    @Test
    public void testFormatDuration_seconds() {
        assertEquals("0 seconds", FormatUtils.formatDuration(0));
        assertEquals("0 seconds", FormatUtils.formatDuration(1));
        assertEquals("0 seconds", FormatUtils.formatDuration(499));
        assertEquals("1 second", FormatUtils.formatDuration(500));
        assertEquals("1 second", FormatUtils.formatDuration(1000));
        assertEquals("2 seconds", FormatUtils.formatDuration(1500));
    }

    @Test
    public void testFormatDuration_Minutes() {
        assertEquals("59 seconds", FormatUtils.formatDuration(59000));
        assertEquals("60 seconds", FormatUtils.formatDuration(59500));
        assertEquals("1 minute", FormatUtils.formatDuration(60000));
        assertEquals("1 minute", FormatUtils.formatDuration(65000));
        assertEquals("2 minutes", FormatUtils.formatDuration(90000));
        assertEquals("2 minutes", FormatUtils.formatDuration(120000));
    }

    @Test
    public void testFormatDuration_Hours() {
        assertEquals("59 minutes", FormatUtils.formatDuration(3540000));
        assertEquals("1 hour", FormatUtils.formatDuration(3600000));
        assertEquals("1 hour", FormatUtils.formatDuration(3660000));
        assertEquals("2 hours", FormatUtils.formatDuration(5400000));
        assertEquals("48 hours", FormatUtils.formatDuration(172800000));
    }

}
