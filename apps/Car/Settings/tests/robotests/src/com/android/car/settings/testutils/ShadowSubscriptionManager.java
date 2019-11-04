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

package com.android.car.settings.testutils;

import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.telephony.SubscriptionPlan;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;

import java.util.ArrayList;
import java.util.List;

@Implements(SubscriptionManager.class)
public class ShadowSubscriptionManager extends org.robolectric.shadows.ShadowSubscriptionManager {

    private static SubscriptionInfo sDefaultDataSubscriptionInfo = null;

    private List<SubscriptionPlan> mSubscriptionPlanList;
    private List<SubscriptionInfo> mSelectableSubscriptionInfoList;
    private List<OnSubscriptionsChangedListener> mOnSubscriptionsChangedListeners =
            new ArrayList<>();
    private int mCurrentActiveSubscriptionId;

    @Implementation
    protected List<SubscriptionPlan> getSubscriptionPlans(int subId) {
        return mSubscriptionPlanList;
    }

    public void setSubscriptionPlans(List<SubscriptionPlan> subscriptionPlanList) {
        mSubscriptionPlanList = subscriptionPlanList;
    }

    @Implementation
    protected SubscriptionInfo getDefaultDataSubscriptionInfo() {
        return sDefaultDataSubscriptionInfo;
    }

    public static void setDefaultDataSubscriptionInfo(SubscriptionInfo subscriptionInfo) {
        sDefaultDataSubscriptionInfo = subscriptionInfo;
    }

    @Implementation
    protected List<SubscriptionInfo> getSelectableSubscriptionInfoList() {
        return mSelectableSubscriptionInfoList;
    }

    public void setSelectableSubscriptionInfoList(List<SubscriptionInfo> infos) {
        mSelectableSubscriptionInfoList = infos;
    }

    @Implementation
    protected void addOnSubscriptionsChangedListener(OnSubscriptionsChangedListener listener) {
        super.addOnSubscriptionsChangedListener(listener);
        mOnSubscriptionsChangedListeners.add(listener);
    }

    @Implementation
    protected void removeOnSubscriptionsChangedListener(OnSubscriptionsChangedListener listener) {
        super.removeOnSubscriptionsChangedListener(listener);
        mOnSubscriptionsChangedListeners.remove(listener);
    }

    public List<OnSubscriptionsChangedListener> getOnSubscriptionChangedListeners() {
        return mOnSubscriptionsChangedListeners;

    }

    @Implementation
    protected boolean isActiveSubscriptionId(int subscriptionId) {
        return mCurrentActiveSubscriptionId == subscriptionId;
    }

    public void setCurrentActiveSubscriptionId(int subscriptionId) {
        mCurrentActiveSubscriptionId = subscriptionId;
    }

    @Resetter
    public static void reset() {
        org.robolectric.shadows.ShadowSubscriptionManager.reset();
        sDefaultDataSubscriptionInfo = null;
    }
}
