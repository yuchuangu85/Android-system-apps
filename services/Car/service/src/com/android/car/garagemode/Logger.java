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

package com.android.car.garagemode;

import android.util.Log;

class Logger {
    private final String mTag;
    private final String mPrefix;

    Logger(String prefix) {
        mTag = "GarageMode";
        mPrefix = prefix;
    }

    /** Passing message further to Log.v() */
    public void v(String msg) {
        Log.v(mTag, buildMessage(msg));
    }

    /** Passing message further to Log.v() */
    public void v(String msg, Exception ex) {
        Log.v(mTag, buildMessage(msg), ex);
    }

    /** Passing message further to Log.i() */
    public void i(String msg) {
        Log.i(mTag, buildMessage(msg));
    }

    /** Passing message further to Log.i() */
    public void i(String msg, Exception ex) {
        Log.i(mTag, buildMessage(msg), ex);
    }

    /** Passing message further to Log.d() */
    public void d(String msg) {
        Log.d(mTag, buildMessage(msg));
    }

    /** Passing message further to Log.d() */
    public void d(String msg, Exception ex) {
        Log.d(mTag, buildMessage(msg), ex);
    }

    /** Passing message further to Log.w() */
    public void w(String msg, Exception ex) {
        Log.w(mTag, buildMessage(msg), ex);
    }

    /** Passing message further to Log.w() */
    public void w(String msg) {
        Log.w(mTag, buildMessage(msg));
    }

    /** Passing message further to Log.e() */
    public void e(String msg) {
        Log.e(mTag, buildMessage(msg));
    }

    /** Passing message further to Log.e() */
    public void e(String msg, Exception ex) {
        Log.e(mTag, buildMessage(msg), ex);
    }

    /** Passing message further to Log.e() */
    public void e(String msg, Throwable ex) {
        Log.e(mTag, buildMessage(msg), ex);
    }

    private String buildMessage(String msg) {
        return String.format("[%s]: %s", mPrefix, msg);
    }
}
