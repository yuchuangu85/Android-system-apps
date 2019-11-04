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
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.net.ConnectivityManager;
import android.os.UserManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

import java.util.List;

/** Controls the preference for accessing mobile network settings. */
public class MobileNetworkEntryPreferenceController extends
        PreferenceController<Preference> implements
        SubscriptionsChangeListener.SubscriptionsChangeAction {

    private final CarUserManagerHelper mCarUserManagerHelper;
    private final SubscriptionsChangeListener mChangeListener;
    private final SubscriptionManager mSubscriptionManager;
    private final ConnectivityManager mConnectivityManager;
    private final TelephonyManager mTelephonyManager;

    public MobileNetworkEntryPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mCarUserManagerHelper = new CarUserManagerHelper(context);
        mChangeListener = new SubscriptionsChangeListener(context, /* action= */ this);
        mSubscriptionManager = context.getSystemService(SubscriptionManager.class);
        mConnectivityManager = context.getSystemService(ConnectivityManager.class);
        mTelephonyManager = context.getSystemService(TelephonyManager.class);
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    protected void onStartInternal() {
        mChangeListener.start();
    }

    @Override
    protected void onStopInternal() {
        mChangeListener.stop();
    }

    @Override
    protected int getAvailabilityStatus() {
        if (!NetworkUtils.hasMobileNetwork(mConnectivityManager)) {
            return UNSUPPORTED_ON_DEVICE;
        }

        boolean isNotAdmin = !mCarUserManagerHelper.getCurrentProcessUserInfo().isAdmin();
        boolean hasRestriction = mCarUserManagerHelper.isCurrentProcessUserHasRestriction(
                UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS);
        if (isNotAdmin || hasRestriction) {
            return DISABLED_FOR_USER;
        }
        return AVAILABLE;
    }

    @Override
    protected void updateState(Preference preference) {
        List<SubscriptionInfo> subs = SubscriptionUtils.getAvailableSubscriptions(
                mSubscriptionManager, mTelephonyManager);
        preference.setEnabled(!subs.isEmpty());
        preference.setSummary(getSummary(subs));
    }

    @Override
    protected boolean handlePreferenceClicked(Preference preference) {
        List<SubscriptionInfo> subs = SubscriptionUtils.getAvailableSubscriptions(
                mSubscriptionManager, mTelephonyManager);
        if (subs.isEmpty()) {
            return true;
        }

        if (subs.size() == 1) {
            getFragmentController().launchFragment(
                    MobileNetworkFragment.newInstance(subs.get(0).getSubscriptionId()));
        } else {
            getFragmentController().launchFragment(new MobileNetworkListFragment());
        }
        return true;
    }

    @Override
    public void onSubscriptionsChanged() {
        refreshUi();
    }

    private CharSequence getSummary(List<SubscriptionInfo> subs) {
        int count = subs.size();
        if (subs.isEmpty()) {
            return null;
        } else if (count == 1) {
            return subs.get(0).getDisplayName();
        } else {
            return getContext().getResources().getQuantityString(
                    R.plurals.mobile_network_summary_count, count, count);
        }
    }
}
