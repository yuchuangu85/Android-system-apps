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

package com.android.tv.perf;

import android.app.Application;

/** Manages the initialization of Performance Monitoring. */
public interface PerformanceMonitorManager {

    /**
     * Initializes the {@link com.android.tv.perf.PerformanceMonitor}.
     *
     * <p>This should only be called once.
     */
    PerformanceMonitor initialize(Application app);

    /**
     * Returns a lightweight object to help measure both cold and warm startup latency.
     *
     * <p>This method is idempotent and lightweight. It can be called multiple times and does not
     * need to be cached.
     */
    StartupMeasure getStartupMeasure();
}
