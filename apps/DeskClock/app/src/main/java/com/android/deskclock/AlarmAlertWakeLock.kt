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

import android.content.Context
import android.os.PowerManager
import android.os.PowerManager.WakeLock

/**
 * Utility class to hold wake lock in app.
 */
object AlarmAlertWakeLock {
    private const val TAG = "AlarmAlertWakeLock"

    private var sCpuWakeLock: WakeLock? = null

    @JvmStatic
    fun createPartialWakeLock(context: Context): WakeLock {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG)
    }

    @JvmStatic
    fun acquireCpuWakeLock(context: Context) {
        if (sCpuWakeLock != null) {
            return
        }

        sCpuWakeLock = createPartialWakeLock(context)
        sCpuWakeLock!!.acquire()
    }

    @JvmStatic
    fun acquireScreenCpuWakeLock(context: Context) {
        if (sCpuWakeLock != null) {
            return
        }
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        sCpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK
                or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE, TAG)
        sCpuWakeLock!!.acquire()
    }

    @JvmStatic
    fun releaseCpuLock() {
        if (sCpuWakeLock != null) {
            sCpuWakeLock!!.release()
            sCpuWakeLock = null
        }
    }
}