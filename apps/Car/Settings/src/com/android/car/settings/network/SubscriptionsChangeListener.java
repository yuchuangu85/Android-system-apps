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

package com.android.car.settings.network;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.SubscriptionManager;

import com.android.internal.telephony.TelephonyIntents;

/** Listens to changes in availability of telephony subscriptions. */
public class SubscriptionsChangeListener {

    /** Client defined action to trigger when there are subscription changes. */
    public interface SubscriptionsChangeAction {
        /** Action taken when subscriptions changed. */
        void onSubscriptionsChanged();
    }

    private static final IntentFilter RADIO_TECH_CHANGED_FILTER = new IntentFilter(
            TelephonyIntents.ACTION_RADIO_TECHNOLOGY_CHANGED);

    private final Context mContext;
    private final SubscriptionManager mSubscriptionManager;
    private final SubscriptionsChangeAction mSubscriptionsChangeAction;

    private final SubscriptionManager.OnSubscriptionsChangedListener mSubscriptionsChangedListener =
            new SubscriptionManager.OnSubscriptionsChangedListener() {
                @Override
                public void onSubscriptionsChanged() {
                    subscriptionsChangedCallback();
                }
            };

    private final BroadcastReceiver mRadioTechChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!isInitialStickyBroadcast()) {
                subscriptionsChangedCallback();
            }
        }
    };

    public SubscriptionsChangeListener(Context context, SubscriptionsChangeAction action) {
        mContext = context;
        mSubscriptionManager = context.getSystemService(SubscriptionManager.class);
        mSubscriptionsChangeAction = action;
    }

    /** Starts the various listeners necessary to track changes in telephony subscriptions. */
    public void start() {
        mSubscriptionManager.addOnSubscriptionsChangedListener(mSubscriptionsChangedListener);
        mContext.registerReceiver(mRadioTechChangeReceiver, RADIO_TECH_CHANGED_FILTER);
    }

    /** Stops the various listeners necessary to track changes in telephony subscriptions. */
    public void stop() {
        mSubscriptionManager.removeOnSubscriptionsChangedListener(mSubscriptionsChangedListener);
        mContext.unregisterReceiver(mRadioTechChangeReceiver);
    }

    private void subscriptionsChangedCallback() {
        if (mSubscriptionsChangeAction != null) {
            mSubscriptionsChangeAction.onSubscriptionsChanged();
        }
    }
}
