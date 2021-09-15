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

import android.os.Handler
import android.os.HandlerThread

/**
 * Helper class for managing the background thread used to perform io operations
 * and handle async broadcasts.
 */
object AsyncHandler {
    private val sHandlerThread = HandlerThread("AsyncHandler")
    private val sHandler: Handler

    init {
        sHandlerThread.start()
        sHandler = Handler(sHandlerThread.looper)
    }

    fun post(r: () -> Unit) {
        sHandler.post(r)
    }
}