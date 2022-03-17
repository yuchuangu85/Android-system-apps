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

import android.view.View
import android.widget.Button
import android.widget.ImageView

/**
 * Implementers of this interface are able to [configure the fab][.onUpdateFab] and associated
 * [left/right buttons][.onUpdateFabButtons] including setting them [View.INVISIBLE] if
 * they are unnecessary. Implementers also attach click handler logic to the
 * [fab][.onFabClick], [left button][.onLeftButtonClick] and
 * [right button][.onRightButtonClick].
 */
interface FabController {
    /**
     * Configures the display of the fab component to match the current state of this controller.
     *
     * @param fab the fab component to be configured based on current state
     */
    fun onUpdateFab(fab: ImageView)

    /**
     * Called before onUpdateFab when the fab should be animated.
     *
     * @param fab the fab component to be configured based on current state
     */
    fun onMorphFab(fab: ImageView)

    /**
     * Configures the display of the buttons to the left and right of the fab to match the current
     * state of this controller.
     *
     * @param left button to the left of the fab to configure based on current state
     * @param right button to the right of the fab to configure based on current state
     */
    fun onUpdateFabButtons(left: Button, right: Button)

    /**
     * Handles a click on the fab.
     *
     * @param fab the fab component on which the click occurred
     */
    fun onFabClick(fab: ImageView)

    /**
     * Handles a click on the button to the left of the fab component.
     *
     * @param left the button to the left of the fab component
     */
    fun onLeftButtonClick(left: Button)

    /**
     * Handles a click on the button to the right of the fab component.
     *
     * @param right the button to the right of the fab component
     */
    fun onRightButtonClick(right: Button)
}