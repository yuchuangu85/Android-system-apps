/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2016 Mopria Alliance, Inc.
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

package com.android.bips;

import android.print.PrintJobId;
import android.print.PrinterId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Manages a job queue, ensuring only one job is printed at a time */
class JobQueue {
    private final List<LocalPrintJob> mJobs = new CopyOnWriteArrayList<>();
    private LocalPrintJob mCurrent;

    /** Queue a print job for printing at the next available opportunity */
    void print(LocalPrintJob job) {
        mJobs.add(job);
        startNextJob();
    }

    /** Cancel any previously queued job for a printer with the supplied ID. */
    void cancel(PrinterId printerId) {
        for (LocalPrintJob job : mJobs) {
            if (printerId.equals(job.getPrintJob().getInfo().getPrinterId())) {
                cancel(job.getPrintJobId());
            }
        }

        if (mCurrent != null && printerId.equals(mCurrent.getPrintJob().getInfo().getPrinterId())) {
            cancel(mCurrent.getPrintJobId());
        }
    }

    /** Restart any blocked job for a printer with this ID. */
    void restart(PrinterId printerId) {
        if (mCurrent != null && printerId.equals(mCurrent.getPrintJob().getInfo().getPrinterId())) {
            mCurrent.restart();
        }
    }

    /** Cancel a previously queued job */
    void cancel(PrintJobId id) {
        // If a job hasn't started, kill it instantly.
        for (LocalPrintJob job : mJobs) {
            if (job.getPrintJobId().equals(id)) {
                mJobs.remove(job);
                job.getPrintJob().cancel();
                return;
            }
        }

        if (mCurrent != null && mCurrent.getPrintJobId().equals(id)) {
            mCurrent.cancel();
        }
    }

    /** Launch the next job if possible */
    private void startNextJob() {
        if (mJobs.isEmpty() || mCurrent != null) {
            return;
        }

        mCurrent = mJobs.remove(0);
        mCurrent.start(job -> {
            mCurrent = null;
            startNextJob();
        });
    }
}
