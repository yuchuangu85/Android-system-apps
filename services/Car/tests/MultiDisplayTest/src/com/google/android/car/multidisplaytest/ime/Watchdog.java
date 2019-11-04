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
package com.google.android.car.multidisplaytest.ime;

import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * Copied from GarageModeTestApp
 * Use Handler to send and process messages in a queue
 * Potentially use for multi-thread
 */
public final class Watchdog {
    private static final String TAG = Watchdog.class.getSimpleName();
    // wait before trying to get new message from the queue and post it
    private static final Integer DELAY = 500; // in millisecond
    private static final Integer MAXQSIZE = 10000;

    private final TextView mView;
    private final ArrayList<String> mEvents;

    private Handler mWatchdogHandler;
    private Runnable mRefreshLoop;

    Watchdog(TextView view) {
        mView = view;
        mEvents = new ArrayList<>();
    }

    public void logEvent(String s) {
        Date date = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("[yyyy-MM-dd hh:mm:ss]");
        mEvents.add(0, dateFormat.format(date) + " " + s);

        if (mEvents.size() > MAXQSIZE) {
            mEvents.remove(mEvents.size() - 1);
        }
    }

    public synchronized void refresh() {
        mView.setText(String.join("\n", mEvents));
    }

    public void start() {
        Log.d(TAG, "Starting Watchdog");
        mEvents.clear();
        mWatchdogHandler = new Handler();
        mRefreshLoop = () -> {
            refresh();
            mWatchdogHandler.postDelayed(mRefreshLoop, DELAY);
        };
        mWatchdogHandler.postDelayed(mRefreshLoop, DELAY);
    }

    public void stop() {
        Log.d(TAG, "Stopping Watchdog");
        mWatchdogHandler.removeCallbacks(mRefreshLoop);
        mWatchdogHandler = null;
        mRefreshLoop = null;
    }
}
