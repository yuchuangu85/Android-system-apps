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

import android.content.Context
import android.content.Intent
import android.view.Menu
import android.view.Menu.NONE
import android.view.MenuItem

import com.android.deskclock.R
import com.android.deskclock.ScreensaverActivity
import com.android.deskclock.events.Events

/**
 * [MenuItemController] for controlling night mode display.
 */
class NightModeMenuItemController(private val context: Context) : MenuItemController {

    override val id: Int = R.id.menu_item_night_mode

    override fun onCreateOptionsItem(menu: Menu) {
        menu.add(NONE, id, NONE, R.string.menu_item_night_mode)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
    }

    override fun onPrepareOptionsItem(item: MenuItem) {
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        context.startActivity(Intent(context, ScreensaverActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(Events.EXTRA_EVENT_LABEL, R.string.label_deskclock))
        return true
    }
}