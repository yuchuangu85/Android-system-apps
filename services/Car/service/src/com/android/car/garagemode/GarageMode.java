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

package com.android.car.garagemode;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.job.JobSnapshot;
import android.content.Intent;
import android.os.Handler;
import android.os.UserHandle;
import android.util.ArraySet;

import com.android.car.CarLocalServices;
import com.android.car.CarStatsLog;
import com.android.car.user.CarUserService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

/**
 * Class that interacts with JobScheduler, controls system idleness and monitor jobs which are
 * in GarageMode interest
 */

class GarageMode {
    private static final Logger LOG = new Logger("GarageMode");

    /**
     * When changing this field value, please update
     * {@link com.android.server.job.controllers.idle.CarIdlenessTracker} as well.
     */
    public static final String ACTION_GARAGE_MODE_ON =
            "com.android.server.jobscheduler.GARAGE_MODE_ON";

    /**
     * When changing this field value, please update
     * {@link com.android.server.job.controllers.idle.CarIdlenessTracker} as well.
     */
    public static final String ACTION_GARAGE_MODE_OFF =
            "com.android.server.jobscheduler.GARAGE_MODE_OFF";

    static final long JOB_SNAPSHOT_INITIAL_UPDATE_MS = 10_000; // 10 seconds
    static final long JOB_SNAPSHOT_UPDATE_FREQUENCY_MS = 1_000; // 1 second
    static final long USER_STOP_CHECK_INTERVAL = 10_000; // 10 secs

    private final Controller mController;

    private boolean mGarageModeActive;
    private JobScheduler mJobScheduler;
    private List<String> mPendingJobs = new ArrayList<>();
    private Handler mHandler;
    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            int numberRunning = numberOfJobsRunning();
            if (numberRunning > 0) {
                LOG.d("" + numberRunning + " jobs are still running. Need to wait more ...");
                mHandler.postDelayed(mRunnable, JOB_SNAPSHOT_UPDATE_FREQUENCY_MS);
            } else {
                LOG.d("No jobs are currently running.");
                finish();
            }
        }
    };

    private final Runnable mStopUserCheckRunnable = new Runnable() {
        @Override
        public void run() {
            int userToStop = UserHandle.USER_SYSTEM; // BG user never becomes system user.
            int remainingUsersToStop = 0;
            synchronized (this) {
                remainingUsersToStop = mStartedBackgroundUsers.size();
                if (remainingUsersToStop > 0) {
                    userToStop = mStartedBackgroundUsers.valueAt(0);
                } else {
                    return;
                }
            }
            if (numberOfJobsRunning() == 0) { // all jobs done or stopped.
                // Keep user until job scheduling is stopped. Otherwise, it can crash jobs.
                if (userToStop != UserHandle.USER_SYSTEM) {
                    CarLocalServices.getService(CarUserService.class).stopBackgroundUser(
                            userToStop);
                    LOG.i("Stopping background user:" + userToStop + " remaining users:"
                            + (remainingUsersToStop - 1));
                }
                synchronized (this) {
                    mStartedBackgroundUsers.remove(userToStop);
                    if (mStartedBackgroundUsers.size() == 0) {
                        LOG.i("all background users stopped");
                        return;
                    }
                }
            } else {
                LOG.i("Waiting for jobs to finish, remaining users:" + remainingUsersToStop);
            }
            // Poll again
            mHandler.postDelayed(mStopUserCheckRunnable, USER_STOP_CHECK_INTERVAL);
        }
    };


    private CompletableFuture<Void> mFuture;
    private ArraySet<Integer> mStartedBackgroundUsers = new ArraySet<>();

    GarageMode(Controller controller) {
        mGarageModeActive = false;
        mController = controller;
        mJobScheduler = controller.getJobSchedulerService();
        mHandler = controller.getHandler();
    }

    boolean isGarageModeActive() {
        return mGarageModeActive;
    }

    synchronized List<String> pendingJobs() {
        return mPendingJobs;
    }

    void enterGarageMode(CompletableFuture<Void> future) {
        LOG.d("Entering GarageMode");
        synchronized (this) {
            mGarageModeActive = true;
        }
        updateFuture(future);
        broadcastSignalToJobSchedulerTo(true);
        CarStatsLog.logGarageModeStart();
        startMonitoringThread();
        ArrayList<Integer> startedUsers =
                CarLocalServices.getService(CarUserService.class).startAllBackgroundUsers();
        synchronized (this) {
            mStartedBackgroundUsers.addAll(startedUsers);
        }
    }

    synchronized void cancel() {
        broadcastSignalToJobSchedulerTo(false);
        if (mFuture != null && !mFuture.isDone()) {
            mFuture.cancel(true);
        }
        mFuture = null;
        startBackgroundUserStopping();
    }

    synchronized void finish() {
        broadcastSignalToJobSchedulerTo(false);
        CarStatsLog.logGarageModeStop();
        mController.scheduleNextWakeup();
        synchronized (this) {
            if (mFuture != null && !mFuture.isDone()) {
                mFuture.complete(null);
            }
            mFuture = null;
        }
        startBackgroundUserStopping();
    }

    private void cleanupGarageMode() {
        LOG.d("Cleaning up GarageMode");
        synchronized (this) {
            mGarageModeActive = false;
        }
        stopMonitoringThread();
        mHandler.removeCallbacks(mRunnable);
        startBackgroundUserStopping();
    }

    private void startBackgroundUserStopping() {
        synchronized (this) {
            if (mStartedBackgroundUsers.size() > 0) {
                mHandler.postDelayed(mStopUserCheckRunnable, USER_STOP_CHECK_INTERVAL);
            }
        }
    }

    private void updateFuture(CompletableFuture<Void> future) {
        synchronized (this) {
            mFuture = future;
        }
        if (mFuture != null) {
            mFuture.whenComplete((result, exception) -> {
                if (exception == null) {
                    LOG.d("GarageMode completed normally");
                } else if (exception instanceof CancellationException) {
                    LOG.d("GarageMode was canceled");
                } else {
                    LOG.e("GarageMode ended due to exception: ", exception);
                }
                cleanupGarageMode();
            });
        }
    }

    private void broadcastSignalToJobSchedulerTo(boolean enableGarageMode) {
        Intent i = new Intent();
        if (enableGarageMode) {
            i.setAction(ACTION_GARAGE_MODE_ON);
        } else {
            i.setAction(ACTION_GARAGE_MODE_OFF);
        }
        i.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY | Intent.FLAG_RECEIVER_NO_ABORT);
        mController.sendBroadcast(i);
    }

    private synchronized void startMonitoringThread() {
        mHandler.postDelayed(mRunnable, JOB_SNAPSHOT_INITIAL_UPDATE_MS);
    }

    private synchronized void stopMonitoringThread() {
        mHandler.removeCallbacks(mRunnable);
    }

    private synchronized int numberOfJobsRunning() {
        List<JobInfo> startedJobs = mJobScheduler.getStartedJobs();
        int count = 0;
        List<String> currentPendingJobs = new ArrayList<>();
        for (JobSnapshot snap : mJobScheduler.getAllJobSnapshots()) {
            if (startedJobs.contains(snap.getJobInfo())
                    && snap.getJobInfo().isRequireDeviceIdle()) {
                currentPendingJobs.add(snap.getJobInfo().toString());
                count++;
            }
        }
        if (count > 0) {
            // We have something pending, so update the list.
            // (Otherwise, keep the old list.)
            mPendingJobs = currentPendingJobs;
        }
        return count;
    }
}
