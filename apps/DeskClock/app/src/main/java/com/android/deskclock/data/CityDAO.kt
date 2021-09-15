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

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.content.res.TypedArray
import android.text.TextUtils
import android.util.ArrayMap
import androidx.annotation.VisibleForTesting

import com.android.deskclock.R

import java.util.Locale
import java.util.regex.Pattern
import java.util.TimeZone

/**
 * This class encapsulates the transfer of data between [City] domain objects and their
 * permanent storage in [Resources] and [SharedPreferences].
 */
internal object CityDAO {
    /** Regex to match numeric index values when parsing city names.  */
    private val NUMERIC_INDEX_REGEX = Pattern.compile("\\d+")

    /** Key to a preference that stores the number of selected cities.  */
    private const val NUMBER_OF_CITIES = "number_of_cities"

    /** Prefix for a key to a preference that stores the id of a selected city.  */
    private const val CITY_ID = "city_id_"

    /**
     * @param cityMap maps city ids to city instances
     * @return the list of city ids selected for display by the user
     */
    fun getSelectedCities(prefs: SharedPreferences, cityMap: Map<String, City>): List<City> {
        val size: Int = prefs.getInt(NUMBER_OF_CITIES, 0)
        val selectedCities: MutableList<City> = ArrayList(size)

        for (i in 0 until size) {
            val id: String? = prefs.getString(CITY_ID + i, null)
            val city = cityMap[id]
            if (city != null) {
                selectedCities.add(city)
            }
        }

        return selectedCities
    }

    /**
     * @param cities the collection of cities selected for display by the user
     */
    fun setSelectedCities(prefs: SharedPreferences, cities: Collection<City>) {
        val editor: SharedPreferences.Editor = prefs.edit()
        editor.putInt(NUMBER_OF_CITIES, cities.size)

        for ((count, city) in cities.withIndex()) {
            editor.putString(CITY_ID + count, city.id)
        }

        editor.apply()
    }

    /**
     * @return the domain of cities from which the user may choose a world clock
     */
    fun getCities(context: Context): Map<String, City> {
        val resources: Resources = context.getResources()
        val cityStrings: TypedArray = resources.obtainTypedArray(R.array.city_ids)
        val citiesCount: Int = cityStrings.length()

        val cities: MutableMap<String, City> = ArrayMap(citiesCount)
        try {
            for (i in 0 until citiesCount) {
                // Attempt to locate the resource id defining the city as a string.
                val cityResourceId: Int = cityStrings.getResourceId(i, 0)
                if (cityResourceId == 0) {
                    val message = String.format(Locale.ENGLISH,
                            "Unable to locate city resource id for index %d", i)
                    throw IllegalStateException(message)
                }

                val id: String = resources.getResourceEntryName(cityResourceId)
                val cityString: String? = cityStrings.getString(i)
                if (cityString == null) {
                    val message = String.format("Unable to locate city with id %s", id)
                    throw IllegalStateException(message)
                }

                // Attempt to parse the time zone from the city entry.
                val cityParts = cityString.split("[|]".toRegex()).toTypedArray()
                if (cityParts.size != 2) {
                    val message = String.format(
                            "Error parsing malformed city %s", cityString)
                    throw IllegalStateException(message)
                }

                val city = createCity(id, cityParts[0], cityParts[1])
                // Skip cities whose timezone cannot be resolved.
                if (city != null) {
                    cities[id] = city
                }
            }
        } finally {
            cityStrings.recycle()
        }

        return cities
    }

    /**
     * @param id unique identifier for city
     * @param formattedName "[index string]=[name]" or "[index string]=[name]:[phonetic name]",
     * If [index string] is empty, use the first character of name as index,
     * If phonetic name is empty, use the name itself as phonetic name.
     * @param tzId the string id of the timezone a given city is located in
     */
    @VisibleForTesting
    fun createCity(id: String?, formattedName: String, tzId: String?): City? {
        val tz = TimeZone.getTimeZone(tzId)
        // If the time zone lookup fails, GMT is returned. No cities actually map to GMT.
        if ("GMT" == tz.id) {
            return null
        }

        val parts = formattedName.split("[=:]".toRegex()).toTypedArray()
        val name = parts[1]
        // Extract index string from input, use the first character of city name as the index string
        // if one is not explicitly provided.
        val indexString = if (TextUtils.isEmpty(parts[0])) name.substring(0, 1) else parts[0]
        val phoneticName = if (parts.size == 3) parts[2] else name

        val matcher = NUMERIC_INDEX_REGEX.matcher(indexString)
        val index = if (matcher.find()) matcher.group().toInt() else -1

        return City(id, index, indexString, name, phoneticName, tz)
    }
}