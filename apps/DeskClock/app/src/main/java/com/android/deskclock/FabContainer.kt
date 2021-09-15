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

package com.android.deskclock

import androidx.annotation.IntDef

/**
 * Implemented by containers that house the fab and its associated buttons. Also implemented by
 * containers that know how to contact the **true** fab container to ferry through
 * commands.
 */
interface FabContainer {
    @IntDef(flag = true, value = [
        FAB_IMMEDIATE,
        FAB_SHRINK_AND_EXPAND,
        FAB_MORPH,
        FAB_REQUEST_FOCUS,
        BUTTONS_IMMEDIATE,
        BUTTONS_SHRINK_AND_EXPAND,
        BUTTONS_DISABLE,
        FAB_AND_BUTTONS_IMMEDIATE,
        FAB_AND_BUTTONS_SHRINK_AND_EXPAND,
        FAB_AND_BUTTONS_SHRINK,
        FAB_AND_BUTTONS_EXPAND
    ])
    annotation class UpdateFabFlag

    /**
     * Requests that this container update the fab and/or its buttons because their state has
     * changed. The update may be immediate or it may be animated depending on the choice of
     * `updateTypes`.
     *
     * @param updateTypes indicates the types of update to apply to the fab and its buttons
     */
    fun updateFab(@UpdateFabFlag updateTypes: Int)

    companion object {
        /** Bit field for updates  */
        /** Bit 0-1  */
        const val FAB_ANIMATION_MASK = 3

        /** Signals that the fab should be updated in place with no animation.  */
        const val FAB_IMMEDIATE = 1

        /** Signals the fab should be "animated away", updated, and "animated back".  */
        const val FAB_SHRINK_AND_EXPAND = 2

        /** Signals that the fab should morph into a new state in place.  */
        const val FAB_MORPH = 3

        /** Bit 2  */
        const val FAB_REQUEST_FOCUS_MASK = 4

        /** Signals that the fab should request focus.  */
        const val FAB_REQUEST_FOCUS = 4

        /** Bit 3-4  */
        const val BUTTONS_ANIMATION_MASK = 24

        /** Signals that the buttons should be updated in place with no animation.  */
        const val BUTTONS_IMMEDIATE = 8

        /** Signals that the buttons should be "animated away", updated, and "animated back".  */
        const val BUTTONS_SHRINK_AND_EXPAND = 16

        /** Bit 5  */
        const val BUTTONS_DISABLE_MASK = 32

        /** Disable the buttons of the fab so they do not respond to clicks.  */
        const val BUTTONS_DISABLE = 32

        /** Bit 6-7  */
        const val FAB_AND_BUTTONS_SHRINK_EXPAND_MASK = 192

        /** Signals the fab and buttons should be "animated away".  */
        const val FAB_AND_BUTTONS_SHRINK = 128

        /** Signals the fab and buttons should be "animated back".  */
        const val FAB_AND_BUTTONS_EXPAND = 64

        /** Convenience flags  */
        const val FAB_AND_BUTTONS_IMMEDIATE = FAB_IMMEDIATE or BUTTONS_IMMEDIATE
        const val FAB_AND_BUTTONS_SHRINK_AND_EXPAND =
                FAB_SHRINK_AND_EXPAND or BUTTONS_SHRINK_AND_EXPAND
    }
}