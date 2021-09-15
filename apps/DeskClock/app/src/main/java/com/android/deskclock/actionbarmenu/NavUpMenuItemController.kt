/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.deskclock.actionbarmenu

import android.app.Activity
import android.view.Menu
import android.view.MenuItem

/**
 * [MenuItemController] for handling navigation up button in actionbar. It is a special
 * menu item because it's not inflated through menu.xml, and has its own predefined id.
 */
class NavUpMenuItemController(private val activity: Activity) : MenuItemController {

    override val id: Int = android.R.id.home

    override fun onCreateOptionsItem(menu: Menu) {
        // "Home" option is automatically created by the Toolbar.
    }

    override fun onPrepareOptionsItem(item: MenuItem) {
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        activity.finish()
        return true
    }
}