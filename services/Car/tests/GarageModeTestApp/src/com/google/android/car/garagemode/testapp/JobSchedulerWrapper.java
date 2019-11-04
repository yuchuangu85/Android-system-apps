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
package com.google.android.car.garagemode.testapp;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.PersistableBundle;
import android.widget.ListView;
import android.widget.Toast;

import com.android.internal.annotations.GuardedBy;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

class JobSchedulerWrapper {
    private static final Logger LOG = new Logger("JobSchedulerWrapper");
    private static final boolean DEBUG = false;

    private static final String ANDROID_COMPONENT_PREFIX = "android/com.android.";
    private static final String ANDROID_SETTINGS_PREFIX =
            "com.android.settings/com.android.settings.";

    private static final String PREFS_FILE_NAME = "garage_mode_job_scheduler";
    private static final String PREFS_NEXT_JOB_ID = "next_job_id";

    // The job scheduler will kill anything that runs more than 10 minutes.
    // Don't create any job that runs longer than 9 minutes
    private static final int MAX_SECONDS_PER_JOB = 9 * 60; // 9 minutes
    private static final int JOB_OVERLAP_SECONDS = 10;

    @GuardedBy("sExtendedJobInfoMap")
    private static final Map<Integer, ExtendedJobInfo> sExtendedJobInfoMap = new HashMap<>();

    private JobScheduler mJobScheduler;
    private Context mContext;
    private ListView mListView;
    private Handler mHandler;
    private Watchdog mWatchdog;
    private Runnable mRefreshWorker;

    private List<JobInfo> mLastJobsList;
    private List<JobInfo> mNewJobs;
    private List<JobInfo> mCompletedJobs;
    private JobInfoRowArrayAdapter mJobsListAdapter;

    JobSchedulerWrapper(Context context, ListView listView) {
        mContext = context;
        mJobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        mListView = listView;

        mLastJobsList = new LinkedList<>();
        mNewJobs = new LinkedList<>();
        mCompletedJobs = new LinkedList<>();
        mJobsListAdapter = new JobInfoRowArrayAdapter(mContext, mListView.getId(), mLastJobsList);
        mListView.setAdapter(mJobsListAdapter);

        updateJobs();
        if (DEBUG) {
            printJobsOnce(mJobScheduler.getAllPendingJobs());
        }
    }

    public void setWatchdog(Watchdog watchdog) {
        mWatchdog = watchdog;
    }

    public synchronized void refresh() {
        updateJobs();

        reportNewJobs();
        reportCompletedJobs();

        if (mNewJobs.size() > 0 || mCompletedJobs.size() > 0) {
            updateListView();
        }
    }

    public void start() {
        LOG.d("Starting JobSchedulerWrapper");
        mHandler = new Handler();
        mRefreshWorker = () -> {
            refresh();
            mHandler.postDelayed(mRefreshWorker, 1000);
        };
        mHandler.postDelayed(mRefreshWorker, 1000);
    }

    public void stop() {
        LOG.d("Stopping JobSchedulerWrapper");
        mHandler.removeCallbacks(mRefreshWorker);
        mRefreshWorker = null;
        mHandler = null;
        mWatchdog = null;
    }

    public void scheduleAJob(
            int numberOfSeconds,
            int networkType,
            boolean isChargingRequired,
            boolean isIdleRequired) {
        ComponentName jobComponentName = new ComponentName(mContext, DishService.class);
        SharedPreferences prefs = mContext
                .getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE);
        int jobId = prefs.getInt(PREFS_NEXT_JOB_ID, 0);
        PersistableBundle bundle = new PersistableBundle();

        int secondsForThisJob = numberOfSeconds;
        int extraSeconds = 0;
        if (numberOfSeconds > MAX_SECONDS_PER_JOB) {
            // We cannot create a job that runs this long.
            // Create one with (almost) the maximum duration and
            // remember how much more time must be handled.
            secondsForThisJob = MAX_SECONDS_PER_JOB - JOB_OVERLAP_SECONDS;
            extraSeconds = numberOfSeconds - secondsForThisJob;
        }

        bundle.putInt(DishService.EXTRA_DISH_COUNT, secondsForThisJob);

        while (checkIdForExistence(jobId)) {
            jobId++;
        }

        JobInfo jobInfo = new JobInfo.Builder(jobId, jobComponentName)
                .setRequiresCharging(isChargingRequired)
                .setRequiresDeviceIdle(isIdleRequired)
                .setExtras(bundle)
                .setRequiredNetworkType(networkType)
                .build();

        mJobScheduler.schedule(jobInfo);
        Toast.makeText(
                mContext,
                "Scheduled new job with id: " + jobInfo.getId(), Toast.LENGTH_LONG).show();

        LOG.d("Scheduled a job: " + jobInfo);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(PREFS_NEXT_JOB_ID, jobId + 1);
        editor.commit();

        refresh();

