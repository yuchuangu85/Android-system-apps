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

package com.android.deskclock.actionbarmenu

import android.view.Menu
import android.view.MenuItem

/**
 * Interface for handling a single menu item in action bar.
 */
interface MenuItemController {
    /**
     * Returns the menu item resource id that the controller manages.
     */
    val id: Int

    /**
     * Create the menu item.
     */
    fun onCreateOptionsItem(menu: Menu)

    /**
     * Called immediately before the [MenuItem] is shown.
     *
     * @param item the [MenuItem] created by the controller
     */
    fun onPrepareOptionsItem(item: MenuItem)

    /**
     * Attempts to handle the click action.
     *
     * @param item the [MenuItem] that was selected
     * @return `true` if the action is handled by this controller
     */
    fun onOptionsItemSelected(item: MenuItem): Boolean
}