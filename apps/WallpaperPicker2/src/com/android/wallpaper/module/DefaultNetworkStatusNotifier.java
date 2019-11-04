/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.wallpaper.module;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of {@link NetworkStatusNotifier} which uses
 * {@link android.net.ConnectivityManager} to provide network status.
 */
public class DefaultNetworkStatusNotifier implements NetworkStatusNotifier {

    private Context mAppContext;
    private ConnectivityManager mConnectivityManager;
    private BroadcastReceiver mReceiver;
    private List<Listener> mListeners;

    public DefaultNetworkStatusNotifier(Context context) {
        mAppContext = context.getApplicationContext();

        mConnectivityManager =
                (ConnectivityManager) mAppContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        mListeners = new ArrayList<>();
    }

    @Override
    @NetworkStatus
    public int getNetworkStatus() {
        NetworkInfo activeNetwork = mConnectivityManager.getActiveNetworkInfo();

        if (activeNetwork != null && activeNetwork.isConnected()) {
            return NETWORK_CONNECTED;
        }
        return NETWORK_NOT_CONNECTED;
    }

    @Override
    public void registerListener(Listener listener) {
        if (mReceiver == null) {
            mReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    @NetworkStatus int networkStatus = getNetworkStatus();
                    for (int i = 0; i < mListeners.size(); i++) {
                        mListeners.get(i).onNetworkChanged(networkStatus);
                    }
                }
            };
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(android.net.ConnectivityManager.CONNECTIVITY_ACTION);

            if (!mListeners.contains(listener)) {
                mListeners.add(listener);
            }
            // Upon an initial register, the broadcast is immediately sent.
            mAppContext.registerReceiver(mReceiver, intentFilter);
            return;
        }

        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    @Override
    public void unregisterListener(Listener listener) {
        mListeners.remove(listener);

        if (!mListeners.isEmpty() || mReceiver == null) {
            return;
        }

        mAppContext.unregisterReceiver(mReceiver);
        mReceiver = null;
    }
}
