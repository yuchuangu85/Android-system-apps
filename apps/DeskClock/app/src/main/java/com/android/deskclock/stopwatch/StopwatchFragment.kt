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

package com.android.deskclock.stopwatch

import android.R.attr.state_activated
import android.R.attr.state_pressed
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM
import android.os.Bundle
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator

import com.android.deskclock.AnimatorUtils
import com.android.deskclock.DeskClockFragment
import com.android.deskclock.FabContainer
import com.android.deskclock.FabContainer.UpdateFabFlag
import com.android.deskclock.data.DataModel
import com.android.deskclock.data.Lap
import com.android.deskclock.data.Stopwatch
import com.android.deskclock.data.StopwatchListener
import com.android.deskclock.events.Events
import com.android.deskclock.LogUtils
import com.android.deskclock.R
import com.android.deskclock.StopwatchTextController
import com.android.deskclock.ThemeUtils
import com.android.deskclock.Utils
import com.android.deskclock.uidata.TabListener
import com.android.deskclock.uidata.UiDataModel

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Fragment that shows the stopwatch and recorded laps.
 */
class StopwatchFragment : DeskClockFragment(UiDataModel.Tab.STOPWATCH) {

    /** Keep the screen on when this tab is selected.  */
    private val mTabWatcher: TabListener = TabWatcher()

    /** Scheduled to update the stopwatch time and current lap time while stopwatch is running.  */
    private val mTimeUpdateRunnable: Runnable = TimeUpdateRunnable()

    /** Updates the user interface in response to stopwatch changes.  */
    private val mStopwatchWatcher: StopwatchListener = StopwatchWatcher()

    /** Draws a gradient over the bottom of the [.mLapsList] to reduce clash with the fab.  */
    private var mGradientItemDecoration: GradientItemDecoration? = null

    /** The data source for [.mLapsList].  */
    private lateinit var mLapsAdapter: LapsAdapter

    /** The layout manager for the [.mLapsAdapter].  */
    private lateinit var mLapsLayoutManager: LinearLayoutManager

    /** Draws the reference lap while the stopwatch is running.  */
    private var mTime: StopwatchCircleView? = null

    /** The View containing both TextViews of the stopwatch.  */
    private lateinit var mStopwatchWrapper: View

    /** Displays the recorded lap times.  */
    private lateinit var mLapsList: RecyclerView

    /** Displays the current stopwatch time (seconds and above only).  */
    private lateinit var mMainTimeText: TextView

    /** Displays the current stopwatch time (hundredths only).  */
    private lateinit var mHundredthsTimeText: TextView

