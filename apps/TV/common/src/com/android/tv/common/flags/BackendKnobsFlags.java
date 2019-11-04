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
 * limitations under the License
 */
package com.android.tv.common.flags;

/** Flags for tuning non ui behavior */
public interface BackendKnobsFlags {

    /**
     * Whether or not this feature is compiled into this build.
     *
     * <p>This returns true by default, unless the is_compiled_selector parameter was set during
     * code generation.
     */
    boolean compiled();

    /** Enable fetching only part of the program data. */
    boolean enablePartialProgramFetch();

    /** EPG fetcher interval in hours */
    long epgFetcherIntervalHour();

    /** Target channel count for EPG. It is used to adjust the EPG length */
    long epgTargetChannelCount();

    /** Enables fetching a few hours of programs only when the epg is scrolled to that time. */
    boolean fetchProgramsAsNeeded();

    /** How many hours of programs are loaded in the program guide for during the initial fetch */
    long programGuideInitialFetchHours();

    /** How many hours of programs are loaded in the program guide */
    long programGuideMaxHours();
}
