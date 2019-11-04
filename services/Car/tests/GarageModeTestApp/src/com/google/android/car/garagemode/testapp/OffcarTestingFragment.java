/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.car.garagemode.testapp;

import android.app.job.JobInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class OffcarTestingFragment extends Fragment implements AdapterView.OnItemSelectedListener {
    private static final Logger LOG = new Logger("OffcarTestingFragment");

    private String mNetworkRequirement;
    private int mJobDurationSelected;

    private CheckBox mRequirePersisted;
    private CheckBox mRequireIdleness;
    private CheckBox mRequireCharging;

    private Spinner mJobDurationSpinner;
    private Spinner mNetworkTypeSpinner;

    private TextView mWatchdogTextView;
    private ListView mJobsListView;

    private Button mEnterGarageModeBtn;
    private Button mExitGarageModeBtn;
    private Button mAddJobBtn;

    private Watchdog mWatchdog;
    private JobSchedulerWrapper mJobSchedulerWrapper;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.offcar_testing, container, false);

        defineViewsFromFragment(v);

        populateNetworkTypeSpinner();
        populateJobDurationSpinner();

        defineButtonActions();

        return v;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        String value = (String) parent.getItemAtPosition(pos);
        switch (parent.getId()) {
            case R.id.networkType:
                applyNetworkTypeRequirement(value);
                break;
            case R.id.jobDuration:
                applyJobDuration(value);
                break;
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    @Override
    public void onResume() {
        super.onResume();
        LOG.d("Resuming app");

        mWatchdog = new Watchdog(getContext(), mWatchdogTextView);
        mWatchdog.start();
        mJobSchedulerWrapper = new JobSchedulerWrapper(
                getContext(),
                mJobsListView);
        mJobSchedulerWrapper.setWatchdog(mWatchdog);
        mJobSchedulerWrapper.start();
    }

    @Override
    public void onPause() {
        super.onPause();
        LOG.d("Pausing app");
        mWatchdog.stop();
        mWatchdog = null;
        mJobSchedulerWrapper.stop();
        mJobSchedulerWrapper = null;
    }

    private void defineViewsFromFragment(View v) {
        mRequirePersisted = v.findViewById(R.id.requirePersistedCheckbox);
        mRequireIdleness = v.findViewById(R.id.requireIdlenessCheckbox);
        mRequireCharging = v.findViewById(R.id.requireChargingCheckbox);

        mJobDurationSpinner = v.findViewById(R.id.jobDuration);
        mNetworkTypeSpinner = v.findViewById(R.id.networkType);

        mWatchdogTextView = v.findViewById(R.id.garageModeWatchdog);
        mJobsListView = v.findViewById(R.id.jobsListView);

        mEnterGarageModeBtn = v.findViewById(R.id.enterGarageModeBtn);
        mExitGarageModeBtn = v.findViewById(R.id.exitGarageModeBtn);
        mAddJobBtn = v.findViewById(R.id.addJobBtn);
    }

    private void defineButtonActions() {
        mEnterGarageModeBtn.setOnClickListener(view -> onEnterGarageModeBtnClick());
        mExitGarageModeBtn.setOnClickListener(view -> onExitGarageModeBtnClick());
        mAddJobBtn.setOnClickListener(view -> onAddJobBtnClick());
    }

    private void onEnterGarageModeBtnClick() {
        LOG.d("Entering garage mode...");
        CarIdlenessTrackerWrapper.sendBroadcastToEnterGarageMode(getContext());
        if (mWatchdog != null) {
            mWatchdog.logEvent("Entering garage mode...");
        }
    }

    private void onExitGarageModeBtnClick() {
        LOG.d("Exiting garage mode...");
        CarIdlenessTrackerWrapper.sendBroadcastToExitGarageMode(getContext());
        if (mWatchdog != null) {
            mWatchdog.logEvent("Exiting garage mode...");
        }
    }

    private void onAddJobBtnClick() {
        LOG.d("Adding a job...");
        if (mJobSchedulerWrapper == null) {
            LOG.e("JobSchedulerWrapper is not initialized yet. Try again later.");
            return;
        }
        mJobSchedulerWrapper.scheduleAJob(
                mJobDurationSelected,
                parseNetworkRequirement(),
                mRequireCharging.isChecked(),
                mRequireIdleness.isChecked());
    }

    private void applyJobDuration(String value) {
        String metric = value.split(" ")[1];
        mJobDurationSelected = Integer.parseInt(value.split(" ")[0]);
        if (metric.startsWith("minute")) {
            mJobDurationSelected *= 60;
        }
        if (metric.startsWith("hour")) {
            mJobDurationSelected *= 3600;
        }
        mWatchdog.logEvent("Job duration is now: " + mJobDurationSelected + "s");
    }

    private void applyNetworkTypeRequirement(String value) {
        mNetworkRequirement = value;
        mWatchdog.logEvent("Job network requirement changed to: " + value);
    }

    private int parseNetworkRequirement() {
        if (mNetworkRequirement.equals("NONE")) {
            return JobInfo.NETWORK_TYPE_NONE;
        }
        if (mNetworkRequirement.equals("UNMETERED")) {
            return JobInfo.NETWORK_TYPE_UNMETERED;
        }
        if (mNetworkRequirement.equals("ANY")) {
            return JobInfo.NETWORK_TYPE_ANY;
        }
        return JobInfo.NETWORK_BYTES_UNKNOWN;
    }

    private void populateJobDurationSpinner() {
        populateSpinner(
                mJobDurationSpinner,
                ArrayAdapter.createFromResource(
                        getContext(),
                        R.array.duration_list,
                        android.R.layout.simple_spinner_item));
    }

    private void populateNetworkTypeSpinner() {
        populateSpinner(
                mNetworkTypeSpinner,
                ArrayAdapter.createFromResource(
                        getContext(),
                        R.array.network_types_list,
                        android.R.layout.simple_spinner_item));
    }

    private void populateSpinner(Spinner spinner, ArrayAdapter adapter) {
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);
    }
}
