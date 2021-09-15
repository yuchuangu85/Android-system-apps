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

import android.content.Context
import android.database.Cursor
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.loader.app.LoaderManager.LoaderCallbacks
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.android.deskclock.ItemAdapter.ItemHolder
import com.android.deskclock.ItemAdapter.OnItemChangedListener
import com.android.deskclock.alarms.AlarmTimeClickHandler
import com.android.deskclock.alarms.AlarmUpdateHandler
import com.android.deskclock.alarms.ScrollHandler
import com.android.deskclock.alarms.TimePickerDialogFragment
import com.android.deskclock.alarms.dataadapter.AlarmItemHolder
import com.android.deskclock.alarms.dataadapter.CollapsedAlarmViewHolder
import com.android.deskclock.alarms.dataadapter.ExpandedAlarmViewHolder
import com.android.deskclock.provider.Alarm
import com.android.deskclock.provider.AlarmInstance
import com.android.deskclock.uidata.UiDataModel
import com.android.deskclock.widget.EmptyViewController
import com.android.deskclock.widget.toast.SnackbarManager
import com.android.deskclock.widget.toast.ToastManager

import com.google.android.material.snackbar.Snackbar

import kotlin.math.max

/**
 * A fragment that displays a list of alarm time and allows interaction with them.
 */
