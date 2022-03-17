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

import android.app.Activity
import android.view.Menu
import android.view.MenuItem

/**
 * Activity scoped singleton that manages action bar menus. Each menu item is controlled by a
 * [MenuItemController] instance.
 */
class OptionsMenuManager {

    private val mControllers: MutableList<MenuItemController?> = ArrayList()

    /**
     * Add one or more [MenuItemController] to the actionbar menu.
     *
     * This should be called in [Activity.onCreate].
     */
    fun addMenuItemController(vararg controllers: MenuItemController?): OptionsMenuManager {
        mControllers.addAll(controllers)
        return this
    }

    /**
     * Inflates [Menu] for the activity.
     *
     * This method should be called during [Activity.onCreateOptionsMenu].
     */
    fun onCreateOptionsMenu(menu: Menu) {
        for (controller in mControllers) {
            controller?.onCreateOptionsItem(menu)
        }
    }

    /**
     * Prepares the popup to displays all required menu items.
     *
     * This method should be called during [Activity.onPrepareOptionsMenu] (Menu)}.
     */
    fun onPrepareOptionsMenu(menu: Menu) {
        for (controller in mControllers) {
            controller?.let {
                val menuItem: MenuItem? = menu.findItem(controller.id)
                if (menuItem != null) {
                    controller.onPrepareOptionsItem(menuItem)
                }
            }
        }
    }

    /**
     * Handles click action for a menu item.
     *
     * This method should be called during [Activity.onOptionsItemSelected].
     */
    fun onOptionsItemSelected(item: MenuItem): Boolean {
        val itemId: Int = item.getItemId()
        for (controller in mControllers) {
            if (controller?.id == itemId && controller.onOptionsItemSelected(item)) {
                return true
            }
        }
        return false
    }
}