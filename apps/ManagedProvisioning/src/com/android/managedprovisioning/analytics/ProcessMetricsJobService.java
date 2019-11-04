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
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.AsyncTask;
import android.os.PersistableBundle;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.DevicePolicyProtos.DevicePolicyEvent;
import com.android.managedprovisioning.common.ProvisionLogger;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A {@link JobService} that reads the logs from the {@link InputStream} written to by
 * {@link DeferredMetricsWriter} and writes them using another {@link MetricsWriter}.
 *
 * @see DeferredMetricsWriter
 */
public class ProcessMetricsJobService extends JobService {

    static String EXTRA_FILE_PATH = "extra_file_path";

    private final MetricsWriter mMetricsWriter;

    @VisibleForTesting
    ProcessMetricsJobService(MetricsWriter metricsWriter) {
        mMetricsWriter = metricsWriter;
    }

    public ProcessMetricsJobService() {
        this(new InstantMetricsWriter());
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        final PersistableBundle extras = params.getExtras();
        if (extras == null || !extras.containsKey(EXTRA_FILE_PATH)) {
            return false;
        }
        final File metrics = new File(extras.getString(EXTRA_FILE_PATH));
        if (!metrics.exists()) {
            return false;
        }
        executeReadDeferredMetrics(params, metrics);
        return true;
    }

    @VisibleForTesting
    void executeReadDeferredMetrics(JobParameters params,
            File metricsFile) {
        new ReadDeferredMetricsAsyncTask(params, metricsFile, mMetricsWriter).execute();
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    /**
     * An {@link AsyncTask} which reads the logs from the {@link File} specified in the constructor
     * and writes them to the specified {@link MetricsWriter}.
     *
     * <p>The {@link File} will be deleted after they are written to the {@link MetricsWriter}.
     */
    private class ReadDeferredMetricsAsyncTask extends AsyncTask<Void, Void, Void> {
        private static final int METRICS_INTERVAL_MILLIS = 10;
        private final MetricsWriter mMetricsWriter;
        private final File mFile;
        private final JobParameters mJobParameters;

        ReadDeferredMetricsAsyncTask(JobParameters params,
                File file,
                MetricsWriter metricsWriter) {
            mFile = checkNotNull(file);
            mMetricsWriter = metricsWriter;
            mJobParameters = params;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            try (InputStream inputStream =  new FileInputStream(mFile)) {
                DevicePolicyEvent event;
                while ((event = DevicePolicyEvent.parseDelimitedFrom(inputStream)) != null) {
                    delayProcessMetric();
                    mMetricsWriter.write(devicePolicyEventToLogger(event));
                }
            } catch (IOException e) {
                ProvisionLogger.loge(
                        "Could not parse DevicePolicyEvent while reading from stream.", e);
            } finally {
                mFile.delete();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            jobFinished(mJobParameters, false);
        }

        /**
         * Waits for {@link #METRICS_INTERVAL_MILLIS}.
         * <p>statsd cannot handle too many metrics at once, so we must wait between each
         * {@link MetricsWriter#write(DevicePolicyEventLogger...)} call.
         */
        private void delayProcessMetric() {
            try {
                Thread.sleep(METRICS_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                ProvisionLogger.loge(
                        "Thread interrupted while waiting to log metric.", e);
            }
        }

        private DevicePolicyEventLogger devicePolicyEventToLogger(DevicePolicyEvent event) {
            final DevicePolicyEventLogger eventLogger = DevicePolicyEventLogger
                    .createEvent(event.getEventId())
                    .setAdmin(event.getAdminPackageName())
                    .setInt(event.getIntegerValue())
                    .setBoolean(event.getBooleanValue())
                    .setTimePeriod(event.getTimePeriodMillis());
            if (event.getStringListValueCount() > 0) {
                eventLogger.setStrings(event.getStringListValueList().toArray(new String[0]));
            }
            return eventLogger;
        }
    }
}
