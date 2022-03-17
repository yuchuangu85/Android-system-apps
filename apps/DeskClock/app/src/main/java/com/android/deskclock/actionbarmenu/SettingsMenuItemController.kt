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
import android.content.Intent
import android.view.Menu
import android.view.Menu.NONE
import android.view.MenuItem

import com.android.deskclock.R
import com.android.deskclock.settings.SettingsActivity

/**
 * [MenuItemController] for settings menu.
 */
class SettingsMenuItemController(private val activity: Activity) : MenuItemController {

    override val id: Int = R.id.menu_item_settings

    override fun onCreateOptionsItem(menu: Menu) {
        menu.add(NONE, id, NONE, R.string.menu_item_settings)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
    }

    override fun onPrepareOptionsItem(item: MenuItem) {
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val settingIntent = Intent(activity, SettingsActivity::class.java)
        activity.startActivityForResult(settingIntent, REQUEST_CHANGE_SETTINGS)
        return true
    }

    companion object {
        const val REQUEST_CHANGE_SETTINGS = 1
    }
}