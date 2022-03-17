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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnPreDrawListener
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import androidx.annotation.VisibleForTesting
import androidx.viewpager.widget.ViewPager

import com.android.deskclock.data.DataModel
import com.android.deskclock.data.Timer
import com.android.deskclock.data.TimerListener
import com.android.deskclock.data.TimerStringFormatter
import com.android.deskclock.events.Events
import com.android.deskclock.uidata.UiDataModel
import com.android.deskclock.AnimatorUtils
import com.android.deskclock.DeskClock
import com.android.deskclock.DeskClockFragment
import com.android.deskclock.FabContainer
import com.android.deskclock.R
import com.android.deskclock.Utils

import java.io.Serializable
import kotlin.math.max
import kotlin.math.min

/**
 * Displays a vertical list of timers in all states.
 */
class TimerFragment : DeskClockFragment(UiDataModel.Tab.TIMERS) {
    /** Notified when the user swipes vertically to change the visible timer.  */
    private val mTimerPageChangeListener = TimerPageChangeListener()

    /** Scheduled to update the timers while at least one is running.  */
    private val mTimeUpdateRunnable: Runnable = TimeUpdateRunnable()

    /** Updates the [.mPageIndicators] in response to timers being added or removed.  */
    private val mTimerWatcher: TimerListener = TimerWatcher()

    private lateinit var mCreateTimerView: TimerSetupView
    private lateinit var mViewPager: ViewPager
    private lateinit var mAdapter: TimerPagerAdapter
    private var mTimersView: View? = null
    private var mCurrentView: View? = null
    private lateinit var mPageIndicators: Array<ImageView>

    private var mTimerSetupState: Serializable? = null

