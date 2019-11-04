/*
 * Copyright 2018 The Android Open Source Project
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

package com.android.pump.util;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import com.android.pump.BuildConfig;

import java.util.Locale;
import java.util.regex.Pattern;

@AnyThread
public final class Clog {
    private static final boolean COLORIZE = BuildConfig.DEBUG;

    public static final int VERBOSE = android.util.Log.VERBOSE;
    public static final int DEBUG = android.util.Log.DEBUG;
    public static final int INFO = android.util.Log.INFO;
    public static final int WARN = android.util.Log.WARN;
    public static final int ERROR = android.util.Log.ERROR;
    public static final int ASSERT = android.util.Log.ASSERT;

    private static final int COLOR_BLACK = 30;
    private static final int COLOR_RED = 31;
    private static final int COLOR_GREEN = 32;
    private static final int COLOR_YELLOW = 33;
    private static final int COLOR_BLUE = 34;
    private static final int COLOR_MAGENTA = 35;
    private static final int COLOR_CYAN = 36;
    //private static final int COLOR_WHITE = 37;

    private static final int MAX_TAG_LENGTH = 23;
    private static final int MAX_LINE_LENGTH = 1024;

    private static final Pattern LINE_BREAKER = Pattern.compile("\\r?\\n");

    private Clog() { }

    public static @NonNull String tag(@NonNull Class<?> clazz) {
        String tag = clazz.getSimpleName();
        return tag.substring(0, Math.min(tag.length(), MAX_TAG_LENGTH));
    }

    public static int v(@NonNull String tag, @NonNull String msg) {
        return println(VERBOSE, tag, msg);
    }

    public static int v(@NonNull String tag, @NonNull String msg, @NonNull Throwable tr) {
        return println(VERBOSE, tag, msg + '\n' + getStackTraceString(tr));
    }

    public static int d(@NonNull String tag, @NonNull String msg) {
        return println(DEBUG, tag, msg);
    }

    public static int d(@NonNull String tag, @NonNull String msg, @NonNull Throwable tr) {
        return println(DEBUG, tag, msg + '\n' + getStackTraceString(tr));
    }

    public static int i(@NonNull String tag, @NonNull String msg) {
        return println(INFO, tag, msg);
    }

    public static int i(@NonNull String tag, @NonNull String msg, @NonNull Throwable tr) {
        return println(INFO, tag, msg + '\n' + getStackTraceString(tr));
    }

    public static int w(@NonNull String tag, @NonNull String msg) {
        return println(WARN, tag, msg);
    }

    public static int w(@NonNull String tag, @NonNull String msg, @NonNull Throwable tr) {
        return println(WARN, tag, msg + '\n' + getStackTraceString(tr));
    }

    public static boolean isLoggable(@NonNull String tag, int level) {
        return android.util.Log.isLoggable(tag, level);
    }

    public static int w(@NonNull String tag, @NonNull Throwable tr) {
        return println(WARN, tag, getStackTraceString(tr));
    }

    public static int e(@NonNull String tag, @NonNull String msg) {
        return println(ERROR, tag, msg);
    }

    public static int e(@NonNull String tag, @NonNull String msg, @NonNull Throwable tr) {
        return println(ERROR, tag, msg + '\n' + getStackTraceString(tr));
    }

    public static int wtf(@NonNull String tag, @NonNull String msg) {
        return android.util.Log.wtf(tag, msg);
    }

    public static int wtf(@NonNull String tag, @NonNull Throwable tr) {
        return android.util.Log.wtf(tag, tr);
    }

    public static int wtf(@NonNull String tag, @NonNull String msg, @NonNull Throwable tr) {
        return android.util.Log.wtf(tag, msg, tr);
    }

    public static @NonNull String getStackTraceString(@NonNull Throwable tr) {
        return android.util.Log.getStackTraceString(tr);
    }

    public static int println(int priority, @NonNull String tag, @NonNull String msg) {
        tag = String.valueOf(tag);
        msg = String.valueOf(msg);

        int color;
        switch (priority) {
            case VERBOSE:
                color = COLOR_CYAN;
                break;
            case DEBUG:
                color = COLOR_BLUE;
                break;
            case INFO:
                color = COLOR_GREEN;
                break;
            case WARN:
                color = COLOR_YELLOW;
                break;
            case ERROR:
                color = COLOR_RED;
                break;
            case ASSERT:
                color = COLOR_MAGENTA;
                break;
            default:
                color = COLOR_BLACK;
                break;
        }

        int result = 0;
        for (String line : LINE_BREAKER.split(msg)) {
            int length = line.length();
            for (int start = 0; start < length; start += MAX_LINE_LENGTH) {
                String part = line.substring(start, Math.min(start + MAX_LINE_LENGTH, length));
                result += android.util.Log.println(priority, tag, colorize(part, color));
            }
        }
        return result;
    }

    private static String colorize(String msg, int color) {
        if (COLORIZE) {
            return String.format(Locale.ROOT, "\033[%2$dm%1$s\033[0m", msg, color);
        }
        return msg;
    }
}
