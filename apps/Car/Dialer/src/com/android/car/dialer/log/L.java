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

package com.android.car.dialer.log;

import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * Util class for logging.
 */
public class L {

    /**
     * Logs verbose level logs if loggable.
     *
     * <p>@see String#format(String, Object...) for formatting log string.
     */
    public static void v(String tag, @NonNull String msg, Object... args) {
        if (Log.isLoggable(tag, Log.VERBOSE) || Build.IS_DEBUGGABLE) {
            Log.v(tag, String.format(msg, args));
        }
    }

    /**
     * Logs debug level logs if loggable.
     *
     * <p>@see String#format(String, Object...) for formatting log string.
     */
    public static void d(String tag, @NonNull String msg, Object... args) {
        if (Log.isLoggable(tag, Log.DEBUG) || Build.IS_DEBUGGABLE) {
            Log.d(tag, String.format(msg, args));
        }
    }

    /**
     * Logs info level logs if loggable.
     *
     * <p>@see String#format(String, Object...) for formatting log string.
     */
    public static void i(String tag, @NonNull String msg, Object... args) {
        if (Log.isLoggable(tag, Log.INFO) || Build.IS_DEBUGGABLE) {
            Log.i(tag, String.format(msg, args));
        }
    }

    /**
     * Logs warning level logs if loggable.
     *
     * <p>@see String#format(String, Object...) for formatting log string.
     */
    public static void w(String tag, @NonNull String msg, Object... args) {
        if (Log.isLoggable(tag, Log.WARN) || Build.IS_DEBUGGABLE) {
            Log.w(tag, String.format(msg, args));
        }
    }

    /**
     * Logs error level logs.
     *
     * <p>@see String#format(String, Object...) for formatting log string.
     */
    public static void e(String tag, @NonNull String msg, Object... args) {
        Log.e(tag, String.format(msg, args));
    }

    /**
     * Logs warning level logs.
     *
     * <p>@see String#format(String, Object...) for formatting log string.
     */
    public static void e(String tag, Exception e, @NonNull String msg, Object... args) {
        Log.e(tag, String.format(msg, args), e);
    }
}
