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

import static android.graphics.Typeface.BOLD;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;

import android.app.job.JobInfo;
import android.content.Context;
import android.graphics.Color;
import android.net.NetworkRequest;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

public class JobInfoRowArrayAdapter extends ArrayAdapter<JobInfo> {
    public static final Logger LOG = new Logger("JobInfoRowArrayAdapter");
    private class ViewHolder {
        TextView mJobIDView;

        TextView mRequiredNetworkView;
        TextView mIsPeriodicView;
        TextView mIsPersistedView;
        TextView mIsPrefetchView;

        TextView mIsRequireBatteryNotLowView;
        TextView mIsRequireChargingView;
        TextView mIsRequireDeviceIdleView;
        TextView mIsRequireStorageNotLowView;

        JobInfo mInfo;
    }
    private LayoutInflater mInflater;

    public JobInfoRowArrayAdapter(Context context, int resource, List<JobInfo> objects) {
        super(context, resource, objects);
        mInflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        final JobInfo info = getItem(position);

        ViewHolder holder;

        if (row == null) {
            row = mInflater.inflate(R.layout.job_info_row, parent, false);

            holder = new ViewHolder();
            holder.mJobIDView = row.findViewById(R.id.jobId);

            holder.mRequiredNetworkView = row.findViewById(R.id.requiredNetwork);
            holder.mIsPeriodicView = row.findViewById(R.id.isPeriodic);
            holder.mIsPersistedView = row.findViewById(R.id.isPersisted);
            holder.mIsPrefetchView = row.findViewById(R.id.isPrefetch);

            holder.mIsRequireBatteryNotLowView = row.findViewById(R.id.isRequireBatteryNotLow);
            holder.mIsRequireChargingView = row.findViewById(R.id.isRequireCharging);
            holder.mIsRequireDeviceIdleView = row.findViewById(R.id.isRequireDeviceIdle);
            holder.mIsRequireStorageNotLowView = row.findViewById(R.id.isRequireStorageNotLow);

            holder.mInfo = info;

            row.setTag(holder);
        } else {
            holder = (ViewHolder) row.getTag();
            holder.mInfo = info;
        }

        holder.mJobIDView.setText("ID: " + info.getId());

        setNetworkColoredText(holder.mRequiredNetworkView, "Network", info.getRequiredNetwork());

        setColoredText(holder.mIsPeriodicView, "Periodic", info.isPeriodic());
        setColoredText(holder.mIsPersistedView, "Persisted", info.isPersisted());
        setColoredText(holder.mIsPrefetchView, "Prefetch", info.isPrefetch());

        setColoredText(
                holder.mIsRequireBatteryNotLowView,
                "BatteryNotLow",
                info.isRequireBatteryNotLow());
        setColoredText(holder.mIsRequireChargingView, "Charging", info.isRequireCharging());
        setColoredText(holder.mIsRequireDeviceIdleView, "DeviceIdle", info.isRequireDeviceIdle());
        setColoredText(
                holder.mIsRequireStorageNotLowView,
                "StorageNotLow",
                info.isRequireStorageNotLow());

        return row;
    }

    private void setColoredText(TextView view, String label, boolean condition) {
        SpannableStringBuilder sb;
        String value = (condition ? "Yes" : "No");
        int color = (condition ? Color.GREEN : Color.RED);
        sb = new SpannableStringBuilder(label + ": " + value);
        applyColorAndBoldness(
                sb, color, label.length() + 2, label.length() + 2 + value.length());
        view.setText(sb);
    }

    private void setNetworkColoredText(TextView view, String label, NetworkRequest networkReq) {
        String networkType = getNetworkType(networkReq);
        SpannableStringBuilder sb = new SpannableStringBuilder(label + ": " + networkType);
        applyColorAndBoldness(
                sb, Color.GREEN, label.length() + 2, label.length() + 2 + networkType.length());
        view.setText(sb);
    }

    private void applyColorAndBoldness(SpannableStringBuilder sb, int color, int start, int end) {
        sb.setSpan(new ForegroundColorSpan(color), start, end, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        sb.setSpan(new StyleSpan(BOLD), start, end, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
    }

    private String getNetworkType(NetworkRequest networkRequest) {
        if (networkRequest == null) {
            return "None";
        } else if (networkRequest.networkCapabilities.hasCapability(NET_CAPABILITY_NOT_METERED)) {
            return "Unmetered";
        } else if (networkRequest.networkCapabilities.hasCapability(NET_CAPABILITY_NOT_ROAMING)) {
            return "Not roaming";
        } else if (networkRequest.networkCapabilities.hasTransport(TRANSPORT_CELLULAR)) {
            return "Cellular";
        } else {
            return "Any";
        }
    }
}
