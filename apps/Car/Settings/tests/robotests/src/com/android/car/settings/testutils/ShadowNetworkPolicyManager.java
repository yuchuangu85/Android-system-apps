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

package com.android.car.settings.testutils;

import android.content.Context;
import android.net.NetworkPolicy;
import android.net.NetworkPolicyManager;
import android.util.Pair;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Implements(NetworkPolicyManager.class)
public class ShadowNetworkPolicyManager {

    private static NetworkPolicyManager sNetworkPolicyManager;
    private static Iterator<Pair<ZonedDateTime, ZonedDateTime>> sCycleIterator;
    private static Map<String, Integer> sResetCalledForSubscriberCount = new HashMap<>();

    public static boolean verifyFactoryResetCalled(String subscriber, int numTimes) {
        if (!sResetCalledForSubscriberCount.containsKey(subscriber)) return false;
        return sResetCalledForSubscriberCount.get(subscriber) == numTimes;
    }

    @Implementation
    protected void factoryReset(String subscriber) {
        sResetCalledForSubscriberCount.put(subscriber,
                sResetCalledForSubscriberCount.getOrDefault(subscriber, 0) + 1);
    }

    @Implementation
    protected int[] getUidsWithPolicy(int policy) {
        return sNetworkPolicyManager == null ? new int[0] : sNetworkPolicyManager
                .getUidsWithPolicy(policy);
    }

    @Implementation
    protected static Iterator<Pair<ZonedDateTime, ZonedDateTime>> cycleIterator(
            NetworkPolicy policy) {
        return sCycleIterator;
    }

    public static void setCycleIterator(
            Iterator<Pair<ZonedDateTime, ZonedDateTime>> cycleIterator) {
        sCycleIterator = cycleIterator;
    }

    @Implementation
    public static NetworkPolicyManager from(Context context) {
        return sNetworkPolicyManager;
    }

    public static void setNetworkPolicyManager(NetworkPolicyManager networkPolicyManager) {
        sNetworkPolicyManager = networkPolicyManager;
    }

    @Resetter
    public static void reset() {
        sResetCalledForSubscriberCount.clear();
        sCycleIterator = null;
        sNetworkPolicyManager = null;
    }
}
