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

import android.app.Activity;
import android.app.Application;

/**
 * Measures App startup. This interface is lightweight to help measure both cold and warm startup
 * latency. Implementations must not throw any Exception.
 */
public interface StartupMeasure {

    /** To be be placed as the first static block in the app's Application class. */
    void onAppClassLoaded();

    /**
     * To be placed in your {@link Application#onCreate} to let Performance Monitoring know when
     * this happen.
     */
    void onAppCreate(Application application);

    /**
     * To be placed in an initialization block of your {@link Activity} to let Performance
     * Monitoring know when this activity is instantiated. Please note that this initialization
     * block should be before other initialization blocks (if any) in your activity class.
     */
    void onActivityInit();
}
