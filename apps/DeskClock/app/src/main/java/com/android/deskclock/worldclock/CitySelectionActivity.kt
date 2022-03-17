/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.deskclock.worldclock

import android.content.Context
import android.os.Bundle
import androidx.appcompat.widget.SearchView
import android.text.TextUtils
import android.text.format.DateFormat
import android.util.ArraySet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.ListView
import android.widget.SectionIndexer
import android.widget.TextView

import com.android.deskclock.BaseActivity
import com.android.deskclock.DropShadowController
import com.android.deskclock.R
import com.android.deskclock.Utils
import com.android.deskclock.actionbarmenu.MenuItemController
import com.android.deskclock.actionbarmenu.MenuItemControllerFactory
import com.android.deskclock.actionbarmenu.NavUpMenuItemController
import com.android.deskclock.actionbarmenu.OptionsMenuManager
import com.android.deskclock.actionbarmenu.SearchMenuItemController
import com.android.deskclock.actionbarmenu.SettingsMenuItemController
import com.android.deskclock.data.City
import com.android.deskclock.data.DataModel

import java.util.ArrayList
import java.util.Calendar
import java.util.Comparator
import java.util.Locale
import java.util.TimeZone

/**
 * This activity allows the user to alter the cities selected for display.
 *
 * Note, it is possible for two instances of this Activity to exist simultaneously:
 * <ul>
 * <li>Clock Tab-> Tap Floating Action Button</li>
 * <li>Digital Widget -> Tap any city clock</li>
 * </ul>
 *
 * As a result, [.onResume] conservatively refreshes itself from the backing
 * [DataModel] which may have changed since this activity was last displayed.
 */
class CitySelectionActivity : BaseActivity() {
    /**
     * The list of all selected and unselected cities, indexed and possibly filtered.
     */
    private lateinit var mCitiesList: ListView

    /**
     * The adapter that presents all of the selected and unselected cities.
     */
    private lateinit var mCitiesAdapter: CityAdapter

    /**
     * Manages all action bar menu display and click handling.
     */
    private val mOptionsMenuManager = OptionsMenuManager()

    /**
     * Menu item controller for search view.
     */
    private lateinit var mSearchMenuItemController: SearchMenuItemController

