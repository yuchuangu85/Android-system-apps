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

package com.android.ons;

import android.telephony.AvailableNetworkInfo;
import android.telephony.SubscriptionManager;

import com.android.internal.telephony.IUpdateAvailableNetworksCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * OpportunisticNetworkService implements ions.
 * It scans network and matches the results with opportunistic subscriptions.
 * Use the same to provide user opportunistic data in areas with corresponding networks
 */
public class ONSConfigInput {
    private static final String TAG = "ONSConfigInput";
    private static final boolean DBG = true;
    private ArrayList<AvailableNetworkInfo> mAvailableNetworkInfos;
    private int mPreferredDataSub;
    private int mPrimarySub;
    private IUpdateAvailableNetworksCallback mAvailableNetworkCallback;

    ONSConfigInput(ArrayList<AvailableNetworkInfo> availableNetworkInfos,
            IUpdateAvailableNetworksCallback callback) {
        mAvailableNetworkInfos = availableNetworkInfos;
        mPreferredDataSub = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        mPrimarySub = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        mAvailableNetworkCallback = callback;
    }

    public IUpdateAvailableNetworksCallback getAvailableNetworkCallback() {
        return mAvailableNetworkCallback;
    }

    public void setAvailableNetworkInfo(ArrayList<AvailableNetworkInfo> availableNetworkInfos) {
        mAvailableNetworkInfos = availableNetworkInfos;
    }

    public void setPreferredDataSub(int preferredDataSub) {
        mPreferredDataSub = preferredDataSub;
    }

    public int getPreferredDataSub() {
        return mPreferredDataSub;
    }

    public void setPrimarySub(int primarySub) {
        mPrimarySub = primarySub;
    }

    public int getPrimarySub() {
        return mPrimarySub;
    }

    public ArrayList<AvailableNetworkInfo> getAvailableNetworkInfos() {
        return mAvailableNetworkInfos;
    }

    @Override
    public String toString() {
        return ("ONSConfigInput:"
                + " " + mAvailableNetworkInfos
                + " " + mPreferredDataSub);
    }
}
