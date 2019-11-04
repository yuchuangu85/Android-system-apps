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

import com.android.managedprovisioning.DevicePolicyProtos.DevicePolicyEvent;
import com.android.managedprovisioning.common.ProvisionLogger;

import static com.android.internal.util.Preconditions.checkNotNull;
import static com.android.managedprovisioning.analytics.ProcessMetricsJobService.EXTRA_FILE_PATH;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;

import java.io.File;

/**
 * Schedules the reading of the metrics written by {@link DeferredMetricsWriter}.
 */
public class DeferredMetricsReader {

    private static final ComponentName PROCESS_METRICS_SERVICE_COMPONENT = new ComponentName(
            "com.android.managedprovisioning", ProcessMetricsJobService.class.getName());
    private static final int JOB_ID = 1;
    private static final long MINIMUM_LATENCY = 10 * 60 * 1000;
    private final File mFile;

    /**
     * Constructs a new {@link DeferredMetricsReader}.
     *
     * <p>The specified {@link File} is deleted after everything has been read from it.
     */
    public DeferredMetricsReader(File file) {
        mFile = checkNotNull(file);
    }

    public void scheduleDumpMetrics(Context context) {
        final JobInfo jobInfo = new JobInfo.Builder(JOB_ID, PROCESS_METRICS_SERVICE_COMPONENT)
                .setExtras(PersistableBundle.forPair(EXTRA_FILE_PATH, mFile.getAbsolutePath()))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(MINIMUM_LATENCY)
                .setPersisted(true)
                .build();
        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            jobScheduler.schedule(jobInfo);
        } else {
            ProvisionLogger.logv("JobScheduler is null.");
        }
    }
}