    /**
     * The controller that shows the drop shadow when content is not scrolled to the top.
     */
    private lateinit var mDropShadowController: DropShadowController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.cities_activity)
        mSearchMenuItemController = SearchMenuItemController(
                getSupportActionBar()!!.getThemedContext(),
                object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean {
                        return false
                    }

                    override fun onQueryTextChange(query: String): Boolean {
                        mCitiesAdapter.filter(query)
                        updateFastScrolling()
                        return true
                    }
                }, savedInstanceState)
        mCitiesAdapter = CityAdapter(this, mSearchMenuItemController)
        mOptionsMenuManager.addMenuItemController(NavUpMenuItemController(this))
                .addMenuItemController(mSearchMenuItemController)
                .addMenuItemController(SortOrderMenuItemController())
                .addMenuItemController(SettingsMenuItemController(this))
                .addMenuItemController(*MenuItemControllerFactory.buildMenuItemControllers(this))
        mCitiesList = findViewById(R.id.cities_list) as ListView
        mCitiesList.adapter = mCitiesAdapter

        updateFastScrolling()
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        super.onSaveInstanceState(bundle)
        mSearchMenuItemController.saveInstance(bundle)
    }

    override fun onResume() {
        super.onResume()

        // Recompute the contents of the adapter before displaying on screen.
        mCitiesAdapter.refresh()

        val dropShadow: View = findViewById(R.id.drop_shadow)
        mDropShadowController = DropShadowController(dropShadow, mCitiesList)
    }

    override fun onPause() {
        super.onPause()

        mDropShadowController.stop()

        // Save the selected cities.
        DataModel.dataModel.selectedCities = mCitiesAdapter.selectedCities
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        mOptionsMenuManager.onCreateOptionsMenu(menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        mOptionsMenuManager.onPrepareOptionsMenu(menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return (mOptionsMenuManager.onOptionsItemSelected(item) ||
                super.onOptionsItemSelected(item))
    }

    /**
     * Fast scrolling is only enabled while no filtering is happening.
     */
    private fun updateFastScrolling() {
        val enabled: Boolean = !mCitiesAdapter.isFiltering
        mCitiesList.isFastScrollAlwaysVisible = enabled
        mCitiesList.isFastScrollEnabled = enabled
    }

    /**
     * This adapter presents data in 2 possible modes. If selected cities exist the format is:
     *
     * <pre>
     * Selected Cities
     * City 1 (alphabetically first)
     * City 2 (alphabetically second)
     * ...
     * A City A1 (alphabetically first starting with A)
     * City A2 (alphabetically second starting with A)
     * ...
     * B City B1 (alphabetically first starting with B)
     * City B2 (alphabetically second starting with B)
     * ...
     * </pre>
     *
     * If selected cities do not exist, that section is removed and all that remains is:
     *
     * <pre>
     * A City A1 (alphabetically first starting with A)
     * City A2 (alphabetically second starting with A)
     * ...
     * B City B1 (alphabetically first starting with B)
     * City B2 (alphabetically second starting with B)
     * ...
     * </pre>
     */
    private class CityAdapter(
        private val mContext: Context,
        /** Menu item controller for search. Search query is maintained here. */
        private val mSearchMenuItemController: SearchMenuItemController
    ) : BaseAdapter(), View.OnClickListener,
            CompoundButton.OnCheckedChangeListener, SectionIndexer {
        private val mInflater: LayoutInflater = LayoutInflater.from(mContext)

        /**
         * The 12-hour time pattern for the current locale.
         */
        private val mPattern12: String

        /**
         * The 24-hour time pattern for the current locale.
         */
        private val mPattern24: String

        /**
         * `true` time should honor [.mPattern24]; [.mPattern12] otherwise.
         */
        private var mIs24HoursMode = false

        /**
         * A calendar used to format time in a particular timezone.
         */
        private val mCalendar: Calendar = Calendar.getInstance()

        /**
         * The list of cities which may be filtered by a search term.
         */
        private var mFilteredCities: List<City> = emptyList()

        /**
         * A mutable set of cities currently selected by the user.
         */
        private val mUserSelectedCities: MutableSet<City> = ArraySet()

        /**
         * The number of user selections at the top of the adapter to avoid indexing.
         */
        private var mOriginalUserSelectionCount = 0

        /**
         * The precomputed section headers.
         */
        private var mSectionHeaders: Array<String>? = null

        /**
         * The corresponding location of each precomputed section header.
         */
        private var mSectionHeaderPositions: Array<Int>? = null

        init {
            mCalendar.timeInMillis = System.currentTimeMillis()

            val locale = Locale.getDefault()
            mPattern24 = DateFormat.getBestDateTimePattern(locale, "Hm")

            var pattern12 = DateFormat.getBestDateTimePattern(locale, "hma")
            if (TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL) {
                // There's an RTL layout bug that causes jank when fast-scrolling through
                // the list in 12-hour mode in an RTL locale. We can work around this by
                // ensuring the strings are the same length by using "hh" instead of "h".
                pattern12 = pattern12.replace("h".toRegex(), "hh")
            }
            mPattern12 = pattern12
        }

        override fun getCount(): Int {
            val headerCount = if (hasHeader()) 1 else 0
            return headerCount + mFilteredCities.size
        }

        override fun getItem(position: Int): City? {
            if (hasHeader()) {
                val itemViewType = getItemViewType(position)
                when (itemViewType) {
                    VIEW_TYPE_SELECTED_CITIES_HEADER -> return null
                    VIEW_TYPE_CITY -> return mFilteredCities[position - 1]
                }
                throw IllegalStateException("unexpected item view type: $itemViewType")
            }

            return mFilteredCities[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getView(position: Int, view: View?, parent: ViewGroup): View {
            var variableView = view
            val itemViewType = getItemViewType(position)
            when (itemViewType) {
                VIEW_TYPE_SELECTED_CITIES_HEADER -> {
                    return variableView
                            ?: mInflater.inflate(R.layout.city_list_header, parent, false)
                }
                VIEW_TYPE_CITY -> {
                    val city = getItem(position)
                            ?: throw IllegalStateException("The desired city does not exist")
                    val timeZone: TimeZone = city.timeZone

                    // Inflate a new view if necessary.
                    if (variableView == null) {
                        variableView = mInflater.inflate(R.layout.city_list_item, parent, false)
                        val index = variableView.findViewById<View>(R.id.index) as TextView
                        val name = variableView.findViewById<View>(R.id.city_name) as TextView
                        val time = variableView.findViewById<View>(R.id.city_time) as TextView
                        val selected = variableView.findViewById<View>(R.id.city_onoff) as CheckBox
                        variableView.tag = CityItemHolder(index, name, time, selected)
                    }

                    // Bind data into the child views.
                    val holder = variableView!!.tag as CityItemHolder
                    holder.selected.tag = city
                    holder.selected.isChecked = mUserSelectedCities.contains(city)
                    holder.selected.contentDescription = city.name
                    holder.selected.setOnCheckedChangeListener(this)
                    holder.name.setText(city.name, TextView.BufferType.SPANNABLE)
                    holder.time.text = getTimeCharSequence(timeZone)

                    val showIndex = getShowIndex(position)
                    holder.index.visibility = if (showIndex) View.VISIBLE else View.INVISIBLE
                    if (showIndex) {
                        when (citySort) {
                            DataModel.CitySort.NAME -> {
                                holder.index.setText(city.indexString)
                                holder.index.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                            }
                            DataModel.CitySort.UTC_OFFSET -> {
                                holder.index.text = Utils.getGMTHourOffset(timeZone, false)
                                holder.index.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                            }
                        }
                    }

                    // skip checkbox and other animations
                    variableView.jumpDrawablesToCurrentState()
                    variableView.setOnClickListener(this)
                    return variableView
                }
                else -> throw IllegalStateException("unexpected item view type: $itemViewType")
            }
        }

        override fun getViewTypeCount(): Int {
            return 2
        }

        override fun getItemViewType(position: Int): Int {
            return if (hasHeader() && position == 0) {
                VIEW_TYPE_SELECTED_CITIES_HEADER
            } else {
                VIEW_TYPE_CITY
            }
        }

        override fun onCheckedChanged(b: CompoundButton, checked: Boolean) {
            val city = b.tag as City
            if (checked) {
                mUserSelectedCities.add(city)
                b.announceForAccessibility(mContext.getString(R.string.city_checked,
                        city.name))
            } else {
                mUserSelectedCities.remove(city)
                b.announceForAccessibility(mContext.getString(R.string.city_unchecked,
                        city.name))
            }
        }

        override fun onClick(v: View) {
            val b = v.findViewById<View>(R.id.city_onoff) as CheckBox
            b.isChecked = !b.isChecked
        }

        override fun getSections(): Array<String>? {
            if (mSectionHeaders == null) {
                // Make an educated guess at the expected number of sections.
                val approximateSectionCount = count / 5
                val sections: MutableList<String> = ArrayList(approximateSectionCount)
                val positions: MutableList<Int> = ArrayList(approximateSectionCount)

                // Add a section for the "Selected Cities" header if it exists.
                if (hasHeader()) {
                    sections.add("+")
                    positions.add(0)
                }

                for (position in 0 until count) {
                    // Add a section if this position should show the section index.
                    if (getShowIndex(position)) {
                        val city = getItem(position)
                                ?: throw IllegalStateException("The desired city does not exist")
                        when (citySort) {
                            DataModel.CitySort.NAME -> sections.add(city.indexString.orEmpty())
                            DataModel.CitySort.UTC_OFFSET -> {
                                val timezone: TimeZone = city.timeZone
                                sections.add(Utils.getGMTHourOffset(timezone, Utils.isPreL))
                            }
                        }
                        positions.add(position)
                    }
                }

                mSectionHeaders = sections.toTypedArray()
                mSectionHeaderPositions = positions.toTypedArray()
            }
            return mSectionHeaders
        }

        override fun getPositionForSection(sectionIndex: Int): Int {
            return if (sections!!.isEmpty()) 0 else mSectionHeaderPositions!![sectionIndex]
        }

        override fun getSectionForPosition(position: Int): Int {
            if (sections!!.isEmpty()) {
                return 0
            }

            for (i in 0 until mSectionHeaderPositions!!.size - 2) {
                if (position < mSectionHeaderPositions!![i]) continue
                if (position >= mSectionHeaderPositions!![i + 1]) continue
                return i
            }

            return mSectionHeaderPositions!!.size - 1
        }

        /**
         * Clear the section headers to force them to be recomputed if they are now stale.
         */
        fun clearSectionHeaders() {
            mSectionHeaders = null
            mSectionHeaderPositions = null
        }

        /**
         * Rebuilds all internal data structures from scratch.
         */
        fun refresh() {
            // Update the 12/24 hour mode.
            mIs24HoursMode = DateFormat.is24HourFormat(mContext)

            // Refresh the user selections.
            val selected = DataModel.dataModel.selectedCities as List<City>
            mUserSelectedCities.clear()
            mUserSelectedCities.addAll(selected)
            mOriginalUserSelectionCount = selected.size

            // Recompute section headers.
            clearSectionHeaders()

            // Recompute filtered cities.
            filter(mSearchMenuItemController.queryText)
        }

        /**
         * Filter the cities using the given `queryText`.
         */
        fun filter(queryText: String) {
            mSearchMenuItemController.queryText = queryText
            val query = City.removeSpecialCharacters(queryText.toUpperCase())

            // Compute the filtered list of cities.
            val filteredCities = if (TextUtils.isEmpty(query)) {
                DataModel.dataModel.allCities
            } else {
                val unselected: List<City> = DataModel.dataModel.unselectedCities
                val queriedCities: MutableList<City> = ArrayList(unselected.size)
                for (city in unselected) {
                    if (city.matches(query)) {
                        queriedCities.add(city)
                    }
                }
                queriedCities
            }

            // Swap in the filtered list of cities and notify of the data change.
            mFilteredCities = filteredCities
            notifyDataSetChanged()
        }

        val isFiltering: Boolean
            get() = !TextUtils.isEmpty(mSearchMenuItemController.queryText.trim({ it <= ' ' }))

        val selectedCities: Collection<City>
            get() = mUserSelectedCities

        private fun hasHeader(): Boolean {
            return !isFiltering && mOriginalUserSelectionCount > 0
        }

        private val citySort: DataModel.CitySort
            get() = DataModel.dataModel.citySort

        private val citySortComparator: Comparator<City>
            get() = DataModel.dataModel.cityIndexComparator

        private fun getTimeCharSequence(timeZone: TimeZone): CharSequence {
            mCalendar.timeZone = timeZone
            return DateFormat.format(if (mIs24HoursMode) mPattern24 else mPattern12, mCalendar)
        }

        private fun getShowIndex(position: Int): Boolean {
            // Indexes are never displayed on filtered cities.
            if (isFiltering) {
                return false
            }

            if (hasHeader()) {
                // None of the original user selections should show their index.
                if (position <= mOriginalUserSelectionCount) {
                    return false
                }

                // The first item after the original user selections must always show its index.
                if (position == mOriginalUserSelectionCount + 1) {
                    return true
                }
            } else {
                // None of the original user selections should show their index.
                if (position < mOriginalUserSelectionCount) {
                    return false
                }

                // The first item after the original user selections must always show its index.
                if (position == mOriginalUserSelectionCount) {
                    return true
                }
            }

            // Otherwise compare the city with its predecessor to test if it is a header.
            val priorCity = getItem(position - 1)
            val city = getItem(position)
            return citySortComparator.compare(priorCity, city) != 0
        }

        /**
         * Cache the child views of each city item view.
         */
        private class CityItemHolder(
            val index: TextView,
            val name: TextView,
            val time: TextView,
            val selected: CheckBox
        )

        companion object {
            /**
             * The type of the single optional "Selected Cities" header entry.
             */
            private const val VIEW_TYPE_SELECTED_CITIES_HEADER = 0

            /**
             * The type of each city entry.
             */
            private const val VIEW_TYPE_CITY = 1
        }
    }

    private inner class SortOrderMenuItemController : MenuItemController {
        private val SORT_MENU_RES_ID = R.id.menu_item_sort

        override val id: Int
            get() = SORT_MENU_RES_ID

        override fun onCreateOptionsItem(menu: Menu) {
            menu.add(Menu.NONE, R.id.menu_item_sort, Menu.NONE,
                    R.string.menu_item_sort_by_gmt_offset)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        }

        override fun onPrepareOptionsItem(item: MenuItem) {
            item.setTitle(if (DataModel.dataModel.citySort == DataModel.CitySort.NAME) {
                R.string.menu_item_sort_by_gmt_offset
            } else {
                R.string.menu_item_sort_by_name
            })
        }

        override fun onOptionsItemSelected(item: MenuItem): Boolean {
            // Save the new sort order.
            DataModel.dataModel.toggleCitySort()

            // Section headers are influenced by sort order and must be cleared.
            mCitiesAdapter.clearSectionHeaders()

            // Honor the new sort order in the adapter.
            mCitiesAdapter.filter(mSearchMenuItemController.queryText)
            return true
        }
    }
}