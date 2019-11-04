/**
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

package com.android.car.radio.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * {@link android.util.Log} wrapper that checks {@link android.util.Log#isLoggable} result first.
 */
public final class Log {
    public static final int VERBOSE = android.util.Log.VERBOSE;
    public static final int DEBUG = android.util.Log.DEBUG;
    public static final int INFO = android.util.Log.INFO;
    public static final int WARN = android.util.Log.WARN;
    public static final int ERROR = android.util.Log.ERROR;
    public static final int ASSERT = android.util.Log.ASSERT;

    private Log() {}

    /** See {@link android.util.Log#isLoggable}. */
    public static boolean isLoggable(@Nullable String tag, int level) {
        return android.util.Log.isLoggable(tag, level);
    }

    /** See {@link android.util.Log#v}. */
    public static int v(@Nullable String tag, @NonNull String msg) {
        if (!isLoggable(tag, VERBOSE)) return 0;
        return android.util.Log.v(tag, msg);
    }

    /** See {@link android.util.Log#d}. */
    public static int d(@Nullable String tag, @NonNull String msg) {
        if (!isLoggable(tag, DEBUG)) return 0;
        return android.util.Log.d(tag, msg);
    }

    /** See {@link android.util.Log#i}. */
    public static int i(@Nullable String tag, @NonNull String msg) {
        if (!isLoggable(tag, INFO)) return 0;
        return android.util.Log.i(tag, msg);
    }

    /** See {@link android.util.Log#w}. */
    public static int w(@Nullable String tag, @NonNull String msg) {
        if (!isLoggable(tag, WARN)) return 0;
        return android.util.Log.w(tag, msg);
    }

    /** See {@link android.util.Log#e}. */
    public static int e(@Nullable String tag, @NonNull String msg) {
        if (!isLoggable(tag, ERROR)) return 0;
        return android.util.Log.e(tag, msg);
    }

    /** See {@link android.util.Log#e}. */
    public static int e(@Nullable String tag, @Nullable String msg, @Nullable Throwable tr) {
        if (!isLoggable(tag, ERROR)) return 0;
        return android.util.Log.e(tag, msg, tr);
    }
}
