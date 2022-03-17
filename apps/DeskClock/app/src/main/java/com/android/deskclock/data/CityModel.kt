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

package com.android.deskclock.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener

import com.android.deskclock.R
import com.android.deskclock.Utils
import com.android.deskclock.data.City.NameComparator
import com.android.deskclock.data.City.NameIndexComparator
import com.android.deskclock.data.City.UtcOffsetComparator
import com.android.deskclock.data.City.UtcOffsetIndexComparator
import com.android.deskclock.data.DataModel.CitySort
import com.android.deskclock.settings.SettingsActivity

import java.util.Collections

/**
 * All [City] data is accessed via this model.
 */
internal class CityModel(
    private val context: Context,
    private val prefs: SharedPreferences,
    /** The model from which settings are fetched.  */
    private val settingsModel: SettingsModel
) {

    /**
     * Retain a hard reference to the shared preference observer to prevent it from being garbage
     * collected. See [SharedPreferences.registerOnSharedPreferenceChangeListener] for detail.
     */
    private val mPreferenceListener: OnSharedPreferenceChangeListener = PreferenceListener()

    /** Clears data structures containing data that is locale-sensitive.  */
    private val mLocaleChangedReceiver: BroadcastReceiver = LocaleChangedReceiver()

    /** List of listeners to invoke upon world city list change  */
    private val mCityListeners: MutableList<CityListener> = ArrayList()

    /** Maps city ID to city instance.  */
    private var mCityMap: Map<String, City>? = null

    /** List of city instances in display order.  */
    private var mAllCities: List<City>? = null

    /** List of selected city instances in display order.  */
    private var mSelectedCities: List<City>? = null

    /** List of unselected city instances in display order.  */
    private var mUnselectedCities: List<City>? = null

    /** A city instance representing the home timezone of the user.  */
    private var mHomeCity: City? = null

    init {
        // Clear caches affected by locale when locale changes.
        val localeBroadcastFilter = IntentFilter(Intent.ACTION_LOCALE_CHANGED)
        context.registerReceiver(mLocaleChangedReceiver, localeBroadcastFilter)

        // Clear caches affected by preferences when preferences change.
        prefs.registerOnSharedPreferenceChangeListener(mPreferenceListener)
    }

    fun addCityListener(cityListener: CityListener) {
        mCityListeners.add(cityListener)
    }

    fun removeCityListener(cityListener: CityListener) {
        mCityListeners.remove(cityListener)
    }

    /**
     * @return a list of all cities in their display order
     */
    val allCities: List<City>
        get() {
            if (mAllCities == null) {
                // Create a set of selections to identify the unselected cities.
                val selected: List<City> = selectedCities.toMutableList()

                // Sort the selected cities alphabetically by name.
                Collections.sort(selected, NameComparator())

                // Combine selected and unselected cities into a single list.
                val allCities: MutableList<City> = ArrayList(cityMap.size)
                allCities.addAll(selected)
                allCities.addAll(unselectedCities)
                mAllCities = allCities
            }

            return mAllCities!!
        }

    /**
     * @return a city representing the user's home timezone
     */
    val homeCity: City
        get() {
            if (mHomeCity == null) {
                val name: String = context.getString(R.string.home_label)
                val timeZone = settingsModel.homeTimeZone
                mHomeCity = City(null, -1, null, name, name, timeZone)
            }

            return mHomeCity!!
        }

    /**
     * @return a list of cities not selected for display
     */
    val unselectedCities: List<City>
        get() {
            if (mUnselectedCities == null) {
                // Create a set of selections to identify the unselected cities.
                val selected: List<City> = selectedCities.toMutableList()
                val selectedSet: Set<City> = Utils.newArraySet(selected)

                val all = cityMap.values
                val unselected: MutableList<City> = ArrayList(all.size - selectedSet.size)
                for (city in all) {
                    if (!selectedSet.contains(city)) {
                        unselected.add(city)
                    }
                }

                // Sort the unselected cities according by the user's preferred sort.
                Collections.sort(unselected, citySortComparator)
                mUnselectedCities = unselected
            }

            return mUnselectedCities!!
        }

    /**
     * @return a list of cities selected for display
     */
    val selectedCities: List<City>
        get() {
            if (mSelectedCities == null) {
                val selectedCities = CityDAO.getSelectedCities(prefs, cityMap)
                Collections.sort(selectedCities, UtcOffsetComparator())
                mSelectedCities = selectedCities
            }

            return mSelectedCities!!
        }

    /**
     * @param cities the new collection of cities selected for display by the user
     */
    fun setSelectedCities(cities: Collection<City>) {
        val oldCities = allCities
        CityDAO.setSelectedCities(prefs, cities)

        // Clear caches affected by this update.
        mAllCities = null
        mSelectedCities = null
        mUnselectedCities = null

        // Broadcast the change to the selected cities for the benefit of widgets.
        fireCitiesChanged(oldCities, allCities)
    }

    /**
     * @return a comparator used to locate index positions
     */
    val cityIndexComparator: Comparator<City>
        get() = when (settingsModel.citySort) {
            CitySort.NAME -> NameIndexComparator()
            CitySort.UTC_OFFSET -> UtcOffsetIndexComparator()
        }

    /**
     * @return the order in which cities are sorted
     */
    val citySort: CitySort
        get() = settingsModel.citySort

    /**
     * Adjust the order in which cities are sorted.
     */
    fun toggleCitySort() {
        settingsModel.toggleCitySort()

        // Clear caches affected by this update.
        mAllCities = null
        mUnselectedCities = null
    }

    private val cityMap: Map<String, City>
        get() {
            if (mCityMap == null) {
                mCityMap = CityDAO.getCities(context)
            }

            return mCityMap!!
        }

    private val citySortComparator: Comparator<City>
        get() = when (settingsModel.citySort) {
            CitySort.NAME -> NameComparator()
            CitySort.UTC_OFFSET -> UtcOffsetComparator()
        }

    private fun fireCitiesChanged(oldCities: List<City>, newCities: List<City>) {
        context.sendBroadcast(Intent(DataModel.ACTION_WORLD_CITIES_CHANGED))
        for (cityListener in mCityListeners) {
            cityListener.citiesChanged(oldCities, newCities)
        }
    }

    /**
     * Cached information that is locale-sensitive must be cleared in response to locale changes.
     */
    private inner class LocaleChangedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            mCityMap = null
            mHomeCity = null
            mAllCities = null
            mSelectedCities = null
            mUnselectedCities = null
        }
    }

    /**
     * This receiver is notified when shared preferences change. Cached information built on
     * preferences must be cleared.
     */
    private inner class PreferenceListener : OnSharedPreferenceChangeListener {
        override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
            when (key) {
                SettingsActivity.KEY_HOME_TZ -> {
                    mHomeCity = null
                    val cities = allCities
                    fireCitiesChanged(cities, cities)
                }
                SettingsActivity.KEY_AUTO_HOME_CLOCK -> {
                    val cities = allCities
                    fireCitiesChanged(cities, cities)
                }
            }
        }
    }
}