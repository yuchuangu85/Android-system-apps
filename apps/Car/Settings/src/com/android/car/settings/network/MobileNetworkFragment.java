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

import android.content.Context;
import android.os.Bundle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.widget.TextView;

import androidx.annotation.VisibleForTesting;
import androidx.annotation.XmlRes;

import com.android.car.settings.R;
import com.android.car.settings.common.SettingsFragment;
import com.android.internal.util.CollectionUtils;

import com.google.android.collect.Lists;

import java.util.List;

/** Mobile network settings homepage. */
public class MobileNetworkFragment extends SettingsFragment implements
        MobileNetworkUpdateManager.MobileNetworkUpdateListener {

    @VisibleForTesting
    static final String ARG_NETWORK_SUB_ID = "network_sub_id";

    private SubscriptionManager mSubscriptionManager;
    private MobileNetworkUpdateManager mMobileNetworkUpdateManager;
    private CharSequence mTitle;

    /**
     * Creates a new instance of the {@link MobileNetworkFragment}, which shows settings related to
     * the given {@code subId}.
     */
    public static MobileNetworkFragment newInstance(int subId) {
        MobileNetworkFragment fragment = new MobileNetworkFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_NETWORK_SUB_ID, subId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.mobile_network_fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mSubscriptionManager = context.getSystemService(SubscriptionManager.class);

        int subId = getArguments() != null
                ? getArguments().getInt(ARG_NETWORK_SUB_ID, MobileNetworkUpdateManager.SUB_ID_NULL)
                : MobileNetworkUpdateManager.SUB_ID_NULL;
        mMobileNetworkUpdateManager = new MobileNetworkUpdateManager(context, subId);
        getLifecycle().addObserver(mMobileNetworkUpdateManager);

        List<MobileNetworkUpdateManager.MobileNetworkUpdateListener> listeners =
                Lists.newArrayList(
                        this,
                        use(MobileDataTogglePreferenceController.class,
                                R.string.pk_mobile_data_toggle),
                        use(RoamingPreferenceController.class, R.string.pk_mobile_roaming_toggle));
        for (MobileNetworkUpdateManager.MobileNetworkUpdateListener listener : listeners) {
            mMobileNetworkUpdateManager.registerListener(listener);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (mTitle != null) {
            TextView titleView = requireActivity().findViewById(R.id.title);
            titleView.setText(mTitle);
        }
    }

    @Override
    public void onMobileNetworkUpdated(int subId) {
        SubscriptionInfo info = null;

        if (subId != MobileNetworkUpdateManager.SUB_ID_NULL) {
            for (SubscriptionInfo subscriptionInfo :
                    mSubscriptionManager.getSelectableSubscriptionInfoList()) {
                if (subscriptionInfo.getSubscriptionId() == subId) {
                    info = subscriptionInfo;
                }
            }
        }

        if (info == null && !CollectionUtils.isEmpty(
                mSubscriptionManager.getActiveSubscriptionInfoList())) {
            info = mSubscriptionManager.getActiveSubscriptionInfoList().get(0);
        }

        if (info != null) {
            TextView titleView = requireActivity().findViewById(R.id.title);

            // It is possible for this to be called before the activity is fully created. If so,
            // cache the value so that it can be constructed onActivityCreated.
            mTitle = info.getDisplayName();
            if (titleView != null) {
                titleView.setText(mTitle);
            }
        }
    }
}