    /** Formats and displays the text in the stopwatch.  */
    private lateinit var mStopwatchTextController: StopwatchTextController

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        state: Bundle?
    ): View {
        mLapsAdapter = LapsAdapter(requireActivity())
        mLapsLayoutManager = LinearLayoutManager(requireActivity())
        mGradientItemDecoration = GradientItemDecoration(requireActivity())

        val v: View = inflater.inflate(R.layout.stopwatch_fragment, container, false)
        mTime = v.findViewById(R.id.stopwatch_circle)
        mLapsList = v.findViewById(R.id.laps_list) as RecyclerView
        (mLapsList.getItemAnimator() as SimpleItemAnimator).setSupportsChangeAnimations(false)
        mLapsList.setLayoutManager(mLapsLayoutManager)
        mLapsList.addItemDecoration(mGradientItemDecoration!!)

        // In landscape layouts, the laps list can reach the top of the screen and thus can cause
        // a drop shadow to appear. The same is not true for portrait landscapes.
        if (Utils.isLandscape(requireActivity())) {
            val scrollPositionWatcher = ScrollPositionWatcher()
            mLapsList.addOnLayoutChangeListener(scrollPositionWatcher)
            mLapsList.addOnScrollListener(scrollPositionWatcher)
        } else {
            setTabScrolledToTop(true)
        }
        mLapsList.setAdapter(mLapsAdapter)

        // Timer text serves as a virtual start/stop button.
        mMainTimeText = v.findViewById(R.id.stopwatch_time_text) as TextView
        mHundredthsTimeText = v.findViewById(R.id.stopwatch_hundredths_text) as TextView
        mStopwatchTextController = StopwatchTextController(mMainTimeText, mHundredthsTimeText)
        mStopwatchWrapper = v.findViewById(R.id.stopwatch_time_wrapper)

        DataModel.dataModel.addStopwatchListener(mStopwatchWatcher)

        mStopwatchWrapper.setOnClickListener(TimeClickListener())
        if (mTime != null) {
            mStopwatchWrapper.setOnTouchListener(CircleTouchListener())
        }

        val c: Context = mMainTimeText.getContext()
        val colorAccent = ThemeUtils.resolveColor(c, R.attr.colorAccent)
        val textColorPrimary = ThemeUtils.resolveColor(c, android.R.attr.textColorPrimary)
        val timeTextColor =
                ColorStateList(
                        arrayOf(intArrayOf(-state_activated, -state_pressed), intArrayOf()),
                        intArrayOf(textColorPrimary, colorAccent)
                )
        mMainTimeText.setTextColor(timeTextColor)
        mHundredthsTimeText.setTextColor(timeTextColor)

        return v
    }

    override fun onStart() {
        super.onStart()

        val activity: Activity = requireActivity()
        val intent: Intent? = activity.getIntent()
        if (intent != null) {
            val action: String? = intent.getAction()
            if (StopwatchService.Companion.ACTION_START_STOPWATCH == action) {
                DataModel.dataModel.startStopwatch()
                // Consume the intent
                activity.setIntent(null)
            } else if (StopwatchService.Companion.ACTION_PAUSE_STOPWATCH == action) {
                DataModel.dataModel.pauseStopwatch()
                // Consume the intent
                activity.setIntent(null)
            }
        }

        // Conservatively assume the data in the adapter has changed while the fragment was paused.
        mLapsAdapter.notifyDataSetChanged()

        // Synchronize the user interface with the data model.
        updateUI(FabContainer.FAB_AND_BUTTONS_IMMEDIATE)

        // Start watching for page changes away from this fragment.
        UiDataModel.uiDataModel.addTabListener(mTabWatcher)
    }

    override fun onStop() {
        super.onStop()

        // Stop all updates while the fragment is not visible.
        stopUpdatingTime()

        // Stop watching for page changes away from this fragment.
        UiDataModel.uiDataModel.removeTabListener(mTabWatcher)

        // Release the wake lock if it is currently held.
        releaseWakeLock()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        DataModel.dataModel.removeStopwatchListener(mStopwatchWatcher)
    }

    override fun onFabClick(fab: ImageView) {
        toggleStopwatchState()
    }

    override fun onLeftButtonClick(left: Button) {
        doReset()
    }

    override fun onRightButtonClick(right: Button) {
        when (stopwatch.state) {
            Stopwatch.State.RUNNING -> doAddLap()
            Stopwatch.State.PAUSED -> doShare()
            Stopwatch.State.RESET -> {
            }
            null -> {
            }
        }
    }

    private fun updateFab(fab: ImageView, animate: Boolean) {
        if (stopwatch.isRunning) {
            if (animate) {
                fab.setImageResource(R.drawable.ic_play_pause_animation)
            } else {
                fab.setImageResource(R.drawable.ic_play_pause)
            }
            fab.setContentDescription(fab.getResources().getString(R.string.sw_pause_button))
        } else {
            if (animate) {
                fab.setImageResource(R.drawable.ic_pause_play_animation)
            } else {
                fab.setImageResource(R.drawable.ic_pause_play)
            }
            fab.setContentDescription(fab.getResources().getString(R.string.sw_start_button))
        }
        fab.setVisibility(VISIBLE)
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
        val resources: Resources = getResources()
        left.setClickable(true)
        left.setText(R.string.sw_reset_button)
        left.setContentDescription(resources.getString(R.string.sw_reset_button))

        when (stopwatch.state) {
            Stopwatch.State.RESET -> {
                left.setVisibility(INVISIBLE)
                right.setClickable(true)
                right.setVisibility(INVISIBLE)
            }
            Stopwatch.State.RUNNING -> {
                left.setVisibility(VISIBLE)
                val canRecordLaps = canRecordMoreLaps()
                right.setText(R.string.sw_lap_button)
                right.setContentDescription(resources.getString(R.string.sw_lap_button))
                right.setClickable(canRecordLaps)
                right.setVisibility(if (canRecordLaps) VISIBLE else INVISIBLE)
            }
            Stopwatch.State.PAUSED -> {
                left.setVisibility(VISIBLE)
                right.setClickable(true)
                right.setVisibility(VISIBLE)
                right.setText(R.string.sw_share_button)
                right.setContentDescription(resources.getString(R.string.sw_share_button))
            }
            null -> {
            }
        }
    }

    /**
     * @param color the newly installed app window color
     */
    override fun onAppColorChanged(@ColorInt color: Int) {
        mGradientItemDecoration?.updateGradientColors(color)
        mLapsList.invalidateItemDecorations()
    }

    /**
     * Start the stopwatch.
     */
    private fun doStart() {
        Events.sendStopwatchEvent(R.string.action_start, R.string.label_deskclock)
        DataModel.dataModel.startStopwatch()
    }

    /**
     * Pause the stopwatch.
     */
    private fun doPause() {
        Events.sendStopwatchEvent(R.string.action_pause, R.string.label_deskclock)
        DataModel.dataModel.pauseStopwatch()
    }

    /**
     * Reset the stopwatch.
     */
    private fun doReset() {
        val priorState = stopwatch.state
        Events.sendStopwatchEvent(R.string.action_reset, R.string.label_deskclock)
        DataModel.dataModel.resetStopwatch()
        mMainTimeText.setAlpha(1f)
        mHundredthsTimeText.setAlpha(1f)
        if (priorState == Stopwatch.State.RUNNING) {
            updateFab(FabContainer.FAB_MORPH)
        }
    }

    /**
     * Send stopwatch time and lap times to an external sharing application.
     */
    private fun doShare() {
        // Disable the fab buttons to avoid double-taps on the share button.
        updateFab(FabContainer.BUTTONS_DISABLE)

        val subjects: Array<String> = getResources().getStringArray(R.array.sw_share_strings)
        val subject = subjects[(Math.random() * subjects.size).toInt()]
        val text = mLapsAdapter.shareText

        @SuppressLint("InlinedApi")
        val shareIntent: Intent = Intent(Intent.ACTION_SEND)
                .addFlags(if (Utils.isLOrLater) {
                    Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                } else {
                    Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET
                })
                .putExtra(Intent.EXTRA_SUBJECT, subject)
                .putExtra(Intent.EXTRA_TEXT, text)
                .setType("text/plain")

        val context: Context = requireActivity()
        val title: String = context.getString(R.string.sw_share_button)
        val shareChooserIntent: Intent = Intent.createChooser(shareIntent, title)
        try {
            context.startActivity(shareChooserIntent)
        } catch (anfe: ActivityNotFoundException) {
            LogUtils.e("Cannot share lap data because no suitable receiving Activity exists")
            updateFab(FabContainer.BUTTONS_IMMEDIATE)
        }
    }

    /**
     * Record and add a new lap ending now.
     */
    private fun doAddLap() {
        Events.sendStopwatchEvent(R.string.action_lap, R.string.label_deskclock)

        // Record a new lap.
        val lap = mLapsAdapter.addLap() ?: return

        // Update button states.
        updateFab(FabContainer.BUTTONS_IMMEDIATE)
        if (lap.lapNumber == 1) {
            // Child views from prior lap sets hang around and blit to the screen when adding the
            // first lap of the subsequent lap set. Remove those superfluous children here manually
            // to ensure they aren't seen as the first lap is drawn.
            mLapsList.removeAllViewsInLayout()
            if (mTime != null) {
                // Start animating the reference lap.
                mTime!!.update()
            }

            // Recording the first lap transitions the UI to display the laps list.
            showOrHideLaps(false)
        }

        // Ensure the newly added lap is visible on screen.
        mLapsList.scrollToPosition(0)
    }

    /**
     * Show or hide the list of laps.
     */
    private fun showOrHideLaps(clearLaps: Boolean) {
        val sceneRoot: ViewGroup = getView() as ViewGroup? ?: return

        TransitionManager.beginDelayedTransition(sceneRoot)

        if (clearLaps) {
            mLapsAdapter.clearLaps()
        }

        val lapsVisible = mLapsAdapter.getItemCount() > 0
        mLapsList.setVisibility(if (lapsVisible) VISIBLE else GONE)

        if (Utils.isPortrait(requireActivity())) {
            // When the lap list is visible, it includes the bottom padding. When it is absent the
            // appropriate bottom padding must be applied to the container.
            val res: Resources = getResources()
            val bottom = if (lapsVisible) 0 else res.getDimensionPixelSize(R.dimen.fab_height)
            val top: Int = sceneRoot.getPaddingTop()
            val left: Int = sceneRoot.getPaddingLeft()
            val right: Int = sceneRoot.getPaddingRight()
            sceneRoot.setPadding(left, top, right, bottom)
        }
    }

    private fun adjustWakeLock() {
        val appInForeground = DataModel.dataModel.isApplicationInForeground
        if (stopwatch.isRunning && isTabSelected && appInForeground) {
            requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            releaseWakeLock()
        }
    }

    private fun releaseWakeLock() {
        requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * Either pause or start the stopwatch based on its current state.
     */
    private fun toggleStopwatchState() {
        if (stopwatch.isRunning) {
            doPause()
        } else {
            doStart()
        }
    }

    private val stopwatch: Stopwatch
        get() = DataModel.dataModel.stopwatch

    private fun canRecordMoreLaps(): Boolean = DataModel.dataModel.canAddMoreLaps()

    /**
     * Post the first runnable to update times within the UI. It will reschedule itself as needed.
     */
    private fun startUpdatingTime() {
        // Ensure only one copy of the runnable is ever scheduled by first stopping updates.
        stopUpdatingTime()
        mMainTimeText.post(mTimeUpdateRunnable)
    }

    /**
     * Remove the runnable that updates times within the UI.
     */
    private fun stopUpdatingTime() {
        mMainTimeText.removeCallbacks(mTimeUpdateRunnable)
    }

    /**
     * Update all time displays based on a single snapshot of the stopwatch progress. This includes
     * the stopwatch time drawn in the circle, the current lap time and the total elapsed time in
     * the list of laps.
     */
    private fun updateTime() {
        // Compute the total time of the stopwatch.
        val stopwatch = stopwatch
        val totalTime = stopwatch.totalTime
        mStopwatchTextController.setTimeString(totalTime)

        // Update the current lap.
        val currentLapIsVisible = mLapsLayoutManager.findFirstVisibleItemPosition() == 0
        if (!stopwatch.isReset && currentLapIsVisible) {
            mLapsAdapter.updateCurrentLap(mLapsList, totalTime)
        }
    }

    /**
     * Synchronize the UI state with the model data.
     */
    private fun updateUI(@UpdateFabFlag updateTypes: Int) {
        adjustWakeLock()

        // Draw the latest stopwatch and current lap times.
        updateTime()
        if (mTime != null) {
            mTime!!.update()
        }
        val stopwatch = stopwatch
        if (!stopwatch.isReset) {
            startUpdatingTime()
        }

        // Adjust the visibility of the list of laps.
        showOrHideLaps(stopwatch.isReset)

        // Update button states.
        updateFab(updateTypes)
    }

    /**
     * This runnable periodically updates times throughout the UI. It stops these updates when the
     * stopwatch is no longer running.
     */
    private inner class TimeUpdateRunnable : Runnable {
        override fun run() {
            val startTime = Utils.now()
            updateTime()

            // Blink text iff the stopwatch is paused and not pressed.
            val touchTarget: View = if (mTime != null) mTime!! else mStopwatchWrapper
            val stopwatch = stopwatch
            val blink = (stopwatch.isPaused && startTime % 1000 < 500 && !touchTarget.isPressed())

            if (blink) {
                mMainTimeText.setAlpha(0f)
                mHundredthsTimeText.setAlpha(0f)
            } else {
                mMainTimeText.setAlpha(1f)
                mHundredthsTimeText.setAlpha(1f)
            }

            if (!stopwatch.isReset) {
                val period = (if (stopwatch.isPaused) {
                    REDRAW_PERIOD_PAUSED
                } else {
                    REDRAW_PERIOD_RUNNING
                }).toLong()
                val endTime = Utils.now()
                val delay: Long = max(0, startTime + period - endTime).toLong()
                mMainTimeText.postDelayed(this, delay)
            }
        }
    }

    /**
     * Acquire or release the wake lock based on the tab state.
     */
    private inner class TabWatcher : TabListener {
        override fun selectedTabChanged(
            oldSelectedTab: UiDataModel.Tab,
            newSelectedTab: UiDataModel.Tab
        ) {
            adjustWakeLock()
        }
    }

    /**
     * Update the user interface in response to a stopwatch change.
     */
    private inner class StopwatchWatcher : StopwatchListener {
        override fun stopwatchUpdated(before: Stopwatch, after: Stopwatch) {
            if (after.isReset) {
                // Ensure the drop shadow is hidden when the stopwatch is reset.
                setTabScrolledToTop(true)
                if (DataModel.dataModel.isApplicationInForeground) {
                    updateUI(FabContainer.BUTTONS_IMMEDIATE)
                }
                return
            }
            if (DataModel.dataModel.isApplicationInForeground) {
                updateUI(FabContainer.FAB_MORPH or FabContainer.BUTTONS_IMMEDIATE)
            }
        }

        override fun lapAdded(lap: Lap) {
        }
    }

    /**
     * Toggles stopwatch state when user taps stopwatch.
     */
    private inner class TimeClickListener : View.OnClickListener {

        override fun onClick(view: View?) {
            if (stopwatch.isRunning) {
                DataModel.dataModel.pauseStopwatch()
            } else {
                DataModel.dataModel.startStopwatch()
            }
        }
    }

    /**
     * Checks if the user is pressing inside of the stopwatch circle.
     */
    private inner class CircleTouchListener : View.OnTouchListener {

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val actionMasked: Int = event.getActionMasked()
            if (actionMasked != MotionEvent.ACTION_DOWN) {
                return false
            }
            val rX: Float = view.getWidth() / 2f
            val rY: Float = (view.getHeight() - view.getPaddingBottom()) / 2f
            val r = min(rX, rY)

            val x: Float = event.getX() - rX
            val y: Float = event.getY() - rY

            val inCircle = (x / r.toDouble()).pow(2.0) + (y / r.toDouble()).pow(2.0) <= 1.0

            // Consume the event if it is outside the circle
            return !inCircle
        }
    }

    /**
     * Updates the vertical scroll state of this tab in the [UiDataModel] as the user scrolls
     * the recyclerview or when the size/position of elements within the recyclerview changes.
     */
    private inner class ScrollPositionWatcher :
            RecyclerView.OnScrollListener(), View.OnLayoutChangeListener {

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            setTabScrolledToTop(Utils.isScrolledToTop(mLapsList))
        }

        override fun onLayoutChange(
            v: View?,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
            oldLeft: Int,
            oldTop: Int,
            oldRight: Int,
            oldBottom: Int
        ) {
            setTabScrolledToTop(Utils.isScrolledToTop(mLapsList))
        }
    }

    /**
     * Draws a tinting gradient over the bottom of the stopwatch laps list. This reduces the
     * contrast between floating buttons and the laps list content.
     */
    private class GradientItemDecoration internal constructor(context: Context)
        : RecyclerView.ItemDecoration() {

        /**
         * A reusable array of control point colors that define the gradient. It is based on the
         * background color of the window and thus recomputed each time that color is changed.
         */
        private val mGradientColors = IntArray(ALPHAS.size)

        /** The drawable that produces the tinting gradient effect of this decoration.  */
        private val mGradient: GradientDrawable = GradientDrawable()

        /** The height of the gradient; sized relative to the fab height.  */
        private val mGradientHeight: Int

        init {
            mGradient.setOrientation(TOP_BOTTOM)
            updateGradientColors(ThemeUtils.resolveColor(context, android.R.attr.windowBackground))

            val resources: Resources = context.getResources()
            val fabHeight: Int = resources.getDimensionPixelSize(R.dimen.fab_height)
            mGradientHeight = (fabHeight * 1.2f).roundToInt()
        }

        override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            super.onDrawOver(c, parent, state)

            val w: Int = parent.getWidth()
            val h: Int = parent.getHeight()

            mGradient.setBounds(0, h - mGradientHeight, w, h)
            mGradient.draw(c)
        }

        /**
         * Given a `baseColor`, compute a gradient of tinted colors that define the fade
         * effect to apply to the bottom of the lap list.
         *
         * @param baseColor a base color to which the gradient tint should be applied
         */
        fun updateGradientColors(@ColorInt baseColor: Int) {
            // Compute the tinted colors that form the gradient.
            mGradientColors.indices.forEach { i ->
                mGradientColors[i] = ColorUtils.setAlphaComponent(baseColor, ALPHAS[i])
            }

            // Set the gradient colors into the drawable.
            mGradient.setColors(mGradientColors)
        }

        companion object {
            //  0% -  25% of gradient length -> opacity changes from 0% to 50%
            // 25% -  90% of gradient length -> opacity changes from 50% to 100%
            // 90% - 100% of gradient length -> opacity remains at 100%
            private val ALPHAS = intArrayOf(
                    0x00, // 0%
                    0x1A, // 10%
                    0x33, // 20%
                    0x4D, // 30%
                    0x66, // 40%
                    0x80, // 50%
                    0x89, // 53.8%
                    0x93, // 57.6%
                    0x9D, // 61.5%
                    0xA7, // 65.3%
                    0xB1, // 69.2%
                    0xBA, // 73.0%
                    0xC4, // 76.9%
                    0xCE, // 80.7%
                    0xD8, // 84.6%
                    0xE2, // 88.4%
                    0xEB, // 92.3%
                    0xF5, // 96.1%
                    0xFF, // 100%
                    0xFF, // 100%
                    0xFF)
        }
    }

    companion object {
        /** Milliseconds between redraws while running.  */
        private const val REDRAW_PERIOD_RUNNING = 25

        /** Milliseconds between redraws while paused.  */
        private const val REDRAW_PERIOD_PAUSED = 500
    }
}