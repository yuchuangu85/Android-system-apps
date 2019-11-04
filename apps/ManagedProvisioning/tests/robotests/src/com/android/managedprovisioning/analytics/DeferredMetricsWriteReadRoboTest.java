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
 * limitations under the License
 */

package com.android.managedprovisioning.analytics;

import static com.android.managedprovisioning.analytics.AnalyticsRoboTestUtils.assertDevicePolicyEventLoggersEqual;

import android.app.admin.DevicePolicyEventLogger;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Robolectric integration tests for {@link DeferredMetricsWriter} and
 * {@link DeferredMetricsReader}.
 */
@RunWith(RobolectricTestRunner.class)
public class DeferredMetricsWriteReadRoboTest {

    private static final DevicePolicyEventLogger[] EVENTS_TO_WRITE
            = new DevicePolicyEventLogger[] {
                    DevicePolicyEventLogger
                            .createEvent(124)
                            .setAdmin("test2")
                            .setBoolean(true)
                            .setInt(37)
                            .setStrings("one", "two")
                            .setTimePeriod(50231L),
                    DevicePolicyEventLogger.createEvent(125),
                    DevicePolicyEventLogger.createEvent(126)
                            .setAdmin("test3")
                            .setStrings("one", null, null, "three"),
                    DevicePolicyEventLogger.createEvent(127)
                            .setStrings(new String[]{null, null, null})
    };

    @Test
    public void writeRead_deferredMetricsWriter_eventsAreCorrect() {
        final File file = new File("test-file");

        writeMetricsToFile(EVENTS_TO_WRITE, file);
        final DevicePolicyEventLogger[] eventsRead = readMetricsFromFile(file);

        assertDevicePolicyEventLoggersEqual(EVENTS_TO_WRITE, eventsRead);
    }

    private DevicePolicyEventLogger[] readMetricsFromFile(File file) {
        final List<DevicePolicyEventLogger> eventsList = new ArrayList<>();
        final ProcessMetricsJobService processMetricsJobService = new ProcessMetricsJobService(
                loggers -> eventsList.addAll(Arrays.asList(loggers)));
        processMetricsJobService.executeReadDeferredMetrics(/* params */ null, file);
        Robolectric.flushBackgroundThreadScheduler();
        return eventsList.toArray(new DevicePolicyEventLogger[0]);
    }

    private void writeMetricsToFile(
            DevicePolicyEventLogger[] devicePolicyEvent, File file) {
        final DeferredMetricsWriter writer = new DeferredMetricsWriter(file);
        writer.write(devicePolicyEvent);
        Robolectric.flushBackgroundThreadScheduler();
    }
}
