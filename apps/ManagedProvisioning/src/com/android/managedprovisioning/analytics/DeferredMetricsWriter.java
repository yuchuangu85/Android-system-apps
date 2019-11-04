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

import static com.android.internal.util.Preconditions.checkNotNull;

import android.app.admin.DevicePolicyEventLogger;
import android.os.AsyncTask;

import com.android.internal.util.Preconditions;
import com.android.managedprovisioning.DevicePolicyProtos.DevicePolicyEvent;
import com.android.managedprovisioning.common.ProvisionLogger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A {@link MetricsWriter} which writes the {@link DevicePolicyEventLogger} events to a file.
 *
 * <p>To read the written logs, use {@link DeferredMetricsReader}.
 *
 * @see DeferredMetricsReader
 */
public class DeferredMetricsWriter implements MetricsWriter {

    private static final Object sLock = new Object();

    private final File mFile;

    DeferredMetricsWriter(File file) {
        mFile = checkNotNull(file);
    }

    @Override
    public void write(DevicePolicyEventLogger... loggers) {
        try {
            final OutputStream outputStream = new FileOutputStream(mFile, true);
            new WriteDeferredMetricsAsyncTask(outputStream).execute(loggers);
        } catch (FileNotFoundException e) {
            // This is an acceptable scenario as we have a check in write(...).
            ProvisionLogger.loge("Could not find file passed to DeferredMetricsWriter.", e);
        }
    }

    private static class WriteDeferredMetricsAsyncTask
            extends AsyncTask<DevicePolicyEventLogger, Void, Void> {

        private final OutputStream mOutputStream;

        WriteDeferredMetricsAsyncTask(OutputStream file) {
            mOutputStream = file;
        }

        @Override
        protected Void doInBackground(DevicePolicyEventLogger... devicePolicyEventLoggers) {
            final List<DevicePolicyEvent> events = Arrays.stream(devicePolicyEventLoggers)
                    .map(WriteDeferredMetricsAsyncTask::eventLoggerToDevicePolicyEvent)
                    .collect(Collectors.toList());
            synchronized (sLock) {
                writeDevicePolicyEventsToStream(events, mOutputStream);
            }
            return null;
        }

        private static void writeDevicePolicyEventsToStream(
                List<DevicePolicyEvent> events, OutputStream outputStream) {
            for (DevicePolicyEvent event : events) {
                try {
                    event.writeDelimitedTo(outputStream);
                } catch (IOException e) {
                    ProvisionLogger.loge("Failed to write DevicePolicyEvent to OutputStream.", e);
                }
            }
            try {
                outputStream.flush();
            } catch (IOException e) {
                ProvisionLogger.loge("Failed to flush OutputStream.", e);
            } finally {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    ProvisionLogger.loge("Failed to close OutputStream.", e);
                }
            }
        }

        private static DevicePolicyEvent eventLoggerToDevicePolicyEvent(
                DevicePolicyEventLogger eventLogger) {
            final DevicePolicyEvent.Builder builder = DevicePolicyEvent.newBuilder()
                    .setEventId(eventLogger.getEventId())
                    .setIntegerValue(eventLogger.getInt())
                    .setBooleanValue(eventLogger.getBoolean())
                    .setTimePeriodMillis(eventLogger.getTimePeriod());
            if (eventLogger.getAdminPackageName() != null) {
                builder.setAdminPackageName(eventLogger.getAdminPackageName());
            }
            final String[] stringValues = eventLogger.getStringArray();
            if (stringValues != null) {
                Arrays.stream(stringValues)
                        .filter(Objects::nonNull)
                        .forEach(stringValue -> builder.addStringListValue(stringValue));
            }
            return builder.build();
        }
    }
}
