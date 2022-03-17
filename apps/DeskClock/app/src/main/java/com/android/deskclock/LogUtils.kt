/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.deskclock

import android.os.Build
import android.util.Log

object LogUtils {
    /** Default logger used for generic logging, i.e TAG. when a specific log tag isn't specified.*/
    private val DEFAULT_LOGGER = Logger("AlarmClock")

    @JvmStatic
    fun v(message: String, vararg args: Any?) {
        DEFAULT_LOGGER.v(message, *args)
    }

    @JvmStatic
    fun d(message: String, vararg args: Any?) {
        DEFAULT_LOGGER.d(message, *args)
    }

    @JvmStatic
    fun i(message: String, vararg args: Any?) {
        DEFAULT_LOGGER.i(message, *args)
    }

    @JvmStatic
    fun w(message: String, vararg args: Any?) {
        DEFAULT_LOGGER.w(message, *args)
    }

    fun e(message: String, vararg args: Any?) {
        DEFAULT_LOGGER.e(message, *args)
    }

    @JvmStatic
    fun e(message: String, e: Throwable) {
        DEFAULT_LOGGER.e(message, e)
    }

    fun wtf(message: String, vararg args: Any?) {
        DEFAULT_LOGGER.wtf(message, *args)
    }

    @JvmStatic
    fun wtf(e: Throwable) {
        DEFAULT_LOGGER.wtf(e)
    }

    class Logger(val logTag: String) {
        val isVerboseLoggable: Boolean
            get() = DEBUG || Log.isLoggable(logTag, Log.VERBOSE)

        val isDebugLoggable: Boolean
            get() = DEBUG || Log.isLoggable(logTag, Log.DEBUG)

        val isInfoLoggable: Boolean
            get() = DEBUG || Log.isLoggable(logTag, Log.INFO)

        val isWarnLoggable: Boolean
            get() = DEBUG || Log.isLoggable(logTag, Log.WARN)

        val isErrorLoggable: Boolean
            get() = DEBUG || Log.isLoggable(logTag, Log.ERROR)

        val isWtfLoggable: Boolean
            get() = DEBUG || Log.isLoggable(logTag, Log.ASSERT)

        fun v(message: String, vararg args: Any?) {
            if (isVerboseLoggable) {
                Log.v(logTag, if (args.isEmpty() || args[0] == null) {
                    message
                } else {
                    String.format(message, *args)
                })
            }
        }

        fun d(message: String, vararg args: Any?) {
            if (isDebugLoggable) {
                Log.d(logTag, if (args.isEmpty() || args[0] == null) {
                    message
                } else {
                    String.format(message, *args)
                })
            }
        }

        fun i(message: String, vararg args: Any?) {
            if (isInfoLoggable) {
                Log.i(logTag, if (args.isEmpty() || args[0] == null) {
                    message
                } else {
                    String.format(message, *args)
                })
            }
        }

        fun w(message: String, vararg args: Any?) {
            if (isWarnLoggable) {
                Log.w(logTag, if (args.isEmpty() || args[0] == null) {
                    message
                } else {
                    String.format(message, *args)
                })
            }
        }

        fun e(message: String, vararg args: Any?) {
            if (isErrorLoggable) {
                Log.e(logTag, if (args.isEmpty() || args[0] == null) {
                    message
                } else {
                    String.format(message, *args)
                })
            }
        }

        fun e(message: String, e: Throwable) {
            if (isErrorLoggable) {
                Log.e(logTag, message, e)
            }
        }

        fun wtf(message: String, vararg args: Any?) {
            if (isWtfLoggable) {
                Log.wtf(logTag, if (args.isEmpty() || args[0] == null) {
                    message
                } else {
                    String.format(message, *args)
                })
            }
        }

        fun wtf(e: Throwable) {
            if (isWtfLoggable) {
                Log.wtf(logTag, e)
            }
        }

        companion object {
            /** Log everything for debug builds or if running on a dev device. */
            val DEBUG = (BuildConfig.DEBUG || "eng" == Build.TYPE || "userdebug" == Build.TYPE)
        }
    }
}