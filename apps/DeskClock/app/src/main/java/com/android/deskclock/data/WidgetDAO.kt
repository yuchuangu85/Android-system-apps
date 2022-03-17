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

package com.android.deskclock.data

import android.content.SharedPreferences

/**
 * This class encapsulates the transfer of data between widget objects and their permanent storage
 * in [SharedPreferences].
 */
internal object WidgetDAO {
    /** Suffix for a key to a preference that stores the instance count for a given widget type.  */
    private const val WIDGET_COUNT = "_widget_count"

    /**
     * @param widgetProviderClass indicates the type of widget being counted
     * @param count the number of widgets of the given type
     * @return the delta between the new count and the old count
     */
    fun updateWidgetCount(
        prefs: SharedPreferences,
        widgetProviderClass: Class<*>,
        count: Int
    ): Int {
        val key = widgetProviderClass.simpleName + WIDGET_COUNT
        val oldCount: Int = prefs.getInt(key, 0)
        if (count == 0) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putInt(key, count).apply()
        }
        return count - oldCount
    }
}