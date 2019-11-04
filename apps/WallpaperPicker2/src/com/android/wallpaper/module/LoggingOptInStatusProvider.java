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
package com.android.wallpaper.module;

/**
 * Provides the status of whether it is OK to opt in this user to logging.
 */
public interface LoggingOptInStatusProvider {

    /**
     * Fetches the current opt-in state of logging and supplies it to the given receiver.
     */
    void fetchOptInValue(OptInValueReceiver receiver);

    /**
     * Interface for receivers of the usage & diagnostics opt-in value.
     */
    interface OptInValueReceiver {
        void onOptInValueReady(boolean optedIn);
    }
}
