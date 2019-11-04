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

import android.content.Context;
import android.os.Looper;

import com.android.car.CarServiceBase;
import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;
import java.util.List;

/**
 * Main service container for car Garage Mode.
 * Garage Mode enables idle time in cars.
 */
public class GarageModeService implements CarServiceBase {
    private static final Logger LOG = new Logger("Service");

    private final Context mContext;
    private final Controller mController;

    public GarageModeService(Context context) {
        this(context, null);
    }

    @VisibleForTesting
    protected GarageModeService(Context context, Controller controller) {
        mContext = context;
        mController = (controller != null ? controller
                : new Controller(context, Looper.myLooper()));
    }

    /**
     * Initializes GarageMode
     */
    @Override
    public void init() {
        mController.init();
    }

    /**
     * Cleans up GarageMode processes
     */
    @Override
    public void release() {
        mController.release();
    }

    /**
     * Dumps useful information about GarageMode
     * @param writer Where to dump the information
     */
    @Override
    public void dump(PrintWriter writer) {
        boolean isActive = mController.isGarageModeActive();
        writer.println("GarageModeInProgress " + isActive);
        List<String> jobs = mController.pendingGarageModeJobs();
        if (isActive) {
            writer.println("GarageMode is currently waiting for " + jobs.size() + " jobs:");
        } else {
            writer.println("GarageMode was last waiting for " + jobs.size() + " jobs:");
        }
        // Dump the names of the jobs that GM is/was waiting for
        int jobNumber = 1;
        for (String job : jobs) {
            writer.println("   " + jobNumber + ": " + job);
            jobNumber++;
        }
    }

    /**
     * @return whether GarageMode is in progress. Used by {@link com.android.car.ICarImpl}.
     */
    public boolean isGarageModeActive() {
        return mController.isGarageModeActive();
    }

    /**
     * Forces GarageMode to start. Used by {@link com.android.car.ICarImpl}.
     */
    public void forceStartGarageMode() {
        mController.initiateGarageMode(null);
    }

    /**
     * Stops and resets the GarageMode. Used by {@link com.android.car.ICarImpl}.
     */
    public void stopAndResetGarageMode() {
        mController.resetGarageMode();
    }
}
