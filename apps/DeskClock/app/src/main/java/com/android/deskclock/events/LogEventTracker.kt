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

package com.android.deskclock.events

import android.content.Context
import androidx.annotation.StringRes

import com.android.deskclock.LogUtils

class LogEventTracker(val context: Context) : EventTracker {

    override fun sendEvent(
        @StringRes category: Int,
        @StringRes action: Int,
        @StringRes label: Int
    ) {
        if (label == 0) {
            LOGGER.d("[%s] [%s]", safeGetString(category), safeGetString(action))
        } else {
            LOGGER.d("[%s] [%s] [%s]", safeGetString(category), safeGetString(action),
                    safeGetString(label))
        }
    }

    /**
     * @return Resource string represented by a given resource id, null if resId is invalid (0).
     */
    private fun safeGetString(@StringRes resId: Int): String? {
        return if (resId == 0) null else context.getString(resId)
    }

    companion object {
        private val LOGGER = LogUtils.Logger("Events")
    }
}
