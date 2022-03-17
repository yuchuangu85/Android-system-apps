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
import androidx.annotation.StringRes

import com.android.deskclock.R
import com.android.deskclock.events.Events

/**
 * All widget data is accessed via this model.
 */
internal class WidgetModel(private val mPrefs: SharedPreferences) {
    /**
     * @param widgetClass indicates the type of widget being counted
     * @param count the number of widgets of the given type
     * @param eventCategoryId identifies the category of event to send
     */
    fun updateWidgetCount(widgetClass: Class<*>, count: Int, @StringRes eventCategoryId: Int) {
        var delta = WidgetDAO.updateWidgetCount(mPrefs, widgetClass, count)
        while (delta > 0) {
            Events.sendEvent(eventCategoryId, R.string.action_create, 0)
            delta--
        }
        while (delta < 0) {
            Events.sendEvent(eventCategoryId, R.string.action_delete, 0)
            delta++
        }
    }
}