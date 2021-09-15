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

import android.animation.ValueAnimator
import android.view.View
import android.widget.AbsListView
import android.widget.ListView
import androidx.recyclerview.widget.RecyclerView

import com.android.deskclock.data.DataModel
import com.android.deskclock.uidata.TabScrollListener
import com.android.deskclock.uidata.UiDataModel

/**
 * This controller encapsulates the logic that watches a model for changes to scroll state and
 * updates the display state of an associated drop shadow. The observable model may take many forms
 * including ListViews, RecyclerViews and this application's UiDataModel. Each of these models can
 * indicate when content is scrolled to its top. When the content is scrolled to the top the drop
 * shadow is hidden and the content appears flush with the app bar. When the content is scrolled
 * up the drop shadow is displayed making the content appear to scroll below the app bar.
 */
class DropShadowController private constructor(
    /** The component that displays a drop shadow.  */
    private val mDropShadowView: View
) {
    /** Updates [.mDropShadowView] in response to changes in the backing scroll model.  */
    private val mScrollChangeWatcher = ScrollChangeWatcher()

    /** Fades the [@mDropShadowView] in/out as scroll state changes.  */
    private val mDropShadowAnimator: ValueAnimator =
            AnimatorUtils.getAlphaAnimator(mDropShadowView, 0f, 1f)
                    .setDuration(UiDataModel.uiDataModel.shortAnimationDuration)

    /** Tab bar's hairline, which is hidden whenever the drop shadow is displayed.  */
    private var mHairlineView: View? = null

    // Supported sources of scroll position include: ListView, RecyclerView and UiDataModel.
    private var mRecyclerView: RecyclerView? = null
    private var mUiDataModel: UiDataModel? = null
    private var mListView: ListView? = null

    /**
     * @param dropShadowView to be hidden/shown as `uiDataModel` reports scrolling changes
     * @param uiDataModel models the vertical scrolling state of the application's selected tab
     * @param hairlineView at the bottom of the tab bar to be hidden or shown when the drop shadow
     * is displayed or hidden, respectively.
     */
    constructor(
        dropShadowView: View,
        uiDataModel: UiDataModel,
        hairlineView: View
    ) : this(dropShadowView) {
        mUiDataModel = uiDataModel
        mUiDataModel?.addTabScrollListener(mScrollChangeWatcher)
        mHairlineView = hairlineView
        updateDropShadow(!uiDataModel.isSelectedTabScrolledToTop)
    }

    /**
     * @param dropShadowView to be hidden/shown as `listView` reports scrolling changes
     * @param listView a scrollable view that dictates the visibility of `dropShadowView`
     */
    constructor(dropShadowView: View, listView: ListView) : this(dropShadowView) {
        mListView = listView
        mListView?.setOnScrollListener(mScrollChangeWatcher)
        updateDropShadow(!Utils.isScrolledToTop(listView))
    }

    /**
     * @param dropShadowView to be hidden/shown as `recyclerView` reports scrolling changes
     * @param recyclerView a scrollable view that dictates the visibility of `dropShadowView`
     */
    constructor(dropShadowView: View, recyclerView: RecyclerView) : this(dropShadowView) {
        mRecyclerView = recyclerView
        mRecyclerView?.addOnScrollListener(mScrollChangeWatcher)
        updateDropShadow(!Utils.isScrolledToTop(recyclerView))
    }

    /**
     * Stop updating the drop shadow in response to scrolling changes. Stop listening to the backing
     * scrollable entity for changes. This is important to avoid memory leaks.
     */
    fun stop() {
        when {
            mRecyclerView != null -> mRecyclerView?.removeOnScrollListener(mScrollChangeWatcher)
            mListView != null -> mListView?.setOnScrollListener(null)
            mUiDataModel != null -> mUiDataModel?.removeTabScrollListener(mScrollChangeWatcher)
        }
    }

    /**
     * @param shouldShowDropShadow `true` indicates the drop shadow should be displayed;
     * `false` indicates the drop shadow should be hidden
     */
    private fun updateDropShadow(shouldShowDropShadow: Boolean) {
        if (!shouldShowDropShadow && mDropShadowView.alpha != 0f) {
            if (DataModel.dataModel.isApplicationInForeground) {
                mDropShadowAnimator.reverse()
            } else {
                mDropShadowView.alpha = 0f
            }
            mHairlineView?.visibility = View.VISIBLE
        }
        if (shouldShowDropShadow && mDropShadowView.alpha != 1f) {
            if (DataModel.dataModel.isApplicationInForeground) {
                mDropShadowAnimator.start()
            } else {
                mDropShadowView.alpha = 1f
            }
            mHairlineView?.visibility = View.INVISIBLE
        }
    }

    /**
     * Update the drop shadow as the scrollable entity is scrolled.
     */
    private inner class ScrollChangeWatcher
        : RecyclerView.OnScrollListener(), TabScrollListener, AbsListView.OnScrollListener {
        // RecyclerView scrolled.
        override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
            updateDropShadow(!Utils.isScrolledToTop(view))
        }

        // ListView scrolled.
        override fun onScrollStateChanged(view: AbsListView, scrollState: Int) {
        }

        override fun onScroll(
            view: AbsListView,
            firstVisibleItem: Int,
            visibleItemCount: Int,
            totalItemCount: Int
        ) {
            updateDropShadow(!Utils.isScrolledToTop(view))
        }

        // UiDataModel reports scroll change.
        override fun selectedTabScrollToTopChanged(
            selectedTab: UiDataModel.Tab,
            scrolledToTop: Boolean
        ) {
            updateDropShadow(!scrolledToTop)
        }
    }
}