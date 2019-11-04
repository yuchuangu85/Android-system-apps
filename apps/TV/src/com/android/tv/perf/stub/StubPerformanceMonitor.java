/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tv.perf.stub;

import android.content.Context;
import com.android.tv.perf.PerformanceMonitor;
import com.android.tv.perf.TimerEvent;

/** Do nothing implementation of {@link PerformanceMonitor}. */
public final class StubPerformanceMonitor implements PerformanceMonitor {

    private static final TimerEvent TIMER_EVENT = new TimerEvent() {};

    @Override
    public void startMemoryMonitor() {}

    @Override
    public void recordMemory(String customEventName) {}

    @Override
    public void startGlobalTimer(String customEventName) {}

    @Override
    public void stopGlobalTimer(String customEventName) {}

    @Override
    public TimerEvent startTimer() {
        return TIMER_EVENT;
    }

    @Override
    public void stopTimer(TimerEvent event, String name) {}

    @Override
    public void startJankRecorder(String eventName) {}

    @Override
    public void stopJankRecorder(String eventName) {}

    @Override
    public boolean startPerformanceMonitorEventDebugActivity(Context context) {
        return false;
    }

    public static TimerEvent startBootstrapTimer() {
        return new TimerEvent() {};
    }
}
