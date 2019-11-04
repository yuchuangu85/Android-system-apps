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

import com.android.tv.perf.stub.StubPerformanceMonitorManager;
import javax.inject.Inject;

public final class PerformanceMonitorManagerFactory {
    private static final PerformanceMonitorManagerFactory INSTANCE =
            new PerformanceMonitorManagerFactory();

    @Inject
    public PerformanceMonitorManagerFactory() {}

    public static PerformanceMonitorManager create() {
        return INSTANCE.get();
    }

    public PerformanceMonitorManager get() {
        return new StubPerformanceMonitorManager();
    }
}
