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

import static android.net.NetworkPolicyManager.POLICY_REJECT_METERED_BACKGROUND;

import android.content.Context;
import android.net.INetworkStatsService;
import android.net.INetworkStatsSession;
import android.net.NetworkPolicyManager;
import android.net.NetworkStats;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;

import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;

import com.android.car.settings.common.Logger;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Class to manage the callbacks needed to calculate network stats for an application.
 */
public class AppsNetworkStatsManager {

    /**
     * Callback that is called once the AppsNetworkStats is loaded.
     */
    public interface Callback {
        /**
         * Called when the data is successfully loaded from
         * {@link AppsNetworkStatsManager.AppsNetworkStatsResult}.
         */
        void onDataLoaded(@Nullable NetworkStats stats, @Nullable int[] restrictedUids);
    }

    private static final Logger LOG = new Logger(AppsNetworkStatsManager.class);
    private static final int NETWORK_STATS_ID = 1;

    private final Context mContext;
    private final NetworkPolicyManager mNetworkPolicyManager;
    private final List<AppsNetworkStatsManager.Callback> mAppsNetworkStatsListeners =
            new ArrayList<>();

    private INetworkStatsSession mStatsSession;

    AppsNetworkStatsManager(Context context) {
        mContext = context;
        mNetworkPolicyManager = NetworkPolicyManager.from(context);
        try {
            mStatsSession = INetworkStatsService.Stub.asInterface(
                    ServiceManager.getService(Context.NETWORK_STATS_SERVICE)).openSession();
        } catch (RemoteException e) {
            LOG.e("Could not open a network session", e);
        }
    }

    /**
     * Registers a listener that will be notified once the data is loaded.
     */
    public void registerListener(AppsNetworkStatsManager.Callback appsNetworkStatsListener) {
        if (!mAppsNetworkStatsListeners.contains(appsNetworkStatsListener)) {
            mAppsNetworkStatsListeners.add(appsNetworkStatsListener);
        }
    }

    /**
     * Unregisters the listener.
     */
    public void unregisterListener(AppsNetworkStatsManager.Callback appsNetworkStatsListener) {
        mAppsNetworkStatsListeners.remove(appsNetworkStatsListener);
    }

    /**
     * Start calculating the storage stats.
     */
    public void startLoading(LoaderManager loaderManager, Bundle bundle) {
        loaderManager.restartLoader(NETWORK_STATS_ID, bundle, new AppsNetworkStatsResult());
    }

    private void onAppsNetworkStatsLoaded(NetworkStats stats, int[] restrictedUids) {
        for (AppsNetworkStatsManager.Callback listener : mAppsNetworkStatsListeners) {
            listener.onDataLoaded(stats, restrictedUids);
        }
    }

    /**
     * Callback to calculate applications network stats.
     */
    private class AppsNetworkStatsResult implements LoaderManager.LoaderCallbacks<NetworkStats> {
        @Override
        public Loader<NetworkStats> onCreateLoader(int id, Bundle args) {
            return new SummaryForAllUidLoader(mContext, mStatsSession, args);
        }

        @Override
        public void onLoadFinished(Loader<NetworkStats> loader, NetworkStats data) {
            int[] restrictedUids = mNetworkPolicyManager.getUidsWithPolicy(
                    POLICY_REJECT_METERED_BACKGROUND);
            onAppsNetworkStatsLoaded(data, restrictedUids);
        }

        @Override
        public void onLoaderReset(Loader<NetworkStats> loader) {
            onAppsNetworkStatsLoaded(/* stats= */ null, /* restrictedUids= */ new int[0]);
        }
    }
}
