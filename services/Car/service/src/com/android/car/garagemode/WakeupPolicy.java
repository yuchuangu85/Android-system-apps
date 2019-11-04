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

import com.android.car.R;
import com.android.internal.annotations.VisibleForTesting;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Default garage mode policy.
 *
 * The first wake up time is set to be 1am the next day. And it keeps waking up every day for a
 * week. After that, wake up every 7 days for a month, and wake up every 30 days thereafter.
 */
class WakeupPolicy {
    private static final Logger LOG = new Logger("WakeupPolicy");
    private static final Map<Character, Integer> TIME_UNITS_LOOKUP_SEC;
    static {
        TIME_UNITS_LOOKUP_SEC = new HashMap<>();
        TIME_UNITS_LOOKUP_SEC.put('m', 60);
        TIME_UNITS_LOOKUP_SEC.put('h', 3600);
        TIME_UNITS_LOOKUP_SEC.put('d', 86400);
    }
    private LinkedList<WakeupInterval> mWakeupIntervals;
    @VisibleForTesting protected int mIndex;

    WakeupPolicy(String[] policy) {
        mWakeupIntervals = parsePolicy(policy);
        mIndex = 0;
    }

    /**
     * Initializes Policy from config_garageModeCadence resource array.
     * @param context to access resources
     * @return Policy instance, created from values in resources
     */
    public static WakeupPolicy initFromResources(Context context) {
        LOG.d("Initiating WakupPolicy from resources ...");
        return new WakeupPolicy(
                context.getResources().getStringArray(R.array.config_garageModeCadence));
    }

    /**
     * Returns the interval in seconds, which defines next wake up time.
     * @return the interval in seconds
     */
    public int getNextWakeUpInterval() {
        if (mWakeupIntervals.size() == 0) {
            LOG.e("No wake up policy configuration was loaded.");
            return 0;
        }

        int index = mIndex;
        for (WakeupInterval wakeupTime : mWakeupIntervals) {
            if (index <= wakeupTime.getNumAttempts()) {
                return wakeupTime.getWakeupInterval();
            }
            index -= wakeupTime.getNumAttempts();
        }
        LOG.w("No more garage mode wake ups scheduled; been sleeping too long.");
        return 0;
    }

    protected int getWakupIntervalsAmount() {
        return mWakeupIntervals.size();
    }

    private LinkedList<WakeupInterval> parsePolicy(String[] policy) {
        LinkedList<WakeupInterval> intervals = new LinkedList<>();
        if (policy == null || policy.length == 0) {
            LOG.e("Trying to parse empty policies!");
            return intervals;
        }

        for (String rule : policy) {
            WakeupInterval interval = parseRule(rule);
            if (interval == null) {
                LOG.e("Invalid Policy! This rule has bad format: " + rule);
                return new LinkedList<>();
            }
            intervals.add(interval);
        }
        return intervals;
    }

    private WakeupInterval parseRule(String rule) {
        String[] str = rule.split(",");

        if (str.length != 2) {
            LOG.e("Policy has bad format: " + rule);
            return null;
        }

        String intervalStr = str[0];
        String timesStr = str[1];

        if (intervalStr.isEmpty() || timesStr.isEmpty()) {
            LOG.e("One of the values is empty. Please check format: " + rule);
            return null;
        }

        char unit = intervalStr.charAt(intervalStr.length() - 1);

        // Removing last letter extension from string
        intervalStr = intervalStr.substring(0, intervalStr.length() - 1);

        int interval, times;
        try {
            interval = Integer.parseInt(intervalStr);
            times = Integer.parseInt(timesStr);
        } catch (NumberFormatException ex)  {
            LOG.d("Invalid input Rule for interval " + rule);
            return null;
        }

        if (!TIME_UNITS_LOOKUP_SEC.containsKey(unit)) {
            LOG.e("Time units map does not contain extension " + unit);
            return null;
        }

        if (interval <= 0) {
            LOG.e("Wake up policy time must be > 0!" + interval);
            return null;
        }

        if (times <= 0) {
            LOG.e("Wake up attempts in policy must be > 0!" + times);
            return null;
        }

        interval *= TIME_UNITS_LOOKUP_SEC.get(unit);

        return new WakeupInterval(interval, times);
    }

    public void incrementCounter() {
        mIndex++;
    }

    public void resetCounter() {
        mIndex = 0;
    }

    /**
     * Defines wake up interval which then will be used by
     * {@link com.android.car.garagemode.GarageModeService} to schedule next wake up time in
     * {@link android.car.hardware.power.CarPowerManager}
     */
    private class WakeupInterval {
        private int mWakeupInterval;
        private int mNumAttempts;

        WakeupInterval(int wakeupTime, int numAttempts) {
            mWakeupInterval = wakeupTime;
            mNumAttempts = numAttempts;
        }

        /**
         * Returns interval between now and next wakeup.
         * @return interval in seconds
         */
        public int getWakeupInterval() {
            return mWakeupInterval;
        }

        /**
         * Returns amount of attempts to wake up with mWakeupInterval
         * @return amount of attempts
         */
        public int getNumAttempts() {
            return mNumAttempts;
        }
    }

}
