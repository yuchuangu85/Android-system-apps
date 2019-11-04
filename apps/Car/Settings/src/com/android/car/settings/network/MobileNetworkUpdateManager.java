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
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.android.car.settings.common.Logger;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Listens to potential changes in subscription id and updates registered {@link
 * MobileNetworkUpdateManager.MobileNetworkUpdateListener} with the new subscription id.
 */
public class MobileNetworkUpdateManager implements DefaultLifecycleObserver {

    /** Value to represent that the subscription id hasn't been computed yet. */
    static final int SUB_ID_NULL = Integer.MIN_VALUE;
    private static final Logger LOG = new Logger(MobileNetworkUpdateManager.class);

    private final List<MobileNetworkUpdateListener> mListeners = new ArrayList<>();
    private final PhoneChangeReceiver mPhoneChangeReceiver;
    private final SubscriptionManager mSubscriptionManager;
    private List<SubscriptionInfo> mSubscriptionInfos;
    private int mCurSubscriptionId;

    private final SubscriptionManager.OnSubscriptionsChangedListener
            mOnSubscriptionsChangeListener =
            new SubscriptionManager.OnSubscriptionsChangedListener() {
                @Override
                public void onSubscriptionsChanged() {
                    if (!Objects.equals(mSubscriptionInfos,
                            mSubscriptionManager.getActiveSubscriptionInfoList(
                                    /* userVisibleOnly= */ true))) {
                        updateSubscriptions(/* forceRefresh= */ false);
                    }
                }
            };

    public MobileNetworkUpdateManager(Context context, int subId) {
        mCurSubscriptionId = subId;
        mSubscriptionManager = context.getSystemService(SubscriptionManager.class);
        mSubscriptionInfos = mSubscriptionManager.getActiveSubscriptionInfoList();

        mPhoneChangeReceiver = new PhoneChangeReceiver(context, () -> {
            if (mCurSubscriptionId != SUB_ID_NULL) {
                // When the radio changes (ex: CDMA->GSM), refresh the fragment.
                // This is very rare.
                LOG.d("Radio change (i.e. CDMA->GSM) received for valid subscription id: "
                        + mCurSubscriptionId);
                updateReceived(mCurSubscriptionId);
            }
        });
    }

    /**
     * Registers a listener that will receive necessary updates to changes in the mobile network.
     */
    public void registerListener(MobileNetworkUpdateListener listener) {
        mListeners.add(listener);
    }

    /**
     * Unregisters a listener that was previously added via
     * {@link MobileNetworkUpdateManager#registerListener(MobileNetworkUpdateListener)}. The
     * provided argument must refer to the same object that was registered in order to securely be
     * unregistered.
     */
    public void unregisterListener(MobileNetworkUpdateListener listener) {
        mListeners.remove(listener);
    }

    @Override
    public void onCreate(@NonNull LifecycleOwner owner) {
        updateSubscriptions(/* forceRefresh= */ true);
    }

    @Override
    public final void onStart(@NonNull LifecycleOwner owner) {
        mPhoneChangeReceiver.register();
        mSubscriptionManager.addOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);
    }

    @Override
    public final void onStop(@NonNull LifecycleOwner owner) {
        mPhoneChangeReceiver.unregister();
        mSubscriptionManager.removeOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);
    }

    private void updateSubscriptions(boolean forceRefresh) {
        LOG.d("updateSubscriptions called");
        mSubscriptionInfos = mSubscriptionManager.getActiveSubscriptionInfoList();
        int subId = getSubscriptionId();
        if (forceRefresh || mCurSubscriptionId != subId) {
            LOG.d("updateSubscriptions updated subscription id! prev: " + mCurSubscriptionId
                    + " new: " + subId);
            mCurSubscriptionId = subId;
            updateReceived(mCurSubscriptionId);
        }
    }

    private void updateReceived(int subId) {
        for (MobileNetworkUpdateListener listener : mListeners) {
            listener.onMobileNetworkUpdated(subId);
        }
    }

    private int getSubscriptionId() {
        SubscriptionInfo subscription = getSubscription();
        return subscription != null ? subscription.getSubscriptionId()
                : SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    /**
     * First, find a subscription with the id provided at construction if it exists. If not, just
     * return the first one in the mSubscriptionInfos list since it is already sorted by sim slot.
     */
    private SubscriptionInfo getSubscription() {
        if (mCurSubscriptionId != SUB_ID_NULL) {
            for (SubscriptionInfo subscriptionInfo :
                    mSubscriptionManager.getSelectableSubscriptionInfoList()) {
                if (subscriptionInfo.getSubscriptionId() == mCurSubscriptionId) {
                    return subscriptionInfo;
                }
            }
        }

        return CollectionUtils.isEmpty(mSubscriptionInfos) ? null : mSubscriptionInfos.get(0);
    }

    /**
     * Interface used by components listening to subscription id updates from {@link
     * MobileNetworkUpdateManager}.
     */
    public interface MobileNetworkUpdateListener {
        /** Called when there is a new subscription id that other components should be aware of. */
        void onMobileNetworkUpdated(int subId);
    }

    /** Broadcast receiver which observes changes in radio technology (i.e. CDMA vs GSM). */
    private static class PhoneChangeReceiver extends BroadcastReceiver {
        private static final IntentFilter RADIO_TECHNOLOGY_CHANGED_FILTER = new IntentFilter(
                TelephonyIntents.ACTION_RADIO_TECHNOLOGY_CHANGED);

        private Context mContext;
        private PhoneChangeReceiver.OnChangeAction mOnChangeAction;

        /** Action to take when receiver receives a non sticky broadcast intent. */
        private interface OnChangeAction {
            void onReceive();
        }

        PhoneChangeReceiver(Context context, PhoneChangeReceiver.OnChangeAction onChangeAction) {
            mContext = context;
            mOnChangeAction = onChangeAction;
        }

        void register() {
            mContext.registerReceiver(this, RADIO_TECHNOLOGY_CHANGED_FILTER);
        }

        void unregister() {
            mContext.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!isInitialStickyBroadcast()) {
                mOnChangeAction.onReceive();
            }
        }
    }
}