        if (extraSeconds > 0) {
            // Remember to schedule another job when this one ends
            synchronized (sExtendedJobInfoMap) {
                sExtendedJobInfoMap.put(jobId,
                                        new ExtendedJobInfo(extraSeconds,
                                                            networkType,
                                                            isChargingRequired,
                                                            isIdleRequired));
            }
            // The new job will be scheduled after we see that 'jobId' has
            // ended. This leaves a sliver of time between the two jobs where
            // Garage Mode might see that no jobs are running. To eliminate
            // this sliver, we schedule a job now that will run in parallel
            // with 'jobId,' but will continue a few seconds longer. This
            // should guarantee that some job is running during the sliver.
            scheduleAJob(MAX_SECONDS_PER_JOB,
                         networkType,
                         isChargingRequired,
                         isIdleRequired);
        }
    }

    private void updateListView() {
        int index = mListView.getFirstVisiblePosition();
        mJobsListAdapter.notifyDataSetChanged();
        mListView.smoothScrollToPosition(index);
    }

    private boolean checkIdForExistence(int jobId) {
        for (JobInfo job : mJobScheduler.getAllPendingJobs()) {
            if (job.getId() == jobId) {
                return true;
            }
        }
        return false;
    }

    private void printJobsOnce(List<JobInfo> list) {
        LOG.d("=========================================================");
        for (JobInfo job : list) {
            LOG.d("Job(" + job.getId() + ") will run " + job.getService());
        }
    }

    private void reportNewJobs() {
        for (JobInfo job : mNewJobs) {
            if (mWatchdog != null) {
                mWatchdog.logEvent("New job with id(" + job.getId() + ") has been scheduled");
            }
        }
    }

    private void reportCompletedJobs() {
        for (JobInfo job : mCompletedJobs) {
            if (mWatchdog != null) {
                mWatchdog.logEvent("Job with id(" + job.getId() + ") has been completed.");
            }
        }
    }

    private synchronized void updateJobs() {
        List<JobInfo> currentJobs = mJobScheduler.getAllPendingJobs();

        if (DEBUG) {
            printJobsOnce(currentJobs);
            printJobsOnce(mLastJobsList);
        }

        removeSystemJobsFromList(currentJobs);

        mNewJobs = newJobsSince(mLastJobsList, currentJobs);
        mCompletedJobs = completedJobsSince(mLastJobsList, currentJobs);

        for (JobInfo job : mNewJobs) {
            mLastJobsList.add(job);
        }

        for (JobInfo job : mCompletedJobs) {
            mLastJobsList.remove(job);
        }
    }

    private synchronized List<JobInfo> newJobsSince(List<JobInfo> oldList, List<JobInfo> newList) {
        return findDiffBetween(newList, oldList);
    }

    private synchronized List<JobInfo> completedJobsSince(
            List<JobInfo> oldList, List<JobInfo> newList) {

        List<JobInfo> completedJobsList = findDiffBetween(oldList, newList);

        // Before returning this list, check if we need to schedule an
        // extension for any of the newly-completed jobs.
        for (JobInfo completedJobInfo : completedJobsList) {
            ExtendedJobInfo extensionInfo;
            synchronized (sExtendedJobInfoMap) {
                extensionInfo = sExtendedJobInfoMap.remove(completedJobInfo.getId());
            }
            if (extensionInfo != null) {
                scheduleAJob(extensionInfo.jobLengthSeconds,
                             extensionInfo.networkType,
                             extensionInfo.chargingRequired,
                             extensionInfo.idleRequired);
            }
        }
        return completedJobsList;
    }

    private synchronized List<JobInfo> findDiffBetween(
            List<JobInfo> fromList, List<JobInfo> toList) {
        List<JobInfo> diffList = new LinkedList<>();
        for (JobInfo fromJob : fromList) {
            if (!toList.contains(fromJob)) {
                diffList.add(fromJob);
            }
        }
        return diffList;
    }

    private synchronized void removeSystemJobsFromList(List<JobInfo> list) {
        List<JobInfo> jobsToRemove = new LinkedList<>();
        for (JobInfo job : list) {
            if (isSystemService(job)) {
                jobsToRemove.add(job);
            }
        }
        for (JobInfo job : jobsToRemove) {
            list.remove(job);
        }
    }

    private boolean isSystemService(JobInfo job) {
        if (job.getService().toString().contains(ANDROID_COMPONENT_PREFIX)) {
            return true;
        }
        if (job.getService().toString().contains(ANDROID_SETTINGS_PREFIX)) {
            return true;
        }
        return false;
    }

    private class ExtendedJobInfo {
        public final int     jobLengthSeconds;
        public final int     networkType;
        public final boolean chargingRequired;
        public final boolean idleRequired;

        ExtendedJobInfo(int     jobLengthSeconds,
                        int     networkType,
                        boolean chargingRequired,
                        boolean idleRequired) {
            this.jobLengthSeconds = jobLengthSeconds;
            this.networkType = networkType;
            this.chargingRequired = chargingRequired;
            this.idleRequired = idleRequired;
        }
    }
}
