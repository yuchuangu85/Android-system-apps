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

import static android.telephony.PhoneStateListener.LISTEN_NONE;

import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Implements(TelephonyManager.class)
public class ShadowTelephonyManager extends org.robolectric.shadows.ShadowTelephonyManager {

    public static final String SUBSCRIBER_ID = "test_id";
    private static Map<Integer, Integer> sSubIdsWithResetCalledCount = new HashMap<>();
    private static int sSimCount = 1;
    private final Map<PhoneStateListener, Integer> mPhoneStateRegistrations = new HashMap<>();
    private boolean mIsDataEnabled = false;
    private boolean mIsRoamingEnabled = false;

    public static boolean verifyFactoryResetCalled(int subId, int numTimes) {
        if (!sSubIdsWithResetCalledCount.containsKey(subId)) return false;
        return sSubIdsWithResetCalledCount.get(subId) == numTimes;
    }

    @Implementation
    protected void listen(PhoneStateListener listener, int flags) {
        super.listen(listener, flags);

        if (flags == LISTEN_NONE) {
            mPhoneStateRegistrations.remove(listener);
        } else {
            mPhoneStateRegistrations.put(listener, flags);
        }
    }

    public List<PhoneStateListener> getListenersForFlags(int flags) {
        List<PhoneStateListener> listeners = new ArrayList<>();
        for (PhoneStateListener listener : mPhoneStateRegistrations.keySet()) {
            if ((mPhoneStateRegistrations.get(listener) & flags) != 0) {
                listeners.add(listener);
            }
        }
        return listeners;
    }

    @Implementation
    protected void setDataEnabled(boolean enable) {
        mIsDataEnabled = enable;
    }

    @Implementation
    protected boolean isDataEnabled() {
        return mIsDataEnabled;
    }

    @Implementation
    protected void factoryReset(int subId) {
        sSubIdsWithResetCalledCount.put(subId,
                sSubIdsWithResetCalledCount.getOrDefault(subId, 0) + 1);
    }

    @Implementation
    protected String getSubscriberId(int subId) {
        return subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID ? null : SUBSCRIBER_ID;
    }

    @Implementation
    protected int getSimCount() {
        return sSimCount;
    }

    public static void setSimCount(int simCount) {
        sSimCount = simCount;
    }

    @Implementation
    protected void setDataRoamingEnabled(boolean isEnabled) {
        mIsRoamingEnabled = isEnabled;
    }

    @Implementation
    protected boolean isDataRoamingEnabled() {
        return mIsRoamingEnabled;
    }

    @Resetter
    public static void reset() {
        sSubIdsWithResetCalledCount.clear();
        sSimCount = 1;
    }
}
