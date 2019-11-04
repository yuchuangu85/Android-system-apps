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
 * limitations under the License.
 */
package com.google.android.car.bugreport;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;

/** Executes a {@link SimpleUploaderAsyncTask}. */
public class UploadJob extends JobService {
    private static final String TAG = UploadJob.class.getSimpleName();

    private SimpleUploaderAsyncTask mUploader;

    @Override
    public boolean onStartJob(final JobParameters jobParameters) {
        Log.v(TAG, "Starting upload job");
        mUploader = new SimpleUploaderAsyncTask(
                this, reschedule -> jobFinished(jobParameters, reschedule));
        mUploader.execute();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        mUploader.cancel(true);
        return false;
    }
}
