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

package com.android.deskclock.uidata

import android.content.SharedPreferences

/**
 * This class encapsulates the storage of tab data in [SharedPreferences].
 */
internal object TabDAO {
    /** Key to a preference that stores the ordinal of the selected tab.  */
    private const val KEY_SELECTED_TAB = "selected_tab"

    /**
     * @return an enumerated value indicating the currently selected primary tab
     */
    fun getSelectedTab(prefs: SharedPreferences): UiDataModel.Tab {
        val ordinal = prefs.getInt(KEY_SELECTED_TAB, UiDataModel.Tab.CLOCKS.ordinal)
        return UiDataModel.Tab.values()[ordinal]
    }

    /**
     * @param tab an enumerated value indicating the newly selected primary tab
     */
    fun setSelectedTab(prefs: SharedPreferences, tab: UiDataModel.Tab) {
        prefs.edit().putInt(KEY_SELECTED_TAB, tab.ordinal).apply()
    }
}