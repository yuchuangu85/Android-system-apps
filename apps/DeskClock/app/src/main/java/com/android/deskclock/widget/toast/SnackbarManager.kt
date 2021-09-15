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

package com.android.deskclock.widget.toast

import com.google.android.material.snackbar.Snackbar

import java.lang.ref.WeakReference

/**
 * Manages visibility of Snackbar and allow preemptive dismiss of current displayed Snackbar.
 */
object SnackbarManager {
    private var sSnackbar: WeakReference<Snackbar>? = null

    @JvmStatic
    fun show(snackbar: Snackbar) {
        sSnackbar = WeakReference<Snackbar>(snackbar)
        snackbar.show()
    }

    @JvmStatic
    fun dismiss() {
        val snackbar: Snackbar? = sSnackbar?.get()
        if (snackbar != null) {
            snackbar.dismiss()
            sSnackbar = null
        }
    }
}