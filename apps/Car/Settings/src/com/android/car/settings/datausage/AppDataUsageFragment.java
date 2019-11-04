/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car.settings.datausage;

import android.content.Context;
import android.net.NetworkPolicy;
import android.net.NetworkPolicyManager;
import android.net.NetworkTemplate;
import android.os.Bundle;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.format.DateUtils;
import android.util.Pair;

import androidx.annotation.VisibleForTesting;
import androidx.annotation.XmlRes;
import androidx.loader.app.LoaderManager;

import com.android.car.settings.R;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.SettingsFragment;
import com.android.settingslib.NetworkPolicyEditor;

import java.time.ZonedDateTime;
import java.util.Iterator;

/**
 * Screen to display list of applications using the data.
 */
public class AppDataUsageFragment extends SettingsFragment {

    private static final Logger LOG = new Logger(AppDataUsageFragment.class);

    private static final String ARG_NETWORK_SUB_ID = "network_sub_id";
    /** Value to represent that the subscription id hasn't been computed yet. */
    private static final int SUB_ID_NULL = Integer.MIN_VALUE;

    private AppsNetworkStatsManager mAppsNetworkStatsManager;
    private NetworkPolicyEditor mPolicyEditor;
    private NetworkTemplate mNetworkTemplate;

    private Bundle mBundle;

    /**
     * Creates a new instance of the {@link AppDataUsageFragment}, which shows settings related to
     * the given {@code subId}.
     */
    public static AppDataUsageFragment newInstance(int subId) {
        AppDataUsageFragment fragment = new AppDataUsageFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_NETWORK_SUB_ID, subId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.app_data_usage_fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
        int subId = getArguments() != null
                ? getArguments().getInt(ARG_NETWORK_SUB_ID, SUB_ID_NULL) : SUB_ID_NULL;
        if (subId == SUB_ID_NULL) {
            LOG.d("Cannot get the subscription id from arguments. Switching to default "
                    + "subscription Id: " + subId);
            SubscriptionManager subscriptionManager = context.getSystemService(
                    SubscriptionManager.class);
            subId = DataUsageUtils.getDefaultSubscriptionId(subscriptionManager);
        }
        mNetworkTemplate = DataUsageUtils.getMobileNetworkTemplate(telephonyManager, subId);
        mPolicyEditor = new NetworkPolicyEditor(NetworkPolicyManager.from(context));
        mAppsNetworkStatsManager = new AppsNetworkStatsManager(getContext());
        mAppsNetworkStatsManager.registerListener(
                use(AppDataUsagePreferenceController.class, R.string.pk_app_data_usage_detail));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBundle = getBundleForNetworkStats();

        LoaderManager loaderManager = LoaderManager.getInstance(this);
        mAppsNetworkStatsManager.startLoading(loaderManager, mBundle);
    }

    private Bundle getBundleForNetworkStats() {
        long historyStart = System.currentTimeMillis();
        long historyEnd = historyStart + 1;

        long start = 0;
        long end = 0;

        boolean hasCycles = false;

        NetworkPolicy policy = mPolicyEditor.getPolicy(mNetworkTemplate);
        if (policy != null) {
            Iterator<Pair<ZonedDateTime, ZonedDateTime>> it = NetworkPolicyManager
                    .cycleIterator(policy);
            while (it.hasNext()) {
                Pair<ZonedDateTime, ZonedDateTime> cycle = it.next();
                start = cycle.first.toInstant().toEpochMilli();
                end = cycle.second.toInstant().toEpochMilli();
                hasCycles = true;
            }
        }

        if (!hasCycles) {
            // no policy defined cycles; show entry for each four-week period
            long cycleEnd = historyEnd;
            while (cycleEnd > historyStart) {
                long cycleStart = cycleEnd - (DateUtils.WEEK_IN_MILLIS * 4);

                start = cycleStart;
                end = cycleEnd;
                cycleEnd = cycleStart;
            }
        }

        return SummaryForAllUidLoader.buildArgs(mNetworkTemplate, start, end);
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    Bundle getBundle() {
        return mBundle;
    }
}