class AlarmClockFragment : DeskClockFragment(UiDataModel.Tab.ALARMS),
        LoaderCallbacks<Cursor>, ScrollHandler, TimePickerDialogFragment.OnTimeSetListener {
    // Updates "Today/Tomorrow" in the UI when midnight passes.
    private val mMidnightUpdater: Runnable = MidnightRunnable()

    // Views
    private lateinit var mMainLayout: ViewGroup
    private lateinit var mRecyclerView: RecyclerView

    // Data
    private var mCursorLoader: Loader<*>? = null
    private var mScrollToAlarmId = Alarm.INVALID_ID
    private var mExpandedAlarmId = Alarm.INVALID_ID
    private var mCurrentUpdateToken: Long = 0

    // Controllers
    private lateinit var mItemAdapter: ItemAdapter<AlarmItemHolder>
    private lateinit var mAlarmUpdateHandler: AlarmUpdateHandler
    private lateinit var mEmptyViewController: EmptyViewController
    private lateinit var mAlarmTimeClickHandler: AlarmTimeClickHandler
    private lateinit var mLayoutManager: LinearLayoutManager

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        mCursorLoader = loaderManager.initLoader(0, Bundle.EMPTY, this)
        savedState?.let {
            mExpandedAlarmId = it.getLong(KEY_EXPANDED_ID, Alarm.INVALID_ID)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val v = inflater.inflate(R.layout.alarm_clock, container, false)
        val context: Context = requireActivity()

        mRecyclerView = v.findViewById<View>(R.id.alarms_recycler_view) as RecyclerView
        mLayoutManager = object : LinearLayoutManager(context) {
            override fun getExtraLayoutSpace(state: RecyclerView.State): Int {
                val extraSpace: Int = super.getExtraLayoutSpace(state)
                return if (state.willRunPredictiveAnimations()) {
                    max(getHeight(), extraSpace)
                } else extraSpace
            }
        }
        mRecyclerView.setLayoutManager(mLayoutManager)
        mMainLayout = v.findViewById<View>(R.id.main) as ViewGroup
        mAlarmUpdateHandler = AlarmUpdateHandler(context, this, mMainLayout)
        val emptyView = v.findViewById<View>(R.id.alarms_empty_view) as TextView
        val noAlarms: Drawable? = Utils.getVectorDrawable(context, R.drawable.ic_noalarms)
        emptyView.setCompoundDrawablesWithIntrinsicBounds(null, noAlarms, null, null)
        mEmptyViewController = EmptyViewController(mMainLayout, mRecyclerView, emptyView)
        mAlarmTimeClickHandler = AlarmTimeClickHandler(this, savedState, mAlarmUpdateHandler, this)

        mItemAdapter = ItemAdapter()
        mItemAdapter.setHasStableIds()
        mItemAdapter.withViewTypes(CollapsedAlarmViewHolder.Factory(inflater),
                null, CollapsedAlarmViewHolder.VIEW_TYPE)
        mItemAdapter.withViewTypes(ExpandedAlarmViewHolder.Factory(context),
                null, ExpandedAlarmViewHolder.VIEW_TYPE)
        mItemAdapter.setOnItemChangedListener(object : OnItemChangedListener {
            override fun onItemChanged(holder: ItemHolder<*>) {
                if ((holder as AlarmItemHolder).isExpanded) {
                    if (mExpandedAlarmId != holder.itemId) {
                        // Collapse the prior expanded alarm.
                        val aih = mItemAdapter.findItemById(mExpandedAlarmId)
                        aih?.collapse()
                        // Record the freshly expanded alarm.
                        mExpandedAlarmId = holder.itemId
                        val viewHolder: RecyclerView.ViewHolder? =
                                mRecyclerView.findViewHolderForItemId(mExpandedAlarmId)
                        viewHolder?.let {
                            smoothScrollTo(viewHolder.getAdapterPosition())
                        }
                    }
                } else if (mExpandedAlarmId == holder.itemId) {
                    // The expanded alarm is now collapsed so update the tracking id.
                    mExpandedAlarmId = Alarm.INVALID_ID
                }
            }

            override fun onItemChanged(holder: ItemHolder<*>, payload: Any) {
                /* No additional work to do */
            }
        })
        val scrollPositionWatcher = ScrollPositionWatcher()
        mRecyclerView.addOnLayoutChangeListener(scrollPositionWatcher)
        mRecyclerView.addOnScrollListener(scrollPositionWatcher)
        mRecyclerView.setAdapter(mItemAdapter)
        val itemAnimator = ItemAnimator()
        itemAnimator.setChangeDuration(300L)
        itemAnimator.setMoveDuration(300L)
        mRecyclerView.setItemAnimator(itemAnimator)
        return v
    }

    override fun onStart() {
        super.onStart()

        if (!isTabSelected) {
            TimePickerDialogFragment.removeTimeEditDialog(parentFragmentManager)
        }
    }

    override fun onResume() {
        super.onResume()

        // Schedule a runnable to update the "Today/Tomorrow" values displayed for non-repeating
        // alarms when midnight passes.
        UiDataModel.uiDataModel.addMidnightCallback(mMidnightUpdater)

        // Check if another app asked us to create a blank new alarm.
        val intent = requireActivity().intent ?: return

        if (intent.hasExtra(ALARM_CREATE_NEW_INTENT_EXTRA)) {
            UiDataModel.uiDataModel.selectedTab = UiDataModel.Tab.ALARMS
            if (intent.getBooleanExtra(ALARM_CREATE_NEW_INTENT_EXTRA, false)) {
                // An external app asked us to create a blank alarm.
                startCreatingAlarm()
            }

            // Remove the CREATE_NEW extra now that we've processed it.
            intent.removeExtra(ALARM_CREATE_NEW_INTENT_EXTRA)
        } else if (intent.hasExtra(SCROLL_TO_ALARM_INTENT_EXTRA)) {
            UiDataModel.uiDataModel.selectedTab = UiDataModel.Tab.ALARMS

            val alarmId = intent.getLongExtra(SCROLL_TO_ALARM_INTENT_EXTRA, Alarm.INVALID_ID)
            if (alarmId != Alarm.INVALID_ID) {
                setSmoothScrollStableId(alarmId)
                if (mCursorLoader != null && mCursorLoader!!.isStarted) {
                    // We need to force a reload here to make sure we have the latest view
                    // of the data to scroll to.
                    mCursorLoader!!.forceLoad()
                }
            }

            // Remove the SCROLL_TO_ALARM extra now that we've processed it.
            intent.removeExtra(SCROLL_TO_ALARM_INTENT_EXTRA)
        }
    }

    override fun onPause() {
        super.onPause()
        UiDataModel.uiDataModel.removePeriodicCallback(mMidnightUpdater)

        // When the user places the app in the background by pressing "home",
        // dismiss the toast bar. However, since there is no way to determine if
        // home was pressed, just dismiss any existing toast bar when restarting
        // the app.
        mAlarmUpdateHandler.hideUndoBar()
    }

    override fun smoothScrollTo(position: Int) {
        mLayoutManager.scrollToPositionWithOffset(position, 0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mAlarmTimeClickHandler.saveInstance(outState)
        outState.putLong(KEY_EXPANDED_ID, mExpandedAlarmId)
    }

    override fun onDestroy() {
        super.onDestroy()
        ToastManager.cancelToast()
    }

    fun setLabel(alarm: Alarm, label: String?) {
        alarm.label = label
        mAlarmUpdateHandler.asyncUpdateAlarm(alarm, popToast = false, minorUpdate = true)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        return Alarm.getAlarmsCursorLoader(requireActivity())
    }

    override fun onLoadFinished(cursorLoader: Loader<Cursor>, data: Cursor) {
        val itemHolders: MutableList<AlarmItemHolder> = ArrayList(data.count)
        data.moveToFirst()
        while (!data.isAfterLast) {
            val alarm = Alarm(data)
            val alarmInstance = if (alarm.canPreemptivelyDismiss()) {
                AlarmInstance(data, joinedTable = true)
            } else {
                null
            }
            val itemHolder = AlarmItemHolder(alarm, alarmInstance, mAlarmTimeClickHandler)
            itemHolders.add(itemHolder)
            data.moveToNext()
        }
        setAdapterItems(itemHolders, SystemClock.elapsedRealtime())
    }

    /**
     * Updates the adapters items, deferring the update until the current animation is finished or
     * if no animation is running then the listener will be automatically be invoked immediately.
     *
     * @param items the new list of [AlarmItemHolder] to use
     * @param updateToken a monotonically increasing value used to preserve ordering of deferred
     * updates
     */
    private fun setAdapterItems(items: List<AlarmItemHolder>, updateToken: Long) {
        if (updateToken < mCurrentUpdateToken) {
            LogUtils.v("Ignoring adapter update: %d < %d", updateToken, mCurrentUpdateToken)
            return
        }

        if (mRecyclerView.getItemAnimator()!!.isRunning()) {
            // RecyclerView is currently animating -> defer update.
            mRecyclerView.getItemAnimator()!!.isRunning(
                    object : RecyclerView.ItemAnimator.ItemAnimatorFinishedListener {
                        override fun onAnimationsFinished() {
                            setAdapterItems(items, updateToken)
                        }
                    })
        } else if (mRecyclerView.isComputingLayout()) {
            // RecyclerView is currently computing a layout -> defer update.
            mRecyclerView.post(Runnable { setAdapterItems(items, updateToken) })
        } else {
            mCurrentUpdateToken = updateToken
            mItemAdapter.setItems(items)

            // Show or hide the empty view as appropriate.
            val noAlarms = items.isEmpty()
            mEmptyViewController.setEmpty(noAlarms)
            if (noAlarms) {
                // Ensure the drop shadow is hidden when no alarms exist.
                setTabScrolledToTop(true)
            }

            // Expand the correct alarm.
            if (mExpandedAlarmId != Alarm.INVALID_ID) {
                val aih = mItemAdapter.findItemById(mExpandedAlarmId)
                if (aih != null) {
                    mAlarmTimeClickHandler.setSelectedAlarm(aih.item)
                    aih.expand()
                } else {
                    mAlarmTimeClickHandler.setSelectedAlarm(null)
                    mExpandedAlarmId = Alarm.INVALID_ID
                }
            }

            // Scroll to the selected alarm.
            if (mScrollToAlarmId != Alarm.INVALID_ID) {
                scrollToAlarm(mScrollToAlarmId)
                setSmoothScrollStableId(Alarm.INVALID_ID)
            }
        }
    }

    /**
     * @param alarmId identifies the alarm to be displayed
     */
    private fun scrollToAlarm(alarmId: Long) {
        val alarmCount = mItemAdapter.itemCount
        var alarmPosition = -1
        for (i in 0 until alarmCount) {
            val id = mItemAdapter.getItemId(i)
            if (id == alarmId) {
                alarmPosition = i
                break
            }
        }

        if (alarmPosition >= 0) {
            mItemAdapter.findItemById(alarmId)?.expand()
            smoothScrollTo(alarmPosition)
        } else {
            // Trying to display a deleted alarm should only happen from a missed notification for
            // an alarm that has been marked deleted after use.
            SnackbarManager.show(Snackbar.make(mMainLayout, R.string.missed_alarm_has_been_deleted,
                    Snackbar.LENGTH_LONG))
        }
    }

    override fun onLoaderReset(cursorLoader: Loader<Cursor>) {
    }

    override fun setSmoothScrollStableId(stableId: Long) {
        mScrollToAlarmId = stableId
    }

    override fun onFabClick(fab: ImageView) {
        mAlarmUpdateHandler.hideUndoBar()
        startCreatingAlarm()
    }

    override fun onUpdateFab(fab: ImageView) {
        fab.visibility = View.VISIBLE
        fab.setImageResource(R.drawable.ic_add_white_24dp)
        fab.contentDescription = fab.resources.getString(R.string.button_alarms)
    }

    override fun onUpdateFabButtons(left: Button, right: Button) {
        left.visibility = View.INVISIBLE
        right.visibility = View.INVISIBLE
    }

    private fun startCreatingAlarm() {
        // Clear the currently selected alarm.
        mAlarmTimeClickHandler.setSelectedAlarm(null)
        TimePickerDialogFragment.show(this)
    }

    override fun onTimeSet(fragment: TimePickerDialogFragment?, hourOfDay: Int, minute: Int) {
        mAlarmTimeClickHandler.onTimeSet(hourOfDay, minute)
    }

    fun removeItem(itemHolder: AlarmItemHolder) {
        mItemAdapter.removeItem(itemHolder)
    }

    /**
     * Updates the vertical scroll state of this tab in the [UiDataModel] as the user scrolls
     * the recyclerview or when the size/position of elements within the recyclerview changes.
     */
    private inner class ScrollPositionWatcher
        : RecyclerView.OnScrollListener(), OnLayoutChangeListener {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            setTabScrolledToTop(Utils.isScrolledToTop(mRecyclerView))
        }

        override fun onLayoutChange(
            v: View,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
            oldLeft: Int,
            oldTop: Int,
            oldRight: Int,
            oldBottom: Int
        ) {
            setTabScrolledToTop(Utils.isScrolledToTop(mRecyclerView))
        }
    }

    /**
     * This runnable executes at midnight and refreshes the display of all alarms. Collapsed alarms
     * that do no repeat will have their "Tomorrow" strings updated to say "Today".
     */
    private inner class MidnightRunnable : Runnable {
        override fun run() {
            mItemAdapter.notifyDataSetChanged()
        }
    }

    companion object {
        // This extra is used when receiving an intent to create an alarm, but no alarm details
        // have been passed in, so the alarm page should start the process of creating a new alarm.
        const val ALARM_CREATE_NEW_INTENT_EXTRA = "deskclock.create.new"

        // This extra is used when receiving an intent to scroll to specific alarm. If alarm
        // can not be found, and toast message will pop up that the alarm has be deleted.
        const val SCROLL_TO_ALARM_INTENT_EXTRA = "deskclock.scroll.to.alarm"

        private const val KEY_EXPANDED_ID = "expandedId"
    }
}