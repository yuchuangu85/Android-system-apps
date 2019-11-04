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

package com.android.car.settings.system;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;

import androidx.preference.ListPreference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller which determines if a network selection option is visible. On devices with multiple
 * network subscriptions, a user may select the network to reset.
 */
public class ResetNetworkSubscriptionPreferenceController extends
        PreferenceController<ListPreference> {

    private final SubscriptionManager mSubscriptionManager;

    public ResetNetworkSubscriptionPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mSubscriptionManager = (SubscriptionManager) context.getSystemService(
                Context.TELEPHONY_SUBSCRIPTION_SERVICE);
    }

    @Override
    protected Class<ListPreference> getPreferenceType() {
        return ListPreference.class;
    }

    @Override
    public int getAvailabilityStatus() {
        return getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
                ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    protected void updateState(ListPreference preference) {
        List<SubscriptionInfo> subscriptions = mSubscriptionManager.getActiveSubscriptionInfoList();
        if (subscriptions == null || subscriptions.isEmpty()) {
            // No subscriptions to reset.
            preference.setValue(String.valueOf(SubscriptionManager.INVALID_SUBSCRIPTION_ID));
            preference.setVisible(false);
            return;
        }
        if (subscriptions.size() == 1) {
            // Only one subscription, so nothing else to select. Use it and hide the preference.
            preference.setValue(String.valueOf(subscriptions.get(0).getSubscriptionId()));
            preference.setVisible(false);
            return;
        }

        int defaultSubscriptionId = getDefaultSubscriptionId();
        int selectedIndex = 0;
        int size = subscriptions.size();
        List<String> subscriptionNames = new ArrayList<>(size);
        List<String> subscriptionIds = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            SubscriptionInfo subscription = subscriptions.get(i);
            int subscriptionId = subscription.getSubscriptionId();
            if (subscriptionId == defaultSubscriptionId) {
                // Set the default as the first selected value.
                selectedIndex = i;
            }
            subscriptionNames.add(getSubscriptionName(subscription));
            subscriptionIds.add(String.valueOf(subscriptionId));
        }

        preference.setEntries(toCharSequenceArray(subscriptionNames));
        preference.setEntryValues(toCharSequenceArray(subscriptionIds));
        preference.setTitle(subscriptionNames.get(selectedIndex));
        preference.setValueIndex(selectedIndex);
    }

    @Override
    protected boolean handlePreferenceChanged(ListPreference preference, Object newValue) {
        String subscriptionIdStr = (String) newValue;
        int index = preference.findIndexOfValue(subscriptionIdStr);
        CharSequence subscriptionName = preference.getEntries()[index];
        preference.setTitle(subscriptionName);
        return true;
    }

    /**
     * Returns the default subscription id in the order of data, voice, sms, system subscription.
     */
    private int getDefaultSubscriptionId() {
        int defaultSubscriptionId = SubscriptionManager.getDefaultDataSubscriptionId();
        if (!SubscriptionManager.isUsableSubIdValue(defaultSubscriptionId)) {
            defaultSubscriptionId = SubscriptionManager.getDefaultVoiceSubscriptionId();
        }
        if (!SubscriptionManager.isUsableSubIdValue(defaultSubscriptionId)) {
            defaultSubscriptionId = SubscriptionManager.getDefaultSmsSubscriptionId();
        }
        if (!SubscriptionManager.isUsableSubIdValue(defaultSubscriptionId)) {
            defaultSubscriptionId = SubscriptionManager.getDefaultSubscriptionId();
        }
        return defaultSubscriptionId;
    }

    /**
     * Returns the subscription display name falling back to the number, the carrier, and then
     * network id codes.
     */
    private String getSubscriptionName(SubscriptionInfo subscription) {
        String name = subscription.getDisplayName().toString();
        if (TextUtils.isEmpty(name)) {
            name = subscription.getNumber();
        }
        if (TextUtils.isEmpty(name)) {
            name = subscription.getCarrierName().toString();
        }
        if (TextUtils.isEmpty(name)) {
            name = getContext().getString(R.string.reset_network_fallback_subscription_name,
                    subscription.getMcc(), subscription.getMnc(), subscription.getSimSlotIndex(),
                    subscription.getSubscriptionId());
        }
        return name;
    }

    private CharSequence[] toCharSequenceArray(List<String> list) {
        CharSequence[] array = new CharSequence[list.size()];
        list.toArray(array);
        return array;
    }
}
