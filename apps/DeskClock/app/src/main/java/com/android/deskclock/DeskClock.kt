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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.format.DateUtils
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.ActionBar
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.ViewPager
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import androidx.viewpager.widget.ViewPager.SCROLL_STATE_DRAGGING
import androidx.viewpager.widget.ViewPager.SCROLL_STATE_IDLE
import androidx.viewpager.widget.ViewPager.SCROLL_STATE_SETTLING

import com.android.deskclock.FabContainer.UpdateFabFlag
import com.android.deskclock.LabelDialogFragment.AlarmLabelDialogHandler
import com.android.deskclock.actionbarmenu.MenuItemControllerFactory
import com.android.deskclock.actionbarmenu.NightModeMenuItemController
import com.android.deskclock.actionbarmenu.OptionsMenuManager
import com.android.deskclock.actionbarmenu.SettingsMenuItemController
import com.android.deskclock.data.DataModel
import com.android.deskclock.data.OnSilentSettingsListener
import com.android.deskclock.events.Events
import com.android.deskclock.provider.Alarm
import com.android.deskclock.uidata.TabListener
import com.android.deskclock.uidata.UiDataModel
import com.android.deskclock.widget.toast.SnackbarManager

import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout

/**
 * The main activity of the application which displays 4 different tabs contains alarms, world
 * clocks, timers and a stopwatch.
 */
class DeskClock : BaseActivity(), FabContainer, AlarmLabelDialogHandler {
    /** Models the interesting state of display the [.mFab] button may inhabit.  */
    private enum class FabState {
        SHOWING, HIDE_ARMED, HIDING
    }

    /** Coordinates handling of context menu items.  */
    private val mOptionsMenuManager = OptionsMenuManager()

    /** Shrinks the [.mFab], [.mLeftButton] and [.mRightButton] to nothing.  */
    private val mHideAnimation = AnimatorSet()

    /** Grows the [.mFab], [.mLeftButton] and [.mRightButton] to natural sizes.  */
    private val mShowAnimation = AnimatorSet()

    /** Hides, updates, and shows only the [.mFab]; the buttons are untouched.  */
    private val mUpdateFabOnlyAnimation = AnimatorSet()

    /** Hides, updates, and shows only the [.mLeftButton] and [.mRightButton].  */
    private val mUpdateButtonsOnlyAnimation = AnimatorSet()

    /** Automatically starts the [.mShowAnimation] after [.mHideAnimation] ends.  */
    private val mAutoStartShowListener: AnimatorListenerAdapter = AutoStartShowListener()

    /** Updates the user interface to reflect the selected tab from the backing model.  */
    private val mTabChangeWatcher: TabListener = TabChangeWatcher()

    /** Shows/hides a snackbar explaining which setting is suppressing alarms from firing.  */
    private val mSilentSettingChangeWatcher: OnSilentSettingsListener = SilentSettingChangeWatcher()

    /** Displays a snackbar explaining why alarms may not fire or may fire silently.  */
    private var mShowSilentSettingSnackbarRunnable: Runnable? = null

    /** The view to which snackbar items are anchored.  */
    private lateinit var mSnackbarAnchor: View

    /** The current display state of the [.mFab].  */
    private var mFabState = FabState.SHOWING

    /** The single floating-action button shared across all tabs in the user interface.  */
    private lateinit var mFab: ImageView

    /** The button left of the [.mFab] shared across all tabs in the user interface.  */
    private lateinit var mLeftButton: Button

    /** The button right of the [.mFab] shared across all tabs in the user interface.  */
    private lateinit var mRightButton: Button

    /** The controller that shows the drop shadow when content is not scrolled to the top.  */
    private var mDropShadowController: DropShadowController? = null

    /** The ViewPager that pages through the fragments representing the content of the tabs.  */
    private lateinit var mFragmentTabPager: ViewPager

    /** Generates the fragments that are displayed by the [.mFragmentTabPager].  */
    private lateinit var mFragmentTabPagerAdapter: FragmentTabPagerAdapter

    /** The container that stores the tab headers.  */
    private lateinit var mTabLayout: TabLayout

    /** `true` when a settings change necessitates recreating this activity.  */
    private var mRecreateActivity = false

