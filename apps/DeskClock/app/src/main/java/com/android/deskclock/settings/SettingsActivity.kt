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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Vibrator
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.preference.ListPreference
import androidx.preference.ListPreferenceDialogFragmentCompat
import androidx.preference.Preference
import androidx.preference.PreferenceDialogFragmentCompat
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.TwoStatePreference

import com.android.deskclock.BaseActivity
import com.android.deskclock.DropShadowController
import com.android.deskclock.R
import com.android.deskclock.Utils
import com.android.deskclock.actionbarmenu.MenuItemControllerFactory
import com.android.deskclock.actionbarmenu.NavUpMenuItemController
import com.android.deskclock.actionbarmenu.OptionsMenuManager
import com.android.deskclock.data.DataModel
import com.android.deskclock.ringtone.RingtonePickerActivity

/**
 * Settings for the Alarm Clock.
 */
class SettingsActivity : BaseActivity() {
    private val mOptionsMenuManager = OptionsMenuManager()

    /**
     * The controller that shows the drop shadow when content is not scrolled to the top.
     */
    private lateinit var mDropShadowController: DropShadowController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings)

        mOptionsMenuManager.addMenuItemController(NavUpMenuItemController(this))
                .addMenuItemController(*MenuItemControllerFactory.buildMenuItemControllers(this))

        // Create the prefs fragment in code to ensure it's created before PreferenceDialogFragment
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.main, PrefsFragment(), PREFS_FRAGMENT_TAG)
                    .disallowAddToBackStack()
                    .commit()
        }
    }

    override fun onResume() {
        super.onResume()

        val dropShadow: View = findViewById(R.id.drop_shadow)
        val fragment = getSupportFragmentManager().findFragmentById(R.id.main) as PrefsFragment
        mDropShadowController = DropShadowController(dropShadow, fragment.getListView())
    }

    override fun onPause() {
        mDropShadowController.stop()
        super.onPause()
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

    class PrefsFragment :
            PreferenceFragmentCompat(),
            Preference.OnPreferenceChangeListener,
            Preference.OnPreferenceClickListener {

        override fun onCreatePreferences(bundle: Bundle?, rootKey: String?) {
            getPreferenceManager().setStorageDeviceProtected()
            addPreferencesFromResource(R.xml.settings)
            val timerVibrate: Preference? = findPreference(KEY_TIMER_VIBRATE)
            timerVibrate?.let {
                val hasVibrator: Boolean = (it.getContext()
                        .getSystemService(VIBRATOR_SERVICE) as Vibrator).hasVibrator()
                it.setVisible(hasVibrator)
            }
            loadTimeZoneList()
        }

        override fun onActivityCreated(savedInstanceState: Bundle?) {
            super.onActivityCreated(savedInstanceState)

            // By default, do not recreate the DeskClock activity
            getActivity()?.setResult(RESULT_CANCELED)
        }

        override fun onResume() {
            super.onResume()
            refresh()
        }

        override fun onPreferenceChange(pref: Preference, newValue: Any): Boolean {
            when (pref.getKey()) {
                KEY_ALARM_CRESCENDO, KEY_HOME_TZ, KEY_ALARM_SNOOZE, KEY_TIMER_CRESCENDO -> {
                    val preference: ListPreference = pref as ListPreference
                    val index: Int = preference.findIndexOfValue(newValue as String)
                    preference.setSummary(preference.getEntries().get(index))
                }
                KEY_CLOCK_STYLE, KEY_WEEK_START, KEY_VOLUME_BUTTONS -> {
                    val simpleMenuPreference = pref as SimpleMenuPreference
                    val i: Int = simpleMenuPreference.findIndexOfValue(newValue as String)
                    pref.setSummary(simpleMenuPreference.getEntries().get(i))
                }
                KEY_CLOCK_DISPLAY_SECONDS -> {
                    DataModel.dataModel.displayClockSeconds = newValue as Boolean
                }
                KEY_AUTO_SILENCE -> {
                    val delay = newValue as String
                    updateAutoSnoozeSummary(pref as ListPreference, delay)
                }
                KEY_AUTO_HOME_CLOCK -> {
                    val autoHomeClockEnabled: Boolean = (pref as TwoStatePreference).isChecked()
                    val homeTimeZonePref: Preference? = findPreference(KEY_HOME_TZ)
                    homeTimeZonePref?.setEnabled(!autoHomeClockEnabled)
                }
                KEY_TIMER_VIBRATE -> {
                    val timerVibratePref: TwoStatePreference = pref as TwoStatePreference
                    DataModel.dataModel.timerVibrate = timerVibratePref.isChecked()
                }
                KEY_TIMER_RINGTONE -> pref.setSummary(DataModel.dataModel.timerRingtoneTitle)
            }

            // Set result so DeskClock knows to refresh itself
            getActivity()?.setResult(RESULT_OK)
            return true
        }

        override fun onPreferenceClick(pref: Preference): Boolean {
            val context: Context = getActivity() ?: return false

            when (pref.getKey()) {
                KEY_DATE_TIME -> {
                    val dialogIntent = Intent(Settings.ACTION_DATE_SETTINGS)
                    dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(dialogIntent)
                    return true
                }
                KEY_TIMER_RINGTONE -> {
                    startActivity(RingtonePickerActivity.createTimerRingtonePickerIntent(context))
                    return true
                }
                else -> return false
            }
        }

        override fun onDisplayPreferenceDialog(preference: Preference) {
            // Only single-selection lists are currently supported.
            val f: PreferenceDialogFragmentCompat
            f = if (preference is ListPreference) {
                ListPreferenceDialogFragmentCompat.newInstance(preference.getKey())
            } else {
                throw IllegalArgumentException("Unsupported DialogPreference type")
            }
            showDialog(f)
        }

        private fun showDialog(fragment: PreferenceDialogFragmentCompat) {
            // Don't show dialog if one is already shown.
            if (parentFragmentManager.findFragmentByTag(PREFERENCE_DIALOG_FRAGMENT_TAG) != null) {
                return
            }
            // Always set the target fragment, this is required by PreferenceDialogFragment
            // internally.
            fragment.setTargetFragment(this, 0)
            // Don't use getChildFragmentManager(), it causes issues on older platforms when the
            // target fragment is being restored after an orientation change.
            fragment.show(parentFragmentManager, PREFERENCE_DIALOG_FRAGMENT_TAG)
        }

        /**
         * Reconstruct the timezone list.
         */
        private fun loadTimeZoneList() {
            val timezones = DataModel.dataModel.timeZones
            val homeTimezonePref: ListPreference? = findPreference(KEY_HOME_TZ)
            homeTimezonePref?.let {
                it.setEntryValues(timezones.timeZoneIds)
                it.setEntries(timezones.timeZoneNames)
                it.setSummary(homeTimezonePref.getEntry())
                it.setOnPreferenceChangeListener(this)
            }
        }

        private fun refresh() {
            val autoSilencePref: ListPreference? = findPreference(KEY_AUTO_SILENCE)
            autoSilencePref?.let {
                val delay: String = it.getValue()
                updateAutoSnoozeSummary(it, delay)
                it.setOnPreferenceChangeListener(this)
            }

            val clockStylePref: SimpleMenuPreference? = findPreference(KEY_CLOCK_STYLE)
            clockStylePref?.let {
                it.setSummary(it.getEntry())
                it.setOnPreferenceChangeListener(this)
            }

            val volumeButtonsPref: SimpleMenuPreference? = findPreference(KEY_VOLUME_BUTTONS)
            volumeButtonsPref?.let {
                it.setSummary(volumeButtonsPref.getEntry())
                it.setOnPreferenceChangeListener(this)
            }

            val clockSecondsPref: Preference? = findPreference(KEY_CLOCK_DISPLAY_SECONDS)
            clockSecondsPref?.setOnPreferenceChangeListener(this)

            val autoHomeClockPref: Preference? = findPreference(KEY_AUTO_HOME_CLOCK)
            val autoHomeClockEnabled: Boolean =
                    (autoHomeClockPref as TwoStatePreference).isChecked()
            autoHomeClockPref.setOnPreferenceChangeListener(this)

            val homeTimezonePref: ListPreference? = findPreference(KEY_HOME_TZ)
            homeTimezonePref?.setEnabled(autoHomeClockEnabled)
            refreshListPreference(homeTimezonePref!!)

            refreshListPreference(findPreference(KEY_ALARM_CRESCENDO)!!)
            refreshListPreference(findPreference(KEY_TIMER_CRESCENDO)!!)
            refreshListPreference(findPreference(KEY_ALARM_SNOOZE)!!)

            val dateAndTimeSetting: Preference? = findPreference(KEY_DATE_TIME)
            dateAndTimeSetting?.setOnPreferenceClickListener(this)

            val weekStartPref: SimpleMenuPreference? = findPreference(KEY_WEEK_START)
            // Set the default value programmatically
            val weekdayOrder = DataModel.dataModel.weekdayOrder
            val firstDay = weekdayOrder.calendarDays[0]
            val value = firstDay.toString()
            weekStartPref?.let {
                val idx: Int = it.findIndexOfValue(value)
                it.setValueIndex(idx)
                it.setSummary(weekStartPref.getEntries().get(idx))
                it.setOnPreferenceChangeListener(this)
            }

            val timerRingtonePref: Preference? = findPreference(KEY_TIMER_RINGTONE)
            timerRingtonePref?.let {
                it.setOnPreferenceClickListener(this)
                it.setSummary(DataModel.dataModel.timerRingtoneTitle)
            }
        }

        private fun refreshListPreference(preference: ListPreference) {
            preference.setSummary(preference.getEntry())
            preference.setOnPreferenceChangeListener(this)
        }

        private fun updateAutoSnoozeSummary(listPref: ListPreference, delay: String) {
            val i = delay.toInt()
            if (i == -1) {
                listPref.setSummary(R.string.auto_silence_never)
            } else {
                listPref.setSummary(Utils.getNumberFormattedQuantityString(getActivity()!!,
                        R.plurals.auto_silence_summary, i))
            }
        }
    }

    companion object {
        const val KEY_ALARM_SNOOZE = "snooze_duration"
        const val KEY_ALARM_CRESCENDO = "alarm_crescendo_duration"
        const val KEY_TIMER_CRESCENDO = "timer_crescendo_duration"
        const val KEY_TIMER_RINGTONE = "timer_ringtone"
        const val KEY_TIMER_VIBRATE = "timer_vibrate"
        const val KEY_AUTO_SILENCE = "auto_silence"
        const val KEY_CLOCK_STYLE = "clock_style"
        const val KEY_CLOCK_DISPLAY_SECONDS = "display_clock_seconds"
        const val KEY_HOME_TZ = "home_time_zone"
        const val KEY_AUTO_HOME_CLOCK = "automatic_home_clock"
        const val KEY_DATE_TIME = "date_time"
        const val KEY_VOLUME_BUTTONS = "volume_button_setting"
        const val KEY_WEEK_START = "week_start"
        const val DEFAULT_VOLUME_BEHAVIOR = "0"
        const val VOLUME_BEHAVIOR_SNOOZE = "1"
        const val VOLUME_BEHAVIOR_DISMISS = "2"
        const val PREFS_FRAGMENT_TAG = "prefs_fragment"
        const val PREFERENCE_DIALOG_FRAGMENT_TAG = "preference_dialog"
    }
}