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

import android.util.ArrayMap
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.viewpager.widget.PagerAdapter

import com.android.deskclock.uidata.UiDataModel

/**
 * This adapter produces the DeskClockFragments that are the content of the DeskClock tabs. The
 * adapter presents the tabs in LTR and RTL order depending on the text layout direction for the
 * current locale. To prevent issues when switching between LTR and RTL, fragments are registered
 * with the manager using position-independent tags, which is an important departure from
 * FragmentPagerAdapter.
 */
internal class FragmentTabPagerAdapter(private val mDeskClock: DeskClock) : PagerAdapter() {

    /** The manager into which fragments are added.  */
    private val mFragmentManager: FragmentManager = mDeskClock.supportFragmentManager

    /** A fragment cache that can be accessed before [.instantiateItem] is called.  */
    private val mFragmentCache: MutableMap<UiDataModel.Tab, DeskClockFragment?> =
            ArrayMap(getCount())

    /** The active fragment transaction if one exists.  */
    private var mCurrentTransaction: FragmentTransaction? = null

    /** The current fragment displayed to the user.  */
    private var mCurrentPrimaryItem: Fragment? = null

    override fun getCount(): Int = UiDataModel.uiDataModel.tabCount

    /**
     * @param position the left-to-right index of the fragment to be returned
     * @return the fragment displayed at the given `position`
     */
    fun getDeskClockFragment(position: Int): DeskClockFragment {
        // Fetch the tab the UiDataModel reports for the position.
        val tab: UiDataModel.Tab = UiDataModel.uiDataModel.getTabAt(position)

        // First check the local cache for the fragment.
        var fragment = mFragmentCache[tab]
        if (fragment != null) {
            return fragment
        }

        // Next check the fragment manager; relevant when app is rebuilt after locale changes
        // because this adapter will be new and mFragmentCache will be empty, but the fragment
        // manager will retain the Fragments built on original application launch.
        fragment = mFragmentManager.findFragmentByTag(tab.name) as DeskClockFragment?
        if (fragment != null) {
            fragment.setFabContainer(mDeskClock)
            mFragmentCache[tab] = fragment
            return fragment
        }

        // Otherwise, build the fragment from scratch.
        val fragmentClassName: String = tab.fragmentClassName
        fragment = Fragment.instantiate(mDeskClock, fragmentClassName) as DeskClockFragment
        fragment.setFabContainer(mDeskClock)
        mFragmentCache[tab] = fragment
        return fragment
    }

    override fun startUpdate(container: ViewGroup) {
        check(container.id != View.NO_ID) { "ViewPager with adapter $this has no id" }
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        if (mCurrentTransaction == null) {
            mCurrentTransaction = mFragmentManager.beginTransaction()
        }

        // Use the fragment located in the fragment manager if one exists.
        val tab: UiDataModel.Tab = UiDataModel.uiDataModel.getTabAt(position)
        var fragment = mFragmentManager.findFragmentByTag(tab.name)
        if (fragment != null) {
            mCurrentTransaction!!.attach(fragment)
        } else {
            fragment = getDeskClockFragment(position)
            mCurrentTransaction!!.add(container.id, fragment, tab.name)
        }

        if (fragment !== mCurrentPrimaryItem) {
            fragment.setMenuVisibility(false)
            fragment.setUserVisibleHint(false)
        }

        return fragment
    }

    override fun destroyItem(container: ViewGroup, position: Int, any: Any) {
        if (mCurrentTransaction == null) {
            mCurrentTransaction = mFragmentManager.beginTransaction()
        }
        val fragment = any as DeskClockFragment
        fragment.setFabContainer(null)
        mCurrentTransaction!!.detach(fragment)
    }

    override fun setPrimaryItem(container: ViewGroup, position: Int, any: Any) {
        val fragment = any as Fragment
        if (fragment !== mCurrentPrimaryItem) {
            mCurrentPrimaryItem?.let {
                it.setMenuVisibility(false)
                it.setUserVisibleHint(false)
            }
            fragment.setMenuVisibility(true)
            fragment.setUserVisibleHint(true)
            mCurrentPrimaryItem = fragment
        }
    }

    override fun finishUpdate(container: ViewGroup) {
        if (mCurrentTransaction != null) {
            mCurrentTransaction!!.commitAllowingStateLoss()
            mCurrentTransaction = null
            mFragmentManager.executePendingTransactions()
        }
    }

    override fun isViewFromObject(view: View, any: Any): Boolean = (any as Fragment).view === view
}