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

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

import java.util.List;

/** Business logic to populate the list of available mobile networks. */
public class MobileNetworkListPreferenceController extends
        PreferenceController<PreferenceGroup> implements
        SubscriptionsChangeListener.SubscriptionsChangeAction {

    private final SubscriptionsChangeListener mSubscriptionsChangeListener;
    private final SubscriptionManager mSubscriptionManager;
    private final TelephonyManager mTelephonyManager;

    public MobileNetworkListPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);

        mSubscriptionsChangeListener = new SubscriptionsChangeListener(context, /* action= */ this);
        mSubscriptionManager = context.getSystemService(SubscriptionManager.class);
        mTelephonyManager = context.getSystemService(TelephonyManager.class);
    }

    @Override
    protected Class<PreferenceGroup> getPreferenceType() {
        return PreferenceGroup.class;
    }

    @Override
    protected void onStartInternal() {
        mSubscriptionsChangeListener.start();
    }

    @Override
    protected void onStopInternal() {
        mSubscriptionsChangeListener.stop();
    }

    @Override
    protected void updateState(PreferenceGroup preferenceGroup) {
        preferenceGroup.removeAll();

        List<SubscriptionInfo> subscriptions = SubscriptionUtils.getAvailableSubscriptions(
                mSubscriptionManager, mTelephonyManager);
        for (SubscriptionInfo info : subscriptions) {
            preferenceGroup.addPreference(createPreference(info));
        }
    }

    @Override
    public void onSubscriptionsChanged() {
        refreshUi();
    }

    private Preference createPreference(SubscriptionInfo info) {
        Preference preference = new Preference(getContext());
        preference.setTitle(info.getDisplayName());
        preference.setKey(Integer.toString(info.getSubscriptionId()));

        boolean isEsim = info.isEmbedded();
        if (mSubscriptionManager.isActiveSubscriptionId(info.getSubscriptionId())) {
            preference.setSummary(isEsim ? R.string.mobile_network_active_esim
                    : R.string.mobile_network_active_sim);
        } else {
            preference.setSummary(isEsim ? R.string.mobile_network_inactive_esim
                    : R.string.mobile_network_inactive_sim);
        }

        preference.setOnPreferenceClickListener(pref -> {
            MobileNetworkFragment fragment = MobileNetworkFragment.newInstance(
                    info.getSubscriptionId());
            getFragmentController().launchFragment(fragment);
            return true;
        });

        return preference;
    }
}
