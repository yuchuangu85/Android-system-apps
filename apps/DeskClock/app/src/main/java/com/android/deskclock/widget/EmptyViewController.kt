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

package com.android.deskclock.widget

import android.transition.Fade
import android.transition.Transition
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.View
import android.view.ViewGroup

import com.android.deskclock.Utils

/**
 * Controller that displays empty view and handles animation appropriately.
 *
 * @param contentView The view that should be displayed when empty view is hidden.
 * @param emptyView The view that should be displayed when main view is empty.
 */
class EmptyViewController(
    private val mMainLayout: ViewGroup,
    private val mContentView: View,
    private val mEmptyView: View
) {
    private var mEmptyViewTransition: Transition? = null
    private var mIsEmpty = false

    init {
        mEmptyViewTransition = if (USE_TRANSITION_FRAMEWORK) {
            TransitionSet()
                    .setOrdering(TransitionSet.ORDERING_SEQUENTIAL)
                    .addTarget(mContentView)
                    .addTarget(mEmptyView)
                    .addTransition(Fade(Fade.OUT))
                    .addTransition(Fade(Fade.IN))
                    .setDuration(ANIMATION_DURATION.toLong())
        } else {
            null
        }
    }

    /**
     * Sets the state for the controller. If it's empty, it will display the empty view.
     *
     * @param isEmpty Whether or not the controller should transition into empty state.
     */
    fun setEmpty(isEmpty: Boolean) {
        if (mIsEmpty == isEmpty) {
            return
        }
        mIsEmpty = isEmpty
        // State changed, perform transition.
        if (USE_TRANSITION_FRAMEWORK) {
            TransitionManager.beginDelayedTransition(mMainLayout, mEmptyViewTransition)
        }
        mEmptyView.visibility = if (mIsEmpty) View.VISIBLE else View.GONE
        mContentView.visibility = if (mIsEmpty) View.GONE else View.VISIBLE
    }

    companion object {
        private const val ANIMATION_DURATION = 300
        private val USE_TRANSITION_FRAMEWORK = Utils.isLOrLater
    }
}