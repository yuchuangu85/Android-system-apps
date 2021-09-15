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

package com.android.deskclock.timer

import android.annotation.SuppressLint
import android.util.ArrayMap
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.viewpager.widget.PagerAdapter

import com.android.deskclock.data.DataModel
import com.android.deskclock.data.Timer
import com.android.deskclock.data.TimerListener

/**
 * This adapter produces a [TimerItemFragment] for each timer.
 */
internal class TimerPagerAdapter(
    private val mFragmentManager: FragmentManager
) : PagerAdapter(), TimerListener {

    /** Maps each timer id to the corresponding [TimerItemFragment] that draws it.  */
    private val mFragments: MutableMap<Int, TimerItemFragment?> = ArrayMap()

    /** The current fragment transaction in play or `null`.  */
    private var mCurrentTransaction: FragmentTransaction? = null

    /** The [TimerItemFragment] that is current visible on screen.  */
    private var mCurrentPrimaryItem: Fragment? = null

    override fun getCount(): Int = timers.size

    override fun isViewFromObject(view: View, any: Any): Boolean {
        return (any as Fragment).view === view
    }

    override fun getItemPosition(any: Any): Int {
        val fragment = any as TimerItemFragment
        val timer = fragment.timer

        val position = timers.indexOf(timer)
        return if (position == -1) POSITION_NONE else position
    }

    @SuppressLint("CommitTransaction")
    override fun instantiateItem(container: ViewGroup, position: Int): Fragment {
        if (mCurrentTransaction == null) {
            mCurrentTransaction = mFragmentManager.beginTransaction()
        }

        val timer = timers[position]

        // Search for the existing fragment by tag.
        val tag = javaClass.simpleName + timer.id
        var fragment = mFragmentManager.findFragmentByTag(tag) as TimerItemFragment?

        if (fragment != null) {
            // Reattach the existing fragment.
            mCurrentTransaction!!.attach(fragment)
        } else {
            // Create and add a new fragment.
            fragment = TimerItemFragment.newInstance(timer)
            mCurrentTransaction!!.add(container.id, fragment, tag)
        }

        if (fragment !== mCurrentPrimaryItem) {
            setItemVisible(fragment, false)
        }

        mFragments[timer.id] = fragment

        return fragment
    }

    @SuppressLint("CommitTransaction")
    override fun destroyItem(container: ViewGroup, position: Int, any: Any) {
        val fragment = any as TimerItemFragment

        if (mCurrentTransaction == null) {
            mCurrentTransaction = mFragmentManager.beginTransaction()
        }

        mFragments.remove(fragment.timerId)
        mCurrentTransaction!!.remove(fragment)
    }

    override fun setPrimaryItem(container: ViewGroup, position: Int, any: Any) {
        val fragment = any as Fragment
        if (fragment !== mCurrentPrimaryItem) {
            mCurrentPrimaryItem?.let {
                setItemVisible(it, false)
            }

            mCurrentPrimaryItem = fragment

            mCurrentPrimaryItem?.let {
                setItemVisible(it, true)
            }
        }
    }

    override fun finishUpdate(container: ViewGroup) {
        if (mCurrentTransaction != null) {
            mCurrentTransaction!!.commitAllowingStateLoss()
            mCurrentTransaction = null

            if (!mFragmentManager.isDestroyed) {
                mFragmentManager.executePendingTransactions()
            }
        }
    }

    override fun timerAdded(timer: Timer) {
        notifyDataSetChanged()
    }

    override fun timerRemoved(timer: Timer) {
        notifyDataSetChanged()
    }

    override fun timerUpdated(before: Timer, after: Timer) {
        val timerItemFragment = mFragments[after.id]
        timerItemFragment?.updateTime()
    }

    /**
     * @return `true` if at least one timer is in a state requiring continuous updates
     */
    fun updateTime(): Boolean {
        var continuousUpdates = false
        for (fragment in mFragments.values) {
            continuousUpdates = continuousUpdates or fragment!!.updateTime()
        }
        return continuousUpdates
    }

    fun getTimer(index: Int): Timer {
        return timers[index]
    }

    private val timers: List<Timer>
        get() = DataModel.dataModel.timers

    companion object {
        private fun setItemVisible(item: Fragment, visible: Boolean) {
            item.setMenuVisibility(visible)
            item.setUserVisibleHint(visible)
        }
    }
}