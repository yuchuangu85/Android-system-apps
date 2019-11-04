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
 * limitations under the License
 */

package com.android.tv.perf;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.support.annotation.StringDef;
import java.lang.annotation.Retention;

/**
 * Constants for performance event names.
 *
 * <p>Only constants are used to insure no PII is sent.

 */
public final class EventNames {

    @Retention(SOURCE)
    @StringDef({
        FETCH_EPG_TASK,
        ON_DEVICE_SEARCH,
        PROGRAM_GUIDE_SHOW,
        PROGRAM_DATA_MANAGER_PROGRAMS_PREFETCH_TASK_DO_IN_BACKGROUND,
        PROGRAM_GUIDE_SHOW_FROM_EMPTY_CACHE,
        PROGRAM_GUIDE_SCROLL_HORIZONTALLY,
        PROGRAM_GUIDE_SCROLL_VERTICALLY,
        MEMORY_ON_PROGRAM_GUIDE_CLOSE
    })
    public @interface EventName {}

    public static final String FETCH_EPG_TASK = "FetchEpgTask";
    /**
     * Event name for query running time of on-device search in {@link
     * com.android.tv.search.LocalSearchProvider}.
     */
    public static final String ON_DEVICE_SEARCH = "OnDeviceSearch";

    public static final String PROGRAM_GUIDE_SHOW = "ProgramGuide.show";
    public static final String PROGRAM_DATA_MANAGER_PROGRAMS_PREFETCH_TASK_DO_IN_BACKGROUND =
            "ProgramDataManager.ProgramsPrefetchTask.doInBackground";
    public static final String PROGRAM_GUIDE_SHOW_FROM_EMPTY_CACHE =
            "ProgramGuide.show.fromEmptyCache";
    public static final String PROGRAM_GUIDE_SCROLL_HORIZONTALLY =
            "ProgramGuide.scroll.horizontally";
    public static final String PROGRAM_GUIDE_SCROLL_VERTICALLY = "ProgramGuide.scroll.vertically";
    public static final String MEMORY_ON_PROGRAM_GUIDE_CLOSE = "ProgramGuide.memory.close";

    private EventNames() {}
}
