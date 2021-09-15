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

import kotlin.collections.ArrayList

/**
 * Factory that builds optional [MenuItemController] instances.
 */
object MenuItemControllerFactory {
    private val mMenuItemProviders: MutableList<MenuItemProvider> = ArrayList()

    fun buildMenuItemControllers(activity: Activity?): Array<MenuItemController?> {
        val providerSize = mMenuItemProviders.size
        val controllers = arrayOfNulls<MenuItemController>(providerSize)
        for (i in 0 until providerSize) {
            controllers[i] = mMenuItemProviders[i].provide(activity)
        }
        return controllers
    }
}