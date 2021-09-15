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
import android.text.TextUtils
import android.view.View

import java.util.Locale

/**
 * All tab data is accessed via this model.
 */
internal class TabModel(private val mPrefs: SharedPreferences) {

    /** The listeners to notify when the selected tab is changed.  */
    private val mTabListeners: MutableList<TabListener> = ArrayList()

    /** The listeners to notify when the vertical scroll state of the selected tab is changed.  */
    private val mTabScrollListeners: MutableList<TabScrollListener> = ArrayList()

    /** The scrolled-to-top state of each tab.  */
    private val mTabScrolledToTop = BooleanArray(UiDataModel.Tab.values().size)

    /** An enumerated value indicating the currently selected tab.  */
    private var mSelectedTab: UiDataModel.Tab? = null

    init {
        mTabScrolledToTop.fill(true)
    }

    //
    // Selected tab
    //

    /**
     * @param tabListener to be notified when the selected tab changes
     */
    fun addTabListener(tabListener: TabListener) {
        mTabListeners.add(tabListener)
    }

    /**
     * @param tabListener to no longer be notified when the selected tab changes
     */
    fun removeTabListener(tabListener: TabListener) {
        mTabListeners.remove(tabListener)
    }

    /**
     * @return the number of tabs
     */
    val tabCount: Int
        get() = UiDataModel.Tab.values().size

    /**
     * @param ordinal the ordinal (left-to-right index) of the tab
     * @return the tab at the given `ordinal`
     */
    fun getTab(ordinal: Int): UiDataModel.Tab {
        return UiDataModel.Tab.values()[ordinal]
    }

    /**
     * @param position the position of the tab in the user interface
     * @return the tab at the given `ordinal`
     */
    fun getTabAt(position: Int): UiDataModel.Tab {
        val layoutDirection = TextUtils.getLayoutDirectionFromLocale(Locale.getDefault())
        val ordinal: Int = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            tabCount - position - 1
        } else {
            position
        }
        return getTab(ordinal)
    }

    /**
     * @return an enumerated value indicating the currently selected primary tab
     */
    val selectedTab: UiDataModel.Tab
        get() {
            if (mSelectedTab == null) {
                mSelectedTab = TabDAO.getSelectedTab(mPrefs)
            }
            return mSelectedTab!!
        }

    /**
     * @param tab an enumerated value indicating the newly selected primary tab
     */
    fun setSelectedTab(tab: UiDataModel.Tab) {
        val oldSelectedTab = selectedTab
        if (oldSelectedTab != tab) {
            mSelectedTab = tab
            TabDAO.setSelectedTab(mPrefs, tab)

            // Notify of the tab change.
            for (tl in mTabListeners) {
                tl.selectedTabChanged(oldSelectedTab, tab)
            }

            // Notify of the vertical scroll position change if there is one.
            val tabScrolledToTop = isTabScrolledToTop(tab)
            if (isTabScrolledToTop(oldSelectedTab) != tabScrolledToTop) {
                for (tsl in mTabScrollListeners) {
                    tsl.selectedTabScrollToTopChanged(tab, tabScrolledToTop)
                }
            }
        }
    }

    //
    // Tab scrolling
    //

    /**
     * @param tabScrollListener to be notified when the scroll position of the selected tab changes
     */
    fun addTabScrollListener(tabScrollListener: TabScrollListener) {
        mTabScrollListeners.add(tabScrollListener)
    }

    /**
     * @param tabScrollListener to be notified when the scroll position of the selected tab changes
     */
    fun removeTabScrollListener(tabScrollListener: TabScrollListener) {
        mTabScrollListeners.remove(tabScrollListener)
    }

    /**
     * Updates the scrolling state in the [UiDataModel] for this tab.
     *
     * @param tab an enumerated value indicating the tab reporting its vertical scroll position
     * @param scrolledToTop `true` iff the vertical scroll position of this tab is at the top
     */
    fun setTabScrolledToTop(tab: UiDataModel.Tab, scrolledToTop: Boolean) {
        if (isTabScrolledToTop(tab) != scrolledToTop) {
            mTabScrolledToTop[tab.ordinal] = scrolledToTop
            if (tab == selectedTab) {
                for (tsl in mTabScrollListeners) {
                    tsl.selectedTabScrollToTopChanged(tab, scrolledToTop)
                }
            }
        }
    }

    /**
     * @param tab identifies the tab
     * @return `true` iff the content in the given `tab` is currently scrolled to top
     */
    fun isTabScrolledToTop(tab: UiDataModel.Tab): Boolean {
        return mTabScrolledToTop[tab.ordinal]
    }
}