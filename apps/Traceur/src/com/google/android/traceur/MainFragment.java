/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.traceur;

import android.annotation.Nullable;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Build;
import android.os.Bundle;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Menu;
import android.view.MenuInflater;
import android.widget.Toast;

import com.android.settingslib.HelpUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

public class MainFragment extends PreferenceFragment {

    static final String TAG = TraceUtils.TAG;

    public static final String ACTION_REFRESH_TAGS = "com.android.traceur.REFRESH_TAGS";

    private SwitchPreference mTracingOn;

    private AlertDialog mAlertDialog;
    private SharedPreferences mPrefs;

    private MultiSelectListPreference mTags;

    private boolean mRefreshing;

    private BroadcastReceiver mRefreshReceiver;

    OnSharedPreferenceChangeListener mSharedPreferenceChangeListener =
        new OnSharedPreferenceChangeListener () {
              public void onSharedPreferenceChanged(
                      SharedPreferences sharedPreferences, String key) {
                  refreshUi();
              }
        };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(
                getActivity().getApplicationContext());

        mTracingOn = (SwitchPreference) findPreference(getActivity().getString(R.string.pref_key_tracing_on));
        mTracingOn.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
              Receiver.updateTracing(getContext());
              return true;
            }
        });

        mTags = (MultiSelectListPreference) findPreference(getContext().getString(R.string.pref_key_tags));
        mTags.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (mRefreshing) {
                    return true;
                }
                Set<String> set = (Set<String>) newValue;
                TreeMap<String, String> available = TraceUtils.listCategories();
                ArrayList<String> clean = new ArrayList<>(set.size());

                for (String s : set) {
                    if (available.containsKey(s)) {
                        clean.add(s);
                    }
                }
                set.clear();
                set.addAll(clean);
                return true;
            }
        });

        findPreference("restore_default_tags").setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        refreshUi(/* restoreDefaultTags =*/ true);
                        Toast.makeText(getContext(),
                            getContext().getString(R.string.default_categories_restored),
                                Toast.LENGTH_SHORT).show();
                        return true;
                    }
                });

        findPreference(getString(R.string.pref_key_quick_setting))
            .setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        Receiver.updateQuickSettings(getContext());
                        return true;
                    }
                });

        findPreference("clear_saved_traces").setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        new AlertDialog.Builder(getContext())
                            .setTitle(R.string.clear_saved_traces_question)
                            .setMessage(R.string.all_traces_will_be_deleted)
                            .setPositiveButton(R.string.clear,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        TraceUtils.clearSavedTraces();
                                    }
                                })
                            .setNegativeButton(android.R.string.no,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                })
                            .create()
                            .show();
                        return true;
                    }
                });

        refreshUi();

        mRefreshReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refreshUi();
            }
        };

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        getPreferenceScreen().getSharedPreferences()
            .registerOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);
        getActivity().registerReceiver(mRefreshReceiver, new IntentFilter(ACTION_REFRESH_TAGS));
        Receiver.updateTracing(getContext());
    }

    @Override
    public void onStop() {
        getPreferenceScreen().getSharedPreferences()
            .unregisterOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);
        getActivity().unregisterReceiver(mRefreshReceiver);

        if (mAlertDialog != null) {
            mAlertDialog.cancel();
            mAlertDialog = null;
        }

        super.onStop();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.main);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        HelpUtils.prepareHelpMenuItem(getActivity(), menu, R.string.help_url,
            this.getClass().getName());
    }

    private void refreshUi() {
        refreshUi(/* restoreDefaultTags =*/ false);
    }

    /*
     * Refresh the preferences UI to make sure it reflects the current state of the preferences and
     * system.
     */
    private void refreshUi(boolean restoreDefaultTags) {
        Context context = getContext();

        // Make sure the Record Trace toggle matches the preference value.
        mTracingOn.setChecked(mTracingOn.getPreferenceManager().getSharedPreferences().getBoolean(
                mTracingOn.getKey(), false));

        // Update category list to match the categories available on the system.
        Set<Entry<String, String>> availableTags = TraceUtils.listCategories().entrySet();
        ArrayList<String> entries = new ArrayList<String>(availableTags.size());
        ArrayList<String> values = new ArrayList<String>(availableTags.size());
        for (Entry<String, String> entry : availableTags) {
            entries.add(entry.getKey() + ": " + entry.getValue());
            values.add(entry.getKey());
        }

        mRefreshing = true;
        try {
            mTags.setEntries(entries.toArray(new String[0]));
            mTags.setEntryValues(values.toArray(new String[0]));
            if (restoreDefaultTags || !mPrefs.contains(context.getString(R.string.pref_key_tags))) {
                mTags.setValues(Receiver.getDefaultTagList());
            }
        } finally {
            mRefreshing = false;
        }

        // Update subtitles on this screen.
        Set<String> categories = mTags.getValues();
        mTags.setSummary(Receiver.getDefaultTagList().equals(categories)
                         ? context.getString(R.string.default_categories)
                         : context.getResources().getQuantityString(R.plurals.num_categories_selected,
                              categories.size(), categories.size()));

        ListPreference bufferSize = (ListPreference)findPreference(
                context.getString(R.string.pref_key_buffer_size));
        bufferSize.setSummary(bufferSize.getEntry());

        // If we are not using the Perfetto trace backend,
        // hide the unsupported preferences.
        if (TraceUtils.currentTraceEngine().equals(PerfettoUtils.NAME)) {
            ListPreference maxLongTraceSize = (ListPreference)findPreference(
                    context.getString(R.string.pref_key_max_long_trace_size));
            maxLongTraceSize.setSummary(maxLongTraceSize.getEntry());

            ListPreference maxLongTraceDuration = (ListPreference)findPreference(
                    context.getString(R.string.pref_key_max_long_trace_duration));
            maxLongTraceDuration.setSummary(maxLongTraceDuration.getEntry());
        } else {
            Preference longTraceCategory = findPreference("long_trace_category");
            if (longTraceCategory != null) {
                getPreferenceScreen().removePreference(longTraceCategory);
            }
        }
    }
}