    /** `true` while this fragment is creating a new timer; `false` otherwise.  */
    private var mCreatingTimer = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.timer_fragment, container, false)

        mAdapter = TimerPagerAdapter(parentFragmentManager)
        mViewPager = view.findViewById<View>(R.id.vertical_view_pager) as ViewPager
        mViewPager.setAdapter(mAdapter)
        mViewPager.addOnPageChangeListener(mTimerPageChangeListener)

        mTimersView = view.findViewById(R.id.timer_view)
        mCreateTimerView = view.findViewById<View>(R.id.timer_setup) as TimerSetupView
        mCreateTimerView.setFabContainer(this)
        mPageIndicators = arrayOf(
                view.findViewById<View>(R.id.page_indicator0) as ImageView,
                view.findViewById<View>(R.id.page_indicator1) as ImageView,
                view.findViewById<View>(R.id.page_indicator2) as ImageView,
                view.findViewById<View>(R.id.page_indicator3) as ImageView
        )

        DataModel.dataModel.addTimerListener(mAdapter)
        DataModel.dataModel.addTimerListener(mTimerWatcher)

        // If timer setup state is present, retrieve it to be later honored.
        savedInstanceState?.let {
            mTimerSetupState = it.getSerializable(KEY_TIMER_SETUP_STATE)
        }

        return view
    }

    override fun onStart() {
        super.onStart()

        // Initialize the page indicators.
        updatePageIndicators()
        var createTimer = false
        var showTimerId = -1

        // Examine the intent of the parent activity to determine which view to display.
        val intent = requireActivity().intent
        intent?.let {
            // These extras are single-use; remove them after honoring them.
            createTimer = it.getBooleanExtra(EXTRA_TIMER_SETUP, false)
            it.removeExtra(EXTRA_TIMER_SETUP)

            showTimerId = it.getIntExtra(TimerService.EXTRA_TIMER_ID, -1)
            it.removeExtra(TimerService.EXTRA_TIMER_ID)
        }

        // Choose the view to display in this fragment.
        if (showTimerId != -1) {
            // A specific timer must be shown; show the list of timers.
            showTimersView(FabContainer.FAB_AND_BUTTONS_IMMEDIATE)
        } else if (!hasTimers() || createTimer || mTimerSetupState != null) {
            // No timers exist, a timer is being created, or the last view was timer setup;
            // show the timer setup view.
            showCreateTimerView(FabContainer.FAB_AND_BUTTONS_IMMEDIATE)

            if (mTimerSetupState != null) {
                mCreateTimerView.state = mTimerSetupState
                mTimerSetupState = null
            }
        } else {
            // Otherwise, default to showing the list of timers.
            showTimersView(FabContainer.FAB_AND_BUTTONS_IMMEDIATE)
        }

        // If the intent did not specify a timer to show, show the last timer that expired.
        if (showTimerId == -1) {
            val timer: Timer? = DataModel.dataModel.mostRecentExpiredTimer
            showTimerId = timer?.id ?: -1
        }

        // If a specific timer should be displayed, display the corresponding timer tab.
        if (showTimerId != -1) {
            val timer: Timer? = DataModel.dataModel.getTimer(showTimerId)
            timer?.let {
                val index: Int = DataModel.dataModel.timers.indexOf(it)
                mViewPager.setCurrentItem(index)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // We may have received a new intent while paused.
        val intent = requireActivity().intent
        if (intent != null && intent.hasExtra(TimerService.EXTRA_TIMER_ID)) {
            // This extra is single-use; remove after honoring it.
            val showTimerId = intent.getIntExtra(TimerService.EXTRA_TIMER_ID, -1)
            intent.removeExtra(TimerService.EXTRA_TIMER_ID)

            val timer: Timer? = DataModel.dataModel.getTimer(showTimerId)
            timer?.let {
                // A specific timer must be shown; show the list of timers.
                val index: Int = DataModel.dataModel.timers.indexOf(it)
                mViewPager.setCurrentItem(index)

                animateToView(mTimersView, null, false)
            }
        }
    }

    override fun onStop() {
        super.onStop()

        // Stop updating the timers when this fragment is no longer visible.
        stopUpdatingTime()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        DataModel.dataModel.removeTimerListener(mAdapter)
        DataModel.dataModel.removeTimerListener(mTimerWatcher)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // If the timer creation view is visible, store the input for later restoration.
        if (mCurrentView === mCreateTimerView) {
            mTimerSetupState = mCreateTimerView.state
            outState.putSerializable(KEY_TIMER_SETUP_STATE, mTimerSetupState)
        }
    }

    private fun updateFab(fab: ImageView, animate: Boolean) {
        if (mCurrentView === mTimersView) {
            val timer = timer
            if (timer == null) {
                fab.visibility = View.INVISIBLE
                return
            }

            fab.visibility = View.VISIBLE
            when (timer.state) {
                Timer.State.RUNNING -> {
                    if (animate) {
                        fab.setImageResource(R.drawable.ic_play_pause_animation)
                    } else {
                        fab.setImageResource(R.drawable.ic_play_pause)
                    }
                    fab.contentDescription = fab.resources.getString(R.string.timer_stop)
                }
                Timer.State.RESET -> {
                    if (animate) {
                        fab.setImageResource(R.drawable.ic_stop_play_animation)
                    } else {
                        fab.setImageResource(R.drawable.ic_pause_play)
                    }
                    fab.contentDescription = fab.resources.getString(R.string.timer_start)
                }
                Timer.State.PAUSED -> {
                    if (animate) {
                        fab.setImageResource(R.drawable.ic_pause_play_animation)
                    } else {
                        fab.setImageResource(R.drawable.ic_pause_play)
                    }
                    fab.contentDescription = fab.resources.getString(R.string.timer_start)
                }
                Timer.State.MISSED, Timer.State.EXPIRED -> {
                    fab.setImageResource(R.drawable.ic_stop_white_24dp)
                    fab.contentDescription = fab.resources.getString(R.string.timer_stop)
                }
            }
        } else if (mCurrentView === mCreateTimerView) {
            if (mCreateTimerView.hasValidInput()) {
                fab.setImageResource(R.drawable.ic_start_white_24dp)
                fab.contentDescription = fab.resources.getString(R.string.timer_start)
                fab.visibility = View.VISIBLE
            } else {
                fab.contentDescription = null
                fab.visibility = View.INVISIBLE
            }
        }
    }

    override fun onUpdateFab(fab: ImageView) {
        updateFab(fab, false)
    }

    override fun onMorphFab(fab: ImageView) {
        // Update the fab's drawable to match the current timer state.
        updateFab(fab, Utils.isNOrLater)
        // Animate the drawable.
        AnimatorUtils.startDrawableAnimation(fab)
    }

    override fun onUpdateFabButtons(left: Button, right: Button) {
        if (mCurrentView === mTimersView) {
            left.isClickable = true
            left.setText(R.string.timer_delete)
            left.contentDescription = left.resources.getString(R.string.timer_delete)
            left.visibility = View.VISIBLE

            right.isClickable = true
            right.setText(R.string.timer_add_timer)
            right.contentDescription = right.resources.getString(R.string.timer_add_timer)
            right.visibility = View.VISIBLE
        } else if (mCurrentView === mCreateTimerView) {
            left.isClickable = true
            left.setText(R.string.timer_cancel)
            left.contentDescription = left.resources.getString(R.string.timer_cancel)
            // If no timers yet exist, the user is forced to create the first one.
            left.visibility = if (hasTimers()) View.VISIBLE else View.INVISIBLE

            right.visibility = View.INVISIBLE
        }
    }

    override fun onFabClick(fab: ImageView) {
        if (mCurrentView === mTimersView) {
            // If no timer is currently showing a fab action is meaningless.
            val timer = timer ?: return

            val context = fab.context
            val currentTime: Long = timer.remainingTime

            when (timer.state) {
                Timer.State.RUNNING -> {
                    DataModel.dataModel.pauseTimer(timer)
                    Events.sendTimerEvent(R.string.action_stop, R.string.label_deskclock)
                    if (currentTime > 0) {
                        mTimersView?.announceForAccessibility(TimerStringFormatter.formatString(
                                context, R.string.timer_accessibility_stopped, currentTime, true))
                    }
                }
                Timer.State.PAUSED, Timer.State.RESET -> {
                    DataModel.dataModel.startTimer(timer)
                    Events.sendTimerEvent(R.string.action_start, R.string.label_deskclock)
                    if (currentTime > 0) {
                        mTimersView?.announceForAccessibility(TimerStringFormatter.formatString(
                                context, R.string.timer_accessibility_started, currentTime, true))
                    }
                }
                Timer.State.MISSED, Timer.State.EXPIRED -> {
                    DataModel.dataModel.resetOrDeleteTimer(timer, R.string.label_deskclock)
                }
            }
        } else if (mCurrentView === mCreateTimerView) {
            mCreatingTimer = true
            try {
                // Create the new timer.
                val timerLength: Long = mCreateTimerView.timeInMillis
                val timer: Timer = DataModel.dataModel.addTimer(timerLength, "", false)
                Events.sendTimerEvent(R.string.action_create, R.string.label_deskclock)

                // Start the new timer.
                DataModel.dataModel.startTimer(timer)
                Events.sendTimerEvent(R.string.action_start, R.string.label_deskclock)

                // Display the freshly created timer view.
                mViewPager.setCurrentItem(0)
            } finally {
                mCreatingTimer = false
            }

            // Return to the list of timers.
            animateToView(mTimersView, null, true)
        }
    }

    override fun onLeftButtonClick(left: Button) {
        if (mCurrentView === mTimersView) {
            // Clicking the "delete" button.
            val timer = timer ?: return

            if (mAdapter.getCount() > 1) {
                animateTimerRemove(timer)
            } else {
                animateToView(mCreateTimerView, timer, false)
            }

            left.announceForAccessibility(requireActivity().getString(R.string.timer_deleted))
        } else if (mCurrentView === mCreateTimerView) {
            // Clicking the "cancel" button on the timer creation page returns to the timers list.
            mCreateTimerView.reset()

            animateToView(mTimersView, null, false)

            left.announceForAccessibility(requireActivity().getString(R.string.timer_canceled))
        }
    }

    override fun onRightButtonClick(right: Button) {
        if (mCurrentView !== mCreateTimerView) {
            animateToView(mCreateTimerView, null, true)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return if (mCurrentView === mCreateTimerView) {
            mCreateTimerView.onKeyDown(keyCode, event)
        } else super.onKeyDown(keyCode, event)
    }

    /**
     * Updates the state of the page indicators so they reflect the selected page in the context of
     * all pages.
     */
    private fun updatePageIndicators() {
        val page: Int = mViewPager.getCurrentItem()
        val pageIndicatorCount = mPageIndicators.size
        val pageCount = mAdapter.getCount()

        val states = computePageIndicatorStates(page, pageIndicatorCount, pageCount)
        for (i in states.indices) {
            val state = states[i]
            val pageIndicator = mPageIndicators[i]
            if (state == 0) {
                pageIndicator.visibility = View.GONE
            } else {
                pageIndicator.visibility = View.VISIBLE
                pageIndicator.setImageResource(state)
            }
        }
    }

    /**
     * Display the view that creates a new timer.
     */
    private fun showCreateTimerView(updateTypes: Int) {
        // Stop animating the timers.
        stopUpdatingTime()

        // Show the creation view; hide the timer view.
        mTimersView?.visibility = View.GONE
        mCreateTimerView.visibility = View.VISIBLE

        // Record the fact that the create view is visible.
        mCurrentView = mCreateTimerView

        // Update the fab and buttons.
        updateFab(updateTypes)
    }

    /**
     * Display the view that lists all existing timers.
     */
    private fun showTimersView(updateTypes: Int) {
        // Clear any defunct timer creation state; the next timer creation starts fresh.
        mTimerSetupState = null

        // Show the timer view; hide the creation view.
        mTimersView?.visibility = View.VISIBLE
        mCreateTimerView.visibility = View.GONE

        // Record the fact that the create view is visible.
        mCurrentView = mTimersView

        // Update the fab and buttons.
        updateFab(updateTypes)

        // Start animating the timers.
        startUpdatingTime()
    }

    /**
     * @param timerToRemove the timer to be removed during the animation
     */
    private fun animateTimerRemove(timerToRemove: Timer) {
        val duration = UiDataModel.uiDataModel.shortAnimationDuration

        val fadeOut: Animator = ObjectAnimator.ofFloat(mViewPager, View.ALPHA, 1f, 0f)
        fadeOut.duration = duration
        fadeOut.interpolator = DecelerateInterpolator()
        fadeOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                DataModel.dataModel.removeTimer(timerToRemove)
                Events.sendTimerEvent(R.string.action_delete, R.string.label_deskclock)
            }
        })

        val fadeIn: Animator = ObjectAnimator.ofFloat(mViewPager, View.ALPHA, 0f, 1f)
        fadeIn.duration = duration
        fadeIn.interpolator = AccelerateInterpolator()

        val animatorSet = AnimatorSet()
        animatorSet.play(fadeOut).before(fadeIn)
        animatorSet.start()
    }

    /**
     * @param toView one of [.mTimersView] or [.mCreateTimerView]
     * @param timerToRemove the timer to be removed during the animation; `null` if no timer
     * should be removed
     * @param animateDown `true` if the views should animate upwards, otherwise downwards
     */
    private fun animateToView(
        toView: View?,
        timerToRemove: Timer?,
        animateDown: Boolean
    ) {
        if (mCurrentView === toView) {
            return
        }

        val toTimers = toView === mTimersView
        if (toTimers) {
            mTimersView?.visibility = View.VISIBLE
        } else {
            mCreateTimerView.visibility = View.VISIBLE
        }
        // Avoid double-taps by enabling/disabling the set of buttons active on the new view.
        updateFab(FabContainer.BUTTONS_DISABLE)

        val animationDuration = UiDataModel.uiDataModel.longAnimationDuration

        val viewTreeObserver = toView!!.viewTreeObserver
        viewTreeObserver.addOnPreDrawListener(object : OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (viewTreeObserver.isAlive) {
                    viewTreeObserver.removeOnPreDrawListener(this)
                }

                val view = mTimersView?.findViewById<View>(R.id.timer_time)
                val distanceY: Float = if (view != null) view.height + view.y else 0f
                val translationDistance = if (animateDown) distanceY else -distanceY

                toView.translationY = -translationDistance
                mCurrentView?.translationY = 0f
                toView.alpha = 0f
                mCurrentView?.alpha = 1f

                val translateCurrent: Animator = ObjectAnimator.ofFloat(mCurrentView,
                        View.TRANSLATION_Y, translationDistance)
                val translateNew: Animator = ObjectAnimator.ofFloat(toView, View.TRANSLATION_Y, 0f)
                val translationAnimatorSet = AnimatorSet()
                translationAnimatorSet.playTogether(translateCurrent, translateNew)
                translationAnimatorSet.duration = animationDuration
                translationAnimatorSet.interpolator = AnimatorUtils.INTERPOLATOR_FAST_OUT_SLOW_IN

                val fadeOutAnimator: Animator = ObjectAnimator.ofFloat(mCurrentView, View.ALPHA, 0f)
                fadeOutAnimator.duration = animationDuration / 2
                fadeOutAnimator.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        super.onAnimationStart(animation)

                        // The fade-out animation and fab-shrinking animation should run together.
                        updateFab(FabContainer.FAB_AND_BUTTONS_SHRINK)
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        if (toTimers) {
                            showTimersView(FabContainer.FAB_AND_BUTTONS_EXPAND)

                            // Reset the state of the create view.
                            mCreateTimerView.reset()
                        } else {
                            showCreateTimerView(FabContainer.FAB_AND_BUTTONS_EXPAND)
                        }
                        if (timerToRemove != null) {
                            DataModel.dataModel.removeTimer(timerToRemove)
                            Events.sendTimerEvent(R.string.action_delete, R.string.label_deskclock)
                        }

                        // Update the fab and button states now that the correct view is visible and
                        // before the animation to expand the fab and buttons starts.
                        updateFab(FabContainer.FAB_AND_BUTTONS_IMMEDIATE)
                    }
                })

                val fadeInAnimator: Animator = ObjectAnimator.ofFloat(toView, View.ALPHA, 1f)
                fadeInAnimator.duration = animationDuration / 2
                fadeInAnimator.startDelay = animationDuration / 2

                val animatorSet = AnimatorSet()
                animatorSet.playTogether(fadeOutAnimator, fadeInAnimator, translationAnimatorSet)
                animatorSet.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        mTimersView?.translationY = 0f
                        mCreateTimerView.translationY = 0f
                        mTimersView?.alpha = 1f
                        mCreateTimerView.alpha = 1f
                    }
                })
                animatorSet.start()

                return true
            }
        })
    }

    private fun hasTimers(): Boolean {
        return mAdapter.getCount() > 0
    }

    private val timer: Timer?
        get() {
            if (!::mViewPager.isInitialized) {
                return null
            }

            return if (mAdapter.getCount() == 0) {
                null
            } else {
                mAdapter.getTimer(mViewPager.getCurrentItem())
            }
        }

    private fun startUpdatingTime() {
        // Ensure only one copy of the runnable is ever scheduled by first stopping updates.
        stopUpdatingTime()
        mViewPager.post(mTimeUpdateRunnable)
    }

    private fun stopUpdatingTime() {
        mViewPager.removeCallbacks(mTimeUpdateRunnable)
    }

    /**
     * Periodically refreshes the state of each timer.
     */
    private inner class TimeUpdateRunnable : Runnable {
        override fun run() {
            val startTime = SystemClock.elapsedRealtime()
            // If no timers require continuous updates, avoid scheduling the next update.
            if (!mAdapter.updateTime()) {
                return
            }
            val endTime = SystemClock.elapsedRealtime()

            // Try to maintain a consistent period of time between redraws.
            val delay = max(0, startTime + 20 - endTime)
            mTimersView?.postDelayed(this, delay)
        }
    }

    /**
     * Update the page indicators and fab in response to a new timer becoming visible.
     */
    private inner class TimerPageChangeListener : ViewPager.SimpleOnPageChangeListener() {
        override fun onPageSelected(position: Int) {
            updatePageIndicators()
            updateFab(FabContainer.FAB_AND_BUTTONS_IMMEDIATE)

            // Showing a new timer page may introduce a timer requiring continuous updates.
            startUpdatingTime()
        }

        override fun onPageScrollStateChanged(state: Int) {
            // Teasing a neighboring timer may introduce a timer requiring continuous updates.
            if (state == ViewPager.SCROLL_STATE_DRAGGING) {
                startUpdatingTime()
            }
        }
    }

    /**
     * Update the page indicators in response to timers being added or removed.
     * Update the fab in response to the visible timer changing.
     */
    private inner class TimerWatcher : TimerListener {
        override fun timerAdded(timer: Timer) {
            updatePageIndicators()
            // If the timer is being created via this fragment avoid adjusting the fab.
            // Timer setup view is about to be animated away in response to this timer creation.
            // Changes to the fab immediately preceding that animation are jarring.
            if (!mCreatingTimer) {
                updateFab(FabContainer.FAB_AND_BUTTONS_IMMEDIATE)
            }
        }

        override fun timerUpdated(before: Timer, after: Timer) {
            // If the timer started, animate the timers.
            if (before.isReset && !after.isReset) {
                startUpdatingTime()
            }

            // Fetch the index of the change.
            val index: Int = DataModel.dataModel.timers.indexOf(after)

            // If the timer just expired but is not displayed, display it now.
            if (!before.isExpired && after.isExpired && index != mViewPager.getCurrentItem()) {
                mViewPager.setCurrentItem(index, true)
            } else if (mCurrentView === mTimersView && index == mViewPager.getCurrentItem()) {
                // Morph the fab from its old state to new state if necessary.
                if (before.state != after.state &&
                        !(before.isPaused && after.isReset)) {
                    updateFab(FabContainer.FAB_MORPH)
                }
            }
        }

        override fun timerRemoved(timer: Timer) {
            updatePageIndicators()
            updateFab(FabContainer.FAB_AND_BUTTONS_IMMEDIATE)

            if (mCurrentView === mTimersView && mAdapter.getCount() == 0) {
                animateToView(mCreateTimerView, null, false)
            }
        }
    }

    companion object {
        private const val EXTRA_TIMER_SETUP = "com.android.deskclock.action.TIMER_SETUP"

        private const val KEY_TIMER_SETUP_STATE = "timer_setup_input"

        /**
         * @return an Intent that selects the timers tab with the
         * setup screen for a new timer in place.
         */
        @VisibleForTesting
        @JvmStatic
        fun createTimerSetupIntent(context: Context): Intent {
            return Intent(context, DeskClock::class.java).putExtra(EXTRA_TIMER_SETUP, true)
        }

        /**
         * @param page the selected page; value between 0 and `pageCount`
         * @param pageIndicatorCount the number of indicators displaying the `page` location
         * @param pageCount the number of pages that exist
         * @return an array of length `pageIndicatorCount` specifying which image to display for
         * each page indicator or 0 if the page indicator should be hidden
         */
        @VisibleForTesting
        @JvmStatic
        fun computePageIndicatorStates(
            page: Int,
            pageIndicatorCount: Int,
            pageCount: Int
        ): IntArray {
            // Compute the number of page indicators that will be visible.
            val rangeSize = min(pageIndicatorCount, pageCount)

            // Compute the inclusive range of pages to indicate centered around the selected page.
            var rangeStart = page - rangeSize / 2
            var rangeEnd = rangeStart + rangeSize - 1

            // Clamp the range of pages if they extend beyond the last page.
            if (rangeEnd >= pageCount) {
                rangeEnd = pageCount - 1
                rangeStart = rangeEnd - rangeSize + 1
            }

            // Clamp the range of pages if they extend beyond the first page.
            if (rangeStart < 0) {
                rangeStart = 0
                rangeEnd = rangeSize - 1
            }

            // Build the result with all page indicators initially hidden.
            val states = IntArray(pageIndicatorCount)
            states.fill(0)

            // If 0 or 1 total pages exist, all page indicators must remain hidden.
            if (rangeSize < 2) {
                return states
            }

            // Initialize the visible page indicators to be dark.
            states.fill(R.drawable.ic_swipe_circle_dark, 0, rangeSize)

            // If more pages exist before the first page indicator, make it a fade-in gradient.
            if (rangeStart > 0) {
                states[0] = R.drawable.ic_swipe_circle_top
            }

            // If more pages exist after the last page indicator, make it a fade-out gradient.
            if (rangeEnd < pageCount - 1) {
                states[rangeSize - 1] = R.drawable.ic_swipe_circle_bottom
            }

            // Set the indicator of the selected page to be light.
            states[page - rangeStart] = R.drawable.ic_swipe_circle_light
            return states
        }
    }
}