    override fun onNewIntent(newIntent: Intent) {
        super.onNewIntent(newIntent)

        // Fragments may query the latest intent for information, so update the intent.
        setIntent(newIntent)
    }

    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.desk_clock)
        mSnackbarAnchor = findViewById(R.id.content)

        // Configure the toolbar.
        val toolbar: Toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)

        val actionBar: ActionBar? = getSupportActionBar()
        actionBar?.setDisplayShowTitleEnabled(false)

        // Configure the menu item controllers add behavior to the toolbar.
        mOptionsMenuManager.addMenuItemController(
                NightModeMenuItemController(this), SettingsMenuItemController(this))
        mOptionsMenuManager.addMenuItemController(
                *MenuItemControllerFactory.buildMenuItemControllers(this))

        // Inflate the menu during creation to avoid a double layout pass. Otherwise, the menu
        // inflation occurs *after* the initial draw and a second layout pass adds in the menu.
        onCreateOptionsMenu(toolbar.getMenu())

        // Create the tabs that make up the user interface.
        mTabLayout = findViewById(R.id.tabs) as TabLayout
        val tabCount: Int = UiDataModel.uiDataModel.tabCount
        val showTabLabel: Boolean = getResources().getBoolean(R.bool.showTabLabel)
        val showTabHorizontally: Boolean = getResources().getBoolean(R.bool.showTabHorizontally)
        for (i in 0 until tabCount) {
            val tabModel: UiDataModel.Tab = UiDataModel.uiDataModel.getTab(i)
            @StringRes val labelResId: Int = tabModel.labelResId

            val tab: TabLayout.Tab = mTabLayout.newTab()
                    .setTag(tabModel)
                    .setIcon(tabModel.iconResId)
                    .setContentDescription(labelResId)

            if (showTabLabel) {
                tab.setText(labelResId)
                tab.setCustomView(R.layout.tab_item)

                val text = tab.getCustomView()!!.findViewById(android.R.id.text1) as TextView
                text.setTextColor(mTabLayout.getTabTextColors())

                // Bind the icon to the TextView.
                val icon: Drawable? = tab.getIcon()
                if (showTabHorizontally) {
                    // Remove the icon so it doesn't affect the minimum TabLayout height.
                    tab.setIcon(null)
                    text.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
                } else {
                    text.setCompoundDrawablesRelativeWithIntrinsicBounds(null, icon, null, null)
                }
            }

            mTabLayout.addTab(tab)
        }

        // Configure the buttons shared by the tabs.
        mFab = findViewById(R.id.fab) as ImageView
        mLeftButton = findViewById(R.id.left_button) as Button
        mRightButton = findViewById(R.id.right_button) as Button

        mFab.setOnClickListener { selectedDeskClockFragment.onFabClick(mFab) }
        mLeftButton.setOnClickListener {
            selectedDeskClockFragment.onLeftButtonClick(mLeftButton)
        }
        mRightButton.setOnClickListener {
            selectedDeskClockFragment.onRightButtonClick(mRightButton)
        }

        val duration: Long = UiDataModel.uiDataModel.shortAnimationDuration

        val hideFabAnimation = AnimatorUtils.getScaleAnimator(mFab, 1f, 0f)
        val showFabAnimation = AnimatorUtils.getScaleAnimator(mFab, 0f, 1f)

        val leftHideAnimation = AnimatorUtils.getScaleAnimator(mLeftButton, 1f, 0f)
        val rightHideAnimation = AnimatorUtils.getScaleAnimator(mRightButton, 1f, 0f)
        val leftShowAnimation = AnimatorUtils.getScaleAnimator(mLeftButton, 0f, 1f)
        val rightShowAnimation = AnimatorUtils.getScaleAnimator(mRightButton, 0f, 1f)

        hideFabAnimation.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                selectedDeskClockFragment.onUpdateFab(mFab)
            }
        })

        leftHideAnimation.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                selectedDeskClockFragment.onUpdateFabButtons(mLeftButton, mRightButton)
            }
        })

        // Build the reusable animations that hide and show the fab and left/right buttons.
        // These may be used independently or be chained together.
        mHideAnimation
                .setDuration(duration)
                .play(hideFabAnimation)
                .with(leftHideAnimation)
                .with(rightHideAnimation)

        mShowAnimation
                .setDuration(duration)
                .play(showFabAnimation)
                .with(leftShowAnimation)
                .with(rightShowAnimation)

        // Build the reusable animation that hides and shows only the fab.
        mUpdateFabOnlyAnimation
                .setDuration(duration)
                .play(showFabAnimation)
                .after(hideFabAnimation)

        // Build the reusable animation that hides and shows only the buttons.
        mUpdateButtonsOnlyAnimation
                .setDuration(duration)
                .play(leftShowAnimation)
                .with(rightShowAnimation)
                .after(leftHideAnimation)
                .after(rightHideAnimation)

        // Customize the view pager.
        mFragmentTabPagerAdapter = FragmentTabPagerAdapter(this)
        mFragmentTabPager = findViewById(R.id.desk_clock_pager) as ViewPager
        // Keep all four tabs to minimize jank.
        mFragmentTabPager.setOffscreenPageLimit(3)
        // Set Accessibility Delegate to null so view pager doesn't intercept movements and
        // prevent the fab from being selected.
        mFragmentTabPager.setAccessibilityDelegate(null)
        // Mirror changes made to the selected page of the view pager into UiDataModel.
        mFragmentTabPager.addOnPageChangeListener(PageChangeWatcher())
        mFragmentTabPager.setAdapter(mFragmentTabPagerAdapter)

        // Mirror changes made to the selected tab into UiDataModel.
        mTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                UiDataModel.uiDataModel.selectedTab = tab.getTag() as UiDataModel.Tab
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
            }
        })

        // Honor changes to the selected tab from outside entities.
        UiDataModel.uiDataModel.addTabListener(mTabChangeWatcher)
    }

    override fun onStart() {
        super.onStart()
        DataModel.dataModel.addSilentSettingsListener(mSilentSettingChangeWatcher)
        DataModel.dataModel.isApplicationInForeground = true
    }

    override fun onResume() {
        super.onResume()

        val dropShadow: View = findViewById(R.id.drop_shadow)
        mDropShadowController = DropShadowController(dropShadow, UiDataModel.uiDataModel,
                mSnackbarAnchor.findViewById(R.id.tab_hairline))

        // ViewPager does not save state; this honors the selected tab in the user interface.
        updateCurrentTab()
    }

    override fun onPostResume() {
        super.onPostResume()

        if (mRecreateActivity) {
            mRecreateActivity = false

            // A runnable must be posted here or the new DeskClock activity will be recreated in a
            // paused state, even though it is the foreground activity.
            mFragmentTabPager.post(Runnable { recreate() })
        }
    }

    override fun onPause() {
        if (mDropShadowController != null) {
            mDropShadowController!!.stop()
            mDropShadowController = null
        }

        super.onPause()
    }

    override fun onStop() {
        DataModel.dataModel.removeSilentSettingsListener(mSilentSettingChangeWatcher)
        if (!isChangingConfigurations()) {
            DataModel.dataModel.isApplicationInForeground = false
        }

        super.onStop()
    }

    override fun onDestroy() {
        UiDataModel.uiDataModel.removeTabListener(mTabChangeWatcher)
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        mOptionsMenuManager.onCreateOptionsMenu(menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        mOptionsMenuManager.onPrepareOptionsMenu(menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return mOptionsMenuManager.onOptionsItemSelected(item) || super.onOptionsItemSelected(item)
    }

    /**
     * Called by the LabelDialogFormat class after the dialog is finished.
     */
    override fun onDialogLabelSet(alarm: Alarm, label: String, tag: String) {
        val frag: Fragment? = supportFragmentManager.findFragmentByTag(tag)
        if (frag is AlarmClockFragment) {
            frag.setLabel(alarm, label)
        }
    }

    /**
     * Listens for keyboard activity for the tab fragments to handle if necessary. A tab may want to
     * respond to key presses even if they are not currently focused.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return (selectedDeskClockFragment.onKeyDown(keyCode, event) ||
                super.onKeyDown(keyCode, event))
    }

    override fun updateFab(@UpdateFabFlag updateType: Int) {
        val f = selectedDeskClockFragment

        when (updateType and FabContainer.FAB_ANIMATION_MASK) {
            FabContainer.FAB_SHRINK_AND_EXPAND -> mUpdateFabOnlyAnimation.start()
            FabContainer.FAB_IMMEDIATE -> f.onUpdateFab(mFab)
            FabContainer.FAB_MORPH -> f.onMorphFab(mFab)
        }
        when (updateType and FabContainer.FAB_REQUEST_FOCUS_MASK) {
            FabContainer.FAB_REQUEST_FOCUS -> mFab.requestFocus()
        }
        when (updateType and FabContainer.BUTTONS_ANIMATION_MASK) {
            FabContainer.BUTTONS_IMMEDIATE -> f.onUpdateFabButtons(mLeftButton, mRightButton)
            FabContainer.BUTTONS_SHRINK_AND_EXPAND -> mUpdateButtonsOnlyAnimation.start()
        }
        when (updateType and FabContainer.BUTTONS_DISABLE_MASK) {
            FabContainer.BUTTONS_DISABLE -> {
                mLeftButton.isClickable = false
                mRightButton.isClickable = false
            }
        }
        when (updateType and FabContainer.FAB_AND_BUTTONS_SHRINK_EXPAND_MASK) {
            FabContainer.FAB_AND_BUTTONS_SHRINK -> mHideAnimation.start()
            FabContainer.FAB_AND_BUTTONS_EXPAND -> mShowAnimation.start()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // Recreate the activity if any settings have been changed
        if (requestCode == SettingsMenuItemController.REQUEST_CHANGE_SETTINGS &&
                resultCode == RESULT_OK) {
            mRecreateActivity = true
        }
    }

    /**
     * Configure the [.mFragmentTabPager] and [.mTabLayout] to display UiDataModel's
     * selected tab.
     */
    private fun updateCurrentTab() {
        // Fetch the selected tab from the source of truth: UiDataModel.
        val selectedTab: UiDataModel.Tab = UiDataModel.uiDataModel.selectedTab

        // Update the selected tab in the tablayout if it does not agree with UiDataModel.
        for (i in 0 until mTabLayout.getTabCount()) {
            val tab: TabLayout.Tab? = mTabLayout.getTabAt(i)
            if (tab?.getTag() == selectedTab && !tab.isSelected()) {
                tab.select()
                break
            }
        }

        // Update the selected fragment in the viewpager if it does not agree with UiDataModel.
        for (i in 0 until mFragmentTabPagerAdapter.count) {
            val fragment = mFragmentTabPagerAdapter.getDeskClockFragment(i)
            if (fragment.isTabSelected && mFragmentTabPager.getCurrentItem() != i) {
                mFragmentTabPager.setCurrentItem(i)
                break
            }
        }
    }

    /**
     * @return the DeskClockFragment that is currently selected according to UiDataModel
     */
    private val selectedDeskClockFragment: DeskClockFragment
        get() {
            for (i in 0 until mFragmentTabPagerAdapter.count) {
                val fragment = mFragmentTabPagerAdapter.getDeskClockFragment(i)
                if (fragment.isTabSelected) {
                    return fragment
                }
            }
            val selectedTab: UiDataModel.Tab = UiDataModel.uiDataModel.selectedTab
            throw IllegalStateException("Unable to locate selected fragment ($selectedTab)")
        }

    /**
     * @return a Snackbar that displays the message with the given id for 5 seconds
     */
    private fun createSnackbar(@StringRes messageId: Int): Snackbar {
        return Snackbar.make(mSnackbarAnchor, messageId, 5000)
    }

    /**
     * As the view pager changes the selected page, update the model to record the new selected tab.
     */
    private inner class PageChangeWatcher : OnPageChangeListener {
        /** The last reported page scroll state; used to detect exotic state changes.  */
        private var mPriorState: Int = SCROLL_STATE_IDLE

        override fun onPageScrolled(
            position: Int,
            positionOffset: Float,
            positionOffsetPixels: Int
        ) {
            // Only hide the fab when a non-zero drag distance is detected. This prevents
            // over-scrolling from needlessly hiding the fab.
            if (mFabState == FabState.HIDE_ARMED && positionOffsetPixels != 0) {
                mFabState = FabState.HIDING
                mHideAnimation.start()
            }
        }

        override fun onPageScrollStateChanged(state: Int) {
            if (mPriorState == SCROLL_STATE_IDLE && state == SCROLL_STATE_SETTLING) {
                // The user has tapped a tab button; play the hide and show animations linearly.
                mHideAnimation.addListener(mAutoStartShowListener)
                mHideAnimation.start()
                mFabState = FabState.HIDING
            } else if (mPriorState == SCROLL_STATE_SETTLING && state == SCROLL_STATE_DRAGGING) {
                // The user has interrupted settling on a tab and the fab button must be re-hidden.
                if (mShowAnimation.isStarted) {
                    mShowAnimation.cancel()
                }
                if (mHideAnimation.isStarted) {
                    // Let the hide animation finish naturally; don't auto show when it ends.
                    mHideAnimation.removeListener(mAutoStartShowListener)
                } else {
                    // Start and immediately end the hide animation to jump to the hidden state.
                    mHideAnimation.start()
                    mHideAnimation.end()
                }
                mFabState = FabState.HIDING
            } else if (state != SCROLL_STATE_DRAGGING && mFabState == FabState.HIDING) {
                // The user has lifted their finger; show the buttons now or after hide ends.
                if (mHideAnimation.isStarted) {
                    // Finish the hide animation and then start the show animation.
                    mHideAnimation.addListener(mAutoStartShowListener)
                } else {
                    updateFab(FabContainer.FAB_AND_BUTTONS_IMMEDIATE)
                    mShowAnimation.start()

                    // The animation to show the fab has begun; update the state to showing.
                    mFabState = FabState.SHOWING
                }
            } else if (state == SCROLL_STATE_DRAGGING) {
                // The user has started a drag so arm the hide animation.
                mFabState = FabState.HIDE_ARMED
            }

            // Update the last known state.
            mPriorState = state
        }

        override fun onPageSelected(position: Int) {
            mFragmentTabPagerAdapter.getDeskClockFragment(position).selectTab()
        }
    }

    /**
     * If this listener is attached to [.mHideAnimation] when it ends, the corresponding
     * [.mShowAnimation] is automatically started.
     */
    private inner class AutoStartShowListener : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            // Prepare the hide animation for its next use; by default do not auto-show after hide.
            mHideAnimation.removeListener(mAutoStartShowListener)

            // Update the buttons now that they are no longer visible.
            updateFab(FabContainer.FAB_AND_BUTTONS_IMMEDIATE)

            // Automatically start the grow animation now that shrinking is complete.
            mShowAnimation.start()

            // The animation to show the fab has begun; update the state to showing.
            mFabState = FabState.SHOWING
        }
    }

    /**
     * Shows/hides a snackbar as silencing settings are enabled/disabled.
     */
    private inner class SilentSettingChangeWatcher : OnSilentSettingsListener {
        override fun onSilentSettingsChange(
            before: DataModel.SilentSetting?,
            after: DataModel.SilentSetting?
        ) {
            if (mShowSilentSettingSnackbarRunnable != null) {
                mSnackbarAnchor.removeCallbacks(mShowSilentSettingSnackbarRunnable)
                mShowSilentSettingSnackbarRunnable = null
            }

            if (after == null) {
                SnackbarManager.dismiss()
            } else {
                mShowSilentSettingSnackbarRunnable = ShowSilentSettingSnackbarRunnable(after)
                mSnackbarAnchor.postDelayed(mShowSilentSettingSnackbarRunnable,
                        DateUtils.SECOND_IN_MILLIS)
            }
        }
    }

    /**
     * Displays a snackbar that indicates a system setting is currently silencing alarms.
     */
    private inner class ShowSilentSettingSnackbarRunnable(
        private val mSilentSetting: DataModel.SilentSetting
    ) : Runnable {
        override fun run() {
            // Create a snackbar with a message explaining the setting that is silencing alarms.
            val snackbar: Snackbar = createSnackbar(mSilentSetting.labelResId)

            // Set the associated corrective action if one exists.
            if (mSilentSetting.isActionEnabled(this@DeskClock)) {
                val actionResId: Int = mSilentSetting.actionResId
                snackbar.setAction(actionResId, mSilentSetting.actionListener)
            }

            SnackbarManager.show(snackbar)
        }
    }

    /**
     * As the model reports changes to the selected tab, update the user interface.
     */
    private inner class TabChangeWatcher : TabListener {
        override fun selectedTabChanged(
            oldSelectedTab: UiDataModel.Tab,
            newSelectedTab: UiDataModel.Tab
        ) {
            // Update the view pager and tab layout to agree with the model.
            updateCurrentTab()

            // Avoid sending events for the initial tab selection on launch and re-selecting a tab
            // after a configuration change.
            if (DataModel.dataModel.isApplicationInForeground) {
                when (newSelectedTab) {
                    UiDataModel.Tab.ALARMS -> {
                        Events.sendAlarmEvent(R.string.action_show, R.string.label_deskclock)
                    }
                    UiDataModel.Tab.CLOCKS -> {
                        Events.sendClockEvent(R.string.action_show, R.string.label_deskclock)
                    }
                    UiDataModel.Tab.TIMERS -> {
                        Events.sendTimerEvent(R.string.action_show, R.string.label_deskclock)
                    }
                    UiDataModel.Tab.STOPWATCH -> {
                        Events.sendStopwatchEvent(R.string.action_show, R.string.label_deskclock)
                    }
                }
            }

            // If the hide animation has already completed, the buttons must be updated now when the
            // new tab is known. Otherwise they are updated at the end of the hide animation.
            if (!mHideAnimation.isStarted) {
                updateFab(FabContainer.FAB_AND_BUTTONS_IMMEDIATE)
            }
        }
    }
}