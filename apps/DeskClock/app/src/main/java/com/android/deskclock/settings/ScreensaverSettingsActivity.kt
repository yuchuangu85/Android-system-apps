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

package com.android.deskclock.settings

import android.annotation.TargetApi
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

import com.android.deskclock.R
import com.android.deskclock.Utils

/**
 * Settings for Clock screen saver
 */
class ScreensaverSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screensaver_settings)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.getItemId()) {
            android.R.id.home -> {
                finish()
                return true
            }
            else -> {
            }
        }
        return super.onOptionsItemSelected(item)
    }

    class PrefsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {

        @TargetApi(Build.VERSION_CODES.N)
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            if (Utils.isNOrLater) {
                getPreferenceManager().setStorageDeviceProtected()
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String) {
            addPreferencesFromResource(R.xml.screensaver_settings)
        }

        override fun onResume() {
            super.onResume()
            refresh()
        }

        override fun onPreferenceChange(pref: Preference, newValue: Any?): Boolean {
            if (KEY_CLOCK_STYLE == pref.getKey()) {
                val clockStylePref: ListPreference = pref as ListPreference
                val index: Int = clockStylePref.findIndexOfValue(newValue as String?)
                clockStylePref.setSummary(clockStylePref.getEntries().get(index))
            }
            return true
        }

        private fun refresh() {
            val clockStylePref = findPreference<ListPreference>(KEY_CLOCK_STYLE) as ListPreference
            clockStylePref.setSummary(clockStylePref.getEntry())
            clockStylePref.setOnPreferenceChangeListener(this)
        }
    }

    companion object {
        const val KEY_CLOCK_STYLE = "screensaver_clock_style"
        const val KEY_NIGHT_MODE = "screensaver_night_mode"
    }
}