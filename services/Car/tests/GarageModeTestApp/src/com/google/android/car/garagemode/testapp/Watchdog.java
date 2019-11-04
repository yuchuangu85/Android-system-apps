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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;

public class Watchdog {
    private static final Logger LOG = new Logger("Watchdog");

    private static final String PREFS_FILE_NAME = "garage_mode_watchdog";
    private static final String PREFS_EVENTS_LIST = "events_list";
    private static final String PREFS_EVENTS_LIST_SEPARATOR = "\n";
    // TODO(serikb): Convert TextView to ListView with per row coloring
    private TextView mView;
    private LinkedList<String> mEvents;
    private Handler mWatchdogHandler;
    private Runnable mRefreshLoop;
    private SharedPreferences mSharedPrefs;

    public Watchdog(Context context, TextView view) {
        this(
                context,
                view,
                context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE));
    }

    public Watchdog(Context context, TextView view, SharedPreferences prefs) {
        mView = view;
        mSharedPrefs = prefs;
        mEvents = getEventsFromSharedPrefs(prefs);
    }

    public void logEvent(String s) {
        Date date = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("[yyyy-MM-dd hh:mm:ss]");
        mEvents.addFirst(dateFormat.format(date) + " " + s);
        if (mEvents.size() > 10000) {
            mEvents.pollLast();
        }
        saveEventsToSharedPrefs(mSharedPrefs, mEvents);
    }

    public synchronized String getEventsAsText() {
        return String.join("\n", mEvents);
    }

    public synchronized void refresh() {
        mView.setText(getEventsAsText());
    }

    public void start() {
        LOG.d("Starting Watchdog");
        mWatchdogHandler = new Handler();
        mRefreshLoop = () -> {
            refresh();
            mWatchdogHandler.postDelayed(mRefreshLoop, 500);
        };
        mWatchdogHandler.postDelayed(mRefreshLoop, 500);
    }

    public void stop() {
        LOG.d("Stopping Watchdog");
        mWatchdogHandler.removeCallbacks(mRefreshLoop);
        mWatchdogHandler = null;
        mRefreshLoop = null;
    }

    private LinkedList<String> getEventsFromSharedPrefs(SharedPreferences prefs) {
        LinkedList<String> list = new LinkedList<>();
        String file = prefs.getString(PREFS_EVENTS_LIST, "");
        for (String line : file.split(PREFS_EVENTS_LIST_SEPARATOR)) {
            list.add(line);
        }
        return list;
    }

    private void saveEventsToSharedPrefs(SharedPreferences prefs, LinkedList<String> list) {
        String file = "";
        for (String item : list) {
            if (!file.isEmpty()) {
                file += PREFS_EVENTS_LIST_SEPARATOR + item;
            } else {
                file += item;
            }
        }
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREFS_EVENTS_LIST, file);
        editor.commit();
    }
}
