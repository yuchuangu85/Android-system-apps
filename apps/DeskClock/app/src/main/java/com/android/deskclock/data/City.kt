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

import java.text.Collator
import java.util.Locale
import java.util.TimeZone

/**
 * A read-only domain object representing a city of the world and associated time information. It
 * also contains static comparators that can be instantiated to order cities in common sort orders.
 */
class City internal constructor(
    /** A unique identifier for the city.  */
    val id: String?,
    /** An optional numeric index used to order cities for display; -1 if no such index exists.  */
    val index: Int,
    /** An index string used to order cities for display.  */
    val indexString: String?,
    /** The display name of the city.  */
    val name: String,
    /** The phonetic name of the city used to order cities for display.  */
    val phoneticName: String,
    /** The TimeZone corresponding to the city.  */
    val timeZone: TimeZone
) {

    /** A cached upper case form of the [.mName] used in case-insensitive name comparisons.  */
    private var mNameUpperCase: String? = null

    /**
     * A cached upper case form of the [.mName] used in case-insensitive name comparisons
     * which ignore [.removeSpecialCharacters] special characters.
     */
    private var mNameUpperCaseNoSpecialCharacters: String? = null

    /**
     * @return the city name converted to upper case
     */
    val nameUpperCase: String
        get() {
            if (mNameUpperCase == null) {
                mNameUpperCase = name.toUpperCase()
            }
            return mNameUpperCase!!
        }

    /**
     * @return the city name converted to upper case with all special characters removed
     */
    private val nameUpperCaseNoSpecialCharacters: String
        get() {
            if (mNameUpperCaseNoSpecialCharacters == null) {
                mNameUpperCaseNoSpecialCharacters = removeSpecialCharacters(nameUpperCase)
            }
            return mNameUpperCaseNoSpecialCharacters!!
        }

    /**
     * @param upperCaseQueryNoSpecialCharacters search term with all special characters removed
     * to match against the upper case city name
     * @return `true` iff the name of this city starts with the given query
     */
    fun matches(upperCaseQueryNoSpecialCharacters: String): Boolean {
        // By removing all special characters, prefix matching becomes more liberal and it is easier
        // to locate the desired city. e.g. "St. Lucia" is matched by "StL", "St.L", "St L", "St. L"
        return nameUpperCaseNoSpecialCharacters.startsWith(upperCaseQueryNoSpecialCharacters)
    }

    override fun toString(): String {
        return String.format(Locale.US,
                "City {id=%s, index=%d, indexString=%s, name=%s, phonetic=%s, tz=%s}",
                id, index, indexString, name, phoneticName, timeZone.id)
    }

    /**
     * Orders by:
     *
     *  1. UTC offset of [timezone][.getTimeZone]
     *  1. [numeric index][.getIndex]
     *  1. [.getIndexString] alphabetic index}
     *  1. [phonetic name][.getPhoneticName]
     */
    class UtcOffsetComparator : Comparator<City> {
        private val mDelegate1: Comparator<City> = UtcOffsetIndexComparator()
        private val mDelegate2: Comparator<City> = NameComparator()

        override fun compare(c1: City, c2: City): Int {
            var result = mDelegate1.compare(c1, c2)

            if (result == 0) {
                result = mDelegate2.compare(c1, c2)
            }

            return result
        }
    }

    /**
     * Orders by:
     *
     *  1. UTC offset of [timezone][.getTimeZone]
     */
    class UtcOffsetIndexComparator : Comparator<City> {
        // Snapshot the current time when the Comparator is created to obtain consistent offsets.
        private val now = System.currentTimeMillis()

        override fun compare(c1: City, c2: City): Int {
            val utcOffset1 = c1.timeZone.getOffset(now)
            val utcOffset2 = c2.timeZone.getOffset(now)
            return utcOffset1.compareTo(utcOffset2)
        }
    }

    /**
     * This comparator sorts using the city fields that influence natural name sort order:
     *
     *  1. [numeric index][.getIndex]
     *  1. [.getIndexString] alphabetic index}
     *  1. [phonetic name][.getPhoneticName]
     */
    class NameComparator : Comparator<City> {
        private val mDelegate: Comparator<City> = NameIndexComparator()

        // Locale-sensitive comparator for phonetic names.
        private val mNameCollator = Collator.getInstance()

        override fun compare(c1: City, c2: City): Int {
            var result = mDelegate.compare(c1, c2)

            if (result == 0) {
                result = mNameCollator.compare(c1.phoneticName, c2.phoneticName)
            }

            return result
        }
    }

    /**
     * Orders by:
     *
     *  1. [numeric index][.getIndex]
     *  1. [.getIndexString] alphabetic index}
     */
    class NameIndexComparator : Comparator<City> {
        // Locale-sensitive comparator for index strings.
        private val mNameCollator = Collator.getInstance()

        override fun compare(c1: City, c2: City): Int {
            var result = c1.index.compareTo(c2.index)

            if (result == 0) {
                result = mNameCollator.compare(c1.indexString, c2.indexString)
            }

            return result
        }
    }

    companion object {
        /**
         * Strips out any characters considered optional for matching purposes. These include spaces,
         * dashes, periods and apostrophes.
         *
         * @param token a city name or search term
         * @return the given `token` without any characters considered optional when matching
         */
        @JvmStatic
        fun removeSpecialCharacters(token: String): String {
            return token.replace("[ -.']".toRegex(), "")
        }
    }
}