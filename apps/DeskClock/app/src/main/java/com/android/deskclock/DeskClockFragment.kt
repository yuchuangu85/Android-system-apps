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

import android.view.KeyEvent
import android.widget.Button
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.fragment.app.Fragment

import com.android.deskclock.FabContainer.UpdateFabFlag
import com.android.deskclock.uidata.UiDataModel

abstract class DeskClockFragment(
    /** The tab associated with this fragment.  */
    private val mTab: UiDataModel.Tab
) : Fragment(), FabContainer, FabController {

    /** The container that houses the fab and its left and right buttons.  */
    private var mFabContainer: FabContainer? = null

    override fun onResume() {
        super.onResume()

        // Update the fab and buttons in case their state changed while the fragment was paused.
        if (isTabSelected) {
            updateFab(FabContainer.FAB_AND_BUTTONS_IMMEDIATE)
        }
    }

    open fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // By default return false so event continues to propagate
        return false
    }

    override fun onLeftButtonClick(left: Button) {
        // Do nothing here, only in derived classes
    }

    override fun onRightButtonClick(right: Button) {
        // Do nothing here, only in derived classes
    }

    override fun onMorphFab(fab: ImageView) {
        // Do nothing here, only in derived classes
    }

    /**
     * @param color the newly installed app window color
     */
    protected open fun onAppColorChanged(@ColorInt color: Int) {
        // Do nothing here, only in derived classes
    }

    /**
     * @param fabContainer the container that houses the fab and its left and right buttons
     */
    fun setFabContainer(fabContainer: FabContainer?) {
        mFabContainer = fabContainer
    }

    /**
     * Requests that the parent activity update the fab and buttons.
     *
     * @param updateTypes the manner in which the fab container should be updated
     */
    override fun updateFab(@UpdateFabFlag updateTypes: Int) {
        mFabContainer?.updateFab(updateTypes)
    }

    /**
     * @return `true` iff the currently selected tab displays this fragment
     */
    val isTabSelected: Boolean
        get() = UiDataModel.uiDataModel.selectedTab == mTab

    /**
     * Select the tab that displays this fragment.
     */
    fun selectTab() {
        UiDataModel.uiDataModel.selectedTab = mTab
    }

    /**
     * Updates the scrolling state in the [UiDataModel] for this tab.
     *
     * @param scrolledToTop `true` iff the vertical scroll position of this tab is at the top
     */
    fun setTabScrolledToTop(scrolledToTop: Boolean) {
        UiDataModel.uiDataModel.setTabScrolledToTop(mTab, scrolledToTop)
    }
}